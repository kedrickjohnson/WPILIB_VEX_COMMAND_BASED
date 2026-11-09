/*
 * Vendor libraries, the FRC way: a JSON file in the project's vendordeps/ folder names the
 * library's Maven repositories and artifacts, and the Gradle build adds every one it finds.
 * Plain Node (no vscode import).
 *
 * Format (a subset of WPILib's vendordep format, Java only):
 *   {
 *     "fileName": "VexLib.json",          file name to save it as in vendordeps/
 *     "name": "VexLib",
 *     "version": "0.1.0",
 *     "uuid": "...",                      identifies the library across versions
 *     "hydrozoaVersion": "0.1.0-alpha.1", optional: Hydrozoa version it was built against
 *     "mavenUrls": ["https://..."],
 *     "jsonUrl": "https://.../VexLib.json",   where to check for updates
 *     "javaDependencies": [{ "groupId": "...", "artifactId": "...", "version": "..." }]
 *   }
 */
'use strict';
const fs = require('fs');
const os = require('os');
const path = require('path');
const https = require('https');
const http = require('http');

const VENDORDEPS_DIR = 'vendordeps';

/** The offline Maven repository the extension fills from its bundled copies. */
function offlineMavenDir() {
  return path.join(os.homedir(), '.vex-command', 'maven');
}

