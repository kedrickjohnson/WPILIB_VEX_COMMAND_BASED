#!/usr/bin/env node
/*
 * Tests for the extension. No npm packages needed.
 *
 *   node test/run.js                      # unit tests + activation with a fake vscode
 *   HYDROZOA_CLASSES=/path node test/run.js   # also compiles generated projects with javac
 *                                             # and runs their simulator (needs a JDK 21+)
 *
 * HYDROZOA_CLASSES is a folder (or jar) with the compiled dev.vexide.hydrozoa SDK classes.
 */
'use strict';
const assert = require('assert');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');
const Module = require('module');

const EXT = path.resolve(__dirname, '..');
const REPO = path.resolve(EXT, '..');
const generator = require('../src/generator');
const wizards = require('../src/wizards');
const project = require('../src/project');

let passed = 0, failed = 0;
function test(name, fn) {
  try { fn(); passed++; console.log('  ok   ' + name); }
  catch (e) { failed++; console.log('  FAIL ' + name + '\n       ' + (e.stack || e).toString().split('\n').slice(0, 4).join('\n       ')); }
}
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'vexext-'));
const bundle = JSON.parse(fs.readFileSync(path.join(EXT, 'resources', 'templates.json'), 'utf8'));
const hz = process.env.HYDROZOA_CLASSES;

console.log('validation');
test('project names', () => {
  assert.strictEqual(generator.validateProjectName('MyRobot'), null);
  assert.strictEqual(generator.validateProjectName('team-1234_bot'), null);
  assert.ok(generator.validateProjectName(''));
  assert.ok(generator.validateProjectName('1234'));
  assert.ok(generator.validateProjectName('my robot'));
});
test('packages', () => {
  assert.strictEqual(generator.validatePackage('com.team1234.robot'), null);
  assert.ok(generator.validatePackage('robot'));
  assert.ok(generator.validatePackage('com.1234.robot'));
  assert.ok(generator.validatePackage('com.class.robot'));
  assert.ok(generator.validatePackage('com.Team.robot'));
  assert.ok(generator.validatePackage('com.robojacks.lib.x'));
  assert.strictEqual(generator.suggestPackage('Team 1234 Bot'), 'com.team1234bot.robot');
  assert.strictEqual(generator.suggestPackage('1234A'), 'com.team1234a.robot');
  assert.strictEqual(generator.validatePackage(generator.suggestPackage('99-X')), null);
});
test('class names', () => {
  assert.strictEqual(wizards.validateClassName('Shooter'), null);
  assert.ok(wizards.validateClassName('shooter'));
  assert.ok(wizards.validateClassName('2Shooter'));
  assert.ok(wizards.validateClassName('My Shooter'));
});
test('package for folder', () => {
  const p = wizards.packageForFolder(path.join('/x', 'proj', 'src', 'main', 'java', 'com', 'team', 'robot'));
  assert.strictEqual(p.pkg, 'com.team.robot');
  assert.strictEqual(p.sourceRoot, path.join('/x', 'proj', 'src', 'main', 'java'));
  assert.strictEqual(wizards.packageForFolder(path.join('/x', 'proj', 'src', 'main', 'java')).pkg, '');
  assert.strictEqual(wizards.packageForFolder('/x/proj/docs'), null);
});

console.log('renaming');
test('package rename touches robot code, not the library', () => {
  const out = generator.renameFiles({
    'src/main/java/com/robojacks/robot/Robot.java': { text: 'package com.robojacks.robot;\nimport com.robojacks.robot.subsystems.Intake;\nimport com.robojacks.lib.command.Command;\nimport static com.robojacks.robot.Constants.*;\n' },
    'src/main/java/com/robojacks/robot/subsystems/Intake.java': { text: 'package com.robojacks.robot.subsystems;' },
    'planner-project.json': { text: '{"package":"com.robojacks.robot.autos"}' }
  }, 'com.robojacks.robot', 'org.team9.bot');
  const robot = out['src/main/java/org/team9/bot/Robot.java'].text;
  assert.ok(robot.includes('package org.team9.bot;'));
  assert.ok(robot.includes('import org.team9.bot.subsystems.Intake;'));
  assert.ok(robot.includes('import com.robojacks.lib.command.Command;'));
  assert.ok(robot.includes('import static org.team9.bot.Constants.*;'));
  assert.ok(out['src/main/java/org/team9/bot/subsystems/Intake.java']);
  assert.ok(out['planner-project.json'].text.includes('org.team9.bot.autos'));
});
test('rename leaves look-alike packages alone', () => {
  const out = generator.renameFiles({ 'src/main/java/A.java': { text: 'com.robojacks.robotics.X; com.robojacks.robot.Y;' } }, 'com.robojacks.robot', 'a.b');
  assert.strictEqual(out['src/main/java/A.java'].text, 'com.robojacks.robotics.X; a.b.Y;');
});

