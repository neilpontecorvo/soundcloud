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

// Field set observed on the live account for private tracks 2379107141,
// 2303298242 and 2381876697: the legacy progressive field is gone and the only
// full-length variants are HLS.
const CURRENT_STREAM_FIELDS = {
  hls_mp3_128_url: 'https://api.provider.test/tracks/soundcloud:tracks:2379107141/streams/1adf965a/hls?secret_token=s-Abc',
  hls_aac_160_url: 'https://api.provider.test/tracks/soundcloud:tracks:2379107141/streams/049d4ddd/hls?secret_token=s-Abc',
  preview_mp3_128_url: 'https://api.provider.test/tracks/soundcloud:tracks:2379107141/streams/1adf965a/http-preview?secret_token=s-Abc'
};

/** Drives the fMP4 fixture, which exercises `EXT-X-MAP` and three-part ranges. */
const AAC_ONLY_STREAM_FIELDS = {
  hls_aac_160_url: CURRENT_STREAM_FIELDS.hls_aac_160_url,
  preview_mp3_128_url: CURRENT_STREAM_FIELDS.preview_mp3_128_url
};

const AAC_MANIFEST_URL = 'https://playback.media-streaming.soundcloud.cloud/NYEPyEojl2nm/aac_160k/049d4ddd/playlist.m3u8?expires=4102444800&Policy=eyJ0ZXN0IjoxfQ&Signature=sig&Key-Pair-Id=KEY';
const AAC_BASE = 'https://playback.media-streaming.soundcloud.cloud/NYEPyEojl2nm/aac_160k/049d4ddd';
const MP3_MANIFEST_URL = 'https://cf-hls-media.sndcdn.com/playlist/NYEPyEojl2nm.128.mp3/playlist.m3u8?Policy=eyJ0ZXN0IjoxfQ&Signature=sig&Key-Pair-Id=KEY';
const MP3_BASE = 'https://cf-hls-media.sndcdn.com/media';

/** fMP4 media playlist: EXT-X-MAP init segment plus .m4s fragments. */
const aacManifest = [
  '#EXTM3U',
  '#EXT-X-VERSION:7',
  '#EXT-X-TARGETDURATION:10',
  '#EXT-X-MEDIA-SEQUENCE:0',
  '#EXT-X-PLAYLIST-TYPE:VOD',
  `#EXT-X-MAP:URI="${AAC_BASE}/init.mp4?expires=4102444800&Signature=sig"`,
  '#EXTINF:10.007800,',
  `${AAC_BASE}/data000.m4s?expires=4102444800&Signature=sig`,
  '#EXTINF:10.007800,',
  `${AAC_BASE}/data001.m4s?expires=4102444800&Signature=sig`,
  '#EXT-X-ENDLIST'
].join('\n');

/** MP3 media playlist: raw MPEG frames, no init segment. */
const mp3Manifest = [
  '#EXTM3U',
  '#EXT-X-VERSION:6',
  '#EXT-X-PLAYLIST-TYPE:VOD',
  '#EXT-X-TARGETDURATION:10',
  '#EXT-X-MEDIA-SEQUENCE:0',
  '#EXTINF:1.985272,',
  `${MP3_BASE}/0/150/31912/NYEPyEojl2nm.128.mp3?Policy=p&Signature=sig`,
  '#EXTINF:2.977908,',
  `${MP3_BASE}/150/375/47868/NYEPyEojl2nm.128.mp3?Policy=p&Signature=sig`,
  '#EXT-X-ENDLIST'
].join('\n');

interface HarnessOptions {
  streams?: Record<string, string>;
  manifestUrl?: string;
  manifest?: string;
  /** Byte content of each part, in playlist order (init segment first). */
  partBodies?: Uint8Array[];
  omitPartLength?: boolean;
}

interface RecordedRequest {
  url: URL;
  method: string;
  authorization: string | null;
  range: string | null;
}

