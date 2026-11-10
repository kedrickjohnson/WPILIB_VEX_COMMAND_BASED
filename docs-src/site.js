/* Site shell: hash routing between views, code highlighting, copy buttons, mobile menu. */
(function () {
  'use strict';

  // ---------------------------------------------------------------- highlighting
  var JAVA_KW = /^(?:abstract|boolean|break|case|catch|class|default|do|double|else|enum|extends|final|finally|float|for|if|implements|import|instanceof|int|interface|long|new|package|private|protected|public|return|static|super|switch|this|throw|throws|try|var|void|while|true|false|null)$/;
  function esc(s) { return s.replace(/[&<>]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]; }); }
  function highlight(el) {
    var src = el.textContent, out = '', m;
    var lang = el.className.indexOf('json') >= 0 ? 'json' : (el.className.indexOf('shell') >= 0 ? 'shell' : 'java');
    var re = lang === 'json'
      ? /("(?:[^"\\]|\\.)*")(\s*:)?|(-?\d+(?:\.\d+)?(?:e-?\d+)?)|\b(true|false|null)\b/g
      : lang === 'shell'
        ? /(#.*$)|("(?:[^"\\]|\\.)*")|(^\s*[$>] )/gm
        : /(\/\/.*$|\/\*[\s\S]*?\*\/)|("(?:[^"\\\n]|\\.)*")|(@\w+)|\b(\d+(?:\.\d+)?)\b|\b([A-Za-z_]\w*)\b/gm;
    var last = 0;
    while ((m = re.exec(src))) {
      out += esc(src.slice(last, m.index));
      var t = m[0], cls = null;
      if (lang === 'json') cls = m[1] ? (m[2] ? 'tk-key' : 'tk-str') : m[3] ? 'tk-num' : 'tk-kw';
      else if (lang === 'shell') cls = m[1] ? 'tk-com' : m[2] ? 'tk-str' : 'tk-kw';
      else if (m[1]) cls = 'tk-com';
      else if (m[2]) cls = 'tk-str';
      else if (m[3]) cls = 'tk-ann';
      else if (m[4]) cls = 'tk-num';
      else if (m[5]) cls = JAVA_KW.test(t) ? 'tk-kw' : (/^[A-Z]/.test(t) ? 'tk-type' : null);
      out += cls ? '<span class="' + cls + '">' + esc(t) + '</span>' : esc(t);
      last = m.index + t.length;
    }
    el.innerHTML = out + esc(src.slice(last));
  }
  window.SiteHighlight = highlight;
  Array.prototype.forEach.call(document.querySelectorAll('pre.code > code'), function (el) {
    highlight(el);
    var pre = el.parentElement;
    if (pre.dataset.nocopy !== undefined) return;
    var b = document.createElement('button');
    b.type = 'button'; b.className = 'copy-btn'; b.textContent = 'Copy';
    b.addEventListener('click', function () {
      var text = el.textContent;
      function done(ok) { b.textContent = ok ? 'Copied' : 'Select and copy'; setTimeout(function () { b.textContent = 'Copy'; }, 1600); }
      function fallback() {
        var r = document.createRange(); r.selectNodeContents(el);
        var s = window.getSelection(); s.removeAllRanges(); s.addRange(r); done(false);
      }
      try { navigator.clipboard.writeText(text).then(function () { done(true); }, fallback); } catch (e) { fallback(); }
    });
    pre.appendChild(b);
  });

  // ---------------------------------------------------------------- routing
  var views = Array.prototype.slice.call(document.querySelectorAll('section.view'));
  var navLinks = Array.prototype.slice.call(document.querySelectorAll('[data-nav]'));
  var menu = document.getElementById('menu');
  var menuBtn = document.getElementById('menu-btn');

  function show() {
    var id = decodeURIComponent((location.hash || '').slice(1)) || 'home';
    var target = document.getElementById(id);
    var view = target ? (target.classList.contains('view') ? target : target.closest('section.view')) : null;
    if (!view) { view = document.getElementById('home'); target = view; }
    views.forEach(function (v) { v.hidden = v !== view; });
    document.body.dataset.view = view.id;
    var guides = document.getElementById('guides-shell');
    if (guides) guides.hidden = view.id === 'home' || view.id === 'planner' || view.id === 'dashboard';
    navLinks.forEach(function (a) {
      var on = a.getAttribute('href') === '#' + view.id;
      if (on) a.setAttribute('aria-current', 'page'); else a.removeAttribute('aria-current');
    });
    if (menu) { menu.classList.remove('is-open'); if (menuBtn) menuBtn.setAttribute('aria-expanded', 'false'); }
    // After the browser's own jump-to-anchor, so it can't leave the page scrolled past its title.
    requestAnimationFrame(function () {
      if (target && target !== view) target.scrollIntoView({ block: 'start' });
      else window.scrollTo(0, 0);
    });
    if (view.id === 'planner' && window.PlannerRedraw) window.PlannerRedraw();
  }
  window.addEventListener('hashchange', show);
  show();

  // ---------------------------------------------------------------- search
  var input = document.getElementById('search-input');
  var results = document.getElementById('search-results');
  var index = window.SEARCH_INDEX || [];
  var active = -1;
  function norm(t) { return t.toLowerCase(); }
  function search(q) {
    var terms = norm(q).split(/\s+/).filter(Boolean);
    if (!terms.length) return [];
    var hits = [];
    index.forEach(function (e) {
      var title = norm(e.title), page = norm(e.page), text = norm(e.text);
      var score = 0;
      for (var i = 0; i < terms.length; i++) {
        var t = terms[i];
        if (title.indexOf(t) >= 0) score += 10;
        else if (page.indexOf(t) >= 0) score += 3;
        else if (text.indexOf(t) >= 0) score += 1;
        else return;
      }
      if (e.kind === 'page') score += 2;
      hits.push({ e: e, score: score });
    });
    hits.sort(function (a, b) { return b.score - a.score; });
    return hits.slice(0, 12).map(function (h) { return h.e; });
  }
  function snippet(e, q) {
    var t = norm(q).split(/\s+/)[0] || '';
    var i = norm(e.text).indexOf(t);
    if (i < 0) return e.text.slice(0, 90);
    var a = Math.max(0, i - 40);
    return (a ? '…' : '') + e.text.slice(a, a + 110) + '…';
  }
  function renderResults() {
    var q = input.value.trim();
    if (!q) { results.hidden = true; return; }
    var hits = search(q);
    active = -1;
    results.innerHTML = hits.length ? hits.map(function (e) {
      return '<li><a href="#' + e.id + '">' + esc(e.title) + (e.kind === 'page' ? '' : ' <small>' + esc(e.page) + '</small>') +
        '<small>' + esc(snippet(e, q)) + '</small></a></li>';
    }).join('') : '<li class="empty">No matches</li>';
    results.hidden = false;
  }
  if (input && results) {
    input.addEventListener('input', renderResults);
    input.addEventListener('keydown', function (ev) {
      var links = results.querySelectorAll('a');
      if (ev.key === 'ArrowDown' || ev.key === 'ArrowUp') {
        if (!links.length) return;
        ev.preventDefault();
        active = (active + (ev.key === 'ArrowDown' ? 1 : -1) + links.length) % links.length;
        links.forEach(function (a, i) { a.classList.toggle('is-active', i === active); });
      } else if (ev.key === 'Enter') {
        var a = links[active >= 0 ? active : 0];
        if (a) { location.hash = a.getAttribute('href').slice(1); input.value = ''; results.hidden = true; }
      } else if (ev.key === 'Escape') {
        input.value = ''; results.hidden = true;
      }
    });
    results.addEventListener('click', function (ev) {
      if (ev.target.closest('a')) { input.value = ''; results.hidden = true; }
    });
    document.addEventListener('click', function (ev) {
      if (!ev.target.closest('.search')) results.hidden = true;
    });
  }

  if (menuBtn) {
    menuBtn.addEventListener('click', function () {
      var open = !menu.classList.contains('is-open');
      menu.classList.toggle('is-open', open);
      menuBtn.setAttribute('aria-expanded', String(open));
    });
  }
})();
