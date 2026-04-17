import { Request, Response, Router } from 'express';
import {
  createDeviceSession,
  DeviceSession,
  getDeviceSession,
  refreshExpiry,
  toSessionResponse
} from '../session/session-store.js';

export const authRouter = Router();

authRouter.post('/device/bootstrap', (req: Request, res: Response) => {
  const session = createDeviceSession({
    deviceName: typeof req.body?.deviceName === 'string' ? req.body.deviceName : undefined,
    appVersion: typeof req.body?.appVersion === 'string' ? req.body.appVersion : undefined
  });

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

  const session = getDeviceSession(sessionId);
  if (!session) {
    res.status(404).json({
      error: 'session_not_found',
      message: 'No matching device session was found.'
    });
    return undefined;
  }

  return session;
};
