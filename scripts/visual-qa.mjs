#!/usr/bin/env node
/* Logisim-evolution — GPLv3. Copyright by the Logisim-evolution developers. */

// Private, disposable real-window QA. Never attach to the user's display or preferences.
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { spawn, spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const self = fileURLToPath(import.meta.url);
const repo = path.resolve(path.dirname(self), '..');
const [command, ...args] = process.argv.slice(2);
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const json = file => JSON.parse(fs.readFileSync(file, 'utf8'));
const writeJson = (file, value) => fs.writeFileSync(file, JSON.stringify(value, null, 2) + '\n');
function run(bin, argv, env = process.env) {
  const r = spawnSync(bin, argv, { env, encoding: 'utf8', timeout: 15000 });
  if (r.error || r.status !== 0) throw new Error(r.error?.message || r.stderr || `${bin}: ${r.status}`);
  return r.stdout.trim();
}
function sessionRoot(value) {
  const root = fs.realpathSync(value);
  if (path.dirname(root) !== fs.realpathSync(os.tmpdir())
      || !/^logisim-visual-qa-[A-Za-z0-9]+$/.test(path.basename(root))) {
    throw new Error('Not a private Logisim QA session directory');
  }
  return root;
}
function identity(pid) {
  try {
    const stat = fs.readFileSync(`/proc/${pid}/stat`, 'utf8');
    return { pid, started: stat.slice(stat.lastIndexOf(')') + 2).split(' ')[19] };
  } catch { return null; }
}
function launch(root, bin, argv, env, logName) {
  const log = fs.openSync(path.join(root, logName), 'a');
  const child = spawn(bin, argv, { env, cwd: root, detached: true, stdio: ['ignore', log, log] });
  fs.closeSync(log);
  child.unref();
  return identity(child.pid);
}
function displayEnv(root) {
  const { DISPLAY, XAUTHORITY } = json(path.join(root, 'display.json'));
  if (!/^:\d+(\.\d+)?$/.test(DISPLAY || '')) throw new Error('Invalid private display');
  return { ...process.env, DISPLAY, XAUTHORITY, WAYLAND_DISPLAY: '',
    XDG_CONFIG_HOME: path.join(root, 'config'), XDG_CACHE_HOME: path.join(root, 'cache'),
    XDG_DATA_HOME: path.join(root, 'data') };
}
function stop(root) {
  const state = json(path.join(root, 'session.json'));
  const helper = fs.existsSync(path.join(root, 'display.json'))
    ? json(path.join(root, 'display.json')).helper : null;
  // Verify start time as well as ownership before signalling; never trust a recycled PID.
  for (const entry of [state.app, state.compositor, helper]) {
    if (!entry || identity(entry.pid)?.started !== entry.started) continue;
    const cmd = fs.readFileSync(`/proc/${entry.pid}/cmdline`, 'utf8');
    if (!cmd.includes(root) && !cmd.includes(path.basename(root))) {
      throw new Error(`Refusing to signal unrecognized PID ${entry.pid}`);
    }
    try { process.kill(entry.pid, 'SIGTERM'); } catch (e) { if (e.code !== 'ESRCH') throw e; }
  }
  console.log(`Stopped owned session processes; evidence retained in ${root}`);
}
async function start() {
  const opts = { scale: '1.6', theme: 'dark', size: '2560x1600' };
  for (let i = 0; i < args.length; i += 2) {
    if (!/^--(jar|scale|theme|size|file)$/.test(args[i]) || !args[i + 1]) throw new Error('Invalid start option');
    opts[args[i].slice(2)] = args[i + 1];
  }
  if (!['dark', 'light', 'system'].includes(opts.theme)
      || (opts.scale !== 'auto' && (!/^\d+(\.\d+)?$/.test(opts.scale)
        || +opts.scale < 0.5 || +opts.scale > 4))
      || !/^\d{3,5}x\d{3,5}$/.test(opts.size)) throw new Error('Invalid theme/scale/size');
  const sourceJar = fs.realpathSync(opts.jar || path.join(repo, 'build/libs/logisim-revolution-5.1.0dev-all.jar'));
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'logisim-visual-qa-'));
  for (const dir of ['home', 'prefs', 'system-prefs', 'config', 'cache', 'data', 'runtime', 'work', 'evidence']) {
    fs.mkdirSync(path.join(root, dir), { mode: 0o700 });
  }
  // A later build must not replace a running JVM's zip file underneath lazy class loading.
  const jar = path.join(root, 'work', 'application.jar');
  fs.copyFileSync(sourceJar, jar);
  const prefs = path.join(root, 'prefs/.java/.userPrefs/dev/briggen/logisimrevolution');
  fs.mkdirSync(prefs, { recursive: true });
  fs.writeFileSync(path.join(prefs, 'prefs.xml'),
    '<?xml version="1.0" encoding="UTF-8"?><!DOCTYPE map SYSTEM "http://java.sun.com/dtd/preferences.dtd">\n'
    + '<map MAP_XML_VERSION="1.0">'
    + (opts.scale === 'auto' ? '' : `<entry key="Scale" value="${opts.scale}"/>`)
    + `<entry key="theme" value="${opts.theme}"/></map>\n`);
  fs.writeFileSync(path.join(root, 'config/kwinrc'), '[Xwayland]\nXwaylandEisNoPrompt=true\n');
  const env = { ...process.env, XDG_CACHE_HOME: path.join(root, 'cache'), XDG_DATA_HOME: path.join(root, 'data'),
    XDG_CONFIG_HOME: path.join(root, 'config'), XDG_RUNTIME_DIR: path.join(root, 'runtime'),
    QA_ROOT: root, QA_RUNNER: self };
  delete env.DISPLAY;
  delete env.WAYLAND_DISPLAY;
  const [width, height] = opts.size.split('x');
  const state = { root, opts, sourceJar, jar,
    sha256: crypto.createHash('sha256').update(fs.readFileSync(jar)).digest('hex'),
    revision: run('git', ['-C', repo, 'rev-parse', 'HEAD']),
    changes: run('git', ['-C', repo, 'status', '--short']), startedAt: new Date().toISOString() };
  state.compositor = launch(root, 'kwin_wayland', ['--virtual', '--xwayland', '--width', width,
    '--height', height, '--socket', path.basename(root), '--exit-with-session',
    path.join(repo, 'scripts/visual-qa-session.sh')], env, 'compositor.log');
  writeJson(path.join(root, 'session.json'), state);
  console.log(root);
  try {
    for (let i = 0; !fs.existsSync(path.join(root, 'display.json')); i++) {
      if (i >= 100) throw new Error('Private compositor did not become ready');
      await sleep(200);
    }
    const fileArgs = [];
    if (opts.file) {
      const copy = path.join(root, 'work', path.basename(opts.file));
      fs.copyFileSync(opts.file, copy);
      fileArgs.push(copy);
    }
    state.app = launch(root, 'java', ['--enable-native-access=ALL-UNNAMED', '-Xmx1500m',
      `-Duser.home=${root}/home`, `-Djava.util.prefs.userRoot=${root}/prefs`,
      `-Djava.util.prefs.systemRoot=${root}/system-prefs`, '-jar', jar, ...fileArgs],
      displayEnv(root), 'app.log');
    writeJson(path.join(root, 'session.json'), state);
    console.log('Starting app; use windows, input, capture and stop with this session path.');
  } catch (e) { stop(root); throw e; }
}
if (command === 'session') {
  const root = sessionRoot(args[0]);
  writeJson(path.join(root, 'display.json'), { DISPLAY: process.env.DISPLAY,
    XAUTHORITY: process.env.XAUTHORITY, helper: identity(process.pid) });
  setInterval(() => {}, 60_000);
} else if (command === 'start') {
  await start();
} else if (['windows', 'input', 'capture', 'stop'].includes(command)) {
  const root = sessionRoot(args[0]);
  if (command === 'stop') stop(root);
  else {
    const env = displayEnv(root);
    if (command === 'input') console.log(run('xdotool', args.slice(1), env));
    if (command === 'windows') {
      const windows = run('xdotool', ['search', '--onlyvisible', '--name', ''], env).split('\n');
      for (const id of windows) {
        try { console.log(JSON.stringify({ id, title: run('xdotool', ['getwindowname', id], env),
          geometry: run('xdotool', ['getwindowgeometry', '--shell', id], env) })); } catch { /* closed */ }
      }
    }
    if (command === 'capture') {
      if (!/^\d+$/.test(args[1] || '') || !/^[\w.-]+\.png$/.test(args[2] || '')) {
        throw new Error('capture requires a window ID and simple PNG filename');
      }
      const output = path.join(root, 'evidence', args[2]);
      run('import', ['-window', args[1], output], env);
      console.log(output);
    }
  }
} else {
  console.log('Usage: node scripts/visual-qa.mjs start [--scale 1.6] [--theme dark] [--size 2560x1600] [--file example.circ] [--jar app.jar]\n'
    + 'Then: windows SESSION | input SESSION <xdotool arguments> | capture SESSION WINDOW_ID NAME.png | stop SESSION\n'
    + 'Requires Linux, KWin/Xwayland, xdotool, ImageMagick and Java 21. Evidence is retained, never recursively deleted.');
  if (command) process.exitCode = 2;
}
