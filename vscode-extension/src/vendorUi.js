/*
 * "VEX: Manage Vendor Libraries": the VS Code side of src/vendordeps.js, laid out like
 * WPILib's vendor dependency manager.
 */
'use strict';
const vscode = require('vscode');
const fs = require('fs');
const path = require('path');
const vendordeps = require('./vendordeps');
const generator = require('./generator');

/**
 * @param {object} host { context, pickRobot(context, forceAsk), loadBundle(context), runAction(context, action) }
 */
async function manage(host) {
  const robot = await host.pickRobot(host.context, false);
  if (!robot) return;
  const root = robot.dir;
  if (!(await ensureSupport(robot))) return;

  const bundle = host.loadBundle(host.context);
  const bundled = bundle.vexlib.vendordep;
  const installed = vendordeps.listInstalled(root);
  const bundledInstalled = installed.find((it) => it.dep && it.dep.uuid === bundled.uuid);
  const names = installed.map((it) => it.dep ? it.dep.name + ' ' + it.dep.version : path.basename(it.file) + ' (broken)');

  const items = [];
  if (!bundledInstalled || vendordeps.compareVersions(bundled.version, bundledInstalled.dep.version) > 0) {
    items.push({ id: 'bundled', label: '$(package) Install ' + bundled.name + ' ' + bundled.version,
      description: bundledInstalled ? 'update from ' + bundledInstalled.dep.version + ' (comes with this extension, works offline)' : 'comes with this extension, works offline' });
  }
  items.push(
    { id: 'online', label: '$(cloud-download) Install new library (online)', description: 'paste the library\'s vendordep JSON URL' },
    { id: 'file', label: '$(file) Install new library (from file)', description: 'a vendordep .json on this computer' },
    { id: 'updates', label: '$(sync) Check for updates (online)', description: installed.length ? '' : 'nothing installed yet' },
    { id: 'remove', label: '$(trash) Remove installed libraries', description: installed.length ? '' : 'nothing installed yet' }
  );
  const pick = await vscode.window.showQuickPick(items, {
    title: 'Vendor Libraries · ' + (robot.name || path.basename(root)),
    placeHolder: installed.length ? 'Installed: ' + names.join(', ') : 'No vendor libraries installed'
  });
  if (!pick) return;

  if (pick.id === 'bundled') {
    vendordeps.installOffline(bundle.vexlib.maven);
    return finishInstall(host, robot, bundled);
  }
  if (pick.id === 'online') {
    const url = await vscode.window.showInputBox({
      title: 'Install Vendor Library (online)', prompt: 'URL of the library\'s vendordep JSON',
      placeHolder: 'https://example.com/vendordeps/MyLib.json',
      validateInput: (v) => /^https?:\/\/\S+$/.test(v.trim()) ? null : 'Enter an http(s) URL.'
    });
    if (!url) return;
    let dep;
    try {
      dep = await vscode.window.withProgress({ location: vscode.ProgressLocation.Notification, title: 'Downloading ' + url },
        () => vendordeps.fetchVendordep(url.trim()));
    } catch (e) { vscode.window.showErrorMessage('Couldn\'t install: ' + e.message); return; }
    return finishInstall(host, robot, dep);
  }
  if (pick.id === 'file') {
    const files = await vscode.window.showOpenDialog({ canSelectMany: false, filters: { 'Vendor library': ['json'] }, openLabel: 'Install' });
    if (!files || !files[0]) return;
    let dep;
    try { dep = vendordeps.parse(fs.readFileSync(files[0].fsPath, 'utf8'), path.basename(files[0].fsPath)); }
    catch (e) { vscode.window.showErrorMessage(e.message); return; }
    return finishInstall(host, robot, dep);
  }
  if (pick.id === 'updates') return checkUpdates(host, robot, bundle);
  if (pick.id === 'remove') return removeLibs(host, robot);
}

/** The build has to read vendordeps/. Offers to add the block to older build files. */
async function ensureSupport(robot) {
  const buildFile = path.join(robot.dir, fs.existsSync(path.join(robot.dir, 'build.gradle.kts')) ? 'build.gradle.kts' : 'build.gradle');
  if (!buildFile.endsWith('.kts')) {
    vscode.window.showWarningMessage('Vendor libraries need a build.gradle.kts (Kotlin) build file.');
    return false;
  }
  const text = fs.readFileSync(buildFile, 'utf8');
  const updated = generator.addVendordepSupport(text);
  if (updated === null) return true;
  const choice = await vscode.window.showWarningMessage(
    path.basename(robot.dir) + '/build.gradle.kts doesn\'t read the vendordeps/ folder yet. Add vendor library support to it?',
    { modal: true }, 'Add Support');
  if (choice !== 'Add Support') return false;
  fs.writeFileSync(buildFile, updated);
  return true;
}