const installFetch = (t: test.TestContext, options: HarnessOptions) => {
  const streams = options.streams ?? AAC_ONLY_STREAM_FIELDS;
  const manifestUrl = options.manifestUrl ?? AAC_MANIFEST_URL;
  const manifest = options.manifest ?? aacManifest;
  const partBodies = options.partBodies ?? [];
  const requests: RecordedRequest[] = [];
  const originalFetch = globalThis.fetch;
  t.after(() => { globalThis.fetch = originalFetch; });

  // Part bodies are addressed by playlist order; map each part path to its index.
  const partOrder = manifest
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .flatMap((line) => {
      if (line.startsWith('#EXT-X-MAP')) return [line.match(/URI="([^"]+)"/)?.[1] ?? ''];
      return line.startsWith('#') ? [] : [line];
    })
    .map((value) => new URL(value).pathname);

  globalThis.fetch = async (input, init) => {
    const url = new URL(String(input));
    const headers = new Headers(init?.headers);
    const method = init?.method ?? 'GET';
    requests.push({
      url,
      method,
      authorization: headers.get('Authorization'),
      range: headers.get('Range')
    });

    if (url.origin === 'https://api.provider.test' && url.pathname.endsWith('/streams')) {
      return Response.json(streams);
    }
    if (url.origin === 'https://api.provider.test' && url.pathname.endsWith('/hls')) {
      return new Response(null, { status: 302, headers: { Location: manifestUrl } });
    }
    if (url.href === manifestUrl) {
      return new Response(manifest, {
        status: 200,
        headers: { 'Content-Type': 'application/vnd.apple.mpegurl' }
      });
    }

    const index = partOrder.indexOf(url.pathname);
    if (index >= 0) {
      const body = partBodies[index] ?? new Uint8Array(0);
      if (method === 'HEAD') {
        return new Response(null, {
          status: 200,
          headers: options.omitPartLength
            ? { 'Accept-Ranges': 'bytes' }
            : { 'Content-Length': String(body.length), 'Accept-Ranges': 'bytes' }
        });
      }
      const range = headers.get('Range');
      const match = range ? /^bytes=(\d+)-(\d+)$/.exec(range) : null;
      const slice = match ? body.slice(Number(match[1]), Number(match[2]) + 1) : body;
      return new Response(slice, { status: match ? 206 : 200 });
    }

    return new Response('not found', { status: 404 });
  };

  return requests;
};

const readBody = async (body: unknown): Promise<Uint8Array> => new Uint8Array(
  await new Response(body as ReadableStream<Uint8Array>).arrayBuffer()
);

const bytes = (start: number, length: number): Uint8Array => Uint8Array.from(
  { length },
  (_, index) => (start + index) % 256
);

test('the current HLS stream response is republished as one progressive response', async (t) => {
  const { session, credentials } = createHarness(t);
  const partBodies = [bytes(0, 32), bytes(32, 64), bytes(96, 48)];
  const requests = installFetch(t, { partBodies });
  const service = new TrackPlaybackService(config, credentials);

  const result = await service.openTrackStream('2379107141', session);

  assert.equal(result.statusCode, 200);
  assert.equal(result.headers['content-type'], 'audio/mp4');
  assert.equal(result.headers['content-length'], '144');
  assert.equal(result.headers['Accept-Ranges'], 'bytes');
  assert.equal(result.headers['Cache-Control'], 'private, no-store');
  assert.equal(result.headers['content-range'], undefined);

  // The declared length must equal the bytes actually written, and the
  // EXT-X-MAP init segment must lead so the fMP4 is readable from byte zero.
  const body = await readBody(result.body);
  assert.equal(body.length, 144);
  assert.deepEqual(
    Array.from(body),
    [...partBodies[0], ...partBodies[1], ...partBodies[2]]
  );

  // The ~29 second preview field is never used, whatever else is on offer.
  assert.ok(!requests.some((request) => request.url.pathname.includes('http-preview')));
});

