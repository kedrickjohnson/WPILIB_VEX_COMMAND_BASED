/*
 * VEX Command-Based for VS Code.
 *
 *   VEX: Create a New Project        template robot or Clawbot, with your name and package
 *   VEX: Build / Simulate / Upload   Gradle tasks for the selected robot (status bar buttons too)
 *   VEX: New Subsystem / Command / Auto   right-click a folder under src/main/java
 *   VEX: Manage Vendor Libraries     install / update / remove vendordeps, like WPILib's
 */
'use strict';
const vscode = require('vscode');
const fs = require('fs');
const path = require('path');
const generator = require('./src/generator');
const wizards = require('./src/wizards');
const project = require('./src/project');
const tasks = require('./src/tasks');
const vendordeps = require('./src/vendordeps');
const vendorUi = require('./src/vendorUi');

let bundleCache = null;
let statusItems = [];
let robotsCache = [];

function loadBundle(context) {
  if (!bundleCache) {
    const file = path.join(context.extensionPath, 'resources', 'templates.json');
    bundleCache = JSON.parse(fs.readFileSync(file, 'utf8'));
  }
  return bundleCache;
}

// ---------------------------------------------------------------- robots in the workspace

function scanRobots() {
  const robots = [];
  for (const folder of vscode.workspace.workspaceFolders || []) {
    for (const r of project.findRobots(folder.uri.fsPath)) robots.push(Object.assign({ folder }, r));
  }
  robotsCache = robots;
  vscode.commands.executeCommand('setContext', 'vexCommandBased.isProject', robots.length > 0);
  return robots;
}

function robotKey(r) {
  return r.gradleRoot + '|' + r.gradlePath;
}

/** The selected robot, asking if there are several and none is chosen yet. */
async function pickRobot(context, forceAsk) {
  const robots = scanRobots();
  if (robots.length === 0) {
    const choice = await vscode.window.showWarningMessage(
      'No VEX robot project found in this workspace. Open a project folder, or create one.',
      'Create a New Project');
    if (choice) vscode.commands.executeCommand('vexCommandBased.newProject');
    return null;
  }
  const saved = context.workspaceState.get('vexCommandBased.robot');
  const current = robots.find((r) => robotKey(r) === saved);
  if (!forceAsk && (robots.length === 1 || current)) return current || robots[0];
  const pick = await vscode.window.showQuickPick(
    robots.map((r) => ({
      label: r.name || path.basename(r.dir),
      description: (r.gradlePath || 'root project') + (r.slot ? '  ·  slot ' + r.slot : ''),
      detail: r.dir,
      robot: r
    })),
    { placeHolder: 'Which robot program?' });
  if (!pick) return null;
  await context.workspaceState.update('vexCommandBased.robot', robotKey(pick.robot));
  updateStatusBar(context);
  return pick.robot;
}

// ---------------------------------------------------------------- build / simulate / upload

async function runAction(context, action) {
  const robot = await pickRobot(context, false);
  if (!robot) return;
  if (action.startsWith('simulate') && !robot.hasSim) {
    vscode.window.showWarningMessage('"' + (robot.name || robot.gradlePath) + '" has no simulate task in its build file.');
    return;
  }
  if (action === 'upload') {
    const cli = await tasks.findHydrozoaCli();
    if (!cli) {
      const choice = await vscode.window.showWarningMessage(
        'Uploading needs the Hydrozoa CLI, which wasn\'t found. Install it with Rust\'s cargo: ' + tasks.CLI_INSTALL,
        'Install in Terminal', 'Copy Command', 'Upload Anyway');
      if (choice === 'Install in Terminal') return installCli();
      if (choice === 'Copy Command') { await vscode.env.clipboard.writeText(tasks.CLI_INSTALL); return; }
      if (choice !== 'Upload Anyway') return;
    }
    if (vscode.workspace.getConfiguration('vexCommandBased').get('saveBeforeRun', true)) await vscode.workspace.saveAll(false);
  } else if (vscode.workspace.getConfiguration('vexCommandBased').get('saveBeforeRun', true)) {
    await vscode.workspace.saveAll(false);
  }
  return vscode.tasks.executeTask(tasks.createTask(action, robot, robot.folder));
}

