import { Request, Response, Router } from 'express';
import { debugRouteDisabled, invalidRequest, invalidSession } from '../errors/api-error.js';
import { apiEnv, providerCredentialsService } from '../provider/provider-runtime.js';
import {
  getDeviceSession,
  refreshExpiry,
  toSessionResponse
} from '../session/session-store.js';

export const debugRouter = Router();

debugRouter.post('/debug/authenticate-session', (req: Request, res: Response) => {
  if (!apiEnv.enableDebugAuth) {
    throw debugRouteDisabled();
  }

  const sessionId = req.body?.sessionId;
  if (typeof sessionId !== 'string' || sessionId.trim().length === 0) {
    throw invalidRequest('sessionId is required.');
  }

  const session = getDeviceSession(sessionId.trim());
  if (!session) {
    throw invalidSession('No matching device session was found.');
  }

  const current = refreshExpiry(session);
  if (current.status === 'expired' || current.status === 'error') {
    throw invalidSession('Only an active local development session can be debug-authenticated.', {
      sessionId: current.sessionId,
      status: current.status
    });
  }

  const nextSession = providerCredentialsService.storeLocalDebugSession(current);
  res.json(toSessionResponse(nextSession));
});