test('MP3/HLS is preferred over AAC/HLS because MediaPlayer cannot read fMP4 duration', async (t) => {
  const { session, credentials } = createHarness(t);
  const partBodies = [bytes(0, 24), bytes(24, 40)];
  const requests = installFetch(t, {
    streams: CURRENT_STREAM_FIELDS,
    manifestUrl: MP3_MANIFEST_URL,
    manifest: mp3Manifest,
    partBodies
  });
  const service = new TrackPlaybackService(config, credentials);

  const result = await service.openTrackStream('2379107141', session);

  assert.equal(result.headers['content-type'], 'audio/mpeg');
  assert.equal(result.headers['content-length'], '64');
  assert.deepEqual(
    Array.from(await readBody(result.body)),
    [...partBodies[0], ...partBodies[1]]
  );

  // Both HLS variants are offered; only the MP3 one is resolved.
  const hlsRequests = requests.filter((request) => request.url.pathname.endsWith('/hls'));
  assert.equal(hlsRequests.length, 1);
  assert.ok(hlsRequests[0].url.pathname.includes('1adf965a'));
  assert.ok(!requests.some((request) => request.url.pathname.includes('049d4ddd')));
});

test('range requests are answered from the concatenated byte map', async (t) => {
  const { session, credentials } = createHarness(t);
  const partBodies = [bytes(0, 32), bytes(32, 64), bytes(96, 48)];
  const requests = installFetch(t, { partBodies });
  const service = new TrackPlaybackService(config, credentials);
  const whole = [...partBodies[0], ...partBodies[1], ...partBodies[2]];

  const result = await service.openTrackStream('2379107141', session, 'bytes=100-139');
  assert.equal(result.statusCode, 206);
  assert.equal(result.headers['content-range'], 'bytes 100-139/144');
  assert.equal(result.headers['content-length'], '40');
  const body = await readBody(result.body);
  assert.equal(body.length, 40);
  assert.deepEqual(Array.from(body), whole.slice(100, 140));

  // Only the parts overlapping the range are fetched. Bytes 100-139 sit wholly
  // inside the final fragment, so the seek does not re-download from byte zero.
  const partGets = (suffix: string) => requests
    .filter((request) => request.method === 'GET' && request.url.pathname.endsWith(suffix))
    .map((request) => request.url.pathname.split('/').pop());
  assert.deepEqual(partGets('.m4s'), ['data001.m4s']);
  assert.deepEqual(partGets('init.mp4'), []);

  // A range spanning three parts slices both boundary parts and passes the
  // interior part through whole.
  const spanning = await service.openTrackStream('2379107141', session, 'bytes=20-100');
  assert.equal(spanning.headers['content-range'], 'bytes 20-100/144');
  assert.equal(spanning.headers['content-length'], '81');
  const spanningBody = await readBody(spanning.body);
  assert.equal(spanningBody.length, 81);
  assert.deepEqual(Array.from(spanningBody), whole.slice(20, 101));
  assert.deepEqual(partGets('init.mp4'), ['init.mp4']);

  // MediaPlayer opens with an open-ended range; that must be a 206 with an
  // exact Content-Range, not a 200.
  const openEnded = await service.openTrackStream('2379107141', session, 'bytes=0-');
  assert.equal(openEnded.statusCode, 206);
  assert.equal(openEnded.headers['content-range'], 'bytes 0-143/144');
  assert.equal(openEnded.headers['content-length'], '144');
  assert.equal((await readBody(openEnded.body)).length, 144);

  const suffix = await service.openTrackStream('2379107141', session, 'bytes=-16');
  assert.equal(suffix.headers['content-range'], 'bytes 128-143/144');
  assert.deepEqual(Array.from(await readBody(suffix.body)), whole.slice(128));
});

test('a range beyond the end of the track is answered with 416', async (t) => {
  const { session, credentials } = createHarness(t);
  installFetch(t, { partBodies: [bytes(0, 32), bytes(32, 64), bytes(96, 48)] });
  const service = new TrackPlaybackService(config, credentials);

  const result = await service.openTrackStream('2379107141', session, 'bytes=200-260');
  assert.equal(result.statusCode, 416);
  assert.equal(result.headers['content-range'], 'bytes */144');
  assert.equal((await readBody(result.body)).length, 0);

  await assert.rejects(
    () => service.openTrackStream('2379107141', session, 'bytes=nonsense'),
    (error: unknown) => error instanceof HttpApiError && error.error === 'invalid_request'
  );
});