console.log('bundle');
test('bundle has library, both templates, wrapper and runtime', () => {
  assert.ok(Object.keys(bundle.vexlib).some((f) => f.endsWith('lib/command/CommandScheduler.java')));
  assert.ok(Object.keys(bundle.vexlib).some((f) => f.endsWith('lib/hal/hydrozoa/HydrozoaHardware.java')));
  assert.ok(bundle.templates.template.files['src/main/java/com/robojacks/robot/subsystems/DriveSubsystem.java']);
  assert.ok(bundle.templates.template.files['src/sim/java/com/robojacks/robot/SimMain.java']);
  assert.ok(bundle.templates.clawbot.files['src/main/java/com/robojacks/clawbot/subsystems/Arm.java']);
  assert.ok(bundle.common['gradle/wrapper/gradle-wrapper.jar'].base64);
  const bin = Buffer.from(bundle.common['hydrozoa.bin'].base64, 'base64');
  assert.strictEqual(bin.length, fs.statSync(path.join(REPO, 'hydrozoa.bin')).size);
});
test('bundle is up to date with the repo', () => {
  for (const [rel, c] of Object.entries(bundle.vexlib)) {
    assert.strictEqual(c.text, fs.readFileSync(path.join(REPO, 'vexlib', rel), 'utf8'), rel + ' changed: run node scripts/bundle.js');
  }
});

console.log('generate');
const projects = {};
for (const kind of ['template', 'clawbot']) {
  test('generates ' + kind, () => {
    const name = kind === 'template' ? 'Team1234Bot' : 'MyClawbot';
    const pkg = kind === 'template' ? 'com.team1234.robot' : 'org.vexu.claw';
    const r = generator.generateProject({ bundle, kind, name, pkg, parentDir: tmp });
    projects[kind] = { root: r.root, pkg };
    assert.ok(fs.existsSync(r.mainFile), 'RobotContainer at ' + r.mainFile);
    const build = fs.readFileSync(path.join(r.root, 'build.gradle.kts'), 'utf8');
    assert.ok(build.includes('entrypoint = "' + pkg + '.Main"'));
    assert.ok(build.includes('mainClass.set("' + pkg + '.SimMain")'));
    assert.ok(fs.readFileSync(path.join(r.root, 'settings.gradle.kts'), 'utf8').includes('include("vexlib")'));
    assert.ok(fs.existsSync(path.join(r.root, 'vexlib', 'build.gradle.kts')));
    assert.ok(fs.existsSync(path.join(r.root, 'vexlib', 'src', 'main', 'java', 'com', 'robojacks', 'lib', 'robot', 'VexRobot.java')));
    if (process.platform !== 'win32') assert.ok(fs.statSync(path.join(r.root, 'gradlew')).mode & 0o100, 'gradlew executable');
    // No old package left in robot code
    const robotSrc = path.join(r.root, 'src');
    const leftovers = [];
    (function walk(d) { for (const e of fs.readdirSync(d, { withFileTypes: true })) {
      const f = path.join(d, e.name);
      if (e.isDirectory()) walk(f); else if (/com\.robojacks\.(robot|clawbot)\b/.test(fs.readFileSync(f, 'utf8'))) leftovers.push(f);
    } })(robotSrc);
    assert.deepStrictEqual(leftovers, []);
  });
}
test('refuses a non-empty folder', () => {
  assert.throws(() => generator.generateProject({ bundle, kind: 'template', name: 'Team1234Bot', pkg: 'a.b', parentDir: tmp }), /already exists/);
});

console.log('detect');
test('finds the generated single-robot project', () => {
  const robots = project.findRobots(projects.template.root);
  assert.strictEqual(robots.length, 1);
  assert.strictEqual(robots[0].gradlePath, '');
  assert.strictEqual(robots[0].name, 'Team1234Bot');
  assert.strictEqual(robots[0].slot, 1);
  assert.ok(robots[0].hasSim);
  assert.strictEqual(project.taskPath(robots[0], 'assemble'), 'assemble');
});
test('finds both robots in the repo, not the library', () => {
  const robots = project.findRobots(REPO);
  assert.deepStrictEqual(robots.map((r) => r.gradlePath).sort(), [':examples:clawbot', ':template']);
  const claw = robots.find((r) => r.gradlePath === ':examples:clawbot');
  assert.strictEqual(claw.slot, 2);
  assert.strictEqual(project.taskPath(claw, 'hydrozoaUpload'), ':examples:clawbot:hydrozoaUpload');
});
test('finds projects one level down (a folder of projects)', () => {
  assert.strictEqual(project.findRobots(tmp).length, 2);
});
test('settings parsing', () => {
  assert.deepStrictEqual(project.parseIncludes('include("a", ":b:c")\n// include("x")\ninclude \'d\''), ['a', 'b:c', 'd']);
});

