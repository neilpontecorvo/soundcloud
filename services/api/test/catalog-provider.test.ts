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
  artistSpotlightIds: [],
  resolvePath: '/resolve',
  feedPath: '/me/activities',
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

  const playlist = await provider.getPlaylistDetail('local-debug-playlist', harness.session);
  assert.equal(playlist.trackCount, 2);
  assert.equal(playlist.tracks.length, 2);
  assert.ok(playlist.tracks.every((track) => track.webUrl?.startsWith('https://soundcloud.com/')));
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
  await assert.rejects(
    () => provider.getPlaylistDetail('local-debug-playlist', harness.session),
    rejectsAsInvalidSession
  );
});

test('real provider sessions continue through the provider adapter', async (t) => {
  const harness = createHarness(t, 'provider', true);
  const provider = new ProviderCatalogProvider(config, harness.credentials);
  const originalFetch = globalThis.fetch;
  let requestCount = 0;

  globalThis.fetch = async (input, init) => {
    requestCount += 1;
    const url = new URL(String(input));
    assert.equal(new Headers(init?.headers).get('Authorization'), 'Bearer provider-access-token');
    if (url.pathname === '/me/recently-played/tracks') {
      return Response.json({
        collection: [
          {
            id: 43,
            kind: 'track',
            title: 'Recently Played Track',
            permalink_url: 'https://provider.test/recent-track'
          }
        ]
      });
    }
    assert.equal(url.pathname, '/me/activities');
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
  assert.equal(requestCount, 2);
  assert.deepEqual(feed.items.map((item) => item.title), ['Provider Track']);
  assert.deepEqual(feed.sections?.map((section) => section.title), [
    'My Feed',
    'Recently Played'
  ]);
});

test('artist Home preserves four deterministic rows with own-music recommendation fallback', async (t) => {
  const harness = createHarness(t, 'provider', true);
  const provider = new ProviderCatalogProvider({
    ...config,
    artistUrl: 'https://soundcloud.com/anelo',
    artistSpotlightIds: ['4', '5', '6', '7', '8']
  }, harness.credentials);
  const originalFetch = globalThis.fetch;

  globalThis.fetch = async (input) => {
    const url = new URL(String(input));
    const track = (id: number, title: string) => ({
      id,
      kind: 'track',
      title,
      permalink_url: `https://soundcloud.com/example/${id}`,
      user: { username: 'Provider Artist' }
    });
    const playlist = (id: number, title: string, playbackCount: number) => ({
      id,
      kind: 'playlist',
      title,
      playback_count: playbackCount,
      permalink_url: `https://soundcloud.com/example/sets/${id}`,
      user: { username: 'ANELO [Unifi Music]' }
    });

    if (url.pathname === '/me/activities') {
      return Response.json({ collection: [{ origin: track(1, 'Followed Artist Track') }] });
    }
    if (url.pathname === '/me/recently-played/tracks') {
      return Response.json({ collection: [{ track: track(2, 'Recent Track') }] });
    }
    if (url.pathname === '/resolve') {
      assert.equal(url.searchParams.get('url'), 'https://soundcloud.com/anelo');
      return Response.json({ id: 99, kind: 'user', username: 'ANELO [Unifi Music]' });
    }
    if (url.pathname === '/users/99/tracks') {
      return Response.json({ collection: [track(3, 'Own Track')] });
    }
    if (url.pathname === '/users/99/playlists') {
      return Response.json({
        collection: [
          playlist(4, 'Featured Playlist', 500),
          playlist(5, 'Second Spotlight', 400),
          playlist(6, 'Third Spotlight', 300),
          playlist(7, 'Fourth Spotlight', 200),
          playlist(8, 'Fifth Spotlight', 100),
          playlist(9, 'More Own Music', 50)
        ]
      });
    }
    throw new Error(`Unexpected provider URL: ${url}`);
  };

  t.after(() => {
    globalThis.fetch = originalFetch;
  });

  const feed = await provider.getFeed(harness.session);
  assert.deepEqual(feed.sections?.map((section) => section.title), [
    'My Feed',
    'More from ANELO [Unifi Music]',
    'ANELO Spotlight',
    'Recently Played'
  ]);
  assert.deepEqual(feed.sections?.[0]?.items.map((item) => item.title), ['Followed Artist Track']);
  assert.deepEqual(feed.sections?.[1]?.items.map((item) => item.title), ['More Own Music']);
  assert.deepEqual(feed.sections?.[2]?.items.map((item) => item.title), [
    'Featured Playlist',
    'Second Spotlight',
    'Third Spotlight',
    'Fourth Spotlight',
    'Fifth Spotlight'
  ]);
  assert.deepEqual(feed.sections?.[3]?.items.map((item) => item.title), ['Recent Track']);
});

test('playlist detail normalizes high-resolution artwork and complete queue metadata', async (t) => {
  const harness = createHarness(t, 'provider', true);
  const provider = new ProviderCatalogProvider(config, harness.credentials);
  const originalFetch = globalThis.fetch;

  globalThis.fetch = async (input) => {
    const url = new URL(String(input));
    if (url.pathname === '/playlists/77') {
      return Response.json({
        id: 77,
        kind: 'playlist',
        title: 'Provider Playlist',
        artwork_url: 'https://i1.sndcdn.com/artworks-test-large.jpg',
        duration: 180000,
        track_count: 2,
        permalink_url: 'https://soundcloud.com/example/sets/provider-playlist',
        user: { username: 'Provider Artist' }
      });
    }
    if (url.pathname === '/playlists/77/tracks') {
      return Response.json({
        collection: [
          {
            id: 1,
            kind: 'track',
            title: 'First',
            sharing: 'private',
            duration: 60000,
            waveform_url: 'https://wave.sndcdn.com/first.png',
            permalink_url: 'https://soundcloud.com/example/first',
            user: {
              username: 'Provider Artist',
              permalink_url: 'https://soundcloud.com/example'
            }
          },
          {
            id: 2,
            kind: 'track',
            title: 'Second',
            duration: 120000,
            permalink_url: 'https://soundcloud.com/example/second',
            user: { username: 'Provider Artist' }
          }
        ]
      });
    }
    throw new Error(`Unexpected provider path: ${url.pathname}`);
  };

  t.after(() => {
    globalThis.fetch = originalFetch;
  });

  const detail = await provider.getPlaylistDetail('77', harness.session);
  assert.equal(detail.artworkUrl, 'https://i1.sndcdn.com/artworks-test-t500x500.jpg');
  assert.equal(detail.durationMs, 180000);
  assert.equal(detail.trackCount, 2);
  assert.deepEqual(detail.tracks.map((track) => track.title), ['First', 'Second']);
  assert.equal(detail.tracks[0]?.waveformUrl, 'https://wave.sndcdn.com/first.png');
  assert.equal(detail.tracks[0]?.isPrivate, true);
  assert.equal(detail.tracks[0]?.creatorProfileUrl, 'https://soundcloud.com/example');
  assert.equal(detail.tracks[1]?.isPrivate, false);
});

test('search and library follow provider pagination links', async (t) => {
  const harness = createHarness(t, 'provider', true);
  const provider = new ProviderCatalogProvider(config, harness.credentials);
  const originalFetch = globalThis.fetch;

  globalThis.fetch = async (input) => {
    const url = new URL(String(input));
    const item = (id: number, kind: string, title: string) => ({
      id,
      kind,
      title,
      permalink_url: `https://soundcloud.com/example/${id}`,
      user: { username: 'Provider Artist' }
    });

    if (url.pathname === '/tracks' && url.searchParams.get('cursor') === 'next') {
      return Response.json({ collection: [item(2, 'track', 'Search Two')] });
    }
    if (url.pathname === '/tracks') {
      assert.equal(url.searchParams.get('q'), 'paged');
      assert.equal(url.searchParams.get('limit'), '200');
      assert.equal(url.searchParams.get('linked_partitioning'), 'true');
      return Response.json({
        collection: [item(1, 'track', 'Search One')],
        next_href: 'https://api.provider.test/tracks?cursor=next'
      });
    }
    if (url.pathname === '/playlists' || url.pathname === '/users') {
      return Response.json({ collection: [] });
    }
    if (url.pathname === '/me/likes/tracks' && url.searchParams.get('cursor') === 'next') {
      return Response.json({ collection: [item(12, 'track', 'Saved Two')] });
    }
    if (url.pathname === '/me/likes/tracks') {
      return Response.json({
        collection: [item(11, 'track', 'Saved One')],
        next_href: 'https://api.provider.test/me/likes/tracks?cursor=next'
      });
    }
    if (url.pathname === '/me/playlists') {
      return Response.json({ collection: [item(21, 'playlist', 'Playlist One')] });
    }
    if (url.pathname === '/me/likes/playlists') {
      return Response.json({
        collection: [
          item(21, 'playlist', 'Playlist One'),
          { ...item(22, 'playlist', 'Provider Album'), set_type: 'album' }
        ]
      });
    }
    throw new Error(`Unexpected provider URL: ${url}`);
  };

  t.after(() => {
    globalThis.fetch = originalFetch;
  });

  const search = await provider.search('paged', harness.session);
  assert.deepEqual(search.items.map((entry) => entry.title), ['Search One', 'Search Two']);

  const library = await provider.getLibrary(harness.session);
  assert.deepEqual(library.sections[0]?.items.map((entry) => entry.title), ['Saved One', 'Saved Two']);
  assert.deepEqual(library.sections[1]?.items.map((entry) => entry.title), ['Playlist One']);
  assert.deepEqual(library.sections[2]?.items.map((entry) => entry.title), ['Provider Album']);
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
