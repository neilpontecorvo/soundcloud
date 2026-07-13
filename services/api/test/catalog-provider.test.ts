import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import {
  ProviderCatalogProvider
} from '../src/content/catalog-provider.js';
import { HttpApiError } from '../src/errors/api-error.js';
import { ProviderCredentialsService } from '../src/provider/credentials-service.js';
import { ProviderOAuthService } from '../src/provider/oauth-service.js';
import { ProviderConfig } from '../src/provider/provider-config.js';
import {
  FileProviderTokenStore,
  StoredProviderTokens
} from '../src/provider/token-store.js';
import { DeviceSession } from '../src/session/session-store.js';

const config = {
  clientId: 'test-client',
  clientSecret: 'test-secret',
  redirectUri: 'https://client.test/callback',
  authorizeUrl: 'https://provider.test/authorize',
  tokenUrl: 'https://provider.test/token',
  apiBaseUrl: 'https://api.provider.test',
  resolvePath: '/resolve',
  feedPath: '/me/activities',
  searchPath: '/tracks',
  libraryTracksPath: '/me/likes/tracks',
  libraryPlaylistsPath: '/me/playlists',
  tokenStorePath: '/unused/provider-token-store.json',
  requestTimeoutMs: 1_000
} as ProviderConfig;

test('debug-enabled local_debug sessions receive normal debug fixtures', async (t) => {
  const harness = createHarness(t, 'local_debug', true);
  const provider = new ProviderCatalogProvider(config, harness.credentials);

  const feed = await provider.getFeed(harness.session);
  assert.deepEqual(feed.items.map((item) => item.title), [
    'Local Debug Track',
    'Local Debug Playlist'
  ]);
  assert.equal(feed.items[0]?.webUrl, 'https://soundcloud.com/forss/flickermood');

  const library = await provider.getLibrary(harness.session);
  assert.equal(library.sections[0]?.title, 'Local Debug Session');
  assert.deepEqual(library.sections[0]?.items.map((item) => item.title), [
    'Local Debug Track',
    'Local Debug Playlist'
  ]);

  const search = await provider.search('local', harness.session);
  assert.deepEqual(search.items.map((item) => item.title), [
    'Local Debug Track',
    'Local Debug Playlist'
  ]);
});

test('debug-disabled persisted local_debug sessions cannot expose debug content', async (t) => {
  const harness = createHarness(t, 'local_debug', false);
  const provider = new ProviderCatalogProvider(config, harness.credentials);
  const rejectsAsInvalidSession = (error: unknown): boolean => (
    error instanceof HttpApiError && error.error === 'invalid_session'
  );

  await assert.rejects(() => provider.getFeed(harness.session), rejectsAsInvalidSession);
  await assert.rejects(() => provider.getLibrary(harness.session), rejectsAsInvalidSession);
  await assert.rejects(() => provider.search('local', harness.session), rejectsAsInvalidSession);
});

test('real provider sessions continue through the provider adapter', async (t) => {
  const harness = createHarness(t, 'provider', true);
  const provider = new ProviderCatalogProvider(config, harness.credentials);
  const originalFetch = globalThis.fetch;
  let requestCount = 0;

  globalThis.fetch = async (input, init) => {
    requestCount += 1;
    assert.equal(String(input), 'https://api.provider.test/me/activities');
    assert.equal(new Headers(init?.headers).get('Authorization'), 'Bearer provider-access-token');
    return new Response(JSON.stringify({
      collection: [
        {
          id: 42,
          kind: 'track',
          title: 'Provider Track',
          permalink_url: 'https://provider.test/provider-track'
        }
      ]
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    });
  };

  t.after(() => {
    globalThis.fetch = originalFetch;
  });

  const feed = await provider.getFeed(harness.session);
  assert.equal(requestCount, 1);
  assert.deepEqual(feed.items.map((item) => item.title), ['Provider Track']);
});

const createHarness = (
  t: test.TestContext,
  source: NonNullable<StoredProviderTokens['source']>,
  allowLocalDebugCredentials: boolean
): {
  session: DeviceSession;
  credentials: ProviderCredentialsService;
} => {
  const directory = mkdtempSync(path.join(tmpdir(), 'soundcloud-catalog-test-'));
  t.after(() => rmSync(directory, { recursive: true, force: true }));

  const now = new Date();
  const expiresAtIso = new Date(now.getTime() + 60 * 60 * 1_000).toISOString();
  const session: DeviceSession = {
    sessionId: `test-${source}-${allowLocalDebugCredentials}`,
    status: 'authenticated',
    createdAtIso: now.toISOString(),
    authenticatedAtIso: now.toISOString(),
    accessTokenExpiresAtIso: expiresAtIso,
    expiresAtIso
  };
  const tokenStore = new FileProviderTokenStore(path.join(directory, 'tokens.json'));
  tokenStore.save({
    session,
    tokens: {
      accessToken: source === 'provider' ? 'provider-access-token' : 'local-debug-access-token',
      refreshToken: source === 'provider' ? 'provider-refresh-token' : 'local-debug-refresh-token',
      tokenType: 'Bearer',
      scope: source === 'provider' ? undefined : 'local_debug',
      accessTokenExpiresAtIso: expiresAtIso,
      updatedAtIso: now.toISOString(),
      source
    }
  });

  return {
    session,
    credentials: new ProviderCredentialsService(
      new ProviderOAuthService(config),
      tokenStore,
      allowLocalDebugCredentials
    )
  };
};
