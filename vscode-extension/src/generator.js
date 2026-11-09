/*
 * Creates a standalone robot project from the bundled templates. Plain Node (no vscode import),
 * so it can be tested on its own.
 *
 * A generated project looks like:
 *   MyRobot/
 *     settings.gradle.kts, build.gradle.kts   robot build (Hydrozoa plugin), includes :vexlib
 *     vexlib/                                 the library's source, with its own build file
 *     src/main/java/<your/package>/...        robot code, package renamed
 *     src/sim/java/<your/package>/SimMain.java
 *     gradlew, gradle/wrapper/, hydrozoa.bin
 *     vex-project.json                        marks it as a VEX command-based project
 */
'use strict';
const fs = require('fs');
const path = require('path');

const JAVA_KEYWORDS = new Set(('abstract assert boolean break byte case catch char class const continue default do double ' +
  'else enum extends final finally float for goto if implements import instanceof int interface long native new ' +
  'package private protected public return short static strictfp super switch synchronized this throw throws ' +
  'transient try void volatile while true false null var record yield').split(' '));

const ICONS = ['ROBOT', 'CLAWBOT', 'ALIEN', 'ALIEN_IN_UFO'];

/** Returns an error message, or null if the name is fine. */
function validateProjectName(name) {
  if (!name || !name.trim()) return 'Enter a name for the project.';
  if (!/^[A-Za-z][A-Za-z0-9_-]*$/.test(name)) return 'Use letters, digits, - and _, starting with a letter.';
  if (name.length > 40) return 'Keep the name under 40 characters.';
  return null;
}

/** Returns an error message, or null if the package name is fine. */
function validatePackage(pkg) {
  if (!pkg || !pkg.trim()) return 'Enter a Java package, for example com.team1234.robot.';
  const parts = pkg.split('.');
  for (const p of parts) {
    if (!/^[a-z_][a-z0-9_]*$/.test(p)) return 'Each part must be lowercase letters, digits or _, not starting with a digit ("' + p + '").';
    if (JAVA_KEYWORDS.has(p)) return '"' + p + '" is a Java keyword.';
  }
  if (parts.length < 2) return 'Use at least two parts, for example team1234.robot.';
  if (pkg === 'com.robojacks.lib' || pkg.startsWith('com.robojacks.lib.')) return 'That package belongs to the library. Pick another.';
  return null;
}

/** Suggests a package from the project name: "Team 1234 Bot" -> com.team1234bot.robot */
function suggestPackage(projectName) {
  const clean = String(projectName || '').toLowerCase().replace(/[^a-z0-9]/g, '');
  const team = /^[a-z_]/.test(clean) ? clean : 'team' + clean;
  return 'com.' + (team || 'myteam') + '.robot';
}

function toDisplayName(projectName) {
  return projectName.replace(/[-_]+/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 23);
}

function settingsGradle(name) {
  return `pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "${name}"

include("vexlib")   // the command-based library, as source
`;
}

function robotBuildGradle(opts) {
  return `import dev.vexide.hydrozoa.gradle.ProgramIcon

plugins {
    java
    id("dev.vexide.hydrozoa") version "0.1.0-alpha.1"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21)) // same as Hydrozoa
    }
}

dependencies {
    implementation(project(":vexlib"))
}

hydrozoa {
    entrypoint = "${opts.pkg}.Main"
    name = "${opts.programName}"   // shown on the brain's screen
    slot = ${opts.slot}               // program slot on the brain, 1-8
    icon = ProgramIcon.${opts.icon}
}

// Desktop simulator: same robot code, fake hardware. Never uploaded to the brain.
sourceSets {
    create("sim") {
        java.srcDir("src/sim/java")
        compileClasspath += sourceSets["main"].output + sourceSets["main"].compileClasspath
        runtimeClasspath += sourceSets["main"].output + sourceSets["main"].runtimeClasspath
    }
}

tasks.register<JavaExec>("simulate") {
    group = "application"
    description = "Runs the robot code in a desktop simulator."
    classpath = sourceSets["sim"].runtimeClasspath
    mainClass.set("${opts.pkg}.SimMain")
}
`;
}

function vexlibBuildGradle() {
  return `plugins {
    \`java-library\`
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Only lib/hal/hydrozoa uses this directly; everything else is plain Java.
    api("dev.vexide:hydrozoa:0.1.0-alpha.1")
}
`;
}

const GITIGNORE = `.gradle/
build/
out/
*.class
.idea/
*.iml
.DS_Store
`;

