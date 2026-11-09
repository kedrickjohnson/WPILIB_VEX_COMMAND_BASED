#!/usr/bin/env node
/*
 * Packs the library, the two robot templates and the Gradle wrapper into
 * resources/templates.json, which "VEX: Create a New Project" unpacks.
 *
 *   node scripts/bundle.js
 *
 * Run it after changing anything in vexlib/, template/ or examples/clawbot/.
 * One JSON file (binary files as base64) keeps the packaged extension simple.
 */
'use strict';
const fs = require('fs');
const path = require('path');

const EXT = path.resolve(__dirname, '..');
const REPO = path.resolve(EXT, '..');
const OUT = path.join(EXT, 'resources', 'templates.json');

const BINARY = new Set(['.bin', '.jar', '.png']);
const SKIP_DIRS = new Set(['build', '.gradle', 'out', '.idea', 'node_modules']);

function walk(dir, base, into) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (SKIP_DIRS.has(entry.name)) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, base, into);
    else into.push(path.relative(base, full).split(path.sep).join('/'));
  }
  return into;
}

function read(file) {
  const ext = path.extname(file).toLowerCase();
  if (BINARY.has(ext)) return { base64: fs.readFileSync(file).toString('base64') };
  return { text: fs.readFileSync(file, 'utf8') };
}

function collect(rootDir, prefix) {
  const files = {};
  for (const rel of walk(rootDir, rootDir, [])) files[prefix + rel] = read(path.join(rootDir, rel));
  return files;
}

function main() {
  const libVersion = JSON.parse(fs.readFileSync(path.join(EXT, 'package.json'), 'utf8')).version;

  // The library: only its Java sources. The generator writes its own build file.
  const vexlib = collect(path.join(REPO, 'vexlib', 'src'), 'src/');

  // Robot templates: main + sim sources, plus the planner project for the template.
  const template = Object.assign(
    collect(path.join(REPO, 'template', 'src'), 'src/'),
    { 'planner-project.json': read(path.join(REPO, 'template', 'planner-project.json')) }
  );
  const clawbot = collect(path.join(REPO, 'examples', 'clawbot', 'src'), 'src/');

  // Files every project gets as-is.
  const common = {
    'gradlew': read(path.join(REPO, 'gradlew')),
    'gradlew.bat': read(path.join(REPO, 'gradlew.bat')),
    'gradle/wrapper/gradle-wrapper.jar': read(path.join(REPO, 'gradle', 'wrapper', 'gradle-wrapper.jar')),
    'gradle/wrapper/gradle-wrapper.properties': read(path.join(REPO, 'gradle', 'wrapper', 'gradle-wrapper.properties')),
    'hydrozoa.bin': read(path.join(REPO, 'hydrozoa.bin'))
  };

  const bundle = {
    libraryVersion: libVersion,
    bundled: new Date().toISOString(),
    vexlib: vexlib,
    common: common,
    templates: {
      template: { sourcePackage: 'com.robojacks.robot', slot: 1, icon: 'ROBOT', files: template },
      clawbot: { sourcePackage: 'com.robojacks.clawbot', slot: 2, icon: 'CLAWBOT', files: clawbot }
    }
  };
  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  fs.writeFileSync(OUT, JSON.stringify(bundle));
  const count = (o) => Object.keys(o).length;
  console.log('bundled', count(vexlib), 'library files,', count(template), 'template files,',
    count(clawbot), 'clawbot files ->', path.relative(EXT, OUT),
    '(' + Math.round(fs.statSync(OUT).size / 1024) + ' KB)');
}

main();