console.log('wizards');
const genPkgDir = (kind, sub) => path.join(projects[kind].root, 'src', 'main', 'java', ...projects[kind].pkg.split('.'), ...(sub ? [sub] : []));
test('subsystem, motor subsystem, command and auto written into the template', () => {
  const subDir = genPkgDir('template', 'subsystems');
  const { sourceRoot, pkg } = wizards.packageForFolder(subDir);
  fs.writeFileSync(path.join(subDir, 'Shooter.java'), wizards.subsystem({ name: 'Shooter', pkg }));

  const constants = wizards.findConstants(sourceRoot, pkg);
  assert.ok(constants && constants.fq === 'com.team1234.robot.Constants');
  const opts = { name: 'Roller', pkg, port: 7, cartridge: 'GREEN', idleMode: 'kCoast', currentLimit: 2.0, constantsClass: constants.fq };
  const updated = wizards.addToConstants(fs.readFileSync(constants.file, 'utf8'), opts);
  assert.ok(updated.includes('class RollerConstants'));
  assert.strictEqual(wizards.addToConstants(updated, opts), null, 'not added twice');
  fs.writeFileSync(constants.file, updated);
  fs.writeFileSync(path.join(subDir, 'Roller.java'), wizards.motorSubsystem(opts));
  fs.writeFileSync(path.join(subDir, 'Lift.java'), wizards.motorSubsystem({ name: 'Lift', pkg, port: 8, cartridge: 'RED', idleMode: 'kHold', currentLimit: 2.5, constantsClass: null }));

  const subs = wizards.findSubsystems(projects.template.root);
  const names = subs.map((s) => s.name);
  for (const n of ['DriveSubsystem', 'Intake', 'Shooter', 'Roller', 'Lift']) assert.ok(names.includes(n), n + ' in ' + names);
  const cmdDir = genPkgDir('template', 'commands');
  const shooter = subs.find((s) => s.name === 'Shooter');
  fs.writeFileSync(path.join(cmdDir, 'RunShooter.java'), wizards.command({ name: 'RunShooter', pkg: wizards.packageForFolder(cmdDir).pkg, requirement: shooter }));
  fs.writeFileSync(path.join(cmdDir, 'Wait2.java'), wizards.command({ name: 'Wait2', pkg: wizards.packageForFolder(cmdDir).pkg, requirement: null }));
  const autoDir = genPkgDir('template', 'autos');
  fs.writeFileSync(path.join(autoDir, 'LeftSideAuto.java'), wizards.auto({ name: 'LeftSideAuto', pkg: wizards.packageForFolder(autoDir).pkg }));
  // same-package requirement gets no import
  const same = wizards.command({ name: 'X', pkg: 'a.b', requirement: { name: 'S', fq: 'a.b.S' } });
  assert.ok(!same.includes('import a.b.S;'));
});

// ---------------------------------------------------------------- javac + simulator
function javaFiles(dir) {
  const out = [];
  (function walk(d) { if (!fs.existsSync(d)) return; for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const f = path.join(d, e.name); if (e.isDirectory()) walk(f); else if (f.endsWith('.java')) out.push(f);
  } })(dir);
  return out;
}
if (hz) {
  console.log('compile + simulate (javac)');
  for (const kind of ['template', 'clawbot']) {
    test(kind + ' compiles and its auto runs in the simulator', () => {
      const root = projects[kind].root;
      const classes = path.join(tmp, kind + '-classes');
      const files = javaFiles(path.join(root, 'vexlib', 'src')).concat(javaFiles(path.join(root, 'src', 'main', 'java')), javaFiles(path.join(root, 'src', 'sim', 'java')));
      fs.writeFileSync(path.join(tmp, kind + '-files.txt'), files.join('\n'));
      execFileSync('javac', ['-nowarn', '-d', classes, '-cp', hz, '@' + path.join(tmp, kind + '-files.txt')], { stdio: 'pipe' });
      const out = execFileSync('java', ['-cp', classes + path.delimiter + hz, projects[kind].pkg + '.SimMain'], { timeout: 120000 }).toString();
      assert.ok(/Auto done/.test(out), 'simulator output:\n' + out.slice(-600));
    });
  }
} else {
  console.log('  skip compile + simulate (set HYDROZOA_CLASSES to run it)');
}

