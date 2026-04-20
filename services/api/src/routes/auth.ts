import { NextFunction, Request, Response, Router } from 'express';
import { invalidRequest, invalidSession } from '../errors/api-error.js';
import {
  consumeProviderAuthPairingByState,
  createProviderAuthPairing,
  getProviderAuthPairingByUserCode
} from '../provider/auth-pairing-store.js';
import {
  providerConfig,
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
  const pairing = createProviderAuthPairing(session.sessionId);
  const publicBaseUrl = publicAuthBaseUrl(req);

  res.status(201).json({
    sessionId: session.sessionId,
    status: session.status,
    verificationUri: `${publicBaseUrl}/v1/auth/pair`,
    verificationUriComplete: `${publicBaseUrl}/v1/auth/start?user_code=${encodeURIComponent(pairing.userCode)}`,
    userCode: pairing.userCode,
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

authRouter.get('/auth/pair', (req: Request, res: Response) => {
  res.type('html').send(pairingPage(
    typeof req.query.user_code === 'string' ? req.query.user_code : ''
  ));
});

authRouter.get('/auth/start', (req: Request, res: Response, next: NextFunction) => {
  try {
    const userCode = req.query.user_code;
    if (typeof userCode !== 'string' || userCode.trim().length === 0) {
      res.status(400).type('html').send(messagePage(
        'Missing sign-in code',
        'Enter the code shown on your Fire TV to continue.'
      ));
      return;
    }

    const pairing = getProviderAuthPairingByUserCode(userCode);
    if (!pairing) {
      res.status(404).type('html').send(messagePage(
        'Sign-in code expired',
        'Return to the Fire TV app and start sign-in again.'
      ));
      return;
    }

    const session = getDeviceSession(pairing.sessionId);
    if (!session || session.status === 'expired' || session.status === 'error') {
      res.status(401).type('html').send(messagePage(
        'Fire TV session expired',
        'Return to the Fire TV app and start sign-in again.'
      ));
      return;
    }

    res.redirect(providerOAuthService.createAuthorizationUrl(pairing.state));
  } catch (error) {
    next(error);
  }
});

authRouter.get('/auth/callback', asyncRoute(async (req: Request, res: Response) => {
  const state = req.query.state;
  const authorizationCode = req.query.code;

  if (typeof state !== 'string' || state.trim().length === 0) {
    throw invalidRequest('state is required.');
  }
  if (typeof authorizationCode !== 'string' || authorizationCode.trim().length === 0) {
    throw invalidRequest('code is required.');
  }

  const pairing = consumeProviderAuthPairingByState(state);
  if (!pairing) {
    throw invalidRequest('Provider sign-in state is expired or invalid.');
  }

  const session = getDeviceSession(pairing.sessionId);
  if (!session) {
    throw invalidSession('No matching device session was found.');
  }
  ensureExchangeableSession(session);

  const tokenSet = await providerOAuthService.exchangeAuthorizationCode(authorizationCode.trim());
  providerCredentialsService.storeExchange(session, tokenSet);

  res.type('html').send(messagePage(
    'Sign-in complete',
    'You can return to your Fire TV. The app will continue automatically.'
  ));
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

const publicAuthBaseUrl = (req: Request): string => (
  providerConfig.authPublicBaseUrl?.replace(/\/+$/, '') ?? `${req.protocol}://${req.get('host')}`
);

const pairingPage = (initialCode: string): string => {
  const escapedCode = escapeHtml(initialCode);
  return htmlPage(
    'Fire TV sign-in',
    `<h1>Fire TV sign-in</h1>
     <p>Enter the code shown on your TV.</p>
     <form action="/v1/auth/start" method="get">
       <label for="user_code">Code</label>
       <input id="user_code" name="user_code" value="${escapedCode}" autocomplete="one-time-code" autofocus />
       <button type="submit">Continue</button>
     </form>`
  );
};

const messagePage = (title: string, message: string): string => (
  htmlPage(title, `<h1>${escapeHtml(title)}</h1><p>${escapeHtml(message)}</p>`)
);

const htmlPage = (title: string, body: string): string => `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>${escapeHtml(title)}</title>
  <style>
    body { background: #050505; color: #f5f5f5; font-family: system-ui, sans-serif; margin: 0; padding: 48px 24px; }
    main { margin: 0 auto; max-width: 560px; }
    h1 { color: #ff6600; font-size: 32px; margin: 0 0 16px; }
    p, label { color: #cfcfcf; font-size: 18px; line-height: 1.5; }
    input { background: #111; border: 1px solid #555; border-radius: 8px; color: #fff; display: block; font-size: 24px; letter-spacing: 2px; margin: 8px 0 20px; padding: 12px; width: 100%; }
    button { background: #ff6600; border: 0; border-radius: 8px; color: #fff; font-size: 18px; font-weight: 700; padding: 12px 18px; }
  </style>
</head>
<body>
  <main>${body}</main>
</body>
</html>`;

const escapeHtml = (value: string): string => (
  value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
);
