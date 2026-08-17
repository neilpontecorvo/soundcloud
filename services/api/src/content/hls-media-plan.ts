import { invalidRequest, providerUpstreamError } from '../errors/api-error.js';

/**
 * SoundCloud no longer returns a progressive `http_mp3_128_url` for private
 * tracks. The current full-length variants are HLS, so the backend resolves the
 * media playlist, validates every part against the approved delivery hosts, and
 * republishes the parts to the Fire TV as one byte-addressable progressive
 * response. Both current variants concatenate losslessly:
 *
 * - `hls_aac_160_url`  -> fMP4: `EXT-X-MAP` init segment plus `.m4s` fragments.
 * - `hls_mp3_128_url`  -> raw MPEG-1 Layer III frames, no init segment.
 */
export type MediaVariantId = 'hls_aac_160' | 'hls_mp3_128' | 'http_mp3_128';

export interface MediaPart {
  url: URL;
  /** Exact byte length, read from the delivery host via HEAD. */
  size: number;
  /** Byte offset of this part inside the concatenated stream. */
  offset: number;
}

export interface ConcatenatedMediaPlan {
  variant: MediaVariantId;
  contentType: string;
  parts: MediaPart[];
  totalBytes: number;
  expiresAtMs: number;
}

export interface ResolvedByteRange {
  start: number;
  end: number;
  isPartial: boolean;
}

/**
 * Highest-quality *playable* variant first.
 *
 * `hls_aac_160` is the higher-bitrate stream and it does concatenate into a
 * well-formed fMP4, but the Fire TV's native MediaPlayer cannot read a total
 * duration from it: on the AFTKM it prepares and then reports only the first
 * fragment (`PrivateTrackPrepared: durationMs=10008` for a 3:52 track), which
 * breaks both full-length playback and seeking. `hls_mp3_128` concatenates into
 * a plain constant-bitrate MPEG stream that the same MediaPlayer path handles
 * correctly, so it leads. AAC stays in the ladder as the fallback for tracks
 * where SoundCloud omits the MP3 variant, and `http_mp3_128_url` is retained
 * only for compatibility if the legacy progressive stream ever returns.
 */
export const MEDIA_VARIANTS: ReadonlyArray<{
  id: MediaVariantId;
  field: string;
  contentType: string;
  isHls: boolean;
}> = [
  { id: 'hls_mp3_128', field: 'hls_mp3_128_url', contentType: 'audio/mpeg', isHls: true },
  { id: 'hls_aac_160', field: 'hls_aac_160_url', contentType: 'audio/mp4', isHls: true },
  { id: 'http_mp3_128', field: 'http_mp3_128_url', contentType: 'audio/mpeg', isHls: false }
];

/**
 * Preview URLs are deliberately excluded: `preview_mp3_128_url` resolves to a
 * ~29 second excerpt, which is the truncated playback this service must avoid.
 */
export const PREVIEW_STREAM_FIELDS: ReadonlySet<string> = new Set([
  'preview_mp3_128_url',
  'http_mp3_128_preview_url'
]);

export const isApprovedMediaUrl = (url: URL): boolean => {
  if (url.protocol !== 'https:') return false;
  const host = url.hostname.toLowerCase();
  return host === 'sndcdn.com'
    || host.endsWith('.sndcdn.com')
    || host === 'soundcloud.cloud'
    || host.endsWith('.soundcloud.cloud');
};

export const parseUrl = (value: string, base?: URL): URL | undefined => {
  try {
    return new URL(value, base);
  } catch {
    return undefined;
  }
};

/**
 * Extracts the ordered part list of an HLS media playlist. The `EXT-X-MAP`
 * initialisation segment, when present, is the first part. Every resolved URI
 * must pass the approved-media-host check; a playlist is untrusted input.
 */
export const parseMediaPlaylist = (body: string, manifestUrl: URL): URL[] => {
  const lines = body.split('\n').map((line) => line.trim()).filter(Boolean);
  if (!lines[0]?.startsWith('#EXTM3U')) {
    throw providerUpstreamError('Provider stream playlist was not a valid HLS manifest.');
  }
  if (lines.some((line) => line.startsWith('#EXT-X-STREAM-INF'))) {
    throw providerUpstreamError('Provider returned a master playlist instead of a media playlist.');
  }
  if (lines.some((line) => line.startsWith('#EXT-X-KEY') && !/METHOD=NONE/i.test(line))) {
    throw providerUpstreamError('Provider stream playlist is encrypted and cannot be republished.');
  }

  const parts: URL[] = [];
  const initUri = lines
    .find((line) => line.startsWith('#EXT-X-MAP'))
    ?.match(/URI="([^"]+)"/)?.[1];
  if (initUri) parts.push(requireApprovedPart(initUri, manifestUrl));
  for (const line of lines) {
    if (line.startsWith('#')) continue;
    parts.push(requireApprovedPart(line, manifestUrl));
  }

  if (parts.length === 0) {
    throw providerUpstreamError('Provider stream playlist contained no media segments.');
  }
  return parts;
};

const requireApprovedPart = (uri: string, manifestUrl: URL): URL => {
  const resolved = parseUrl(uri, manifestUrl);
  if (!resolved || !isApprovedMediaUrl(resolved)) {
    throw providerUpstreamError('Provider stream playlist referenced an unapproved media host.');
  }
  return resolved;
};

