import { NextFunction, Request, Response } from 'express';
import { HttpApiError } from '../errors/api-error.js';

export const errorHandler = (
  err: unknown,
  _req: Request,
  res: Response,
  _next: NextFunction
): void => {
  if (err instanceof HttpApiError) {
    res.status(err.statusCode).json(err.toBody());
    return;
  }

  console.error('[api] unhandled error', err);
  res.status(500).json({
    error: 'internal_error',
    message: 'An internal API error occurred.'
  });
};
