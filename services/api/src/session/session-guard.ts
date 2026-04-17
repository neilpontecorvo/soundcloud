import { NextFunction, Request, Response } from 'express';
import { DeviceSession, getDeviceSession } from './session-store.js';

declare module 'express-serve-static-core' {
  interface Request {
    deviceSession?: DeviceSession;
  }
}

export const requireActiveSession = (req: Request, res: Response, next: NextFunction): void => {
  const sessionId = readSessionId(req);
  if (!sessionId) {
    res.status(401).json({
      error: 'invalid_session',
      message: 'A valid backend session is required for this route.'
    });
    return;
  }

  const session = getDeviceSession(sessionId);
  if (!session) {
    res.status(401).json({
      error: 'session_not_found',
      message: 'No matching device session was found.'
    });
    return;
  }

  if (session.status === 'expired' || session.status === 'error') {
    res.status(401).json({
      error: 'session_not_active',
      message: `The backend session is ${session.status}.`,
      sessionId: session.sessionId,
      status: session.status
    });
    return;
  }

  req.deviceSession = session;
  next();
};

const readSessionId = (req: Request): string | undefined => {
  const headerValue = req.header('x-session-id');
  if (headerValue && headerValue.trim().length > 0) {
    return headerValue.trim();
  }

  const queryValue = req.query.sessionId;
  if (typeof queryValue === 'string' && queryValue.trim().length > 0) {
    return queryValue.trim();
  }

  return undefined;
};