/** Returns a list of problems with a vendordep object; empty if it's usable. */
function validate(dep) {
  const problems = [];
  if (!dep || typeof dep !== 'object' || Array.isArray(dep)) return ['not a JSON object'];
  for (const key of ['fileName', 'name', 'version', 'uuid']) {
    if (typeof dep[key] !== 'string' || !dep[key].trim()) problems.push('missing "' + key + '"');
  }
  if (typeof dep.fileName === 'string' && !/^[\w.-]+\.json$/.test(dep.fileName)) problems.push('"fileName" must be a plain .json file name');
  if (!Array.isArray(dep.mavenUrls)) problems.push('missing "mavenUrls" list');
  else if (dep.mavenUrls.some((u) => typeof u !== 'string' || !/^(https?|file):\/\//.test(u))) problems.push('"mavenUrls" must be http(s) or file URLs');
  if (!Array.isArray(dep.javaDependencies) || dep.javaDependencies.length === 0) problems.push('no "javaDependencies"');
  else dep.javaDependencies.forEach((d, i) => {
    for (const key of ['groupId', 'artifactId', 'version']) {
      if (!d || typeof d[key] !== 'string' || !/^[\w.+-]+$/.test(d[key])) problems.push('javaDependencies[' + i + '] has a bad "' + key + '"');
    }
  });
  if (dep.jsonUrl !== undefined && (typeof dep.jsonUrl !== 'string' || !/^https?:\/\//.test(dep.jsonUrl))) problems.push('"jsonUrl" must be an http(s) URL');
  return problems;
}

/** Parses text into a vendordep, throwing a readable error if it isn't one. */
function parse(text, source) {
  let dep;
  try { dep = JSON.parse(text); } catch (e) { throw new Error((source || 'The file') + ' isn\'t valid JSON: ' + e.message); }
  const problems = validate(dep);
  if (problems.length) throw new Error((source || 'The file') + ' isn\'t a vendor library: ' + problems.join(', ') + '.');
  return dep;
}

/** Installed vendordeps of a project: [{ file, dep }] (unreadable files get an error instead). */
function listInstalled(projectRoot) {
  const dir = path.join(projectRoot, VENDORDEPS_DIR);
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir).filter((f) => f.endsWith('.json')).sort().map((f) => {
    const file = path.join(dir, f);
    try { return { file, dep: parse(fs.readFileSync(file, 'utf8'), f) }; }
    catch (e) { return { file, error: e.message }; }
  });
}

/**
 * Writes a vendordep into a project. Replaces an installed copy of the same library (same uuid),
 * even if its file name changed. Returns { file, replaced: previous dep or null }.
 */
function install(projectRoot, dep) {
  const problems = validate(dep);
  if (problems.length) throw new Error('Not a vendor library: ' + problems.join(', '));
  const dir = path.join(projectRoot, VENDORDEPS_DIR);
  fs.mkdirSync(dir, { recursive: true });
  let replaced = null;
  for (const it of listInstalled(projectRoot)) {
    if (it.dep && it.dep.uuid === dep.uuid) { replaced = it.dep; fs.unlinkSync(it.file); }
  }
  const file = path.join(dir, dep.fileName);
  fs.writeFileSync(file, JSON.stringify(dep, null, 2) + '\n');
  return { file, replaced };
}

function remove(projectRoot, fileName) {
  const file = path.join(projectRoot, VENDORDEPS_DIR, path.basename(fileName));
  if (fs.existsSync(file)) fs.unlinkSync(file);
}

/** Compares versions like 1.2.10 and 1.2.10-beta.1. Returns <0, 0, >0. A pre-release sorts before its release. */
function compareVersions(a, b) {
  const split = (v) => {
    const [main, pre] = String(v).split('-', 2);
    return { nums: main.split('.').map((n) => parseInt(n, 10) || 0), pre: pre || null };
  };
  const x = split(a), y = split(b);
  for (let i = 0; i < Math.max(x.nums.length, y.nums.length); i++) {
    const d = (x.nums[i] || 0) - (y.nums[i] || 0);
    if (d) return d;
  }
  if (x.pre === y.pre) return 0;
  if (!x.pre) return 1;
  if (!y.pre) return -1;
  return x.pre.localeCompare(y.pre, undefined, { numeric: true });
}

/** Downloads a URL as text (follows up to 5 redirects). */
function fetchText(url, redirects) {
  redirects = redirects === undefined ? 5 : redirects;
  return new Promise((resolve, reject) => {
    const lib = url.startsWith('https:') ? https : url.startsWith('http:') ? http : null;
    if (!lib) return reject(new Error('Only http(s) URLs can be downloaded: ' + url));
    const req = lib.get(url, { headers: { 'User-Agent': 'vex-command-based-vscode', Accept: 'application/json' }, timeout: 15000 }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location && redirects > 0) {
        res.resume();
        return resolve(fetchText(new URL(res.headers.location, url).toString(), redirects - 1));
      }
      if (res.statusCode !== 200) { res.resume(); return reject(new Error('HTTP ' + res.statusCode + ' from ' + url)); }
      let body = '';
      res.setEncoding('utf8');
      res.on('data', (c) => { body += c; if (body.length > 2e6) req.destroy(new Error('Response too large')); });
      res.on('end', () => resolve(body));
    });
    req.on('timeout', () => req.destroy(new Error('Timed out downloading ' + url)));
    req.on('error', reject);
  });
}

async function fetchVendordep(url) {
  return parse(await fetchText(url), url);
}

/**
 * Checks installed libraries against their jsonUrl. Returns
 * [{ file, dep, latest, newer: bool, error }] for every installed library.
 */
async function checkUpdates(projectRoot, fetcher) {
  fetcher = fetcher || fetchVendordep;
  const out = [];
  for (const it of listInstalled(projectRoot)) {
    if (!it.dep) { out.push(Object.assign({ newer: false }, it)); continue; }
    if (!it.dep.jsonUrl) { out.push({ file: it.file, dep: it.dep, newer: false, error: 'no jsonUrl to check' }); continue; }
    try {
      const latest = await fetcher(it.dep.jsonUrl);
      out.push({ file: it.file, dep: it.dep, latest, newer: latest.uuid === it.dep.uuid && compareVersions(latest.version, it.dep.version) > 0 });
    } catch (e) {
      out.push({ file: it.file, dep: it.dep, newer: false, error: e.message });
    }
  }
  return out;
}

/**
 * Copies bundled Maven files ({ "group/path/artifact/version/file": { base64 } }) into the
 * offline repository. Skips files that are already there with the same size. Returns the count written.
 */
function installOffline(mavenFiles, dir) {
  dir = dir || offlineMavenDir();
  let written = 0;
  for (const [rel, c] of Object.entries(mavenFiles || {})) {
    const target = path.join(dir, ...rel.split('/'));
    const data = Buffer.from(c.base64, 'base64');
    try { if (fs.statSync(target).size === data.length) continue; } catch (e) { /* missing */ }
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(target, data);
    written++;
  }
  return written;
}

/**
 * Finds a dependency's jar in a Maven repository folder (used by the tests and the
 * "is it available offline" check). Returns the path or null.
 */
function findJar(repoDir, d) {
  const file = path.join(repoDir, ...d.groupId.split('.'), d.artifactId, d.version, d.artifactId + '-' + d.version + '.jar');
  return fs.existsSync(file) ? file : null;
}

module.exports = {
  VENDORDEPS_DIR, offlineMavenDir, validate, parse, listInstalled, install, remove,
  compareVersions, fetchText, fetchVendordep, checkUpdates, installOffline, findJar
};
