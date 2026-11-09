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
    if (guides) guides.hidden = view.id === 'home' || view.id === 'planner';
    navLinks.forEach(function (a) {
      var on = a.getAttribute('href') === '#' + view.id;
      if (on) a.setAttribute('aria-current', 'page'); else a.removeAttribute('aria-current');
    });
    if (menu) { menu.classList.remove('is-open'); if (menuBtn) menuBtn.setAttribute('aria-expanded', 'false'); }
    if (target && target !== view) target.scrollIntoView({ block: 'start' });
    else window.scrollTo(0, 0);
    if (view.id === 'planner' && window.PlannerRedraw) window.PlannerRedraw();
  }
  window.addEventListener('hashchange', show);
  show();

  if (menuBtn) {
    menuBtn.addEventListener('click', function () {
      var open = !menu.classList.contains('is-open');
      menu.classList.toggle('is-open', open);
      menuBtn.setAttribute('aria-expanded', String(open));
    });
  }
})();