function installCli() {
  const term = vscode.window.createTerminal('Install Hydrozoa CLI');
  term.show();
  term.sendText(tasks.CLI_INSTALL);
}

function taskProvider() {
  return {
    provideTasks() {
      const out = [];
      for (const r of scanRobots()) {
        for (const action of Object.keys(tasks.ACTIONS)) {
          if (action.startsWith('simulate') && !r.hasSim) continue;
          out.push(tasks.createTask(action, r, r.folder));
        }
      }
      return out;
    },
    resolveTask(task) {
      const def = task.definition;
      if (!def || !tasks.ACTIONS[def.task]) return undefined;
      const robots = scanRobots();
      const r = robots.find((x) => x.gradlePath === (def.robot || '')) || robots[0];
      if (!r) return undefined;
      const resolved = tasks.createTask(def.task, r, task.scope);
      resolved.definition = def;
      return resolved;
    }
  };
}

// ---------------------------------------------------------------- status bar

function createStatusBar(context) {
  const make = (text, command, tooltip, priority) => {
    const item = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Left, priority);
    item.text = text;
    item.command = command;
    item.tooltip = tooltip;
    context.subscriptions.push(item);
    return item;
  };
  statusItems = [
    make('$(robot) VEX', 'vexCommandBased.selectRobot', 'Choose which robot program to build', 50),
    make('$(tools) Build', 'vexCommandBased.build', 'Build the robot program (gradlew assemble)', 49),
    make('$(play) Simulate', 'vexCommandBased.simulate', 'Run the robot code in the desktop simulator', 48),
    make('$(cloud-upload) Upload', 'vexCommandBased.upload', 'Build and upload to the V5 brain over USB', 47)
  ];
  updateStatusBar(context);
}

function updateStatusBar(context) {
  const show = vscode.workspace.getConfiguration('vexCommandBased').get('showStatusBar', true);
  const robots = robotsCache;
  if (!show || robots.length === 0) { statusItems.forEach((i) => i.hide()); return; }
  const saved = context.workspaceState.get('vexCommandBased.robot');
  const current = robots.find((r) => robotKey(r) === saved) || robots[0];
  statusItems[0].text = '$(robot) ' + (current.name || 'VEX') + (current.slot ? ' · ' + current.slot : '');
  statusItems[0].tooltip = robots.length > 1 ? 'Robot program: ' + current.label + ' (click to change)' : 'Robot program: ' + current.label;
  statusItems.forEach((i) => i.show());
}

// ---------------------------------------------------------------- new project

