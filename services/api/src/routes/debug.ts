import { NextFunction, Request, Response, Router } from 'express';
import {
  localDebugFeed,
  localDebugLibrary,
  localDebugSearch
} from '../content/catalog-provider.js';
import { debugRouteDisabled, invalidRequest, invalidSession } from '../errors/api-error.js';
import { apiEnv, providerCredentialsService } from '../provider/provider-runtime.js';
import { requireAuthenticatedSession } from '../session/session-guard.js';
import {
  getDeviceSession,
  refreshExpiry,
  toSessionResponse
} from '../session/session-store.js';

export const debugRouter = Router();

// Dev-only guard: every `/debug/*` route must go through this so the whole
// debug surface is off when ENABLE_DEBUG_AUTH=false or NODE_ENV=production.
const requireDebugEnabled = (_req: Request, _res: Response, next: NextFunction): void => {
  if (!apiEnv.enableDebugAuth) {
    throw debugRouteDisabled();
  }
  next();
};

// Explicit "load debug rails" fallback: the normal `/v1/content/*` endpoints
// no longer silently return debug rails for local_debug sessions, so this
// route is the only way to reach the debug feed/library/search items. It is
// intentionally gated on (a) ENABLE_DEBUG_AUTH and (b) the caller's session
// actually being a local_debug session, so production or real-provider
// sessions cannot accidentally surface debug content.
const requireLocalDebugSession = (req: Request, _res: Response, next: NextFunction): void => {
  const session = req.deviceSession;
  if (!session || !providerCredentialsService.isLocalDebugSession(session)) {
    throw invalidSession('Debug content is only available for local-debug sessions.', {
      sessionId: session?.sessionId,
      status: session?.status
    });
  }
  next();
};

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

debugRouter.get(
  '/debug/content/feed',
  requireDebugEnabled,
  requireAuthenticatedSession,
  requireLocalDebugSession,
  (_req: Request, res: Response) => {
    res.json({ ...localDebugFeed(), cacheStatus: 'miss' });
  }
);

debugRouter.get(
  '/debug/content/search',
  requireDebugEnabled,
  requireAuthenticatedSession,
  requireLocalDebugSession,
  (req: Request, res: Response) => {
    const query = typeof req.query.q === 'string' ? req.query.q : '';
    res.json({ ...localDebugSearch(query), cacheStatus: 'miss' });
  }
);

debugRouter.get(
  '/debug/content/library',
  requireDebugEnabled,
  requireAuthenticatedSession,
  requireLocalDebugSession,
  (_req: Request, res: Response) => {
    res.json({ ...localDebugLibrary(), cacheStatus: 'miss' });
  }
);
