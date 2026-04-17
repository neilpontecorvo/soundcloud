interface CacheEntry<T> {
  expiresAtMs: number;
  value: T;
}

export interface CachedResult<T> {
  cacheStatus: 'hit' | 'miss';
  value: T;
}

export class ContentCache {
  private readonly entries = new Map<string, CacheEntry<unknown>>();

  async getOrSet<T>(key: string, ttlMs: number, load: () => Promise<T>): Promise<CachedResult<T>> {
    const existing = this.entries.get(key) as CacheEntry<T> | undefined;
    if (existing && existing.expiresAtMs > Date.now()) {
      return { cacheStatus: 'hit', value: existing.value };
    }

    const value = await load();
    this.entries.set(key, {
      expiresAtMs: Date.now() + ttlMs,
      value
    });

    return { cacheStatus: 'miss', value };
  }
}