test('AAC/HLS is used as the fallback when the MP3 variant is absent', async (t) => {
  const { session, credentials } = createHarness(t);
  const requests = installFetch(t, {
    streams: AAC_ONLY_STREAM_FIELDS,
    partBodies: [bytes(0, 32), bytes(32, 64), bytes(96, 48)]
  });
  const service = new TrackPlaybackService(config, credentials);

  const result = await service.openTrackStream('2379107141', session);
  assert.equal(result.statusCode, 200);
  assert.equal(result.headers['content-type'], 'audio/mp4');
  assert.equal(result.headers['content-length'], '144');
  const hlsRequests = requests.filter((request) => request.url.pathname.endsWith('/hls'));
  assert.equal(hlsRequests.length, 1);
  assert.ok(hlsRequests[0].url.pathname.includes('049d4ddd'));
});

test('a preview-only stream response is rejected instead of played', async (t) => {
  const { session, credentials } = createHarness(t);
  installFetch(t, {
    streams: { preview_mp3_128_url: CURRENT_STREAM_FIELDS.preview_mp3_128_url }
  });
  const service = new TrackPlaybackService(config, credentials);

  await assert.rejects(
    () => service.openTrackStream('2379107141', session),
    (error: unknown) => error instanceof HttpApiError && error.error === 'provider_upstream_error'
  );
});

test('playlist segments on unapproved media hosts are rejected', async (t) => {
  const { session, credentials } = createHarness(t);
  installFetch(t, {
    manifest: [
      '#EXTM3U',
      '#EXT-X-VERSION:7',
      '#EXT-X-PLAYLIST-TYPE:VOD',
      '#EXTINF:10.0,',
      'https://malicious.example/aac_160k/data000.m4s',
      '#EXT-X-ENDLIST'
    ].join('\n')
  });
  const service = new TrackPlaybackService(config, credentials);

  await assert.rejects(
    () => service.openTrackStream('2379107141', session),
    (error: unknown) => error instanceof HttpApiError && error.error === 'provider_upstream_error'
  );
});

test('an EXT-X-MAP init segment on an unapproved host is rejected', async (t) => {
  const { session, credentials } = createHarness(t);
  installFetch(t, {
    manifest: [
      '#EXTM3U',
      '#EXT-X-VERSION:7',
      '#EXT-X-MAP:URI="https://malicious.example/init.mp4"',
      '#EXTINF:10.0,',
      `${AAC_BASE}/data000.m4s?expires=4102444800&Signature=sig`,
      '#EXT-X-ENDLIST'
    ].join('\n')
  });
  const service = new TrackPlaybackService(config, credentials);

  await assert.rejects(
    () => service.openTrackStream('2379107141', session),
    (error: unknown) => error instanceof HttpApiError && error.error === 'provider_upstream_error'
  );
});

test('a media redirect to an unapproved host is rejected', async (t) => {
  const { session, credentials } = createHarness(t);
  installFetch(t, { manifestUrl: 'https://malicious.example/playlist.m3u8' });
  const service = new TrackPlaybackService(config, credentials);

  await assert.rejects(
    () => service.openTrackStream('2379107141', session),
    (error: unknown) => error instanceof HttpApiError && error.error === 'provider_upstream_error'
  );
});

test('a part without an exact length is rejected rather than mis-declared', async (t) => {
  const { session, credentials } = createHarness(t);
  installFetch(t, {
    partBodies: [bytes(0, 32), bytes(32, 64), bytes(96, 48)],
    omitPartLength: true
  });
  const service = new TrackPlaybackService(config, credentials);

  await assert.rejects(
    () => service.openTrackStream('2379107141', session),
    (error: unknown) => error instanceof HttpApiError && error.error === 'provider_upstream_error'
  );
});

