/*
 * Build / Simulate / Upload as VS Code tasks (type "vex"), so they also show up under
 * Terminal → Run Task and can be bound to keys or used in tasks.json.
 */
'use strict';
const vscode = require('vscode');
const { execFile } = require('child_process');
const os = require('os');
const path = require('path');
const fs = require('fs');
const { taskPath } = require('./project');

const TASK_TYPE = 'vex';

const ACTIONS = {
  build:        { label: 'Build',             gradle: ['assemble'],                  group: 'build' },
  simulate:     { label: 'Simulate',          gradle: ['simulate'] },
  simulateTune: { label: 'Simulate (tuning)', gradle: ['simulate', '--args=tune'] },
  upload:       { label: 'Upload',            gradle: ['hydrozoaUpload'] }
};

function gradleCommand(robot) {
  if (process.platform === 'win32') return '.\\gradlew.bat';
  return './gradlew';
}

/** Builds the vscode.Task for an action on a robot. */
function createTask(action, robot, folder) {
  const a = ACTIONS[action];
  if (!a) throw new Error('Unknown VEX task: ' + action);
  const args = a.gradle.slice();
  args[0] = taskPath(robot, args[0]);
  const extra = vscode.workspace.getConfiguration('vexCommandBased').get('gradleArgs', []);
  const definition = { type: TASK_TYPE, task: action, robot: robot.gradlePath };
  const options = { cwd: robot.gradleRoot };
  if (action === 'upload') {
    // The Gradle task runs `hydrozoa` from PATH; cargo installs it to ~/.cargo/bin, which
    // VS Code's environment doesn't always include.
    const dirs = [path.join(os.homedir(), '.cargo', 'bin')];
    const custom = vscode.workspace.getConfiguration('vexCommandBased').get('hydrozoaPath', '');
    if (custom) dirs.unshift(path.dirname(custom));
    const key = Object.keys(process.env).find((k) => k.toUpperCase() === 'PATH') || 'PATH';
    options.env = { [key]: dirs.join(path.delimiter) + path.delimiter + (process.env[key] || '') };
  }
  const exec = new vscode.ShellExecution(gradleCommand(robot), args.concat(extra), options);
  const task = new vscode.Task(
    definition,
    folder || vscode.TaskScope.Workspace,
    a.label + (robot.name ? ' ' + robot.name : ''),
    'VEX',
    exec,
    ['$vex-javac']
  );
  if (a.group === 'build') task.group = vscode.TaskGroup.Build;
  task.presentationOptions = { reveal: vscode.TaskRevealKind.Always, panel: vscode.TaskPanelKind.Dedicated, clear: true };
  return task;
}

/** Where the Hydrozoa CLI might be, besides PATH. */
function cliCandidates() {
  const exe = process.platform === 'win32' ? 'hydrozoa.exe' : 'hydrozoa';
  const custom = vscode.workspace.getConfiguration('vexCommandBased').get('hydrozoaPath', '');
  const list = [];
  if (custom) list.push(custom);
  list.push(exe);
  list.push(path.join(os.homedir(), '.cargo', 'bin', exe));
  return list;
}

/** Resolves to the CLI's path if it runs, or null. */
function findHydrozoaCli() {
  const candidates = cliCandidates();
  return new Promise((resolve) => {
    (function next(i) {
      if (i >= candidates.length) return resolve(null);
      const c = candidates[i];
      if (path.isAbsolute(c) && !fs.existsSync(c)) return next(i + 1);
      execFile(c, ['--help'], { timeout: 5000 }, (err) => {
        if (!err || (err.code !== 'ENOENT' && err.code !== 'EACCES' && !err.killed)) resolve(c);
        else next(i + 1);
      });
    })(0);
  });
}

const CLI_INSTALL = 'cargo install --git https://github.com/vexide/hydrozoa-cli.git';

module.exports = { TASK_TYPE, ACTIONS, createTask, findHydrozoaCli, CLI_INSTALL };
