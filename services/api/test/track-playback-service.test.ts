import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { TrackPlaybackService } from '../src/content/track-playback-service.js';
import { HttpApiError } from '../src/errors/api-error.js';
import { ProviderCredentialsService } from '../src/provider/credentials-service.js';
import { ProviderOAuthService } from '../src/provider/oauth-service.js';
import { ProviderConfig } from '../src/provider/provider-config.js';
import { FileProviderTokenStore } from '../src/provider/token-store.js';
import { DeviceSession } from '../src/session/session-store.js';

const config = {
  clientId: 'test-client',
  clientSecret: 'test-secret',
  redirectUri: 'https://client.test/callback',
  authorizeUrl: 'https://provider.test/authorize',
  tokenUrl: 'https://provider.test/token',
  apiBaseUrl: 'https://api.provider.test',
  artistSpotlightIds: [],
  resolvePath: '/resolve',
  feedPath: '/me/feed',
  recentlyPlayedPath: '/me/recently-played/tracks',
  searchPath: '/tracks',
  searchPlaylistsPath: '/playlists',
  searchUsersPath: '/users',
  libraryTracksPath: '/me/likes/tracks',
  libraryPlaylistsPath: '/me/playlists',
  libraryLikedPlaylistsPath: '/me/likes/playlists',
  tokenStorePath: '/unused/provider-token-store.json',
  requestTimeoutMs: 1_000
} as ProviderConfig;

test('private track audio is proxied without forwarding provider credentials to media CDN', async (t) => {
  const { session, credentials } = createHarness(t);
  const service = new TrackPlaybackService(config, credentials);
  const originalFetch = globalThis.fetch;
  let requestCount = 0;

  globalThis.fetch = async (input, init) => {
    requestCount += 1;
    const url = new URL(String(input));
    const headers = new Headers(init?.headers);
    if (requestCount === 1) {
      assert.equal(url.pathname, '/tracks/2303298242/streams');
      assert.equal(headers.get('Authorization'), 'Bearer provider-access-token');
      assert.equal(headers.get('Range'), null);
      return Response.json({
        http_mp3_128_url: 'https://api.provider.test/tracks/soundcloud:tracks:2303298242/streams/full/http'
      });
    }
    if (requestCount === 2) {
      assert.equal(url.pathname, '/tracks/soundcloud:tracks:2303298242/streams/full/http');
      assert.equal(headers.get('Authorization'), 'Bearer provider-access-token');
      assert.equal(headers.get('Range'), 'bytes=1024-2047');
      return new Response(null, {
        status: 302,
        headers: { Location: 'https://cf-preview-media.sndcdn.com/private-audio.mp3?Policy=ephemeral' }
      });
    }

    assert.equal(url.hostname, 'cf-preview-media.sndcdn.com');
    assert.equal(headers.get('Authorization'), null);
    assert.equal(headers.get('Range'), 'bytes=1024-2047');
    return new Response(new Uint8Array([1, 2, 3, 4]), {
      status: 206,
      headers: {
        'Content-Type': 'audio/mpeg',
        'Content-Length': '4',
        'Content-Range': 'bytes 1024-1027/4096'
      }
    });
  };
  t.after(() => { globalThis.fetch = originalFetch; });

  const result = await service.openTrackStream('2303298242', session, 'bytes=1024-2047');
  assert.equal(requestCount, 3);
  assert.equal(result.statusCode, 206);
  assert.equal(result.headers['content-type'], 'audio/mpeg');
  assert.equal(result.headers['content-range'], 'bytes 1024-1027/4096');
  assert.equal(result.headers['Cache-Control'], 'private, no-store');
  assert.deepEqual(Array.from(new Uint8Array(await new Response(result.body).arrayBuffer())), [1, 2, 3, 4]);
});

test('media redirects outside SoundCloud delivery hosts are rejected', async (t) => {
  const { session, credentials } = createHarness(t);
  const service = new TrackPlaybackService(config, credentials);
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => Response.json({
    http_mp3_128_url: 'https://malicious.example/private-audio.mp3'
  });
  t.after(() => { globalThis.fetch = originalFetch; });

  await assert.rejects(
    () => service.openTrackStream('2303298242', session),
    (error: unknown) => error instanceof HttpApiError && error.error === 'provider_upstream_error'
  );
});

const createHarness = (t: test.TestContext): {
  session: DeviceSession;
  credentials: ProviderCredentialsService;
} => {
  const directory = mkdtempSync(path.join(tmpdir(), 'soundcloud-playback-test-'));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const now = new Date();
  const expiresAtIso = new Date(now.getTime() + 60 * 60 * 1_000).toISOString();
  const session: DeviceSession = {
    sessionId: 'test-provider-session',
    status: 'authenticated',
    createdAtIso: now.toISOString(),
    authenticatedAtIso: now.toISOString(),
    accessTokenExpiresAtIso: expiresAtIso,
    expiresAtIso
  };
  const store = new FileProviderTokenStore(path.join(directory, 'tokens.json'));
  store.save({
    session,
    tokens: {
      accessToken: 'provider-access-token',
      refreshToken: 'provider-refresh-token',
      tokenType: 'Bearer',
      accessTokenExpiresAtIso: expiresAtIso,
      updatedAtIso: now.toISOString(),
      source: 'provider'
    }
  });
  return {
    session,
    credentials: new ProviderCredentialsService(
      new ProviderOAuthService(config),
      store,
      false
    )
  };
};
