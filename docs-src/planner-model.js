/*
 * Path planner model: Bezier paths, trajectory generation, autos, warnings, Java export.
 * Pure functions (no DOM), so they also run under Node for testing.
 *
 * Units: meters, seconds, radians internally; headings in the saved file are degrees.
 * Field: V5RC, 12 ft square, origin at the center, +X right, +Y up, CCW-positive headings.
 */
(function (root) {
  'use strict';

  var HALF_FIELD = 1.8288;
  var INCH = 0.0254;
  var AUTO_SECONDS = 15;
  var SKILLS_SECONDS = 60;
  var DT = 0.02;          // trajectory sample period, s
  var DS = 0.01;          // arc-length resolution for velocity planning, m

  function clone(o) { return JSON.parse(JSON.stringify(o)); }
  function rad(d) { return d * Math.PI / 180; }
  function deg(r) { return r * 180 / Math.PI; }
  function wrap180(d) { var x = ((d + 180) % 360 + 360) % 360 - 180; return x === -180 ? 180 : x; }
  function wrapPi(r) { var x = ((r + Math.PI) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI) - Math.PI; return x; }

  // ---------------------------------------------------------------- sample project
  function samplePlan() {
    return {
      version: 2,
      robot: { width: 18 * INCH, length: 18 * INCH, trackWidth: 11.5 * INCH },
      constraints: { maxVel: 1.2, maxAccel: 2.0, maxAngVel: 360 },
      commands: [
        { name: 'intake', seconds: 0 },
        { name: 'outtake', seconds: 0.8 },
        { name: 'closeClaw', seconds: 0.5 },
        { name: 'openClaw', seconds: 0.5 },
        { name: 'armCarry', seconds: 0.8 },
        { name: 'armDown', seconds: 0.8 }
      ],
      paths: [
        {
          name: 'pickup', reversed: false,
          waypoints: [
            { x: -1.5, y: 0.6, h: 0, lp: 0.3, ln: 0.45 },
            { x: -0.75, y: 0.35, h: -45, lp: 0.3, ln: 0.3 },
            { x: -0.55, y: -0.25, h: -90, lp: 0.3, ln: 0.3 }
          ],
          markers: [{ name: 'intake', pos: 0.9, duration: 0 }],
          zones: [{ start: 1.6, end: 2.0, maxVel: 0.5 }]
        },
        {
          name: 'score', reversed: true,
          waypoints: [
            { x: -0.55, y: -0.25, h: 90, lp: 0.3, ln: 0.35 },
            { x: -1.25, y: 0.25, h: 180, lp: 0.4, ln: 0.3 }
          ],
          markers: [],
          zones: []
        }
      ],
      autos: [
        { name: 'exampleAuto', steps: [
          { type: 'path', path: 'pickup' },
          { type: 'wait', seconds: 0.3 },
          { type: 'path', path: 'score' },
          { type: 'command', name: 'outtake' }
        ] }
      ]
    };
  }

  // ---------------------------------------------------------------- geometry
  function controlPoints(path, i) {
    var a = path.waypoints[i], b = path.waypoints[i + 1];
    var ha = rad(a.h), hb = rad(b.h);
    return [
      { x: a.x, y: a.y },
      { x: a.x + a.ln * Math.cos(ha), y: a.y + a.ln * Math.sin(ha) },
      { x: b.x - b.lp * Math.cos(hb), y: b.y - b.lp * Math.sin(hb) },
      { x: b.x, y: b.y }
    ];
  }

  function handles(path, i) {
    var w = path.waypoints[i], h = rad(w.h), out = {};
    if (i > 0) out.prev = { x: w.x - w.lp * Math.cos(h), y: w.y - w.lp * Math.sin(h) };
    if (i < path.waypoints.length - 1) out.next = { x: w.x + w.ln * Math.cos(h), y: w.y + w.ln * Math.sin(h) };
    return out;
  }

  function bez(p, t) {
    var u = 1 - t;
    var x = u * u * u * p[0].x + 3 * u * u * t * p[1].x + 3 * u * t * t * p[2].x + t * t * t * p[3].x;
    var y = u * u * u * p[0].y + 3 * u * u * t * p[1].y + 3 * u * t * t * p[2].y + t * t * t * p[3].y;
    var dx = 3 * u * u * (p[1].x - p[0].x) + 6 * u * t * (p[2].x - p[1].x) + 3 * t * t * (p[3].x - p[2].x);
    var dy = 3 * u * u * (p[1].y - p[0].y) + 6 * u * t * (p[2].y - p[1].y) + 3 * t * t * (p[3].y - p[2].y);
    var ddx = 6 * u * (p[2].x - 2 * p[1].x + p[0].x) + 6 * t * (p[3].x - 2 * p[2].x + p[1].x);
    var ddy = 6 * u * (p[2].y - 2 * p[1].y + p[0].y) + 6 * t * (p[3].y - 2 * p[2].y + p[1].y);
    var sp = Math.hypot(dx, dy);
    var k = sp > 1e-9 ? (dx * ddy - dy * ddx) / (sp * sp * sp) : 0;
    return { x: x, y: y, tangent: Math.atan2(dy, dx), k: k };
  }

  /** Dense points along the whole path, evenly spaced by arc length (DS apart). */
  function pathPoints(path) {
    var raw = [];
    var nSeg = path.waypoints.length - 1;
    for (var i = 0; i < nSeg; i++) {
      var cp = controlPoints(path, i);
      var steps = 400;
      for (var j = (i === 0 ? 0 : 1); j <= steps; j++) {
        var t = j / steps, b = bez(cp, t);
        b.pos = i + t;
        raw.push(b);
      }
    }
    if (!raw.length) return [];
    // cumulative arc length, then resample
    raw[0].s = 0;
    for (var r = 1; r < raw.length; r++) raw[r].s = raw[r - 1].s + Math.hypot(raw[r].x - raw[r - 1].x, raw[r].y - raw[r - 1].y);
    var total = raw[raw.length - 1].s;
    var n = Math.max(2, Math.ceil(total / DS) + 1);
    var out = [], c = 0;
    for (var q = 0; q < n; q++) {
      var s = total * q / (n - 1);
      while (c < raw.length - 2 && raw[c + 1].s < s) c++;
      var A = raw[c], B = raw[c + 1] || A, span = B.s - A.s, f = span > 1e-12 ? (s - A.s) / span : 0;
      out.push({
        s: s,
        x: A.x + (B.x - A.x) * f,
        y: A.y + (B.y - A.y) * f,
        tangent: A.tangent + wrapPi(B.tangent - A.tangent) * f,
        k: A.k + (B.k - A.k) * f,
        pos: A.pos + (B.pos - A.pos) * f
      });
    }
    return out;
  }

  // ---------------------------------------------------------------- trajectory generation
  /**
   * Time-parameterizes a path: trapezoid-style speed limited by max speed, slow zones,
   * max acceleration and max turn rate (v <= maxAngVel / |curvature|), starting and ending at rest.
   * Returns { states: [{t,x,y,h,v,w}], total, length, posToTime(pos) }.
   */
  function generate(path, constraints) {
    var pts = pathPoints(path);
    var empty = { states: [], total: 0, length: 0, posToTime: function () { return 0; }, points: [] };
    if (pts.length < 2) return empty;
    var a = Math.max(0.05, constraints.maxAccel);
    var wMax = rad(Math.max(5, constraints.maxAngVel));
    var vCap = pts.map(function (p) {
      var lim = constraints.maxVel;
      (path.zones || []).forEach(function (z) {
        if (p.pos >= Math.min(z.start, z.end) && p.pos <= Math.max(z.start, z.end)) lim = Math.min(lim, z.maxVel);
      });
      if (Math.abs(p.k) > 1e-6) lim = Math.min(lim, wMax / Math.abs(p.k));
      return Math.max(0.02, lim);
    });
    var v = new Array(pts.length);
    v[0] = 0;
    for (var i = 1; i < pts.length; i++) {
      var ds = pts[i].s - pts[i - 1].s;
      v[i] = Math.min(vCap[i], Math.sqrt(v[i - 1] * v[i - 1] + 2 * a * ds));
    }
    v[pts.length - 1] = 0;
    for (var j = pts.length - 2; j >= 0; j--) {
      var ds2 = pts[j + 1].s - pts[j].s;
      v[j] = Math.min(v[j], Math.sqrt(v[j + 1] * v[j + 1] + 2 * a * ds2));
    }
    // time at each point
    var t = [0];
    for (var k = 1; k < pts.length; k++) {
      var d = pts[k].s - pts[k - 1].s, avg = (v[k] + v[k - 1]) / 2;
      t.push(t[k - 1] + (avg > 1e-6 ? d / avg : Math.sqrt(2 * d / a)));
    }
    var total = t[t.length - 1];
    var rev = !!path.reversed;
    var states = [];
    var n = Math.max(2, Math.round(total / DT) + 1), c = 0;
    for (var q = 0; q < n; q++) {
      var tq = Math.min(total, q * DT);
      while (c < t.length - 2 && t[c + 1] < tq) c++;
      var span = t[c + 1] - t[c], f = span > 1e-12 ? (tq - t[c]) / span : 0;
      var A = pts[c], B = pts[c + 1];
      var speed = v[c] + (v[c + 1] - v[c]) * f;
      var tangent = A.tangent + wrapPi(B.tangent - A.tangent) * f;
      var curv = A.k + (B.k - A.k) * f;
      states.push({
        t: q * DT,
        x: A.x + (B.x - A.x) * f,
        y: A.y + (B.y - A.y) * f,
        h: wrapPi(rev ? tangent + Math.PI : tangent),
        v: rev ? -speed : speed,
        w: speed * curv,
        frac: speed / Math.max(0.01, constraints.maxVel)
      });
    }
    // make the last sample land exactly on the end time
    states[states.length - 1].t = (states.length - 1) * DT;
    function posToTime(pos) {
      for (var i = 0; i < pts.length; i++) if (pts[i].pos >= pos) return t[i];
      return total;
    }
    return { states: states, total: (states.length - 1) * DT, length: pts[pts.length - 1].s, posToTime: posToTime, points: pts };
  }

  function stateAt(traj, time) {
    var s = traj.states;
    if (!s.length) return null;
    var f = Math.max(0, Math.min(time, traj.total)) / DT, i = Math.min(s.length - 1, Math.floor(f)), u = f - i;
    var A = s[i], B = s[Math.min(s.length - 1, i + 1)];
    return { x: A.x + (B.x - A.x) * u, y: A.y + (B.y - A.y) * u, h: A.h + wrapPi(B.h - A.h) * u, v: A.v };
  }

  function startPose(path, constraints) {
    var w = path.waypoints[0];
    return { x: w.x, y: w.y, h: wrapPi(rad(w.h) + (path.reversed ? Math.PI : 0)) };
  }
  function endPose(path) {
    var w = path.waypoints[path.waypoints.length - 1];
    return { x: w.x, y: w.y, h: wrapPi(rad(w.h) + (path.reversed ? Math.PI : 0)) };
  }

  function findPath(project, name) {
    for (var i = 0; i < project.paths.length; i++) if (project.paths[i].name === name) return project.paths[i];
    return null;
  }

  function commandSeconds(project, name) {
    for (var i = 0; i < project.commands.length; i++) if (project.commands[i].name === name) return Number(project.commands[i].seconds) || 0;
    return 0.5;
  }

  /** Timeline of an auto for preview and time estimates. */
  function autoTimeline(project, auto) {
    var segs = [], t = 0, pose = null;
    var turnRate = Math.max(5, project.constraints.maxAngVel);
    var first = null;
    auto.steps.forEach(function (s) { if (!first && s.type === 'path') first = findPath(project, s.path); });
    if (first && first.waypoints.length >= 2) pose = startPose(first);
    auto.steps.forEach(function (s, i) {
      if (s.type === 'path') {
        var p = findPath(project, s.path);
        if (!p || p.waypoints.length < 2) return;
        var tr = generate(p, project.constraints);
        segs.push({ kind: 'path', step: i, t0: t, t1: t + tr.total, traj: tr, path: p });
        t += tr.total;
        pose = endPose(p);
      } else if (s.type === 'turn' && pose) {
        var target = rad(wrap180(Number(s.heading) || 0));
        var dh = wrapPi(target - pose.h), dur = Math.abs(dh) < 0.01 ? 0 : Math.abs(deg(dh)) / turnRate + 0.15;
        segs.push({ kind: 'turn', step: i, t0: t, t1: t + dur, from: clone(pose), to: { x: pose.x, y: pose.y, h: target } });
        t += dur;
        pose = { x: pose.x, y: pose.y, h: target };
      } else if (s.type === 'command' || s.type === 'wait') {
        var d = s.type === 'wait' ? Math.max(0, Number(s.seconds) || 0) : commandSeconds(project, s.name);
        segs.push({ kind: s.type, step: i, t0: t, t1: t + d, pose: pose && clone(pose) });
        t += d;
      }
    });
    return { segments: segs, total: t };
  }

  function poseAtAuto(tl, time) {
    var last = null;
    for (var i = 0; i < tl.segments.length; i++) {
      var s = tl.segments[i];
      if (time <= s.t1 || i === tl.segments.length - 1) {
        var lt = Math.max(0, Math.min(time - s.t0, s.t1 - s.t0));
        if (s.kind === 'path') return stateAt(s.traj, lt);
        if (s.kind === 'turn') {
          var f = s.t1 > s.t0 ? lt / (s.t1 - s.t0) : 1;
          return { x: s.from.x, y: s.from.y, h: s.from.h + wrapPi(s.to.h - s.from.h) * f };
        }
        return s.pose || last;
      }
      if (s.kind === 'path') last = stateAt(s.traj, s.traj.total);
      else if (s.kind === 'turn') last = s.to;
    }
    return last;
  }

  function corners(x, y, h, w, l) {
    var c = Math.cos(h), s = Math.sin(h);
    return [[l / 2, w / 2], [l / 2, -w / 2], [-l / 2, -w / 2], [-l / 2, w / 2]].map(function (p) {
      return { x: x + p[0] * c - p[1] * s, y: y + p[0] * s + p[1] * c };
    });
  }

  // ---------------------------------------------------------------- names
  var JAVA_KEYWORDS = ('abstract assert boolean break byte case catch char class const continue default do double ' +
    'else enum extends final finally float for goto if implements import instanceof int interface long native new ' +
    'package private protected public return short static strictfp super switch synchronized this throw throws ' +
    'transient try void volatile while true false null var record yield').split(' ');
  function isJavaIdentifier(n) { return typeof n === 'string' && /^[A-Za-z_$][A-Za-z0-9_$]*$/.test(n) && JAVA_KEYWORDS.indexOf(n) < 0; }
  function constName(n) {
    return n.replace(/([a-z0-9])([A-Z])/g, '$1_$2').replace(/[^A-Za-z0-9]+/g, '_').replace(/^_+|_+$/g, '').toUpperCase() || 'PATH';
  }
  function uniqueName(base, taken) {
    var n = base, k = 2;
    while (taken.indexOf(n) >= 0) n = base + k++;
    return n;
  }

  // ---------------------------------------------------------------- warnings
  function warnings(project) {
    var out = [];
    var lim = HALF_FIELD + 0.005, R = project.robot;
    var known = project.commands.map(function (c) { return c.name; });
    var pathNames = [];
    project.paths.forEach(function (p) {
      var where = 'Path "' + p.name + '"';
      if (!isJavaIdentifier(p.name)) out.push({ level: 'error', text: where + ': name must be a valid Java name (letters, digits, _; not starting with a digit).', path: p.name });
      if (pathNames.indexOf(p.name) >= 0) out.push({ level: 'error', text: where + ': two paths have this name.', path: p.name });
      pathNames.push(p.name);
      if (p.waypoints.length < 2) { out.push({ level: 'warn', text: where + ': needs at least 2 points. Tap the field to add one.', path: p.name }); return; }
      var tr = generate(p, project.constraints);
      var sharp = null;
      tr.points.forEach(function (q) { if (!sharp && Math.abs(q.k) > 1 / 0.04) sharp = q; });
      if (sharp) out.push({ level: 'warn', text: where + ': very sharp bend near point ' + (Math.floor(sharp.pos) + 1) +
        ' (under 4 cm radius). The robot will almost stop there. Lengthen the handles or move the points apart.', path: p.name });
      for (var i = 0; i < tr.states.length; i += 5) {
        var s = tr.states[i];
        if (corners(s.x, s.y, s.h, R.width, R.length).some(function (c) { return Math.abs(c.x) > lim || Math.abs(c.y) > lim; })) {
          out.push({ level: 'warn', text: where + ': the robot hits the field wall around ' + s.t.toFixed(1) + ' s.', path: p.name });
          break;
        }
      }
      (p.markers || []).forEach(function (m) {
        if (known.indexOf(m.name) < 0) out.push({ level: 'warn', text: where + ': marker "' + m.name + '" isn\'t in your command list.', path: p.name });
      });
    });
    var autoNames = [];
    project.autos.forEach(function (a) {
      var where = 'Auto "' + a.name + '"';
      if (!isJavaIdentifier(a.name)) out.push({ level: 'error', text: where + ': name must be a valid Java method name.', auto: a.name });
      if (autoNames.indexOf(a.name) >= 0) out.push({ level: 'error', text: where + ': two autos have this name.', auto: a.name });
      autoNames.push(a.name);
      var pose = null, sawPath = false;
      a.steps.forEach(function (s, i) {
        var n = 'step ' + (i + 1);
        if (s.type === 'path') {
          var p = findPath(project, s.path);
          if (!p) { out.push({ level: 'error', text: where + ', ' + n + ': there is no path called "' + s.path + '".', auto: a.name }); return; }
          if (p.waypoints.length < 2) return;
          var sp = startPose(p);
          if (pose) {
            var gap = Math.hypot(sp.x - pose.x, sp.y - pose.y);
            var turn = Math.abs(deg(wrapPi(sp.h - pose.h)));
            if (gap > 0.05) out.push({ level: 'warn', text: where + ', ' + n + ': "' + p.name + '" starts ' + (gap / INCH).toFixed(0) + ' in from where the robot is.', auto: a.name });
            else if (turn > 10) out.push({ level: 'warn', text: where + ', ' + n + ': the robot faces ' + turn.toFixed(0) + '° away from where "' + p.name + '" starts. Add a turn step before it.', auto: a.name });
          }
          pose = endPose(p); sawPath = true;
        } else if (s.type === 'turn') {
          if (pose) pose = { x: pose.x, y: pose.y, h: rad(Number(s.heading) || 0) };
        } else if (s.type === 'command') {
          if (known.indexOf(s.name) < 0) out.push({ level: 'warn', text: where + ', ' + n + ': "' + s.name + '" isn\'t in your command list.', auto: a.name });
        }
      });
      if (!sawPath) out.push({ level: 'warn', text: where + ': add a path so the auto knows where the robot starts.', auto: a.name });
      var total = autoTimeline(project, a).total;
      if (total > AUTO_SECONDS) out.push({ level: total > SKILLS_SECONDS ? 'error' : 'warn', auto: a.name,
        text: where + ': about ' + total.toFixed(1) + ' s, longer than the ' + AUTO_SECONDS + ' s autonomous period' + (total > SKILLS_SECONDS ? ' and the 60 s skills run.' : ' (fine for a 60 s skills run).') });
    });
    return out;
  }

  // ---------------------------------------------------------------- mirroring
  /** Rotates a path 180 degrees about the field center (the other starting side). */
  function mirrorPath(path) {
    var p = clone(path);
    p.waypoints.forEach(function (w) { w.x = -w.x; w.y = -w.y; w.h = wrap180(w.h + 180); });
    return p;
  }

  // ---------------------------------------------------------------- Java export
  function num(v, d) {
    var s = Number(v).toFixed(d);
    if (s.indexOf('.') >= 0) s = s.replace(/0+$/, '').replace(/\.$/, '');
    if (s === '-0') s = '0';
    return s;
  }
  function javaString(s) { return '"' + String(s).replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"'; }

  function encodeTrajectory(tr) {
    return tr.states.map(function (s) {
      return [num(s.x, 3), num(s.y, 3), num(s.h, 4), num(s.v, 3), num(s.w, 3)].join(' ');
    }).join(' ');
  }

  function chunk(str, width) {
    var out = [];
    while (str.length > width) {
      var cut = str.lastIndexOf(' ', width);
      if (cut <= 0) cut = width;
      out.push(str.slice(0, cut + 1));
      str = str.slice(cut + 1);
    }
    out.push(str);
    return out;
  }

  function toJava(project, opts) {
    opts = opts || {};
    var pkg = opts.packageName || 'com.robojacks.robot.autos';
    var cls = opts.className || 'PlannerAutos';
    var L = [];
    L.push('package ' + pkg + ';', '');
    L.push('import com.robojacks.lib.auto.AutoBuilder;');
    L.push('import com.robojacks.lib.command.Command;');
    L.push('import com.robojacks.lib.command.Commands;');
    L.push('import com.robojacks.lib.auto.NamedCommands;');
    L.push('import com.robojacks.lib.drive.TankDriveSubsystem;');
    L.push('import com.robojacks.lib.trajectory.Trajectory;', '');
    L.push('/**');
    L.push(' * Paths and autos made with the VEX path planner. Regenerate this file from the planner');
    L.push(' * instead of editing it: paste the planner\'s JSON back in to keep working on it.');
    L.push(' */');
    L.push('public final class ' + cls + ' {');
    L.push('    private ' + cls + '() {}', '');
    var used = {};
    project.paths.forEach(function (p) {
      if (p.waypoints.length < 2 || !isJavaIdentifier(p.name)) return;
      var tr = generate(p, project.constraints);
      var c = constName(p.name);
      used[p.name] = c;
      L.push('    /** ' + p.name + (p.reversed ? ' (reversed)' : '') + ': ' + num(tr.length, 2) + ' m, ' + tr.total.toFixed(2) + ' s. */');
      var parts = chunk(encodeTrajectory(tr), 92);
      L.push('    public static final Trajectory ' + c + ' = Trajectory.parse(' + DT + ',');
      parts.forEach(function (part, i) {
        L.push('            ' + javaString(part) + (i < parts.length - 1 ? ' +' : ');'));
      });
      L.push('');
    });
    project.autos.forEach(function (a) {
      if (!isJavaIdentifier(a.name)) return;
      var total = autoTimeline(project, a).total;
      var lines = [], I = '                ';
      var firstPath = null;
      a.steps.forEach(function (s) { if (!firstPath && s.type === 'path' && used[s.path]) firstPath = s.path; });
      if (firstPath) lines.push(I + 'AutoBuilder.resetPose(drive, ' + used[firstPath] + ')');
      a.steps.forEach(function (s) {
        if (s.type === 'path') {
          if (!used[s.path]) return;
          var p = findPath(project, s.path), tr = generate(p, project.constraints);
          var markers = (p.markers || []).filter(function (m) { return m.name; }).map(function (m) {
            var at = num(Math.max(0, tr.posToTime(m.pos)), 2);
            return Number(m.duration) > 0
              ? 'AutoBuilder.marker(' + at + ', ' + num(m.duration, 2) + ', ' + javaString(m.name) + ')'
              : 'AutoBuilder.marker(' + at + ', ' + javaString(m.name) + ')';
          });
          if (!markers.length) lines.push(I + 'AutoBuilder.followPath(drive, ' + used[s.path] + ')');
          else lines.push(I + 'AutoBuilder.followPath(drive, ' + used[s.path] + ',\n' + markers.map(function (m) { return I + '        ' + m; }).join(',\n') + ')');
        } else if (s.type === 'turn') {
          lines.push(I + 'AutoBuilder.turnTo(drive, ' + num(wrap180(Number(s.heading) || 0), 1) + ')');
        } else if (s.type === 'command') {
          var secs = commandSeconds(project, s.name);
          lines.push(I + 'NamedCommands.get(' + javaString(s.name) + ')' + (secs > 0 ? '.withTimeout(' + num(secs, 2) + ')' : ''));
        } else if (s.type === 'wait') {
          lines.push(I + 'Commands.waitSeconds(' + num(Math.max(0, Number(s.seconds) || 0), 2) + ')');
        }
      });
      L.push('    /** ' + a.name + ': about ' + total.toFixed(1) + ' s. */');
      L.push('    public static Command ' + a.name + '(TankDriveSubsystem drive) {');
      if (!lines.length) L.push('        return Commands.none().withName(' + javaString(a.name) + ');');
      else {
        L.push('        return Commands.sequence(');
        L.push(lines.join(',\n'));
        L.push('        ).withName(' + javaString(a.name) + ');');
      }
      L.push('    }', '');
    });
    if (L[L.length - 1] === '') L.pop();
    L.push('}');
    return L.join('\n');
  }

  // ---------------------------------------------------------------- loading
  function normalize(raw) {
    if (!raw || typeof raw !== 'object' || !Array.isArray(raw.paths)) {
      throw new Error('This isn\'t a planner project: it has no "paths" list.');
    }
    var base = samplePlan();
    function n(v, d) { v = Number(v); return isFinite(v) ? v : d; }
    var p = {
      version: 2,
      robot: { width: n((raw.robot || {}).width, base.robot.width), length: n((raw.robot || {}).length, base.robot.length),
        trackWidth: n((raw.robot || {}).trackWidth, base.robot.trackWidth) },
      constraints: { maxVel: n((raw.constraints || {}).maxVel, base.constraints.maxVel), maxAccel: n((raw.constraints || {}).maxAccel, base.constraints.maxAccel),
        maxAngVel: n((raw.constraints || {}).maxAngVel, base.constraints.maxAngVel) },
      commands: Array.isArray(raw.commands) ? raw.commands.filter(function (c) { return c && typeof c.name === 'string'; })
        .map(function (c) { return { name: c.name, seconds: n(c.seconds, 0) }; }) : base.commands,
      paths: raw.paths.map(function (rp) {
        return {
          name: String(rp.name || 'path'), reversed: !!rp.reversed,
          waypoints: (rp.waypoints || []).map(function (w) { return { x: n(w.x, 0), y: n(w.y, 0), h: n(w.h, 0), lp: Math.max(0.02, n(w.lp, 0.3)), ln: Math.max(0.02, n(w.ln, 0.3)) }; }),
          markers: (rp.markers || []).map(function (m) { return { name: String(m.name || ''), pos: n(m.pos, 0), duration: Math.max(0, n(m.duration, 0)) }; }),
          zones: (rp.zones || []).map(function (z) { return { start: n(z.start, 0), end: n(z.end, 1), maxVel: Math.max(0.05, n(z.maxVel, 0.5)) }; })
        };
      }),
      autos: (raw.autos || []).map(function (ra) {
        return { name: String(ra.name || 'auto'), steps: (ra.steps || []).map(function (s) {
          if (s.type === 'path') return { type: 'path', path: String(s.path || '') };
          if (s.type === 'turn') return { type: 'turn', heading: n(s.heading, 0) };
          if (s.type === 'command') return { type: 'command', name: String(s.name || '') };
          return { type: 'wait', seconds: Math.max(0, n(s.seconds, 0)) };
        }) };
      })
    };
    return p;
  }

  var api = {
    HALF_FIELD: HALF_FIELD, INCH: INCH, AUTO_SECONDS: AUTO_SECONDS, DT: DT,
    samplePlan: samplePlan, clone: clone, rad: rad, deg: deg, wrap180: wrap180, wrapPi: wrapPi,
    controlPoints: controlPoints, handles: handles, pathPoints: pathPoints, generate: generate, stateAt: stateAt,
    startPose: startPose, endPose: endPose, findPath: findPath, autoTimeline: autoTimeline, poseAtAuto: poseAtAuto,
    corners: corners, isJavaIdentifier: isJavaIdentifier, constName: constName, uniqueName: uniqueName,
    warnings: warnings, mirrorPath: mirrorPath, toJava: toJava, normalize: normalize
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.PlannerModel = api;
})(this);
