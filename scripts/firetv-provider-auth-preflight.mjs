#!/usr/bin/env node

import { execFile } from 'node:child_process';
import http from 'node:http';
import https from 'node:https';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const DEFAULT_BACKEND_URL = 'http://192.168.1.167:4000';
const DEFAULT_FIRE_TV_HOST = '192.168.1.168';
const DEFAULT_FIRE_TV_ADB_PORT = '5555';
const REQUIRED_PROVIDER_ENV = [
  'PROVIDER_CLIENT_ID',
  'PROVIDER_CLIENT_SECRET',
  'PROVIDER_REDIRECT_URI',
  'PROVIDER_AUTH_PUBLIC_BASE_URL'
];

const TIMEOUT_MS = Number(process.env.PREFLIGHT_TIMEOUT_MS ?? 3000);
const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const backendUrlInput = process.env.FIRETV_BACKEND_URL
  ?? process.env.PROVIDER_AUTH_PUBLIC_BASE_URL
  ?? DEFAULT_BACKEND_URL;
const fireTvHost = process.env.FIRETV_DEVICE_HOST ?? DEFAULT_FIRE_TV_HOST;
const fireTvAdbPort = process.env.FIRETV_ADB_PORT ?? DEFAULT_FIRE_TV_ADB_PORT;
const fireTvTarget = `${fireTvHost}:${fireTvAdbPort}`;

const checks = [];

const addCheck = (name, status, detail) => {
  checks.push({ name, status, detail });
};

const isPresent = (value) => typeof value === 'string' && value.trim().length > 0;
const trimTrailingSlash = (value) => value.replace(/\/+$/, '');

const run = (cmd, args, options = {}) => new Promise((resolve) => {
  execFile(cmd, args, {
    cwd: options.cwd ?? repoRoot,
    env: process.env,
    timeout: options.timeout ?? TIMEOUT_MS,
    windowsHide: true
  }, (error, stdout, stderr) => {
    resolve({
      ok: !error,
      code: error?.code ?? 0,
      signal: error?.signal,
      stdout: stdout.trim(),
      stderr: stderr.trim(),
      error
    });
  });
});

const request = (url) => new Promise((resolve) => {
  const client = url.protocol === 'https:' ? https : http;
  const req = client.request(url, { method: 'GET', timeout: TIMEOUT_MS }, (res) => {
    res.resume();
    res.on('end', () => {
      resolve({ ok: res.statusCode >= 200 && res.statusCode < 300, statusCode: res.statusCode });
    });
  });

  req.on('timeout', () => {
    req.destroy(new Error(`timed out after ${TIMEOUT_MS}ms`));
  });
  req.on('error', (error) => {
    resolve({ ok: false, error });
  });
  req.end();
});

const tcpConnect = (host, port) => new Promise((resolve) => {
  const socket = net.createConnection({ host, port: Number(port), timeout: TIMEOUT_MS });
  socket.on('connect', () => {
    socket.destroy();
    resolve({ ok: true });
  });
  socket.on('timeout', () => {
    socket.destroy();
    resolve({ ok: false, reason: `timed out after ${TIMEOUT_MS}ms` });
  });
  socket.on('error', (error) => {
    resolve({ ok: false, reason: error.message });
  });
});

const localIpv4Addresses = () => Object.values(os.networkInterfaces())
  .flatMap((interfaces) => interfaces ?? [])
  .filter((entry) => entry.family === 'IPv4' && !entry.internal)
  .map((entry) => entry.address);

const findAdb = async () => {
  if (isPresent(process.env.ADB)) {
    return process.env.ADB;
  }

  if (isPresent(process.env.ANDROID_HOME)) {
    const adbPath = path.join(process.env.ANDROID_HOME, 'platform-tools', 'adb');
    const result = await run(adbPath, ['version']);
    if (result.ok) {
      return adbPath;
    }
  }

  const result = await run('adb', ['version']);
  return result.ok ? 'adb' : null;
};

const formatCommand = (command) => `  ${command}`;

let backendUrl;
try {
  backendUrl = new URL(backendUrlInput);
  if (!['http:', 'https:'].includes(backendUrl.protocol)) {
    throw new Error('URL must use http or https.');
  }
} catch (error) {
  addCheck('Backend URL parse', 'fail', `${backendUrlInput} is not a valid HTTP(S) URL: ${error.message}`);
}