async function newProject(context) {
  const kind = await vscode.window.showQuickPick([
    { label: '$(rocket) Command-based template', description: '6-motor tank drive, intake, path planner autos', kind: 'template' },
    { label: '$(robot) V5 Clawbot', description: 'Complete Clawbot: drive, arm, claw, autos', kind: 'clawbot' }
  ], { placeHolder: 'Start from', title: 'New VEX Project (1/4)' });
  if (!kind) return;

  const parent = await vscode.window.showOpenDialog({
    canSelectFolders: true, canSelectFiles: false, canSelectMany: false,
    openLabel: 'Create Project Here', title: 'New VEX Project (2/4): choose the parent folder',
    defaultUri: vscode.workspace.workspaceFolders ? vscode.Uri.file(path.dirname(vscode.workspace.workspaceFolders[0].uri.fsPath)) : undefined
  });
  if (!parent || !parent[0]) return;
  const parentDir = parent[0].fsPath;

  const name = await vscode.window.showInputBox({
    title: 'New VEX Project (3/4)', prompt: 'Project name (also the folder name)',
    value: kind.kind === 'clawbot' ? 'Clawbot' : 'MyRobot',
    validateInput: (v) => generator.validateProjectName(v) ||
      (fs.existsSync(path.join(parentDir, v)) && fs.readdirSync(path.join(parentDir, v)).length ? 'A folder with that name already exists there.' : null)
  });
  if (!name) return;

  const pkg = await vscode.window.showInputBox({
    title: 'New VEX Project (4/4)', prompt: 'Java package for your robot code',
    value: vscode.workspace.getConfiguration('vexCommandBased').get('defaultPackage', '') || generator.suggestPackage(name),
    validateInput: (v) => generator.validatePackage(v)
  });
  if (!pkg) return;

  let result;
  try {
    result = await vscode.window.withProgress(
      { location: vscode.ProgressLocation.Notification, title: 'Creating ' + name + '...' },
      async () => {
        const bundle = loadBundle(context);
        installOfflineLibraries(context);
        return generator.generateProject({ bundle, kind: kind.kind, name, pkg, parentDir });
      });
  } catch (e) {
    vscode.window.showErrorMessage('Couldn\'t create the project: ' + e.message);
    return;
  }

  const uri = vscode.Uri.file(result.root);
  const inWorkspace = (vscode.workspace.workspaceFolders || []).some((f) => result.root.startsWith(f.uri.fsPath + path.sep));
  if (inWorkspace) {
    scanRobots(); updateStatusBar(context);
    vscode.window.showTextDocument(vscode.Uri.file(result.mainFile));
    return;
  }
  // Remember to open RobotContainer once the new window loads.
  await context.globalState.update('vexCommandBased.openOnStart', result.mainFile);
  const choice = await vscode.window.showInformationMessage(
    'Created ' + name + '.', 'Open', 'Open in New Window', 'Add to Workspace');
  if (choice === 'Open') vscode.commands.executeCommand('vscode.openFolder', uri, false);
  else if (choice === 'Open in New Window') vscode.commands.executeCommand('vscode.openFolder', uri, true);
  else if (choice === 'Add to Workspace') {
    vscode.workspace.updateWorkspaceFolders((vscode.workspace.workspaceFolders || []).length, 0, { uri });
    context.globalState.update('vexCommandBased.openOnStart', undefined);
    vscode.window.showTextDocument(vscode.Uri.file(result.mainFile));
  }
}

// ---------------------------------------------------------------- vendor libraries

/**
 * Copies the bundled VexLib Maven files to ~/.vex-command/maven, so projects build without
 * reaching the library's website (like WPILib's offline vendordep folder).
 */
function installOfflineLibraries(context) {
  try {
    const n = vendordeps.installOffline(loadBundle(context).vexlib.maven);
    if (n) console.log('[vex] installed ' + n + ' offline library files to ' + vendordeps.offlineMavenDir());
  } catch (e) {
    console.warn('[vex] could not install offline libraries: ' + e.message);
  }
}

// ---------------------------------------------------------------- class wizards

/** The folder a wizard writes into: the right-clicked folder, the open file's folder, or ask. */
async function targetFolder(uri) {
  let folder = uri && uri.fsPath;
  if (folder && fs.existsSync(folder) && fs.statSync(folder).isFile()) folder = path.dirname(folder);
  if (!folder && vscode.window.activeTextEditor && vscode.window.activeTextEditor.document.uri.scheme === 'file') {
    folder = path.dirname(vscode.window.activeTextEditor.document.uri.fsPath);
  }
  if (folder && wizards.packageForFolder(folder)) return folder;
  // Offer the robot's own packages.
  const robots = scanRobots();
  const choices = [];
  for (const r of robots) {
    const src = path.join(r.dir, 'src', 'main', 'java');
    (function walk(d, depth) {
      if (depth > 8) return;
      let entries; try { entries = fs.readdirSync(d, { withFileTypes: true }); } catch (e) { return; }
      const hasJava = entries.some((e) => e.isFile() && e.name.endsWith('.java'));
      if (hasJava) choices.push({ label: wizards.packageForFolder(d).pkg, description: path.relative(r.gradleRoot, d), folder: d });
      for (const e of entries) if (e.isDirectory()) walk(path.join(d, e.name), depth + 1);
    })(src, 0);
  }
  if (choices.length === 0) {
    vscode.window.showWarningMessage('Right-click a folder under src/main/java, or open a VEX robot project first.');
    return null;
  }
  const pick = await vscode.window.showQuickPick(choices, { placeHolder: 'Which package should the new class go in?' });
  return pick ? pick.folder : null;
}

