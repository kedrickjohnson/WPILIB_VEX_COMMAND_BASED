/*
 * Finds the robot programs in a workspace folder. Plain Node (no vscode import).
 *
 * A robot program is a Gradle project whose build.gradle.kts (or build.gradle) has a
 * `hydrozoa {` block. Works for a generated single-robot project (the root is the robot)
 * and for the multi-project repo (":template", ":examples:clawbot").
 */
'use strict';
const fs = require('fs');
const path = require('path');

function readIf(file) {
  try { return fs.readFileSync(file, 'utf8'); } catch (e) { return null; }
}

function buildFile(dir) {
  for (const f of ['build.gradle.kts', 'build.gradle']) {
    const text = readIf(path.join(dir, f));
    if (text !== null) return { file: path.join(dir, f), text };
  }
  return null;
}

function stripComments(text) {
  return text.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');
}

/** include("a", "b:c") / include 'a' -> ["a", "b:c"] */
function parseIncludes(settingsText) {
  const out = [];
  const re = /\binclude\s*\(?([^)\n]*)\)?/g;
  let m;
  while ((m = re.exec(stripComments(settingsText)))) {
    const strs = m[1].match(/["']([^"']+)["']/g) || [];
    for (const s of strs) out.push(s.slice(1, -1).replace(/^:/, ''));
  }
  return out;
}

function readHydrozoaBlock(text) {
  const clean = stripComments(text);
  const start = clean.search(/\bhydrozoa\s*\{/);
  if (start < 0) return null;
  let i = clean.indexOf('{', start), depth = 0, end = clean.length;
  for (; i < clean.length; i++) {
    if (clean[i] === '{') depth++;
    else if (clean[i] === '}' && --depth === 0) { end = i; break; }
  }
  const block = clean.slice(start, end);
  const str = (key) => { const m = block.match(new RegExp('\\b' + key + '\\s*=\\s*"([^"]*)"')); return m ? m[1] : null; };
  const num = (key) => { const m = block.match(new RegExp('\\b' + key + '\\s*=\\s*(\\d+)')); return m ? Number(m[1]) : null; };
  // Name and slot are `val programName` / `val programSlot` at the top of the file (0.3.1+), or
  // `name =` / `slot =` inside the block (older build files).
  const top = (re) => { const m = clean.match(re); return m ? m[1] : null; };
  const name = top(/\bval\s+programName\s*=\s*"([^"]*)"/) ?? str('name');
  const slotVal = top(/\bval\s+programSlot\s*=\s*(\d+)/);
  return { entrypoint: str('entrypoint'), name: name, slot: slotVal != null ? Number(slotVal) : num('slot') };
}

/**
 * Returns the robot programs under a workspace folder, nearest Gradle root first.
 * Each: { label, gradlePath, gradleRoot, dir, name, slot, entrypoint, hasSim }
 *   gradlePath "" means the root project; otherwise ":template" etc.
 */
function findRobots(folder) {
  const roots = findGradleRoots(folder);
  const robots = [];
  for (const root of roots) {
    const settings = readIf(path.join(root, 'settings.gradle.kts')) ?? readIf(path.join(root, 'settings.gradle')) ?? '';
    const candidates = [''].concat(parseIncludes(settings));
    for (const inc of candidates) {
      const dir = inc ? path.join(root, ...inc.split(':')) : root;
      const b = buildFile(dir);
      if (!b || !/\bdev\.vexide\.hydrozoa\b/.test(b.text)) continue;
      const block = readHydrozoaBlock(b.text);
      if (!block) continue; // `apply false` at the root: not itself a robot
      robots.push({
        label: (block.name || inc || path.basename(root)) + (inc ? '  (:' + inc + ')' : ''),
        gradlePath: inc ? ':' + inc : '',
        gradleRoot: root,
        dir: dir,
        name: block.name,
        slot: block.slot,
        entrypoint: block.entrypoint,
        hasSim: /register<JavaExec>\("simulate"\)|register\("simulate"/.test(b.text)
      });
    }
  }
  return robots;
}

/** Folders with a gradlew wrapper: the folder itself, or up to two levels below it. */
function findGradleRoots(folder) {
  const roots = [];
  const isRoot = (d) => fs.existsSync(path.join(d, 'gradlew')) &&
    (fs.existsSync(path.join(d, 'settings.gradle.kts')) || fs.existsSync(path.join(d, 'settings.gradle')));
  if (isRoot(folder)) return [folder];
  const skip = new Set(['node_modules', '.git', 'build', '.gradle']);
  (function walk(dir, depth) {
    if (depth > 2) return;
    let entries;
    try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch (e) { return; }
    for (const e of entries) {
      if (!e.isDirectory() || skip.has(e.name) || e.name.startsWith('.')) continue;
      const d = path.join(dir, e.name);
      if (isRoot(d)) roots.push(d); else walk(d, depth + 1);
    }
  })(folder, 1);
  return roots;
}

/** "assemble" for the root robot, ":template:assemble" for a subproject. */
function taskPath(robot, task) {
  return robot.gradlePath ? robot.gradlePath + ':' + task : task;
}

module.exports = { findRobots, findGradleRoots, parseIncludes, readHydrozoaBlock, taskPath };