test('provider credentials never reach media hosts and signed URLs never reach the client', async (t) => {
  const { session, credentials } = createHarness(t);
  const requests = installFetch(t, {
    partBodies: [bytes(0, 32), bytes(32, 64), bytes(96, 48)]
  });
  const service = new TrackPlaybackService(config, credentials);

  const result = await service.openTrackStream('2379107141', session, 'bytes=0-63');
  await readBody(result.body);

  for (const request of requests) {
    if (request.url.origin === 'https://api.provider.test') {
      assert.equal(request.authorization, 'Bearer provider-access-token');
    } else {
      // Manifest, HEAD and part requests all go to signed delivery URLs and
      // must never carry the OAuth bearer.
      assert.equal(request.authorization, null, `bearer leaked to ${request.url.hostname}`);
    }
  }
  assert.ok(requests.some((request) => request.url.hostname.endsWith('.soundcloud.cloud')));

  // Nothing signed or secret is echoed back to the Fire TV.
  const serialisedHeaders = JSON.stringify(result.headers);
  for (const marker of ['secret_token', 'Signature', 'Policy', 'Key-Pair-Id', 'provider-access-token']) {
    assert.ok(!serialisedHeaders.includes(marker), `${marker} leaked to the client`);
  }
});

test('a long playlist is measured with bounded concurrency', async (t) => {
  const { session, credentials } = createHarness(t);
  // A ~100 minute DJ set runs to hundreds of segments; an unbounded fan-out
  // would open that many CDN sockets at once and let self-inflicted contention
  // trip the per-request timeout.
  const segmentCount = 400;
  const segments = Array.from(
    { length: segmentCount },
    (_, index) => `${MP3_BASE}/${index}/150/32/NYEPyEojl2nm.128.mp3?Policy=p&Signature=sig`
  );
  const manifest = [
    '#EXTM3U',
    '#EXT-X-VERSION:6',
    '#EXT-X-PLAYLIST-TYPE:VOD',
    ...segments.flatMap((uri) => ['#EXTINF:9.0,', uri]),
    '#EXT-X-ENDLIST'
  ].join('\n');

  let inFlight = 0;
  let peakInFlight = 0;
  const originalFetch = globalThis.fetch;
  t.after(() => { globalThis.fetch = originalFetch; });
  globalThis.fetch = async (input, init) => {
    const url = new URL(String(input));
    if (url.pathname.endsWith('/streams')) {
      return Response.json({ hls_mp3_128_url: CURRENT_STREAM_FIELDS.hls_mp3_128_url });
    }
    if (url.pathname.endsWith('/hls')) {
      return new Response(null, { status: 302, headers: { Location: MP3_MANIFEST_URL } });
    }
    if (url.href === MP3_MANIFEST_URL) return new Response(manifest, { status: 200 });

    if ((init?.method ?? 'GET') === 'HEAD') {
      inFlight += 1;
      peakInFlight = Math.max(peakInFlight, inFlight);
      await new Promise((resolve) => setTimeout(resolve, 1));
      inFlight -= 1;
      return new Response(null, { status: 200, headers: { 'Content-Length': '32' } });
    }
    return new Response(new Uint8Array(32), { status: 200 });
  };

  const service = new TrackPlaybackService(config, credentials);
  const result = await service.openTrackStream('2283620006', session, 'bytes=0-31');

  assert.equal(result.statusCode, 206);
  assert.equal(result.headers['content-range'], `bytes 0-31/${segmentCount * 32}`);
  assert.ok(peakInFlight > 1, 'measurement should still run in parallel');
  assert.ok(
    peakInFlight <= 12,
    `expected at most 12 concurrent HEAD requests, saw ${peakInFlight}`
  );
});

test('a warm plan serves seeks without re-resolving the provider playlist', async (t) => {
  const { session, credentials } = createHarness(t);
  const requests = installFetch(t, {
    partBodies: [bytes(0, 32), bytes(32, 64), bytes(96, 48)]
  });
  const service = new TrackPlaybackService(config, credentials);

  await readBody((await service.openTrackStream('2379107141', session)).body);
  const afterFirst = requests.filter((request) => request.url.pathname.endsWith('/streams')).length;
  await readBody((await service.openTrackStream('2379107141', session, 'bytes=64-95')).body);
  const afterSeek = requests.filter((request) => request.url.pathname.endsWith('/streams')).length;

  assert.equal(afterFirst, 1);
  assert.equal(afterSeek, 1);
});

test('legacy progressive audio is proxied without forwarding credentials to the media CDN', async (t) => {
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
  assert.deepEqual(Array.from(await readBody(result.body)), [1, 2, 3, 4]);
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
    sessionId: `test-provider-session-${Math.random().toString(36).slice(2)}`,
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