async function askClassName(kindLabel, folder, suggestion) {
  return vscode.window.showInputBox({
    title: 'New ' + kindLabel,
    prompt: 'Class name (in ' + (wizards.packageForFolder(folder).pkg || 'the default package') + ')',
    value: suggestion,
    validateInput: (v) => wizards.validateClassName(v) ||
      (fs.existsSync(path.join(folder, v + '.java')) ? v + '.java already exists here.' : null)
  });
}

async function writeAndOpen(folder, name, text) {
  const file = path.join(folder, name + '.java');
  if (fs.existsSync(file)) { vscode.window.showErrorMessage(name + '.java already exists.'); return; }
  fs.writeFileSync(file, text);
  const doc = await vscode.workspace.openTextDocument(file);
  await vscode.window.showTextDocument(doc);
  return file;
}

async function newSubsystem(uri) {
  const folder = await targetFolder(uri);
  if (!folder) return;
  const name = await askClassName('Subsystem', folder, 'Shooter');
  if (!name) return;
  const { pkg } = wizards.packageForFolder(folder);
  await writeAndOpen(folder, name, wizards.subsystem({ name, pkg }));
}

async function newMotorSubsystem(uri) {
  const folder = await targetFolder(uri);
  if (!folder) return;
  const name = await askClassName('Subsystem with a Motor', folder, 'Roller');
  if (!name) return;
  const portText = await vscode.window.showInputBox({
    title: 'New Subsystem with a Motor', prompt: 'Motor smart port (1-21)', value: '7',
    validateInput: (v) => /^\d+$/.test(v) && +v >= 1 && +v <= 21 ? null : 'Enter a port from 1 to 21.'
  });
  if (!portText) return;
  const cart = await vscode.window.showQuickPick([
    { label: 'GREEN', description: '200 rpm, 18:1 (standard)' },
    { label: 'BLUE', description: '600 rpm, 6:1 (fast)' },
    { label: 'RED', description: '100 rpm, 36:1 (torque)' }
  ], { title: 'New Subsystem with a Motor', placeHolder: 'Gear cartridge installed in the motor' });
  if (!cart) return;
  const idle = await vscode.window.showQuickPick([
    { label: 'kCoast', description: 'spins freely when stopped (rollers, flywheels)' },
    { label: 'kBrake', description: 'stops quickly' },
    { label: 'kHold', description: 'holds its position (arms, lifts)' }
  ], { title: 'New Subsystem with a Motor', placeHolder: 'What the motor does when stopped' });
  if (!idle) return;

  const { sourceRoot, pkg } = wizards.packageForFolder(folder);
  const opts = { name, pkg, port: +portText, cartridge: cart.label, idleMode: idle.label, currentLimit: 2.5, constantsClass: null };
  const constants = wizards.findConstants(sourceRoot, pkg);
  if (constants) {
    const text = fs.readFileSync(constants.file, 'utf8');
    const updated = wizards.addToConstants(text, opts);
    if (updated) {
      fs.writeFileSync(constants.file, updated);
      opts.constantsClass = constants.fq;
    } else if (new RegExp('class\\s+' + name + 'Constants\\b').test(text)) {
      opts.constantsClass = constants.fq; // already there: use it
    }
  }
  await writeAndOpen(folder, name, wizards.motorSubsystem(opts));
  if (opts.constantsClass) {
    vscode.window.showInformationMessage('Added ' + name + 'Constants (port ' + opts.port + ') to ' + path.basename(constants.file) + '.');
  }
}

