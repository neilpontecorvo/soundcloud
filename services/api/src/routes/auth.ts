import { Request, Response, Router } from 'express';
import { randomUUID } from 'node:crypto';

export const authRouter = Router();

type SessionStatus = 'awaiting_auth' | 'authenticated' | 'expired' | 'error';

interface DeviceSession {
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

authRouter.post('/device/bootstrap', (req: Request, res: Response) => {
  const now = new Date();
  const session: DeviceSession = {
    sessionId: randomUUID(),
    status: 'awaiting_auth',
    deviceName: typeof req.body?.deviceName === 'string' ? req.body.deviceName : undefined,
    appVersion: typeof req.body?.appVersion === 'string' ? req.body.appVersion : undefined,
    createdAtIso: now.toISOString(),
    expiresAtIso: new Date(now.getTime() + SESSION_TTL_MS).toISOString()
  };

  sessions.set(session.sessionId, session);

  res.status(201).json({
    sessionId: session.sessionId,
    status: session.status,
    verificationUri: null,
    userCode: null,
    expiresAtIso: session.expiresAtIso,
    pollIntervalSeconds: 5
  });
});

authRouter.get('/session/:sessionId', (req: Request, res: Response) => {
  const session = findSession(req.params.sessionId, res);
  if (!session) return;

  res.json(toSessionResponse(refreshExpiry(session)));
});

authRouter.post('/auth/exchange', (req: Request, res: Response) => {
  const session = findSession(req.body?.sessionId, res);
  if (!session) return;

  // TODO: Implement OAuth code exchange via secure backend credentials only.
  res.status(501).json({
    error: 'provider_not_configured',
    message: 'OAuth exchange is wired but requires provider credentials and token logic before it can authenticate a session.',
    sessionId: session.sessionId,
    status: refreshExpiry(session).status
  });
});

authRouter.post('/auth/refresh', (req: Request, res: Response) => {
  const session = findSession(req.body?.sessionId, res);
  if (!session) return;

  // TODO: Implement refresh token exchange and secure rotation policy.
  res.status(501).json({
    error: 'provider_not_configured',
    message: 'Refresh is wired but requires persisted provider refresh tokens before it can rotate a session.',
    sessionId: session.sessionId,
    status: refreshExpiry(session).status
  });
});

const findSession = (sessionId: unknown, res: Response): DeviceSession | undefined => {
  if (typeof sessionId !== 'string' || sessionId.length === 0) {
    res.status(400).json({
      error: 'invalid_session',
      message: 'A valid sessionId is required.'
    });
    return undefined;
  }

  const session = sessions.get(sessionId);
  if (!session) {
    res.status(404).json({
      error: 'session_not_found',
      message: 'No matching device session was found.'
    });
    return undefined;
  }

  return session;
};

const refreshExpiry = (session: DeviceSession): DeviceSession => {
  if (session.status !== 'authenticated' && Date.parse(session.expiresAtIso) <= Date.now()) {
    session.status = 'expired';
  }
  return session;
};

const toSessionResponse = (session: DeviceSession) => ({
  sessionId: session.sessionId,
  status: session.status,
  expiresAtIso: session.expiresAtIso,
  authenticatedAtIso: session.authenticatedAtIso ?? null,
  accessTokenExpiresAtIso: session.accessTokenExpiresAtIso ?? null
});