if (backendUrl) {
  const normalizedBackendUrl = trimTrailingSlash(backendUrl.toString());
  const localIps = localIpv4Addresses();
  const isLanUrl = !['localhost', '127.0.0.1', '::1', '10.0.2.2', '0.0.0.0'].includes(backendUrl.hostname);
  const hostIsThisMac = localIps.includes(backendUrl.hostname);

  addCheck(
    'Backend URL is LAN-routable',
    isLanUrl && hostIsThisMac ? 'pass' : 'fail',
    isLanUrl && hostIsThisMac
      ? `${normalizedBackendUrl} is assigned to this Mac`
      : `${normalizedBackendUrl} must use this Mac's LAN IP. Local non-loopback IPs: ${localIps.join(', ') || 'none detected'}`
  );

  const healthUrl = new URL('/health', normalizedBackendUrl);
  const health = await request(healthUrl);
  addCheck(
    'Backend health over LAN URL',
    health.ok ? 'pass' : 'fail',
    health.ok
      ? `${healthUrl.toString()} returned HTTP ${health.statusCode}`
      : `${healthUrl.toString()} failed${health.statusCode ? ` with HTTP ${health.statusCode}` : ''}${health.error ? `: ${health.error.message}` : ''}`
  );

  for (const name of REQUIRED_PROVIDER_ENV) {
    addCheck(
      `Env ${name}`,
      isPresent(process.env[name]) ? 'pass' : 'fail',
      isPresent(process.env[name]) ? 'set' : 'missing or empty'
    );
  }

  const publicBaseUrl = process.env.PROVIDER_AUTH_PUBLIC_BASE_URL;
  addCheck(
    'PROVIDER_AUTH_PUBLIC_BASE_URL matches backend URL',
    isPresent(publicBaseUrl) && trimTrailingSlash(publicBaseUrl) === normalizedBackendUrl ? 'pass' : 'fail',
    isPresent(publicBaseUrl)
      ? `expected ${normalizedBackendUrl}`
      : `set to ${normalizedBackendUrl}`
  );

  const expectedRedirectUri = `${normalizedBackendUrl}/v1/auth/callback`;
  const redirectUri = process.env.PROVIDER_REDIRECT_URI;
  addCheck(
    'PROVIDER_REDIRECT_URI matches callback URL',
    isPresent(redirectUri) && trimTrailingSlash(redirectUri) === expectedRedirectUri ? 'pass' : 'fail',
    isPresent(redirectUri)
      ? `expected ${expectedRedirectUri}`
      : `set to ${expectedRedirectUri}`
  );
}

const route = await run('route', ['-n', 'get', fireTvHost]);
addCheck(
  'Mac route to Fire TV IP',
  route.ok && !/interface:\s*(utun|ppp|tap|tun)/i.test(route.stdout) ? 'pass' : 'fail',
  route.ok
    ? route.stdout.split('\n').map((line) => line.trim()).filter((line) => /^(gateway|interface):/.test(line)).join(', ')
    : `route lookup failed: ${route.stderr || route.stdout || route.error?.message || 'unknown error'}`
);

const ping = await run('ping', ['-c', '1', '-W', String(TIMEOUT_MS), fireTvHost]);
addCheck(
  'Fire TV ping reachability',
  ping.ok ? 'pass' : 'fail',
  ping.ok
    ? `ICMP replied from ${fireTvHost}`
    : `no ICMP reply from ${fireTvHost}; confirm same LAN and Fire TV VPN is off`
);

const tcp = await tcpConnect(fireTvHost, fireTvAdbPort);
addCheck(
  'Fire TV ADB TCP port',
  tcp.ok ? 'pass' : 'fail',
  tcp.ok
    ? `${fireTvTarget} accepted a TCP connection`
    : `${fireTvTarget} failed: ${tcp.reason}. Confirm ADB debugging is enabled and VPN/LAN routing is clear.`
);

const adb = await findAdb();
addCheck(
  'ADB executable',
  adb ? 'pass' : 'fail',
  adb ? adb : 'adb was not found. Set ANDROID_HOME or ADB before rebuild/install.'
);

if (adb) {
  const adbConnect = await run(adb, ['connect', fireTvTarget], { timeout: 5000 });
  const adbConnected = adbConnect.ok && /(connected to|already connected to)/i.test(`${adbConnect.stdout}\n${adbConnect.stderr}`);
  addCheck(
    'ADB connect to Fire TV',
    adbConnected ? 'pass' : 'fail',
    adbConnected
      ? `${adbConnect.stdout || adbConnect.stderr}`
      : `${adbConnect.stdout || adbConnect.stderr || adbConnect.error?.message || 'adb connect failed'}`
  );

  const adbDevices = await run(adb, ['devices'], { timeout: 5000 });
  const deviceLine = adbDevices.stdout.split('\n').find((line) => line.startsWith(fireTvTarget));
  addCheck(
    'ADB device listed',
    deviceLine?.includes('\tdevice') ? 'pass' : 'fail',
    deviceLine ?? `${fireTvTarget} was not listed by adb devices`
  );
}

const failed = checks.filter((check) => check.status === 'fail');

console.log('Fire TV provider-auth preflight');
console.log(`Backend URL: ${backendUrl ? trimTrailingSlash(backendUrl.toString()) : backendUrlInput}`);
console.log(`Fire TV ADB target: ${fireTvTarget}`);
console.log('');

for (const check of checks) {
  const marker = check.status === 'pass' ? 'PASS' : 'FAIL';
  console.log(`[${marker}] ${check.name}: ${check.detail}`);
}

console.log('');

if (failed.length > 0) {
  console.log(`Preflight failed: ${failed.length} check(s) need attention before rebuild/install.`);
  console.log('Fix the failures, then rerun:');
  console.log(formatCommand('npm run preflight:firetv-provider-auth'));
  process.exit(1);
}

console.log('Preflight passed. Next commands for the on-device provider sign-in pass:');
console.log(formatCommand(`cd ${repoRoot}/apps/firetv-client`));
console.log(formatCommand(`ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew :app:assembleDebug -PapiBaseUrl=${trimTrailingSlash(backendUrl.toString())}`));
console.log(formatCommand(`${adb ?? 'adb'} connect ${fireTvTarget}`));
console.log(formatCommand(`${adb ?? 'adb'} install -r app/build/outputs/apk/debug/app-debug.apk`));
console.log(formatCommand(`${adb ?? 'adb'} shell am start -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER -n com.neilpontecorvo.soundcloudfiretv/.app.MainActivity`));
