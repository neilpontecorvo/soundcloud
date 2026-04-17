import { NextFunction, Request, Response, Router } from 'express';
import { ContentCache } from '../content/content-cache.js';
import { CatalogProvider, ScaffoldCatalogProvider } from '../content/catalog-provider.js';
import { requireActiveSession } from '../session/session-guard.js';

export const contentRouter = Router();

const provider: CatalogProvider = new ScaffoldCatalogProvider();
const cache = new ContentCache();
const FEED_CACHE_TTL_MS = 30 * 1000;
const LIBRARY_CACHE_TTL_MS = 30 * 1000;
const SEARCH_CACHE_TTL_MS = 15 * 1000;

contentRouter.get('/feed', requireActiveSession, asyncRoute(async (req, res) => {
  const session = requireSession(req);
  const result = await cache.getOrSet(
    `feed:${session.sessionId}`,
    FEED_CACHE_TTL_MS,
    () => provider.getFeed(session)
  );

  res.json({
    ...result.value,
    cacheStatus: result.cacheStatus
  });
}));

contentRouter.get('/search', requireActiveSession, asyncRoute(async (req, res) => {
  const session = requireSession(req);
  const query = typeof req.query.q === 'string' ? req.query.q.trim() : '';
  const result = await cache.getOrSet(
    `search:${session.sessionId}:${query.toLocaleLowerCase()}`,
    SEARCH_CACHE_TTL_MS,
    () => provider.search(query, session)
  );

  res.json({
    ...result.value,
    cacheStatus: result.cacheStatus
  });
}));

contentRouter.get('/library', requireActiveSession, asyncRoute(async (req, res) => {
  const session = requireSession(req);
  const result = await cache.getOrSet(
    `library:${session.sessionId}`,
    LIBRARY_CACHE_TTL_MS,
    () => provider.getLibrary(session)
  );

  res.json({
    ...result.value,
    cacheStatus: result.cacheStatus
  });
}));

function asyncRoute(
  handler: (req: Request, res: Response) => Promise<void>
): (req: Request, res: Response, next: NextFunction) => void {
  return (req, res, next) => {
    handler(req, res).catch(next);
  };
}

function requireSession(req: Request) {
  if (!req.deviceSession) {
    throw new Error('Session guard did not attach a session.');
  }
  return req.deviceSession;
}
