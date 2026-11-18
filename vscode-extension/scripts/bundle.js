#!/usr/bin/env node
/*
 * Packs the VexLib vendor library, the two robot templates and the Gradle wrapper into
 * resources/templates.json, which "VEX: Create a New Project" unpacks.
 *
 *   node scripts/bundle.js
 *
 * Run it after publishing a new VexLib, or changing template/ or examples/clawbot/.
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
  // The library as a vendor library: its vendordep JSON and the published Maven files for this
  // version (from docs/, written by tools/release_vexlib.py or ./gradlew :vexlib:publish).
  // The extension copies the Maven files into ~/.vex-command/maven so builds work offline.
  const vendordepFile = path.join(REPO, 'docs', 'vendordeps', 'VexLib.json');
  if (!fs.existsSync(vendordepFile)) throw new Error('docs/vendordeps/VexLib.json is missing: publish VexLib first (tools/release_vexlib.py).');
  const vendordep = JSON.parse(fs.readFileSync(vendordepFile, 'utf8'));
  const dep = vendordep.javaDependencies[0];
  const artifactDir = path.join(REPO, 'docs', 'maven', ...dep.groupId.split('.'), dep.artifactId);
  const versionDir = path.join(artifactDir, dep.version);
  if (!fs.existsSync(versionDir)) throw new Error('VexLib ' + dep.version + ' isn\'t published in docs/maven yet.');
  const maven = {};
  const addArtifact = (groupId, artifactId, version) => {
    const dir = path.join(REPO, 'docs', 'maven', ...groupId.split('.'), artifactId, version);
    if (!fs.existsSync(dir)) throw new Error(groupId + ':' + artifactId + ':' + version + ' isn\'t published in docs/maven yet.');
    const prefix = [...groupId.split('.'), artifactId, version].join('/') + '/';
    for (const f of fs.readdirSync(dir)) maven[prefix + f] = { base64: fs.readFileSync(path.join(dir, f)).toString('base64') };
  };
  addArtifact(dep.groupId, dep.artifactId, dep.version);
  // VexLib's Hydrozoa (a build of its main branch, not on Maven Central): see tools/build_hydrozoa.py.
  const props = Object.fromEntries(fs.readFileSync(path.join(REPO, 'gradle.properties'), 'utf8').split(/\r?\n/)
    .filter((l) => /^[A-Za-z]/.test(l) && l.includes('=')).map((l) => [l.slice(0, l.indexOf('=')).trim(), l.slice(l.indexOf('=') + 1).trim()]));
  for (const a of ['hydrozoa', 'vex-sdk']) addArtifact(props.hydrozoaGroup, a, props.hydrozoaVersion);
  // The javadoc jar is large and only for IDE hovers; Gradle fetches it online when the IDE asks.
  for (const k of Object.keys(maven)) if (/-javadoc\.jar/.test(k)) delete maven[k];
  const vexlib = { vendordep: vendordep, maven: maven };

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
    'gradle/gradle-daemon-jvm.properties': read(path.join(REPO, 'gradle', 'gradle-daemon-jvm.properties')),
    'hydrozoa.bin': read(path.join(REPO, 'hydrozoa.bin'))
  };

  const bundle = {
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
  console.log('bundled VexLib', vendordep.version, '(' + count(maven) + ' Maven files),', count(template), 'template files,',
    count(clawbot), 'clawbot files ->', path.relative(EXT, OUT),
    '(' + Math.round(fs.statSync(OUT).size / 1024) + ' KB)');
}

main();