async function newCommand(uri) {
  const folder = await targetFolder(uri);
  if (!folder) return;
  const name = await askClassName('Command', folder, 'RunShooter');
  if (!name) return;
  const { sourceRoot } = wizards.packageForFolder(folder);
  const projectRoot = path.resolve(sourceRoot, '..', '..', '..');
  const subs = wizards.findSubsystems(projectRoot);
  let requirement = null;
  if (subs.length) {
    const pick = await vscode.window.showQuickPick(
      [{ label: '$(circle-slash) None', sub: null }].concat(subs.map((s) => ({ label: s.name, description: s.fq, sub: s }))),
      { title: 'New Command', placeHolder: 'Which subsystem does it use? (added with addRequirements)' });
    if (!pick) return;
    requirement = pick.sub;
  }
  const { pkg } = wizards.packageForFolder(folder);
  await writeAndOpen(folder, name, wizards.command({ name, pkg, requirement }));
}

async function newAuto(uri) {
  const folder = await targetFolder(uri);
  if (!folder) return;
  const name = await askClassName('Auto', folder, 'LeftSideAuto');
  if (!name) return;
  const { pkg } = wizards.packageForFolder(folder);
  await writeAndOpen(folder, name, wizards.auto({ name, pkg }));
}

// ---------------------------------------------------------------- activation

const COMMANDS = {
  'vexCommandBased.newProject': (ctx) => newProject(ctx),
  'vexCommandBased.build': (ctx) => runAction(ctx, 'build'),
  'vexCommandBased.simulate': (ctx) => runAction(ctx, 'simulate'),
  'vexCommandBased.simulateTune': (ctx) => runAction(ctx, 'simulateTune'),
  'vexCommandBased.upload': (ctx) => runAction(ctx, 'upload'),
  'vexCommandBased.selectRobot': (ctx) => pickRobot(ctx, true),
  'vexCommandBased.installCli': () => installCli(),
  'vexCommandBased.newSubsystem': (ctx, uri) => newSubsystem(uri),
  'vexCommandBased.newMotorSubsystem': (ctx, uri) => newMotorSubsystem(uri),
  'vexCommandBased.newCommand': (ctx, uri) => newCommand(uri),
  'vexCommandBased.newAuto': (ctx, uri) => newAuto(uri),
  'vexCommandBased.manageVendorLibs': (ctx) => vendorUi.manage({ context: ctx, pickRobot, loadBundle, runAction }),
  'vexCommandBased.openDocs': () => vscode.env.openExternal(vscode.Uri.parse(
    vscode.workspace.getConfiguration('vexCommandBased').get('docsUrl', 'https://kedrickjohnson.github.io/vex-command-template/')))
};

function activate(context) {
  for (const [id, fn] of Object.entries(COMMANDS)) {
    context.subscriptions.push(vscode.commands.registerCommand(id, (...args) => fn(context, ...args)));
  }
  context.subscriptions.push(vscode.tasks.registerTaskProvider(tasks.TASK_TYPE, taskProvider()));

  const robots = scanRobots();
  createStatusBar(context);
  if (robots.some((r) => fs.existsSync(path.join(r.dir, vendordeps.VENDORDEPS_DIR)))) {
    setTimeout(() => installOfflineLibraries(context), 0);
  }

  const watcher = vscode.workspace.createFileSystemWatcher('**/{build.gradle.kts,build.gradle,settings.gradle.kts,settings.gradle}');
  const rescan = () => { scanRobots(); updateStatusBar(context); };
  watcher.onDidChange(rescan); watcher.onDidCreate(rescan); watcher.onDidDelete(rescan);
  context.subscriptions.push(watcher);
  context.subscriptions.push(vscode.workspace.onDidChangeWorkspaceFolders(rescan));
  context.subscriptions.push(vscode.workspace.onDidChangeConfiguration((e) => {
    if (e.affectsConfiguration('vexCommandBased')) updateStatusBar(context);
  }));

  // A project created in the previous window: open its RobotContainer.
  const pending = context.globalState.get('vexCommandBased.openOnStart');
  if (pending && (vscode.workspace.workspaceFolders || []).some((f) => pending.startsWith(f.uri.fsPath + path.sep))) {
    context.globalState.update('vexCommandBased.openOnStart', undefined);
    if (fs.existsSync(pending)) vscode.window.showTextDocument(vscode.Uri.file(pending));
  }
}

function deactivate() {}

module.exports = { activate, deactivate, COMMANDS };
