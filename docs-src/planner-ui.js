/* Path planner UI: project list, field editor, inspector, settings, export. Uses PlannerModel. */
(function () {
  'use strict';
  var M = window.PlannerModel;
  var H = M.HALF_FIELD, INCH = M.INCH;
  var STORE = 'vexPlanner.project.v2', PREFS = 'vexPlanner.prefs.v2';

  var $ = function (id) { return document.getElementById(id); };
  var canvas = $('pl-field');
  if (!canvas) return;
  var ctx = canvas.getContext('2d');

  // ---------------------------------------------------------------- state
  function load(k) { try { var v = localStorage.getItem(k); return v ? JSON.parse(v) : null; } catch (e) { return null; } }
  function save(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) { /* storage unavailable */ } }

  var project;
  try { project = load(STORE) ? M.normalize(load(STORE)) : M.samplePlan(); } catch (e) { project = M.samplePlan(); }
  var prefs = Object.assign({ units: 'in', snap: 1, tab: 'java', pkg: 'com.robojacks.robot.autos', cls: 'PlannerAutos' }, load(PREFS) || {});
  var sel = { type: 'path', i: 0 };   // or { type: 'auto', i }
  var wp = -1;                        // selected waypoint in the selected path
  var undoStack = [];
  var playing = null, drag = null, size = 600, dpr = 1;
  var trajCache = {};

  function curPath() { return sel.type === 'path' ? project.paths[sel.i] || null : null; }
  function curAuto() { return sel.type === 'auto' ? project.autos[sel.i] || null : null; }
  function traj(p) {
    var key = JSON.stringify([p, project.constraints]);
    if (!trajCache[key]) trajCache[key] = M.generate(p, project.constraints);
    return trajCache[key];
  }

  function snapshot() {
    undoStack.push(JSON.stringify({ project: project, sel: sel, wp: wp }));
    if (undoStack.length > 80) undoStack.shift();
    $('pl-undo').disabled = false;
  }
  function undo() {
    if (!undoStack.length) return;
    var s = JSON.parse(undoStack.pop());
    project = s.project; sel = s.sel; wp = s.wp;
    $('pl-undo').disabled = !undoStack.length;
    syncSettings();
    changed(true);
  }
  function fixSelection() {
    var list = sel.type === 'path' ? project.paths : project.autos;
    if (!list.length) {
      if (project.paths.length) sel = { type: 'path', i: 0 };
      else if (project.autos.length) sel = { type: 'auto', i: 0 };
      else sel = { type: 'path', i: -1 };
    } else if (sel.i >= list.length) sel.i = list.length - 1;
    var p = curPath();
    if (!p || wp >= p.waypoints.length) wp = -1;
  }
  function changed(rebuild) {
    fixSelection();
    save(STORE, project);
    trajCache = Object.keys(trajCache).length > 60 ? {} : trajCache;
    renderProject();
    if (rebuild) { renderInspector(); renderCommands(); } else { syncInspector(); }
    renderOutput();
    draw();
  }

  // ---------------------------------------------------------------- units
  function toDisp(m) { return prefs.units === 'in' ? +(m / INCH).toFixed(1) : +m.toFixed(3); }
  function fromDisp(v) { return prefs.units === 'in' ? v * INCH : v; }
  function unit() { return prefs.units === 'in' ? 'in' : 'm'; }
  function snapM(v) { var st = prefs.snap * INCH; return st > 0 ? Math.round(v / st) * st : v; }
  function clampF(v) { return Math.max(-H, Math.min(H, v)); }
  function esc(s) { return String(s).replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }

  // ---------------------------------------------------------------- geometry
  function toPx(x, y) { return { x: (x + H) / (2 * H) * size, y: (H - y) / (2 * H) * size }; }
  function fromPx(px, py) { return { x: px / size * 2 * H - H, y: H - py / size * 2 * H }; }
  function mToPx(m) { return m / (2 * H) * size; }

  // ---------------------------------------------------------------- drawing
  var colors = {};
  function readColors() {
    var cs = getComputedStyle(document.documentElement);
    ['--tile-a', '--tile-b', '--tile-seam', '--wall', '--accent', '--accent-wash', '--red', '--signal', '--on-signal',
      '--ink', '--ink-wash', '--surface', '--muted', '--slow', '--fast', '--zone', '--font-mono'].forEach(function (k) { colors[k] = cs.getPropertyValue(k).trim(); });
  }
  function hexToRgb(h) {
    h = h.replace('#', '');
    if (h.length === 3) h = h.split('').map(function (c) { return c + c; }).join('');
    var n = parseInt(h, 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  }
  function speedColor(f) {
    var a = hexToRgb(colors['--slow'] || '#c8312f'), b = hexToRgb(colors['--fast'] || '#1f5fd1');
    f = Math.max(0, Math.min(1, f));
    return 'rgb(' + a.map(function (v, i) { return Math.round(v + (b[i] - v) * f); }).join(',') + ')';
  }

  function resize() {
    var wrap = canvas.parentElement;
    if (!wrap.clientWidth) return;
    size = Math.max(240, Math.min(wrap.clientWidth, 760));
    dpr = window.devicePixelRatio || 1;
    canvas.style.width = size + 'px';
    canvas.style.height = size + 'px';
    canvas.width = Math.round(size * dpr);
    canvas.height = Math.round(size * dpr);
    draw();
  }

  function drawField() {
    var n = 6, t = size / n;
    for (var r = 0; r < n; r++) for (var c = 0; c < n; c++) {
      ctx.fillStyle = (r + c) % 2 ? colors['--tile-a'] : colors['--tile-b'];
      ctx.fillRect(c * t, r * t, t + 0.5, t + 0.5);
    }
    ctx.strokeStyle = colors['--tile-seam']; ctx.lineWidth = 1;
    for (var k = 1; k < n; k++) {
      ctx.beginPath(); ctx.moveTo(k * t, 0); ctx.lineTo(k * t, size); ctx.stroke();
      ctx.beginPath(); ctx.moveTo(0, k * t); ctx.lineTo(size, k * t); ctx.stroke();
    }
    ctx.save();
    ctx.strokeStyle = colors['--muted']; ctx.setLineDash([3, 5]);
    ctx.beginPath(); ctx.moveTo(size / 2, 0); ctx.lineTo(size / 2, size); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(0, size / 2); ctx.lineTo(size, size / 2); ctx.stroke();
    ctx.restore();
    ctx.fillStyle = colors['--muted'];
    ctx.font = '600 11px ' + colors['--font-mono'];
    ctx.textAlign = 'right'; ctx.fillText('+X', size - 10, size / 2 - 7);
    ctx.textAlign = 'left'; ctx.fillText('+Y', size / 2 + 7, 18);
    ctx.fillText('0,0', size / 2 + 6, size / 2 + 15);
    ctx.strokeStyle = colors['--wall']; ctx.lineWidth = 6; ctx.strokeRect(3, 3, size - 6, size - 6);
  }

  function robot(x, y, h, fill, stroke, lw) {
    var c = M.corners(x, y, h, project.robot.width, project.robot.length).map(function (p) { return toPx(p.x, p.y); });
    ctx.save();
    ctx.beginPath(); ctx.moveTo(c[0].x, c[0].y);
    for (var i = 1; i < 4; i++) ctx.lineTo(c[i].x, c[i].y);
    ctx.closePath();
    if (fill) { ctx.fillStyle = fill; ctx.fill(); }
    ctx.strokeStyle = stroke; ctx.lineWidth = lw; ctx.stroke();
    ctx.beginPath(); ctx.moveTo(c[0].x, c[0].y); ctx.lineTo(c[1].x, c[1].y); ctx.lineWidth = lw + 2.5; ctx.stroke();
    ctx.restore();
  }

  function drawTraj(tr, path, faint) {
    var s = tr.states;
    if (s.length < 2) return;
    ctx.save();
    ctx.lineCap = 'round';
    if (path.reversed) ctx.setLineDash([8, 6]);
    if (faint) {
      ctx.globalAlpha = 0.35; ctx.strokeStyle = colors['--ink']; ctx.lineWidth = 2;
      ctx.beginPath();
      s.forEach(function (st, i) { var p = toPx(st.x, st.y); if (i) ctx.lineTo(p.x, p.y); else ctx.moveTo(p.x, p.y); });
      ctx.stroke();
    } else {
      ctx.lineWidth = 4;
      for (var i = 1; i < s.length; i++) {
        var a = toPx(s[i - 1].x, s[i - 1].y), b = toPx(s[i].x, s[i].y);
        ctx.strokeStyle = speedColor(s[i].frac);
        ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(b.x, b.y); ctx.stroke();
      }
    }
    ctx.restore();
  }

  function drawZones(path, tr) {
    (path.zones || []).forEach(function (z) {
      var lo = Math.min(z.start, z.end), hi = Math.max(z.start, z.end);
      ctx.save();
      ctx.strokeStyle = colors['--zone']; ctx.lineWidth = 14; ctx.lineCap = 'round';
      ctx.beginPath();
      var started = false;
      tr.points.forEach(function (q) {
        if (q.pos < lo || q.pos > hi) return;
        var p = toPx(q.x, q.y);
        if (started) ctx.lineTo(p.x, p.y); else { ctx.moveTo(p.x, p.y); started = true; }
      });
      ctx.stroke();
      ctx.restore();
    });
  }

  function drawMarkers(path, tr) {
    (path.markers || []).forEach(function (m) {
      var q = null;
      for (var i = 0; i < tr.points.length; i++) if (tr.points[i].pos >= m.pos) { q = tr.points[i]; break; }
      if (!q && tr.points.length) q = tr.points[tr.points.length - 1];
      if (!q) return;
      var p = toPx(q.x, q.y);
      ctx.save();
      ctx.fillStyle = colors['--signal']; ctx.strokeStyle = colors['--ink']; ctx.lineWidth = 1.5;
      ctx.beginPath(); ctx.moveTo(p.x, p.y - 8); ctx.lineTo(p.x + 8, p.y); ctx.lineTo(p.x, p.y + 8); ctx.lineTo(p.x - 8, p.y); ctx.closePath();
      ctx.fill(); ctx.stroke();
      ctx.font = '600 10px ' + colors['--font-mono']; ctx.textAlign = 'left';
      var label = m.name || '?', w = ctx.measureText(label).width + 8, lx = Math.min(p.x + 11, size - w - 6);
      ctx.fillStyle = colors['--signal']; ctx.fillRect(lx, p.y - 22, w, 15);
      ctx.fillStyle = colors['--on-signal']; ctx.fillText(label, lx + 4, p.y - 11);
      ctx.restore();
    });
  }

  function drawHandles(path) {
    path.waypoints.forEach(function (w, i) {
      var a = toPx(w.x, w.y), hs = M.handles(path, i);
      ctx.save();
      ['prev', 'next'].forEach(function (k) {
        if (!hs[k]) return;
        var h = toPx(hs[k].x, hs[k].y);
        ctx.strokeStyle = colors['--ink']; ctx.globalAlpha = 0.55; ctx.lineWidth = 1.5;
        ctx.beginPath(); ctx.moveTo(a.x, a.y); ctx.lineTo(h.x, h.y); ctx.stroke();
        ctx.globalAlpha = 1;
        ctx.fillStyle = colors['--surface']; ctx.strokeStyle = colors['--ink']; ctx.lineWidth = 2;
        ctx.fillRect(h.x - 5, h.y - 5, 10, 10); ctx.strokeRect(h.x - 5, h.y - 5, 10, 10);
      });
      var isSel = i === wp;
      ctx.beginPath(); ctx.arc(a.x, a.y, 11, 0, Math.PI * 2);
      ctx.fillStyle = isSel ? colors['--accent'] : colors['--surface']; ctx.fill();
      ctx.lineWidth = 2.5; ctx.strokeStyle = colors['--accent']; ctx.stroke();
      ctx.fillStyle = isSel ? colors['--surface'] : colors['--ink'];
      ctx.font = '700 11px ' + colors['--font-mono']; ctx.textAlign = 'center';
      ctx.fillText(String(i + 1), a.x, a.y + 4);
      ctx.restore();
    });
  }

  function draw() {
    if (!size) return;
    readColors();
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    drawField();
    var path = curPath(), auto = curAuto();
    if (path) {
      project.paths.forEach(function (p) { if (p !== path && p.waypoints.length > 1) drawTraj(traj(p), p, true); });
      if (path.waypoints.length > 1) {
        var tr = traj(path);
        drawZones(path, tr);
        drawTraj(tr, path, false);
        var e = M.endPose(path);
        if (!playing) robot(e.x, e.y, e.h, null, colors['--ink'], 1.5);
        var s = M.startPose(path);
        robot(s.x, s.y, s.h, colors['--accent-wash'], colors['--accent'], 2);
        drawMarkers(path, tr);
      }
      drawHandles(path);
    } else if (auto) {
      var tl = M.autoTimeline(project, auto), first = true;
      tl.segments.forEach(function (sg) {
        if (sg.kind === 'path') {
          drawTraj(sg.traj, sg.path, false);
          drawMarkers(sg.path, sg.traj);
          if (first) { var st = M.startPose(sg.path); robot(st.x, st.y, st.h, colors['--accent-wash'], colors['--accent'], 2); first = false; }
        } else if (sg.kind === 'turn') {
          var p = toPx(sg.to.x, sg.to.y), L = mToPx(0.28);
          ctx.save(); ctx.strokeStyle = colors['--signal']; ctx.lineWidth = 3;
          ctx.beginPath(); ctx.moveTo(p.x, p.y); ctx.lineTo(p.x + L * Math.cos(sg.to.h), p.y - L * Math.sin(sg.to.h)); ctx.stroke();
          ctx.restore();
        }
      });
    }
    if (playing) {
      var now = Math.min((performance.now() - playing.t0) / 1000, playing.total);
      var pose = playing.pose(now);
      if (pose) robot(pose.x, pose.y, pose.h, colors['--ink-wash'], colors['--ink'], 2.5);
      $('pl-clock').textContent = now.toFixed(1) + ' / ' + playing.total.toFixed(1) + ' s';
    }
  }

  // ---------------------------------------------------------------- pointer
  function pt(e) { var r = canvas.getBoundingClientRect(); return { px: e.clientX - r.left, py: e.clientY - r.top }; }
  canvas.addEventListener('pointerdown', function (e) {
    var path = curPath();
    if (!path) return;
    if (playing) stopPlay();
    var p = pt(e), m = fromPx(p.px, p.py);
    snapshot();
    var hit = null;
    path.waypoints.forEach(function (w, i) {
      var hs = M.handles(path, i);
      ['prev', 'next'].forEach(function (k) {
        if (!hs[k]) return;
        var h = toPx(hs[k].x, hs[k].y);
        if (Math.abs(p.px - h.x) < 11 && Math.abs(p.py - h.y) < 11) hit = { mode: 'handle', i: i, which: k };
      });
    });
    if (!hit) path.waypoints.forEach(function (w, i) {
      var a = toPx(w.x, w.y);
      if (Math.hypot(p.px - a.x, p.py - a.y) < 15) hit = { mode: 'anchor', i: i };
    });
    if (!hit) {
      var nw = { x: clampF(snapM(m.x)), y: clampF(snapM(m.y)), h: 0, lp: 0.3, ln: 0.3 };
      var last = path.waypoints[path.waypoints.length - 1];
      if (last) {
        var d = Math.hypot(nw.x - last.x, nw.y - last.y);
        var dir = M.deg(Math.atan2(nw.y - last.y, nw.x - last.x));
        if (path.waypoints.length === 1) last.h = Math.round(dir);
        last.ln = Math.max(0.05, d / 3);
        nw.h = Math.round(dir); nw.lp = Math.max(0.05, d / 3);
      }
      path.waypoints.push(nw);
      hit = { mode: 'anchor', i: path.waypoints.length - 1 };
      wp = hit.i;
      drag = hit;
      canvas.setPointerCapture(e.pointerId);
      changed(true);
      e.preventDefault();
      return;
    }
    drag = hit;
    if (hit.mode === 'anchor' && wp !== hit.i) { wp = hit.i; renderInspector(); }
    else if (hit.mode === 'handle' && wp !== hit.i) { wp = hit.i; renderInspector(); }
    canvas.setPointerCapture(e.pointerId);
    draw();
    e.preventDefault();
  });
  canvas.addEventListener('pointermove', function (e) {
    var p = pt(e), m = fromPx(p.px, p.py);
    $('pl-readout').textContent = 'x ' + toDisp(m.x) + ', y ' + toDisp(m.y) + ' ' + unit() +
      '  ·  tile ' + (Math.floor((m.x + H) / 0.6096) + 1) + ',' + (Math.floor((m.y + H) / 0.6096) + 1);
    if (!drag) return;
    var path = curPath(), w = path.waypoints[drag.i];
    if (drag.mode === 'anchor') { w.x = clampF(snapM(m.x)); w.y = clampF(snapM(m.y)); }
    else {
      var dx = m.x - w.x, dy = m.y - w.y, len = Math.max(0.05, Math.hypot(dx, dy));
      var ang = M.deg(Math.atan2(dy, dx));
      if (drag.which === 'next') { w.h = Math.round(ang); w.ln = len; }
      else { w.h = Math.round(M.wrap180(ang + 180)); w.lp = len; }
    }
    changed(false);
  });
  function endDrag() { if (drag) { drag = null; save(STORE, project); } }
  canvas.addEventListener('pointerup', endDrag);
  canvas.addEventListener('pointercancel', endDrag);

  // ---------------------------------------------------------------- project list
  function renderProject() {
    $('pl-paths').innerHTML = project.paths.map(function (p, i) {
      var on = sel.type === 'path' && sel.i === i, tr = p.waypoints.length > 1 ? traj(p) : null;
      return '<li><button type="button" class="pl-item' + (on ? ' is-sel' : '') + '" data-kind="path" data-i="' + i + '">' +
        '<span class="pl-item-name">' + esc(p.name) + (p.reversed ? ' <em class="pl-rev">rev</em>' : '') + '</span>' +
        '<span class="pl-item-meta">' + (tr ? tr.total.toFixed(1) + ' s' : p.waypoints.length + ' pt') + '</span></button></li>';
    }).join('') || '<li class="pl-hint">No paths yet.</li>';
    $('pl-autos').innerHTML = project.autos.map(function (a, i) {
      var on = sel.type === 'auto' && sel.i === i, t = M.autoTimeline(project, a).total;
      return '<li><button type="button" class="pl-item' + (on ? ' is-sel' : '') + '" data-kind="auto" data-i="' + i + '">' +
        '<span class="pl-item-name">' + esc(a.name) + '</span><span class="pl-item-meta' + (t > M.AUTO_SECONDS ? ' is-over' : '') + '">' + t.toFixed(1) + ' s</span></button></li>';
    }).join('') || '<li class="pl-hint">No autos yet.</li>';
    var p = curPath(), a = curAuto();
    $('pl-title').textContent = p ? 'Editing path: ' + p.name : a ? 'Auto: ' + a.name : 'Make a path to start';
    $('pl-hint-line').textContent = p
      ? (p.waypoints.length < 2 ? 'Tap the field to add points.' : 'Drag points and the square handles to shape the curve. Tap empty field to add a point at the end.')
      : 'Autos run paths and commands in order. Edit paths to change the route.';
  }
  function selectItem(kind, i) {
    if (playing) stopPlay();
    sel = { type: kind, i: i }; wp = -1;
    changed(true);
  }
  $('pl-project').addEventListener('click', function (e) {
    var b = e.target.closest('button.pl-item'); if (!b) return;
    selectItem(b.dataset.kind, Number(b.dataset.i));
  });
  $('pl-new-path').addEventListener('click', function () {
    snapshot();
    var names = project.paths.map(function (p) { return p.name; });
    var np = { name: M.uniqueName('path', names), reversed: false, waypoints: [], markers: [], zones: [] };
    var prev = curPath();
    if (prev && prev.waypoints.length > 1) {
      var e = M.endPose(prev), w = prev.waypoints[prev.waypoints.length - 1];
      np.waypoints.push({ x: w.x, y: w.y, h: Math.round(M.deg(e.h)), lp: 0.3, ln: 0.3 });
    }
    project.paths.push(np);
    sel = { type: 'path', i: project.paths.length - 1 }; wp = -1;
    changed(true);
  });
  $('pl-new-auto').addEventListener('click', function () {
    snapshot();
    var names = project.autos.map(function (a) { return a.name; });
    var p = curPath();
    project.autos.push({ name: M.uniqueName('auto', names), steps: p ? [{ type: 'path', path: p.name }] : [] });
    sel = { type: 'auto', i: project.autos.length - 1 }; wp = -1;
    changed(true);
  });

  // ---------------------------------------------------------------- inspector
  function numField(id, label, value, step, suffix, extra) {
    return '<label class="pl-field" for="' + id + '"><span>' + label + '</span><span class="pl-input-wrap"><input id="' + id +
      '" type="number" inputmode="decimal" step="' + step + '" value="' + value + '"' + (extra || '') + '><em>' + suffix + '</em></span></label>';
  }
  function cmdOptions(selectedName) {
    var names = project.commands.map(function (c) { return c.name; });
    if (selectedName && names.indexOf(selectedName) < 0) names.push(selectedName);
    return names.map(function (n) { return '<option' + (n === selectedName ? ' selected' : '') + '>' + esc(n) + '</option>'; }).join('');
  }
  function confirmButton(id, label) { return '<button type="button" class="pl-danger" id="' + id + '">' + label + '</button>'; }

  function renderInspector() {
    var box = $('pl-inspector'), path = curPath(), auto = curAuto(), html = '';
    if (path && wp >= 0) {
      var w = path.waypoints[wp], n = path.waypoints.length;
      html = '<div class="pl-insp-head"><button type="button" class="pl-back" id="pi-back">← ' + esc(path.name) + '</button><h4>Point ' + (wp + 1) + ' of ' + n + '</h4></div>' +
        '<div class="pl-grid">' + numField('pi-x', 'X', toDisp(w.x), 'any', unit()) + numField('pi-y', 'Y', toDisp(w.y), 'any', unit()) +
        numField('pi-h', 'Direction', w.h, '1', '°') + '</div>' +
        '<div class="pl-grid">' + (wp > 0 ? numField('pi-lp', 'Handle in', toDisp(w.lp), 'any', unit()) : '') +
        (wp < n - 1 ? numField('pi-ln', 'Handle out', toDisp(w.ln), 'any', unit()) : '') + '</div>' +
        '<p class="pl-hint">Direction is the way the path is heading here (0° = +X, counter-clockwise positive). Longer handles make wider, smoother curves.</p>' +
        '<div class="pl-actions">' + (wp < n - 1 ? '<button type="button" id="pi-insert">Add point after</button>' : '') +
        (n > 2 ? confirmButton('pi-delwp', 'Delete point') : '') + '</div>';
    } else if (path) {
      var tr = path.waypoints.length > 1 ? traj(path) : null, maxPos = Math.max(1, path.waypoints.length - 1);
      html = '<h4>Path</h4><label class="pl-field" for="pi-name"><span>Name</span><input id="pi-name" type="text" spellcheck="false" autocomplete="off" value="' + esc(path.name) + '"></label>' +
        '<label class="pl-check" for="pi-rev"><input id="pi-rev" type="checkbox"' + (path.reversed ? ' checked' : '') + '> Drive backward (back of the robot leads)</label>' +
        (tr ? '<p class="pl-stats"><span>' + toDisp(tr.length) + ' ' + unit() + '</span><span>' + tr.total.toFixed(2) + ' s</span><span>' + path.waypoints.length + ' points</span></p>' : '<p class="pl-hint">Tap the field to add at least 2 points.</p>') +
        '<div class="pl-subhead"><h5>Event markers</h5><button type="button" id="pi-addm">Add marker</button></div>' +
        ((path.markers || []).map(function (m, k) {
          var at = tr ? tr.posToTime(m.pos).toFixed(2) + ' s' : '—';
          return '<div class="pl-row"><label class="pl-field" for="pm-name-' + k + '"><span>Command</span><select id="pm-name-' + k + '" data-m="' + k + '" data-f="name">' + cmdOptions(m.name) + '</select></label>' +
            '<label class="pl-field pl-range" for="pm-pos-' + k + '"><span>Starts at <b id="pm-at-' + k + '">' + at + '</b></span><input id="pm-pos-' + k + '" type="range" min="0" max="' + maxPos + '" step="0.01" value="' + m.pos + '" data-m="' + k + '" data-f="pos"></label>' +
            numField('pm-dur-' + k, 'Run for', m.duration, '0.1', 's', ' min="0" data-m="' + k + '" data-f="duration"') +
            '<button type="button" class="pl-x" data-delm="' + k + '" aria-label="Remove marker">✕</button></div>';
        }).join('') || '<p class="pl-hint">Markers start a command partway along the path (an intake as you reach a game piece). "Run for 0" means until it finishes or the path ends.</p>') +
        '<div class="pl-subhead"><h5>Slow zones</h5><button type="button" id="pi-addz">Add zone</button></div>' +
        ((path.zones || []).map(function (z, k) {
          return '<div class="pl-row">' + numField('pz-s-' + k, 'From point', +(z.start + 1).toFixed(2), '0.1', '', ' min="1" max="' + (maxPos + 1) + '" data-z="' + k + '" data-f="start"') +
            numField('pz-e-' + k, 'To point', +(z.end + 1).toFixed(2), '0.1', '', ' min="1" max="' + (maxPos + 1) + '" data-z="' + k + '" data-f="end"') +
            numField('pz-v-' + k, 'Max speed', z.maxVel, '0.05', 'm/s', ' min="0.05" data-z="' + k + '" data-f="maxVel"') +
            '<button type="button" class="pl-x" data-delz="' + k + '" aria-label="Remove zone">✕</button></div>';
        }).join('') || '<p class="pl-hint">Slow down for part of the path, such as while picking something up. Point numbers can be fractional (1.5 is halfway from point 1 to 2).</p>') +
        '<div class="pl-actions"><button type="button" id="pi-dup">Duplicate</button><button type="button" id="pi-mirror">Mirror to other side</button>' + confirmButton('pi-delpath', 'Delete path') + '</div>';
    } else if (auto) {
      var tl = M.autoTimeline(project, auto);
      html = '<h4>Auto</h4><label class="pl-field" for="ai-name"><span>Name</span><input id="ai-name" type="text" spellcheck="false" autocomplete="off" value="' + esc(auto.name) + '"></label>' +
        '<div class="pl-meter' + (tl.total > M.AUTO_SECONDS ? ' is-over' : '') + '"><div class="pl-meter-track"><div class="pl-meter-fill" style="width:' + Math.min(100, tl.total / M.AUTO_SECONDS * 100) + '%"></div></div><span>' + tl.total.toFixed(1) + ' of 15 s</span></div>' +
        '<ol class="pl-steps">' + auto.steps.map(function (s, k) {
          var body = '';
          if (s.type === 'path') body = '<select id="as-' + k + '" data-s="' + k + '" data-f="path" aria-label="Path">' + project.paths.map(function (p) { return '<option' + (p.name === s.path ? ' selected' : '') + '>' + esc(p.name) + '</option>'; }).join('') +
            (M.findPath(project, s.path) ? '' : '<option selected>' + esc(s.path) + '</option>') + '</select>';
          else if (s.type === 'command') body = '<select id="as-' + k + '" data-s="' + k + '" data-f="name" aria-label="Command">' + cmdOptions(s.name) + '</select>';
          else if (s.type === 'wait') body = '<span class="pl-input-wrap"><input id="as-' + k + '" type="number" min="0" step="0.05" value="' + s.seconds + '" data-s="' + k + '" data-f="seconds" aria-label="Seconds"><em>s</em></span>';
          else body = '<span class="pl-input-wrap"><input id="as-' + k + '" type="number" step="1" value="' + s.heading + '" data-s="' + k + '" data-f="heading" aria-label="Heading"><em>°</em></span>';
          return '<li class="pl-astep"><span class="pl-kind pl-kind-' + s.type + '">' + { path: 'Path', command: 'Run', wait: 'Wait', turn: 'Turn to' }[s.type] + '</span>' + body +
            '<span class="pl-step-tools"><button type="button" data-up="' + k + '" aria-label="Move up"' + (k ? '' : ' disabled') + '>↑</button>' +
            '<button type="button" data-down="' + k + '" aria-label="Move down"' + (k < auto.steps.length - 1 ? '' : ' disabled') + '>↓</button>' +
            '<button type="button" data-dels="' + k + '" aria-label="Remove step">✕</button></span></li>';
        }).join('') + '</ol>' +
        (auto.steps.length ? '' : '<p class="pl-hint">Add a path first: the auto starts wherever its first path starts.</p>') +
        '<div class="pl-actions"><button type="button" id="ai-add-path">+ Path</button><button type="button" id="ai-add-turn">+ Turn</button><button type="button" id="ai-add-cmd">+ Command</button><button type="button" id="ai-add-wait">+ Wait</button></div>' +
        '<div class="pl-actions">' + confirmButton('ai-del', 'Delete auto') + '</div>';
    } else {
      html = '<h4>Nothing selected</h4><p class="pl-hint">Make a path with "New path", then tap the field to add points.</p>';
    }
    box.innerHTML = html;
  }

  function syncInspector() {
    var path = curPath();
    var set = function (id, v) { var el = $(id); if (el && document.activeElement !== el) el.value = v; };
    if (path && wp >= 0) {
      var w = path.waypoints[wp];
      set('pi-x', toDisp(w.x)); set('pi-y', toDisp(w.y)); set('pi-h', w.h);
      set('pi-lp', toDisp(w.lp)); set('pi-ln', toDisp(w.ln));
    } else if (path) {
      var tr = path.waypoints.length > 1 ? traj(path) : null;
      (path.markers || []).forEach(function (m, k) { var el = $('pm-at-' + k); if (el && tr) el.textContent = tr.posToTime(m.pos).toFixed(2) + ' s'; });
      var stats = document.querySelector('#pl-inspector .pl-stats');
      if (stats && tr) stats.innerHTML = '<span>' + toDisp(tr.length) + ' ' + unit() + '</span><span>' + tr.total.toFixed(2) + ' s</span><span>' + path.waypoints.length + ' points</span>';
    } else if (curAuto()) {
      var tl = M.autoTimeline(project, curAuto()), meter = document.querySelector('#pl-inspector .pl-meter');
      if (meter) {
        meter.classList.toggle('is-over', tl.total > M.AUTO_SECONDS);
        meter.querySelector('.pl-meter-fill').style.width = Math.min(100, tl.total / M.AUTO_SECONDS * 100) + '%';
        meter.querySelector('span').textContent = tl.total.toFixed(1) + ' of 15 s';
      }
    }
  }

  var editSnap = false;
  $('pl-inspector').addEventListener('focusin', function () { editSnap = false; });
  $('pl-inspector').addEventListener('input', function (e) {
    var el = e.target, path = curPath(), auto = curAuto();
    if (!editSnap) { snapshot(); editSnap = true; }
    var v = parseFloat(el.value);
    if (path && wp >= 0) {
      var w = path.waypoints[wp];
      if (isNaN(v)) return;
      if (el.id === 'pi-x') w.x = clampF(fromDisp(v));
      if (el.id === 'pi-y') w.y = clampF(fromDisp(v));
      if (el.id === 'pi-h') w.h = M.wrap180(v);
      if (el.id === 'pi-lp') w.lp = Math.max(0.02, fromDisp(v));
      if (el.id === 'pi-ln') w.ln = Math.max(0.02, fromDisp(v));
      changed(false); return;
    }
    if (path) {
      if (el.id === 'pi-name') {
        var old = path.name, nn = el.value.trim();
        path.name = nn;
        project.autos.forEach(function (a) { a.steps.forEach(function (s) { if (s.type === 'path' && s.path === old) s.path = nn; }); });
        changed(false); return;
      }
      if (el.id === 'pi-rev') { path.reversed = el.checked; changed(false); return; }
      if (el.dataset.m !== undefined) {
        var mk = path.markers[Number(el.dataset.m)];
        if (el.dataset.f === 'name') mk.name = el.value;
        else if (!isNaN(v)) mk[el.dataset.f] = el.dataset.f === 'duration' ? Math.max(0, v) : v;
        changed(false); return;
      }
      if (el.dataset.z !== undefined) {
        if (isNaN(v)) return;
        var z = path.zones[Number(el.dataset.z)];
        if (el.dataset.f === 'maxVel') z.maxVel = Math.max(0.05, v); else z[el.dataset.f] = v - 1;
        changed(false); return;
      }
    }
    if (auto) {
      if (el.id === 'ai-name') { auto.name = el.value.trim(); changed(false); return; }
      if (el.dataset.s !== undefined) {
        var st = auto.steps[Number(el.dataset.s)], f = el.dataset.f;
        if (f === 'path' || f === 'name') st[f] = el.value;
        else if (!isNaN(v)) st[f] = f === 'seconds' ? Math.max(0, v) : M.wrap180(v);
        changed(false);
      }
    }
  });

  var armed = null;
  function confirmClick(btn, action) {
    if (armed === btn.id) { armed = null; action(); return; }
    armed = btn.id;
    var label = btn.textContent;
    btn.textContent = 'Tap again to confirm'; btn.classList.add('is-armed');
    setTimeout(function () { if (armed === btn.id) { armed = null; if (btn.isConnected) { btn.textContent = label; btn.classList.remove('is-armed'); } } }, 3000);
  }

  $('pl-inspector').addEventListener('click', function (e) {
    var b = e.target.closest('button'); if (!b) return;
    var path = curPath(), auto = curAuto();
    if (b.id === 'pi-back') { wp = -1; changed(true); return; }
    if (b.id === 'pi-insert') {
      snapshot();
      var cp = M.controlPoints(path, wp), mid = (function () {
        var t = 0.5, u = 0.5;
        var x = u * u * u * cp[0].x + 3 * u * u * t * cp[1].x + 3 * u * t * t * cp[2].x + t * t * t * cp[3].x;
        var y = u * u * u * cp[0].y + 3 * u * u * t * cp[1].y + 3 * u * t * t * cp[2].y + t * t * t * cp[3].y;
        var dx = 3 * u * u * (cp[1].x - cp[0].x) + 6 * u * t * (cp[2].x - cp[1].x) + 3 * t * t * (cp[3].x - cp[2].x);
        var dy = 3 * u * u * (cp[1].y - cp[0].y) + 6 * u * t * (cp[2].y - cp[1].y) + 3 * t * t * (cp[3].y - cp[2].y);
        return { x: x, y: y, h: Math.round(M.deg(Math.atan2(dy, dx))) };
      })();
      var a = path.waypoints[wp], c = path.waypoints[wp + 1], len = Math.hypot(c.x - a.x, c.y - a.y) / 6;
      path.waypoints.splice(wp + 1, 0, { x: mid.x, y: mid.y, h: mid.h, lp: Math.max(0.05, len), ln: Math.max(0.05, len) });
      (path.markers || []).forEach(function (m) { if (m.pos > wp) m.pos += m.pos >= wp + 1 ? 1 : 0; });
      (path.zones || []).forEach(function (z) { if (z.start >= wp + 1) z.start += 1; if (z.end >= wp + 1) z.end += 1; });
      wp = wp + 1; changed(true); return;
    }
    if (b.id === 'pi-delwp') { confirmClick(b, function () { snapshot(); path.waypoints.splice(wp, 1); wp = -1; changed(true); }); return; }
    if (b.id === 'pi-addm') {
      snapshot(); path.markers.push({ name: project.commands.length ? project.commands[0].name : '', pos: 0.5, duration: 0 }); changed(true); return;
    }
    if (b.dataset.delm !== undefined) { snapshot(); path.markers.splice(Number(b.dataset.delm), 1); changed(true); return; }
    if (b.id === 'pi-addz') {
      snapshot(); var last = Math.max(1, path.waypoints.length - 1);
      path.zones.push({ start: Math.max(0, last - 0.5), end: last, maxVel: Math.min(0.5, project.constraints.maxVel) }); changed(true); return;
    }
    if (b.dataset.delz !== undefined) { snapshot(); path.zones.splice(Number(b.dataset.delz), 1); changed(true); return; }
    if (b.id === 'pi-dup') {
      snapshot(); var copy = M.clone(path);
      copy.name = M.uniqueName(path.name + 'Copy', project.paths.map(function (p) { return p.name; }));
      project.paths.splice(sel.i + 1, 0, copy); sel = { type: 'path', i: sel.i + 1 }; changed(true); return;
    }
    if (b.id === 'pi-mirror') {
      snapshot(); var mp = M.mirrorPath(path);
      mp.name = M.uniqueName(path.name + 'Mirrored', project.paths.map(function (p) { return p.name; }));
      project.paths.splice(sel.i + 1, 0, mp); sel = { type: 'path', i: sel.i + 1 }; changed(true); return;
    }
    if (b.id === 'pi-delpath') { confirmClick(b, function () { snapshot(); project.paths.splice(sel.i, 1); wp = -1; changed(true); }); return; }
    if (auto) {
      var add = { 'ai-add-path': { type: 'path', path: project.paths.length ? project.paths[0].name : '' },
        'ai-add-turn': { type: 'turn', heading: 0 },
        'ai-add-cmd': { type: 'command', name: project.commands.length ? project.commands[0].name : '' },
        'ai-add-wait': { type: 'wait', seconds: 0.5 } }[b.id];
      if (add) { snapshot(); auto.steps.push(add); changed(true); return; }
      if (b.dataset.up !== undefined) { var k = Number(b.dataset.up); snapshot(); var t = auto.steps[k]; auto.steps[k] = auto.steps[k - 1]; auto.steps[k - 1] = t; changed(true); return; }
      if (b.dataset.down !== undefined) { var k2 = Number(b.dataset.down); snapshot(); var t2 = auto.steps[k2]; auto.steps[k2] = auto.steps[k2 + 1]; auto.steps[k2 + 1] = t2; changed(true); return; }
      if (b.dataset.dels !== undefined) { snapshot(); auto.steps.splice(Number(b.dataset.dels), 1); changed(true); return; }
      if (b.id === 'ai-del') { confirmClick(b, function () { snapshot(); project.autos.splice(sel.i, 1); changed(true); }); }
    }
  });
  // structural edits in the inspector that should rebuild it after the value settles
  $('pl-inspector').addEventListener('change', function (e) {
    if (e.target.id === 'pi-rev' || e.target.dataset.s !== undefined || e.target.dataset.z !== undefined) changed(true);
  });

  // ---------------------------------------------------------------- settings + commands
  function syncSettings() {
    $('pl-units').value = prefs.units;
    $('pl-snap').value = String(prefs.snap);
    $('pl-rw').value = +(project.robot.width / INCH).toFixed(1);
    $('pl-rl').value = +(project.robot.length / INCH).toFixed(1);
    $('pl-tw').value = +(project.robot.trackWidth / INCH).toFixed(1);
    $('pl-maxv').value = project.constraints.maxVel;
    $('pl-maxa').value = project.constraints.maxAccel;
    $('pl-maxw').value = project.constraints.maxAngVel;
    $('pl-pkg').value = prefs.pkg;
    $('pl-cls').value = prefs.cls;
  }
  function onSetting(id, fn) { $(id).addEventListener('change', function (e) { var v = Number(e.target.value); if (v > 0) { snapshot(); fn(v); changed(true); } }); }
  onSetting('pl-rw', function (v) { project.robot.width = v * INCH; });
  onSetting('pl-rl', function (v) { project.robot.length = v * INCH; });
  onSetting('pl-tw', function (v) { project.robot.trackWidth = v * INCH; });
  onSetting('pl-maxv', function (v) { project.constraints.maxVel = v; });
  onSetting('pl-maxa', function (v) { project.constraints.maxAccel = v; });
  onSetting('pl-maxw', function (v) { project.constraints.maxAngVel = v; });
  $('pl-units').addEventListener('change', function (e) { prefs.units = e.target.value; save(PREFS, prefs); changed(true); });
  $('pl-snap').addEventListener('change', function (e) { prefs.snap = Number(e.target.value); save(PREFS, prefs); });
  $('pl-pkg').addEventListener('input', function (e) { prefs.pkg = e.target.value.trim() || 'com.robojacks.robot.autos'; save(PREFS, prefs); renderOutput(); });
  $('pl-cls').addEventListener('input', function (e) { prefs.cls = e.target.value.trim() || 'PlannerAutos'; save(PREFS, prefs); renderOutput(); });

  function renderCommands() {
    $('pl-cmds').innerHTML = project.commands.map(function (c, k) {
      return '<li><code>' + esc(c.name) + '</code><label for="pl-cs' + k + '" class="pl-input-wrap" title="Time limit when run as an auto step (0 = until it finishes)"><input id="pl-cs' + k +
        '" type="number" min="0" step="0.1" value="' + c.seconds + '" data-k="' + k + '"><em>s</em></label><button type="button" class="pl-x" data-k="' + k + '" aria-label="Remove ' + esc(c.name) + '">✕</button></li>';
    }).join('') || '<li class="pl-hint">No commands yet.</li>';
  }
  $('pl-cmds').addEventListener('change', function (e) {
    var k = e.target.dataset.k; if (k === undefined) return;
    snapshot(); project.commands[k].seconds = Math.max(0, Number(e.target.value) || 0); changed(false);
  });
  $('pl-cmds').addEventListener('click', function (e) {
    var b = e.target.closest('button'); if (!b) return;
    snapshot(); project.commands.splice(Number(b.dataset.k), 1); changed(true);
  });
  $('pl-cmd-form').addEventListener('submit', function (e) {
    e.preventDefault();
    var name = $('pl-cmd-new').value.trim();
    if (!name || project.commands.some(function (c) { return c.name === name; })) return;
    snapshot(); project.commands.push({ name: name, seconds: 0 }); $('pl-cmd-new').value = ''; changed(true);
  });

  // ---------------------------------------------------------------- output
  function renderOutput() {
    var w = M.warnings(project);
    $('pl-warnings').innerHTML = w.map(function (x) { return '<li class="is-' + x.level + '">' + esc(x.text) + '</li>'; }).join('');
    $('pl-warnings').hidden = !w.length;
    var code = prefs.tab === 'json' ? JSON.stringify(project, null, 2) : M.toJava(project, { packageName: prefs.pkg, className: prefs.cls });
    var out = $('pl-code');
    out.textContent = code;
    out.className = prefs.tab === 'json' ? 'lang-json' : 'lang-java';
    if (window.SiteHighlight && code.length < 60000) window.SiteHighlight(out);
    $('pl-tab-java').setAttribute('aria-selected', String(prefs.tab === 'java'));
    $('pl-tab-json').setAttribute('aria-selected', String(prefs.tab === 'json'));
    $('pl-out-hint').textContent = prefs.tab === 'json'
      ? 'Your whole project. Copy it into a file (planner-project.json) and paste it back below to keep editing later.'
      : 'Save as ' + prefs.cls + '.java in your autos folder, then return ' + prefs.cls + '.yourAuto(m_robotDrive) from getAutonomousCommand().';
  }
  $('pl-tab-java').addEventListener('click', function () { prefs.tab = 'java'; save(PREFS, prefs); renderOutput(); });
  $('pl-tab-json').addEventListener('click', function () { prefs.tab = 'json'; save(PREFS, prefs); renderOutput(); });
  $('pl-copy').addEventListener('click', function () {
    var btn = $('pl-copy'), text = $('pl-code').textContent;
    function done(ok) { btn.textContent = ok ? 'Copied' : 'Select and copy'; setTimeout(function () { btn.textContent = 'Copy'; }, 1600); }
    function fallback() { var r = document.createRange(); r.selectNodeContents($('pl-code')); var s = window.getSelection(); s.removeAllRanges(); s.addRange(r); done(false); }
    try { navigator.clipboard.writeText(text).then(function () { done(true); }, fallback); } catch (e) { fallback(); }
  });
  function importText(text) {
    var msg = $('pl-import-msg');
    try {
      var p = M.normalize(JSON.parse(text));
      snapshot(); project = p; sel = { type: 'path', i: 0 }; wp = -1;
      syncSettings(); changed(true);
      msg.textContent = 'Loaded ' + project.paths.length + ' paths and ' + project.autos.length + ' autos.';
      msg.className = 'pl-import-msg is-ok';
    } catch (e) {
      msg.textContent = e instanceof SyntaxError ? 'That text isn\'t valid JSON. Copy it again from the JSON tab.' : e.message;
      msg.className = 'pl-import-msg is-error';
    }
  }
  $('pl-import-btn').addEventListener('click', function () { importText($('pl-import').value); });
  $('pl-file').addEventListener('change', function (e) {
    var f = e.target.files && e.target.files[0]; if (!f) return;
    var r = new FileReader(); r.onload = function () { importText(String(r.result)); }; r.readAsText(f);
    e.target.value = '';
  });

  // ---------------------------------------------------------------- toolbar
  $('pl-undo').addEventListener('click', undo);
  $('pl-sample').addEventListener('click', function (e) {
    confirmClick(e.currentTarget, function () {
      snapshot(); project = M.samplePlan(); sel = { type: 'path', i: 0 }; wp = -1; syncSettings(); changed(true);
      e.currentTarget.textContent = 'Load example'; e.currentTarget.classList.remove('is-armed');
    });
  });
  document.addEventListener('keydown', function (e) {
    var view = $('planner');
    if ((e.ctrlKey || e.metaKey) && e.key === 'z' && view && !view.hidden && !/INPUT|TEXTAREA|SELECT/.test(document.activeElement.tagName)) { e.preventDefault(); undo(); }
  });
  function stopPlay() { playing = null; $('pl-play').textContent = 'Preview'; $('pl-clock').textContent = ''; draw(); }
  $('pl-play').addEventListener('click', function () {
    if (playing) { stopPlay(); return; }
    var path = curPath(), auto = curAuto();
    if (path && path.waypoints.length > 1) {
      var tr = traj(path);
      playing = { t0: performance.now(), total: tr.total, pose: function (t) { return M.stateAt(tr, t); } };
    } else if (auto) {
      var tl = M.autoTimeline(project, auto);
      if (!tl.segments.length) return;
      playing = { t0: performance.now(), total: tl.total, pose: function (t) { return M.poseAtAuto(tl, t); } };
    } else return;
    $('pl-play').textContent = 'Stop';
    (function frame() {
      if (!playing) return;
      draw();
      if ((performance.now() - playing.t0) / 1000 > playing.total + 0.6) { stopPlay(); return; }
      requestAnimationFrame(frame);
    })();
  });

  // ---------------------------------------------------------------- boot
  syncSettings();
  changed(true);
  if (window.ResizeObserver) new ResizeObserver(resize).observe(canvas.parentElement);
  window.addEventListener('resize', resize);
  var mq = window.matchMedia('(prefers-color-scheme: dark)');
  if (mq.addEventListener) mq.addEventListener('change', draw);
  new MutationObserver(draw).observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
  window.PlannerRedraw = resize;
  resize();
})();
