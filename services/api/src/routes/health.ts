import { Request, Response, Router } from 'express';

export const healthRouter = Router();

healthRouter.get('/health', (_req: Request, res: Response) => {
  res.json({
    service: 'soundcloud-private-api',
    status: 'ok',
    timestamp: new Date().toISOString()
  });
});
