import { NextFunction, Request, Response, Router } from 'express';
import { invalidRequest, invalidSession } from '../errors/api-error.js';
import {
  providerCredentialsService,
  providerOAuthService
} from '../provider/provider-runtime.js';
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

authRouter.post('/auth/exchange', asyncRoute(async (req: Request, res: Response) => {
  const session = findSession(req.body?.sessionId, res);
  if (!session) return;
  ensureExchangeableSession(session);

  const authorizationCode = req.body?.authorizationCode;
  if (typeof authorizationCode !== 'string' || authorizationCode.trim().length === 0) {
    throw invalidRequest('authorizationCode is required.');
  }

  const tokenSet = await providerOAuthService.exchangeAuthorizationCode(authorizationCode.trim());
  const nextSession = providerCredentialsService.storeExchange(session, tokenSet);

  res.json(toSessionResponse(nextSession));
}));

authRouter.post('/auth/refresh', asyncRoute(async (req: Request, res: Response) => {
  const session = findSession(req.body?.sessionId, res);
  if (!session) return;
  ensureRefreshableSession(session);

  const nextSession = await providerCredentialsService.refreshSession(session);
  res.json(toSessionResponse(nextSession));
}));

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
    res.status(401).json({
      error: 'invalid_session',
      message: 'No matching device session was found.'
    });
    return undefined;
  }

  return session;
};

const ensureExchangeableSession = (session: DeviceSession): void => {
  if (session.status === 'expired' || session.status === 'error') {
    throw invalidSession('A non-expired backend session is required for provider exchange.', {
      sessionId: session.sessionId,
      status: session.status
    });
  }
};

const ensureRefreshableSession = (session: DeviceSession): void => {
  if (session.status !== 'authenticated') {
    throw invalidSession('An authenticated backend session is required for provider refresh.', {
      sessionId: session.sessionId,
      status: session.status
    });
  }
};

function asyncRoute(
  handler: (req: Request, res: Response) => Promise<void>
): (req: Request, res: Response, next: NextFunction) => void {
  return (req, res, next) => {
    handler(req, res).catch(next);
  };
}