async function finishInstall(host, robot, dep) {
  const buildText = fs.readFileSync(path.join(robot.dir, 'build.gradle.kts'), 'utf8');
  const bundled = host.loadBundle(host.context).vexlib.vendordep;
  if (dep.uuid === bundled.uuid && /project\(":vexlib"\)/.test(buildText)) {
    const go = await vscode.window.showWarningMessage(
      'This robot already uses the library from source (project(":vexlib") in its build file). Installing ' + dep.name +
      ' as well would put the library on the classpath twice. Remove the project(":vexlib") line after installing.',
      'Install Anyway');
    if (go !== 'Install Anyway') return;
  }
  let result;
  try { result = vendordeps.install(robot.dir, dep); }
  catch (e) { vscode.window.showErrorMessage(e.message); return; }
  refreshJava(robot);
  const what = result.replaced
    ? (result.replaced.version === dep.version ? 'Reinstalled ' : 'Updated ') + dep.name + (result.replaced.version === dep.version ? ' ' + dep.version : ' ' + result.replaced.version + ' → ' + dep.version)
    : 'Installed ' + dep.name + ' ' + dep.version;
  const choice = await vscode.window.showInformationMessage(what + '. Build to download it.', 'Build Now', 'Open JSON');
  if (choice === 'Build Now') host.runAction(host.context, 'build');
  else if (choice === 'Open JSON') vscode.window.showTextDocument(vscode.Uri.file(result.file));
}

async function checkUpdates(host, robot, bundle) {
  if (!vendordeps.listInstalled(robot.dir).length) { vscode.window.showInformationMessage('No vendor libraries installed.'); return; }
  const results = await vscode.window.withProgress({ location: vscode.ProgressLocation.Notification, title: 'Checking vendor libraries for updates' },
    () => vendordeps.checkUpdates(robot.dir));
  const newer = results.filter((r) => r.newer);
  // The bundled copy counts as an update source too, for when the site is offline.
  for (const r of results) {
    if (!r.newer && r.dep && r.dep.uuid === bundle.vexlib.vendordep.uuid &&
        vendordeps.compareVersions(bundle.vexlib.vendordep.version, r.dep.version) > 0) {
      newer.push(Object.assign({}, r, { latest: bundle.vexlib.vendordep, newer: true, fromBundle: true }));
    }
  }
  const failed = results.filter((r) => r.error && !newer.some((n) => n.file === r.file));
  if (!newer.length) {
    const msg = 'All vendor libraries are up to date.' + (failed.length ? ' Couldn\'t check: ' + failed.map((f) => (f.dep ? f.dep.name : path.basename(f.file)) + ' (' + f.error + ')').join('; ') : '');
    (failed.length ? vscode.window.showWarningMessage : vscode.window.showInformationMessage)(msg);
    return;
  }
  const picks = await vscode.window.showQuickPick(newer.map((r) => ({
    label: r.dep.name, description: r.dep.version + ' → ' + r.latest.version + (r.fromBundle ? ' (bundled)' : ''), picked: true, r
  })), { canPickMany: true, title: 'Update Vendor Libraries', placeHolder: 'Choose which to update' });
  if (!picks || !picks.length) return;
  for (const p of picks) {
    if (p.r.fromBundle) vendordeps.installOffline(bundle.vexlib.maven);
    vendordeps.install(robot.dir, p.r.latest);
  }
  refreshJava(robot);
  const choice = await vscode.window.showInformationMessage('Updated ' + picks.map((p) => p.r.dep.name + ' ' + p.r.latest.version).join(', ') + '. Build to download the new versions.', 'Build Now');
  if (choice) host.runAction(host.context, 'build');
}

async function removeLibs(host, robot) {
  const installed = vendordeps.listInstalled(robot.dir);
  if (!installed.length) { vscode.window.showInformationMessage('No vendor libraries installed.'); return; }
  const picks = await vscode.window.showQuickPick(installed.map((it) => ({
    label: it.dep ? it.dep.name : path.basename(it.file),
    description: it.dep ? it.dep.version + '  ·  ' + path.basename(it.file) : it.error,
    it
  })), { canPickMany: true, title: 'Remove Vendor Libraries', placeHolder: 'Choose which to remove' });
  if (!picks || !picks.length) return;
  for (const p of picks) vendordeps.remove(robot.dir, path.basename(p.it.file));
  refreshJava(robot);
  vscode.window.showInformationMessage('Removed ' + picks.map((p) => p.label).join(', ') + '.');
}

/** Asks the Java extension (if installed) to re-read the Gradle build. */
function refreshJava(robot) {
  const build = vscode.Uri.file(path.join(robot.dir, 'build.gradle.kts'));
  Promise.resolve(vscode.commands.executeCommand('java.projectConfiguration.update', build)).catch(() => {});
}

module.exports = { manage };