/**
 * Measuring parts is a fan-out over the whole playlist, and a long DJ set runs
 * to hundreds of segments. Bounding the in-flight requests keeps one slow
 * delivery response from tripping the per-request timeout under self-inflicted
 * contention, and keeps the backend from opening hundreds of CDN sockets at
 * once. Results stay in playlist order, which the byte-offset map depends on.
 */
export const mapWithConcurrency = async <T, R>(
  items: readonly T[],
  limit: number,
  map: (item: T, index: number) => Promise<R>
): Promise<R[]> => {
  const results = new Array<R>(items.length);
  let next = 0;
  const worker = async (): Promise<void> => {
    while (true) {
      const index = next;
      next += 1;
      if (index >= items.length) return;
      results[index] = await map(items[index], index);
    }
  };
  await Promise.all(
    Array.from({ length: Math.max(1, Math.min(limit, items.length)) }, worker)
  );
  return results;
};

export const PART_MEASUREMENT_CONCURRENCY = 12;

export const toMediaParts = (urls: URL[], sizes: number[]): MediaPart[] => {
  let offset = 0;
  return urls.map((url, index) => {
    const size = sizes[index];
    if (!Number.isSafeInteger(size) || size <= 0) {
      throw providerUpstreamError('Provider media part did not report an exact length.');
    }
    const part = { url, size, offset };
    offset += size;
    return part;
  });
};

/**
 * Signed delivery URLs expire. The plan is only reusable while its signature is
 * comfortably valid, so seeks re-use a warm plan but a stale one is re-resolved.
 */
export const planExpiryMs = (
  urls: URL[],
  nowMs: number,
  maxTtlMs = 5 * 60 * 1000,
  safetyMarginMs = 60 * 1000
): number => {
  const ceiling = nowMs + maxTtlMs;
  const signatureExpiries = urls
    .map((url) => Number(url.searchParams.get('expires')))
    .filter((seconds) => Number.isFinite(seconds) && seconds > 0)
    .map((seconds) => seconds * 1000 - safetyMarginMs);
  if (signatureExpiries.length === 0) return ceiling;
  return Math.min(ceiling, ...signatureExpiries);
};

/**
 * Parses a single HTTP byte range against a known total length. Returns
 * `undefined` for an unsatisfiable range so the caller can answer 416.
 */
export const resolveByteRange = (
  rangeHeader: string | undefined,
  totalBytes: number
): ResolvedByteRange | undefined => {
  if (!rangeHeader) return { start: 0, end: Math.max(totalBytes - 1, 0), isPartial: false };

  const match = /^bytes=(\d*)-(\d*)$/.exec(rangeHeader.trim());
  if (!match) throw invalidRequest('Only a single valid byte range is supported.');
  const [, rawStart, rawEnd] = match;
  if (rawStart === '' && rawEnd === '') {
    throw invalidRequest('Only a single valid byte range is supported.');
  }

  if (rawStart === '') {
    const suffixLength = Number(rawEnd);
    if (suffixLength <= 0) return undefined;
    return {
      start: Math.max(totalBytes - suffixLength, 0),
      end: totalBytes - 1,
      isPartial: true
    };
  }

  const start = Number(rawStart);
  if (start >= totalBytes) return undefined;
  const end = rawEnd === '' ? totalBytes - 1 : Math.min(Number(rawEnd), totalBytes - 1);
  if (end < start) return undefined;
  return { start, end, isPartial: true };
};

export type PartFetcher = (url: URL, range?: string) => Promise<Response>;

/**
 * Streams `[start, end]` of the concatenated media without buffering the whole
 * track: only the overlapping parts are fetched, the boundary parts are sliced
 * with their own byte range, and the next part is prefetched one deep so audio
 * keeps flowing while the following request is in flight.
 */
export const createConcatenatedStream = (
  parts: MediaPart[],
  range: ResolvedByteRange,
  fetchPart: PartFetcher
): ReadableStream<Uint8Array> => {
  const needed = parts.filter(
    (part) => part.offset <= range.end && part.offset + part.size > range.start
  );

  const requestFor = (part: MediaPart): Promise<Response> => {
    const from = Math.max(range.start - part.offset, 0);
    const to = Math.min(range.end - part.offset, part.size - 1);
    const isWholePart = from === 0 && to === part.size - 1;
    return fetchPart(part.url, isWholePart ? undefined : `bytes=${from}-${to}`);
  };

  let index = 0;
  let pending: Promise<Response> | undefined;
  let reader: ReadableStreamDefaultReader<Uint8Array> | undefined;

  return new ReadableStream<Uint8Array>({
    async pull(controller) {
      while (true) {
        if (!reader) {
          if (index >= needed.length) {
            controller.close();
            return;
          }
          const response = await (pending ?? requestFor(needed[index]));
          index += 1;
          pending = index < needed.length ? requestFor(needed[index]) : undefined;
          if (!response.ok || !response.body) {
            throw providerUpstreamError('Provider media part was unavailable.');
          }
          reader = response.body.getReader();
        }

        const { done, value } = await reader.read();
        if (done) {
          reader = undefined;
          continue;
        }
        if (value.byteLength > 0) {
          controller.enqueue(value);
          return;
        }
      }
    },
    async cancel() {
      await reader?.cancel().catch(() => undefined);
      await pending?.then((response) => response.body?.cancel()).catch(() => undefined);
    }
  });
};