// ---------------------------------------------------------------- activation with a fake vscode
console.log('activation');
test('activate registers every command in package.json and finds the robot', () => {
  const registered = [], contexts = {}, items = [];
  const disposable = { dispose() {} };
  const emitter = () => disposable;
  const fakeVscode = {
    commands: {
      registerCommand: (id) => { registered.push(id); return disposable; },
      executeCommand: (id, key, val) => { if (id === 'setContext') contexts[key] = val; return Promise.resolve(); }
    },
    tasks: { registerTaskProvider: () => disposable, executeTask: () => Promise.resolve() },
    workspace: {
      workspaceFolders: [{ uri: { fsPath: projects.clawbot.root }, name: 'MyClawbot', index: 0 }],
      getConfiguration: () => ({ get: (k, d) => d }),
      createFileSystemWatcher: () => ({ onDidChange: emitter, onDidCreate: emitter, onDidDelete: emitter, dispose() {} }),
      onDidChangeWorkspaceFolders: emitter, onDidChangeConfiguration: emitter
    },
    window: {
      createStatusBarItem: () => { const i = { show() { i.shown = true; }, hide() { i.shown = false; }, dispose() {} }; items.push(i); return i; },
      showTextDocument: () => Promise.resolve()
    },
    StatusBarAlignment: { Left: 1, Right: 2 },
    TaskScope: { Workspace: 2 }, TaskGroup: { Build: {} },
    TaskRevealKind: { Always: 1 }, TaskPanelKind: { Dedicated: 2 },
    ShellExecution: function (cmd, args, opts) { this.command = cmd; this.args = args; this.options = opts; },
    Task: function (def, scope, name, source, exec) { this.definition = def; this.name = name; this.execution = exec; },
    Uri: { file: (p) => ({ fsPath: p }), parse: (s) => ({ s }) },
    env: {}
  };
  const origLoad = Module._load;
  Module._load = function (request) { return request === 'vscode' ? fakeVscode : origLoad.apply(this, arguments); };
  try {
    for (const k of Object.keys(require.cache)) if (k.startsWith(EXT + path.sep) && !k.includes(path.sep + 'test' + path.sep)) delete require.cache[k];
    const ext = require('../extension');
    const state = new Map();
    const ctx = { subscriptions: [], extensionPath: EXT,
      workspaceState: { get: (k) => state.get(k), update: (k, v) => { state.set(k, v); return Promise.resolve(); } },
      globalState: { get: () => undefined, update: () => Promise.resolve() } };
    ext.activate(ctx);
    const pkg = JSON.parse(fs.readFileSync(path.join(EXT, 'package.json'), 'utf8'));
    const declared = pkg.contributes.commands.map((c) => c.command).sort();
    assert.deepStrictEqual(registered.slice().sort(), declared);
    assert.strictEqual(contexts['vexCommandBased.isProject'], true);
    assert.strictEqual(items.length, 4);
    assert.ok(items.every((i) => i.shown));
    assert.ok(items[0].text.includes('MyClawbot'));

    // The task for upload runs the right gradle task from the project root.
    const tasks = require('../src/tasks');
    const robot = project.findRobots(projects.clawbot.root)[0];
    const t = tasks.createTask('upload', robot);
    assert.deepStrictEqual(t.execution.args, ['hydrozoaUpload']);
    assert.strictEqual(t.execution.options.cwd, projects.clawbot.root);
    const repoClaw = project.findRobots(REPO).find((r) => r.gradlePath === ':examples:clawbot');
    assert.deepStrictEqual(tasks.createTask('simulateTune', repoClaw).execution.args, [':examples:clawbot:simulate', '--args=tune']);
    // Menus reference only declared commands
    const used = [].concat(...Object.values(pkg.contributes.menus)).map((m) => m.command).filter(Boolean);
    for (const c of used) assert.ok(declared.includes(c), c + ' in menus but not declared');
  } finally {
    Module._load = origLoad;
  }
});

test('problem matcher matches javac and Kotlin script errors', () => {
  const pkg = JSON.parse(fs.readFileSync(path.join(EXT, 'package.json'), 'utf8'));
  const re = new RegExp(pkg.contributes.problemMatchers[0].pattern.regexp);
  const m = re.exec('/home/me/Bot/src/main/java/com/a/Robot.java:12: error: cannot find symbol');
  assert.ok(m && m[1].endsWith('Robot.java') && m[2] === '12' && m[4] === 'error');
  const k = re.exec('e: file:///home/me/Bot/build.gradle.kts:7:5: error: unresolved reference');
  assert.ok(k && k[1] === '/home/me/Bot/build.gradle.kts' && k[3] === '5');
});

console.log('\n' + passed + ' passed, ' + failed + ' failed' + (failed ? '' : '  (temp: ' + tmp + ')'));
if (!failed && !process.env.KEEP) fs.rmSync(tmp, { recursive: true, force: true });
process.exit(failed ? 1 : 0);
