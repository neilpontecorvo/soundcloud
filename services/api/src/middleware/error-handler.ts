import { NextFunction, Request, Response } from 'express';

export const errorHandler = (
  err: unknown,
  _req: Request,
  res: Response,
  _next: NextFunction
): void => {
  console.error('[api] unhandled error', err);
  res.status(500).json({
    error: 'internal_error'
  });
};
