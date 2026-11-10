/* Dashboard: reads Telemetry lines ("[t=12.3] key=value ...") live over USB serial (Web Serial,
   Chrome/Edge) or from a log file, then graphs numbers, lists values and draws poses on the field. */
(function () {
  'use strict';
  var root = document.getElementById('dashboard');
  if (!root) return;

  var $ = function (id) { return document.getElementById(id); };
  var els = {
    connect: $('db-connect'), file: $('db-file'), sample: $('db-sample-btn'), clear: $('db-clear'),
    status: $('db-status'), play: $('db-play'), slider: $('db-slider'), time: $('db-time'),
    keys: $('db-keys'), values: $('db-values'), graph: $('db-graph'), field: $('db-field'),
    console: $('db-console'), serialNote: $('db-serial-note'), window: $('db-window')
  };

  var FIELD_HALF = 1.8288;          // m, half of 12 ft
  var ROBOT_SIZE = 0.381;           // 15 in
  var PALETTE_VARS = ['--accent', '--signal', '--red', '--ok', '--tk-type', '--slow'];

  // ---------------------------------------------------------------- data
  var data;
  function resetData() {
    data = {
      series: {},     // key -> { t: [], v: [] } numbers (booleans as 0/1)
      text: {},       // key -> { t: [], s: [] } raw strings, for the values table
      poses: {},      // key -> [{ t, x, y, deg }]
      kinds: {},      // key -> 'number' | 'bool' | 'pose' | 'text'
      tMin: Infinity, tMax: -Infinity,
      lines: []
    };
    selected = null;
    cursor = 0;
  }
  var selected = null;   // keys shown on the graph
  var cursor = 0;        // time shown (replay) or latest (live)
  var live = false;
  var playing = false;
  var lastFrame = 0;

  function pushConsole(line) {
    data.lines.push(line);
    if (data.lines.length > 400) data.lines.shift();
  }

  var LINE_RE = /^\[t=(-?[\d.]+)\]\s*(.*)$/;
  var POSE_RE = /^(-?[\d.]+(?:e-?\d+)?),(-?[\d.]+(?:e-?\d+)?),(-?[\d.]+(?:e-?\d+)?)$/;

  function parseLine(raw) {
    var line = raw.replace(/\r$/, '');
    if (!line) return;
    var m = LINE_RE.exec(line);
    if (!m) { pushConsole(line); return; }
    var t = parseFloat(m[1]);
    if (t < data.tMax - 1) {           // time went backwards: the program restarted
      pushConsole('--- program restarted ---');
      var lines = data.lines; resetData(); data.lines = lines;
    }
    data.tMin = Math.min(data.tMin, t);
    data.tMax = Math.max(data.tMax, t);
    if (live && ++trimCounter % 500 === 0) trimOld(t - 300); // keep the last 5 minutes while live
    m[2].split(/\s+/).forEach(function (tok) {
      var eq = tok.indexOf('=');
      if (eq <= 0) return;
      var key = tok.slice(0, eq), val = tok.slice(eq + 1);
      var text = data.text[key] || (data.text[key] = { t: [], s: [] });
      text.t.push(t); text.s.push(val);
      var p = POSE_RE.exec(val);
      if (p) {
        data.kinds[key] = 'pose';
        (data.poses[key] || (data.poses[key] = [])).push({ t: t, x: +p[1], y: +p[2], deg: +p[3] });
        return;
      }
      var num = val === 'true' ? 1 : val === 'false' ? 0 : Number(val);
      if (val !== '' && !isNaN(num)) {
        if (!data.kinds[key]) data.kinds[key] = (val === 'true' || val === 'false') ? 'bool' : 'number';
        var s = data.series[key] || (data.series[key] = { t: [], v: [] });
        s.t.push(t); s.v.push(num);
      } else {
        data.kinds[key] = 'text';
      }
    });
  }

  var trimCounter = 0;
  function trimOld(before) {
    function cut(obj, fields) {
      var n = 0;
      while (n < obj.t.length && obj.t[n] < before) n++;
      if (n) fields.forEach(function (f) { obj[f].splice(0, n); });
    }
    Object.keys(data.series).forEach(function (k) { cut(data.series[k], ['t', 'v']); });
    Object.keys(data.text).forEach(function (k) { cut(data.text[k], ['t', 's']); });
    Object.keys(data.poses).forEach(function (k) {
      var l = data.poses[k], n = 0;
      while (n < l.length && l[n].t < before) n++;
      l.splice(0, n);
    });
    data.tMin = before;
  }

  // Last index with t <= time (binary search), or -1.
  function indexAt(ts, time) {
    var lo = 0, hi = ts.length - 1, ans = -1;
    while (lo <= hi) {
      var mid = (lo + hi) >> 1;
      if (ts[mid] <= time) { ans = mid; lo = mid + 1; } else hi = mid - 1;
    }
    return ans;
  }

  // ---------------------------------------------------------------- live serial
  var port = null, reader = null;
  if (!('serial' in navigator)) {
    els.connect.disabled = true;
    els.serialNote.hidden = false;
  }
  els.connect.addEventListener('click', function () {
    if (port) { disconnect(); return; }
    navigator.serial.requestPort().then(function (p) {
      port = p;
      return port.open({ baudRate: 115200 });
    }).then(function () {
      resetData();
      live = true; playing = false;
      els.connect.textContent = 'Disconnect';
      setStatus('Connected. Waiting for telemetry…', 'ok');
      readLoop();
    }).catch(function (e) {
      port = null;
      if (e && e.name === 'NotFoundError') return; // user closed the picker
      setStatus('Couldn\'t open the port: ' + (e && e.message ? e.message : e), 'err');
    });
  });

  function readLoop() {
    var decoder = new TextDecoderStream();
    port.readable.pipeTo(decoder.writable).catch(function () {});
    reader = decoder.readable.getReader();
    var buf = '';
    (function pump() {
      reader.read().then(function (r) {
        if (r.done) return;
        buf += r.value;
        var nl;
        while ((nl = buf.indexOf('\n')) >= 0) {
          parseLine(buf.slice(0, nl));
          buf = buf.slice(nl + 1);
        }
        if (data.tMax > -Infinity) {
          cursor = data.tMax;
          setStatus('Live · ' + Object.keys(data.kinds).length + ' values', 'ok');
        }
        pump();
      }).catch(function (e) {
        setStatus('Disconnected: ' + (e && e.message ? e.message : e), 'warn');
        disconnect();
      });
    })();
  }

  function disconnect() {
    var p = port; port = null;
    els.connect.textContent = 'Connect to robot';
    live = false;
    if (reader) { reader.cancel().catch(function () {}); reader = null; }
    if (p) setTimeout(function () { p.close().catch(function () {}); }, 50);
    syncSlider();
  }

  // ---------------------------------------------------------------- files
  function loadText(text, label) {
    if (port) disconnect();
    resetData();
    text.split('\n').forEach(parseLine);
    live = false;
    if (data.tMax === -Infinity) {
      setStatus(label + ': no telemetry lines found. Lines look like "[t=1.0] key=value".', 'warn');
    } else {
      cursor = data.tMin;
      setStatus(label + ' · ' + (data.tMax - data.tMin).toFixed(1) + ' s · press Play', 'ok');
    }
    syncSlider();
  }
  els.file.addEventListener('change', function () {
    var f = els.file.files && els.file.files[0];
    if (!f) return;
    var r = new FileReader();
    r.onload = function () { loadText(String(r.result), f.name); };
    r.readAsText(f);
    els.file.value = '';
  });
  els.sample.addEventListener('click', function () {
    var s = document.getElementById('db-sample');
    loadText(s ? s.textContent : '', 'Sample: template robot in the simulator');
    play();
  });
  els.clear.addEventListener('click', function () {
    resetData(); syncSlider();
    setStatus(port ? 'Cleared. Still connected.' : 'Cleared.', port ? 'ok' : '');
  });

  // ---------------------------------------------------------------- replay controls
  function syncSlider() {
    var has = data.tMax > -Infinity;
    els.slider.disabled = live || !has;
    els.play.disabled = live || !has;
    if (has) {
      els.slider.min = data.tMin; els.slider.max = data.tMax; els.slider.step = 0.01;
      els.slider.value = cursor;
    }
  }
  function play() {
    if (data.tMax === -Infinity) return;
    if (cursor >= data.tMax) cursor = data.tMin;
    playing = true; lastFrame = 0;
    els.play.textContent = 'Pause';
  }
  function pause() { playing = false; els.play.textContent = 'Play'; }
  els.play.addEventListener('click', function () { if (playing) pause(); else play(); });
  els.slider.addEventListener('input', function () { pause(); cursor = parseFloat(els.slider.value); });

  function setStatus(msg, kind) {
    els.status.textContent = msg;
    els.status.dataset.kind = kind || '';
  }

  // ---------------------------------------------------------------- drawing
  function css(name) { return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || '#888'; }
  function colorFor(i) { return css(PALETTE_VARS[i % PALETTE_VARS.length]); }

  function fitCanvas(c) {
    var dpr = window.devicePixelRatio || 1;
    var w = c.clientWidth, h = c.clientHeight;
    if (c.width !== Math.round(w * dpr) || c.height !== Math.round(h * dpr)) {
      c.width = Math.round(w * dpr); c.height = Math.round(h * dpr);
    }
    var g = c.getContext('2d');
    g.setTransform(dpr, 0, 0, dpr, 0, 0);
    return { g: g, w: w, h: h };
  }

  function numericKeys() {
    return Object.keys(data.series).filter(function (k) { return data.kinds[k] !== 'pose'; }).sort();
  }

  var keysSig = '';
  function renderKeys() {
    var keys = numericKeys();
    if (selected === null && keys.length) {
      selected = {};
      keys.slice(0, 3).forEach(function (k) { selected[k] = true; });
    }
    var sig = keys.join('|') + '#' + JSON.stringify(selected);
    if (sig === keysSig) return;
    keysSig = sig;
    els.keys.innerHTML = '';
    if (!keys.length) {
      els.keys.innerHTML = '<p class="pl-hint">Numbers you send with <code>Telemetry.put</code> show up here.</p>';
      return;
    }
    keys.forEach(function (k, i) {
      var label = document.createElement('label');
      label.className = 'db-key';
      var box = document.createElement('input');
      box.type = 'checkbox'; box.checked = !!(selected && selected[k]);
      box.addEventListener('change', function () { selected[k] = box.checked; });
      var sw = document.createElement('span');
      sw.className = 'db-swatch';
      sw.style.background = colorFor(keys.indexOf(k));
      label.appendChild(box); label.appendChild(sw);
      label.appendChild(document.createTextNode(k));
      els.keys.appendChild(label);
    });
  }

  function renderValues() {
    var keys = Object.keys(data.text).sort();
    var rows = keys.map(function (k) {
      var tx = data.text[k], i = indexAt(tx.t, cursor);
      var v = i >= 0 ? tx.s[i] : '—';
      if (data.kinds[k] === 'pose' && i >= 0) {
        var p = v.split(',');
        v = 'x ' + (+p[0]).toFixed(2) + ' m, y ' + (+p[1]).toFixed(2) + ' m, ' + (+p[2]).toFixed(0) + '°';
      }
      return '<tr><th scope="row">' + esc(k) + '</th><td>' + esc(v) + '</td></tr>';
    });
    var html = rows.length ? '<table><tbody>' + rows.join('') + '</tbody></table>'
      : '<p class="pl-hint">No values yet.</p>';
    if (els.values.innerHTML !== html) els.values.innerHTML = html;
  }
  function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }

  function renderGraph() {
    var f = fitCanvas(els.graph), g = f.g, w = f.w, h = f.h;
    g.clearRect(0, 0, w, h);
    var span = parseFloat(els.window.value) || 20;
    // Window ends at the cursor, but never starts before the first sample (so early data fills from the left).
    var t0 = data.tMax === -Infinity ? 0 : Math.max(data.tMin, cursor - span);
    var tEnd = cursor; // data after the cursor isn't shown (replay)
    var keys = numericKeys().filter(function (k) { return selected && selected[k]; });
    var lo = Infinity, hi = -Infinity;
    keys.forEach(function (k) {
      var s = data.series[k], a = Math.max(0, indexAt(s.t, t0)), b = indexAt(s.t, tEnd);
      for (var i = a; i <= b; i++) { lo = Math.min(lo, s.v[i]); hi = Math.max(hi, s.v[i]); }
    });
    if (lo === Infinity) { lo = -1; hi = 1; }
    if (hi - lo < 1e-6) { lo -= 1; hi += 1; }
    var pad = (hi - lo) * 0.08; lo -= pad; hi += pad;
    var L = 46, R = 10, T = 10, B = 22;
    var X = function (t) { return L + (t - t0) / span * (w - L - R); };
    var Y = function (v) { return T + (hi - v) / (hi - lo) * (h - T - B); };
    // grid
    g.strokeStyle = css('--line'); g.lineWidth = 1; g.fillStyle = css('--muted');
    g.font = '11px ' + css('--font-mono');
    g.textAlign = 'right'; g.textBaseline = 'middle';
    for (var i = 0; i <= 4; i++) {
      var v = lo + (hi - lo) * i / 4, y = Y(v);
      g.beginPath(); g.moveTo(L, y); g.lineTo(w - R, y); g.stroke();
      g.fillText(fmt(v), L - 6, y);
    }
    g.textAlign = 'center'; g.textBaseline = 'top';
    var step = span <= 10 ? 2 : span <= 30 ? 5 : 10;
    for (var tt = Math.ceil(t0 / step) * step; tt <= t0 + span; tt += step) {
      if (tt < 0) continue;
      g.fillText(tt.toFixed(0) + 's', X(tt), h - B + 6);
    }
    // series
    var all = numericKeys();
    keys.forEach(function (k) {
      var s = data.series[k], a = Math.max(0, indexAt(s.t, t0)), b = indexAt(s.t, tEnd);
      if (b < 0) return;
      g.strokeStyle = colorFor(all.indexOf(k)); g.lineWidth = 2;
      g.beginPath();
      for (var i = a; i <= b; i++) {
        var px = X(s.t[i]), py = Y(s.v[i]);
        if (i === a) g.moveTo(px, py); else g.lineTo(px, py);
      }
      g.stroke();
    });
    if (!keys.length) {
      g.fillStyle = css('--muted'); g.textAlign = 'center'; g.textBaseline = 'middle';
      g.font = '13px ' + css('--font-body');
      g.fillText(data.tMax === -Infinity ? 'Connect to a robot or open a log' : 'Tick values on the left to graph them', w / 2, h / 2);
    }
  }
  function fmt(v) {
    var a = Math.abs(v);
    return a >= 100 ? v.toFixed(0) : a >= 10 ? v.toFixed(1) : v.toFixed(2);
  }

  function renderField() {
    var f = fitCanvas(els.field), g = f.g, size = Math.min(f.w, f.h);
    var ox = (f.w - size) / 2, oy = (f.h - size) / 2;
    g.clearRect(0, 0, f.w, f.h);
    var px = function (x) { return ox + (x + FIELD_HALF) / (2 * FIELD_HALF) * size; };
    var py = function (y) { return oy + (FIELD_HALF - y) / (2 * FIELD_HALF) * size; };
    var tile = size / 6;
    for (var r = 0; r < 6; r++) for (var c = 0; c < 6; c++) {
      g.fillStyle = (r + c) % 2 ? css('--tile-a') : css('--tile-b');
      g.fillRect(ox + c * tile, oy + r * tile, tile, tile);
    }
    g.strokeStyle = css('--wall'); g.lineWidth = 3; g.strokeRect(ox + 1.5, oy + 1.5, size - 3, size - 3);
    g.fillStyle = css('--muted'); g.font = '11px ' + css('--font-mono');
    g.textAlign = 'left'; g.textBaseline = 'top';
    g.fillText('+X →', px(1.3), py(-0.05) + 4);
    var keys = Object.keys(data.poses).sort();
    keys.forEach(function (k, n) {
      var list = data.poses[k], i = indexAt(list.map(function (p) { return p.t; }), cursor);
      if (i < 0) return;
      var color = colorFor(n);
      // trail: last 15 s
      g.strokeStyle = color; g.globalAlpha = 0.55; g.lineWidth = 2; g.beginPath();
      var started = false, prev = null;
      for (var j = 0; j <= i; j++) {
        if (list[j].t < cursor - 15) continue;
        var X = px(list[j].x), Y = py(list[j].y);
        // A jump of more than 0.3 m between samples is a pose reset, not motion: don't connect it.
        var jump = prev && Math.hypot(list[j].x - prev.x, list[j].y - prev.y) > 0.3;
        if (!started || jump) { g.moveTo(X, Y); started = true; } else g.lineTo(X, Y);
        prev = list[j];
      }
      g.stroke(); g.globalAlpha = 1;
      // robot
      var p = list[i], rad = p.deg * Math.PI / 180, half = ROBOT_SIZE / 2 / (2 * FIELD_HALF) * size;
      g.save(); g.translate(px(p.x), py(p.y)); g.rotate(-rad);
      g.fillStyle = color; g.globalAlpha = 0.85; g.fillRect(-half, -half, 2 * half, 2 * half);
      g.globalAlpha = 1; g.strokeStyle = css('--ink'); g.lineWidth = 1.5; g.strokeRect(-half, -half, 2 * half, 2 * half);
      g.beginPath(); g.moveTo(0, 0); g.lineTo(half * 1.25, 0); g.lineWidth = 3; g.strokeStyle = css('--surface'); g.stroke();
      g.restore();
      g.fillStyle = css('--ink'); g.textAlign = 'left'; g.textBaseline = 'bottom';
      g.fillText(k, px(p.x) + half + 4, py(p.y) - half);
    });
    if (!keys.length) {
      g.fillStyle = css('--ink'); g.globalAlpha = 0.7; g.textAlign = 'center'; g.textBaseline = 'middle';
      g.font = '13px ' + css('--font-body');
      g.fillText('Poses from Telemetry.putPose appear here', ox + size / 2, oy + size / 2);
      g.globalAlpha = 1;
    }
  }

  var consoleCount = -1;
  function renderConsole() {
    if (data.lines.length === consoleCount) return;
    consoleCount = data.lines.length;
    els.console.textContent = data.lines.slice(-200).join('\n') || 'Other output (prints, errors) shows here.';
    els.console.scrollTop = els.console.scrollHeight;
  }

  function frame(ts) {
    requestAnimationFrame(frame);
    if (root.hidden) return;
    if (playing) {
      if (lastFrame) cursor += (ts - lastFrame) / 1000;
      lastFrame = ts;
      if (cursor >= data.tMax) { cursor = data.tMax; pause(); }
      els.slider.value = cursor;
    }
    els.time.textContent = data.tMax === -Infinity ? '—' : 't = ' + cursor.toFixed(2) + ' s';
    renderKeys(); renderValues(); renderGraph(); renderField(); renderConsole();
  }

  resetData();
  syncSlider();
  requestAnimationFrame(frame);
})();
