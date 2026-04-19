import { randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

export type SessionStatus = 'awaiting_auth' | 'authenticated' | 'expired' | 'error';

export interface DeviceSession {
  sessionId: string;
  status: SessionStatus;
  deviceName?: string;
  appVersion?: string;
  createdAtIso: string;
  expiresAtIso: string;
  authenticatedAtIso?: string;
  accessTokenExpiresAtIso?: string;
}

const SESSION_TTL_MS = 10 * 60 * 1000;
const sessions = new Map<string, DeviceSession>();

// File-backed persistence: without this, a backend restart invalidates every
// device's stored sessionId and forces re-auth on the TV. Only authenticated
// sessions are persisted — short-lived awaiting_auth rows stay in-memory so
// a crash mid-pairing doesn't leave stale codes on disk.
const SESSION_FILE = resolve(
  process.env.SESSION_STORE_PATH ?? './data/sessions.json'
);

const loadFromDisk = (): void => {
  try {
    const raw = readFileSync(SESSION_FILE, 'utf-8');
    const parsed = JSON.parse(raw) as { sessions?: DeviceSession[] };
    if (!parsed.sessions) return;
    for (const session of parsed.sessions) {
      sessions.set(session.sessionId, session);
    }
    console.log(`[session-store] loaded ${sessions.size} session(s) from ${SESSION_FILE}`);
  } catch (err) {
    const code = (err as NodeJS.ErrnoException).code;
    if (code !== 'ENOENT') {
      console.warn(`[session-store] failed to load ${SESSION_FILE}:`, err);
    }
  }
};

const flushToDisk = (): void => {
  const authenticated: DeviceSession[] = [];
  for (const session of sessions.values()) {
    if (session.status === 'authenticated') {
      authenticated.push(session);
    }
  }
  try {
    mkdirSync(dirname(SESSION_FILE), { recursive: true });
    const tmp = `${SESSION_FILE}.tmp`;
    writeFileSync(
      tmp,
      JSON.stringify({ sessions: authenticated }, null, 2),
      'utf-8'
    );
    renameSync(tmp, SESSION_FILE);
  } catch (err) {
    console.warn(`[session-store] failed to persist ${SESSION_FILE}:`, err);
  }
};

loadFromDisk();

export const createDeviceSession = (input: {
  deviceName?: string;
  appVersion?: string;
}): DeviceSession => {
  const now = new Date();
  const session: DeviceSession = {
    sessionId: randomUUID(),
    status: 'awaiting_auth',
    deviceName: input.deviceName,
    appVersion: input.appVersion,
    createdAtIso: now.toISOString(),
    expiresAtIso: new Date(now.getTime() + SESSION_TTL_MS).toISOString()
  };

  sessions.set(session.sessionId, session);
  return session;
};

export const getDeviceSession = (sessionId: string): DeviceSession | undefined => {
  const session = sessions.get(sessionId);
  return session ? refreshExpiry(session) : undefined;
};

export const updateDeviceSession = (
  sessionId: string,
  patch: Partial<DeviceSession>
): DeviceSession | undefined => {
  const existing = sessions.get(sessionId);
  if (!existing) return undefined;

  const next = {
    ...existing,
    ...patch,
    sessionId: existing.sessionId,
    createdAtIso: existing.createdAtIso
  };
  sessions.set(sessionId, next);
  const refreshed = refreshExpiry(next);
  if (refreshed.status === 'authenticated' || existing.status === 'authenticated') {
    flushToDisk();
  }
  return refreshed;
};

export const restoreDeviceSession = (session: DeviceSession): void => {
  sessions.set(session.sessionId, refreshExpiry(session));
  if (session.status === 'authenticated') {
    flushToDisk();
  }
};

export const refreshExpiry = (session: DeviceSession): DeviceSession => {
  if (session.status !== 'authenticated' && Date.parse(session.expiresAtIso) <= Date.now()) {
    session.status = 'expired';
  }
  return session;
};

export const toSessionResponse = (session: DeviceSession) => ({
  sessionId: session.sessionId,
  status: session.status,
  expiresAtIso: session.expiresAtIso,
  authenticatedAtIso: session.authenticatedAtIso ?? null,
  accessTokenExpiresAtIso: session.accessTokenExpiresAtIso ?? null
});
