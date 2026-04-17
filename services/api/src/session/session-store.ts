import { randomUUID } from 'node:crypto';

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