function readme(opts) {
  return `# ${opts.name}

A VEX V5 robot written in Java with FRC-style command-based programming
(${opts.kind === 'clawbot' ? 'started from the Clawbot example' : 'started from the command-based template'}).

| Command | Does |
| --- | --- |
| \`./gradlew simulate\` | Runs the robot code in the desktop simulator |
| \`./gradlew assemble\` | Builds the program for the brain |
| \`./gradlew hydrozoaUpload\` | Builds and uploads it (needs the Hydrozoa CLI) |

In VS Code with the VEX Command-Based extension, use the Build, Simulate and Upload buttons in the
status bar, and right-click a folder under \`src/main/java\` for **VEX → New Subsystem / Command / Auto**.

- Robot code: \`src/main/java/${opts.pkg.replace(/\./g, '/')}/\`
- Library: \`vexlib/\` (version ${opts.libraryVersion})
- Simulator: \`src/sim/java/\`
`;
}

/**
 * Rewrites a template's files for a new package: paths under src/<set>/java/<old package>/
 * move to the new package's folder, and every reference to the old package in the text changes.
 * The library's own package (com.robojacks.lib) is untouched.
 */
function renameFiles(files, fromPkg, toPkg) {
  const fromPath = fromPkg.replace(/\./g, '/');
  const toPath = toPkg.replace(/\./g, '/');
  const pkgRe = new RegExp('\\b' + fromPkg.replace(/\./g, '\\.') + '(?=[.;\\s*]|$)', 'g');
  const out = {};
  for (const [rel, content] of Object.entries(files)) {
    const newRel = rel.replace(new RegExp('^(src/[^/]+/java/)' + fromPath + '(?=/)'), '$1' + toPath);
    if (content.text !== undefined && /\.(java|kts|json|md)$/.test(rel)) {
      out[newRel] = { text: content.text.replace(pkgRe, toPkg) };
    } else {
      out[newRel] = content;
    }
  }
  return out;
}

function writeFile(root, rel, content) {
  const full = path.join(root, ...rel.split('/'));
  fs.mkdirSync(path.dirname(full), { recursive: true });
  if (content.base64 !== undefined) fs.writeFileSync(full, Buffer.from(content.base64, 'base64'));
  else fs.writeFileSync(full, content.text);
  return full;
}

/**
 * Creates the project. Throws with a readable message if something is wrong.
 * @param {object} opts { bundle, kind: 'template'|'clawbot', name, pkg, parentDir, slot?, icon? }
 * @returns {{ root: string, files: string[], mainFile: string }}
 */
function generateProject(opts) {
  const { bundle, kind, name, pkg, parentDir } = opts;
  const tpl = bundle.templates[kind];
  if (!tpl) throw new Error('Unknown project type: ' + kind);
  const nameErr = validateProjectName(name);
  if (nameErr) throw new Error(nameErr);
  const pkgErr = validatePackage(pkg);
  if (pkgErr) throw new Error(pkgErr);
  const root = path.join(parentDir, name);
  if (fs.existsSync(root) && fs.readdirSync(root).length > 0) {
    throw new Error('The folder "' + name + '" already exists there and isn\'t empty. Pick another name or location.');
  }
  const slot = opts.slot || tpl.slot;
  const icon = ICONS.includes(opts.icon) ? opts.icon : tpl.icon;

  const files = {};
  Object.assign(files, bundle.common);
  for (const [rel, c] of Object.entries(bundle.vexlib)) files['vexlib/' + rel] = c;
  Object.assign(files, renameFiles(tpl.files, tpl.sourcePackage, pkg));
  files['vexlib/build.gradle.kts'] = { text: vexlibBuildGradle() };
  files['settings.gradle.kts'] = { text: settingsGradle(name) };
  files['build.gradle.kts'] = { text: robotBuildGradle({ pkg, programName: toDisplayName(name), slot, icon }) };
  files['.gitignore'] = { text: GITIGNORE };
  files['README.md'] = { text: readme({ name, pkg, kind, libraryVersion: bundle.libraryVersion }) };
  files['vex-project.json'] = { text: JSON.stringify({
    type: 'vex-command-based', template: kind, name: name, package: pkg,
    libraryVersion: bundle.libraryVersion, created: new Date().toISOString()
  }, null, 2) + '\n' };
  files['.vscode/extensions.json'] = { text: JSON.stringify({
    recommendations: ['kedrickjohnson.vex-command-based', 'redhat.java', 'vscjava.vscode-gradle']
  }, null, 2) + '\n' };

  const written = [];
  for (const [rel, content] of Object.entries(files)) written.push(writeFile(root, rel, content));
  try { fs.chmodSync(path.join(root, 'gradlew'), 0o755); } catch (e) { /* Windows */ }

  const pkgPath = pkg.replace(/\./g, '/');
  return {
    root: root,
    files: written,
    mainFile: path.join(root, 'src', 'main', 'java', ...pkgPath.split('/'), 'RobotContainer.java')
  };
}

module.exports = {
  validateProjectName, validatePackage, suggestPackage, renameFiles, generateProject,
  JAVA_KEYWORDS
};
