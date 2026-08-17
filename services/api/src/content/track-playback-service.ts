import { invalidRequest, providerUpstreamError } from '../errors/api-error.js';
import { ProviderCredentialsService } from '../provider/credentials-service.js';
import { ProviderConfig, requireProviderApiConfig } from '../provider/provider-config.js';
import { DeviceSession } from '../session/session-store.js';
import {
  ConcatenatedMediaPlan,
  MEDIA_VARIANTS,
  MediaVariantId,
  PART_MEASUREMENT_CONCURRENCY,
  PREVIEW_STREAM_FIELDS,
  createConcatenatedStream,
  isApprovedMediaUrl,
  mapWithConcurrency,
  parseMediaPlaylist,
  parseUrl,
  planExpiryMs,
  resolveByteRange,
  toMediaParts
} from './hls-media-plan.js';
import type { ReadableStream as NodeReadableStream } from 'node:stream/web';

export interface TrackStreamResult {
  statusCode: number;
  body: NodeReadableStream<Uint8Array>;
  headers: Record<string, string>;
}

/**
 * Resolves and proxies SoundCloud audio while keeping provider credentials on
 * the backend. The Fire TV authenticates only with its opaque app session.
 *
 * SoundCloud now publishes full-length private-track audio as HLS only, so the
 * service resolves the media playlist server-side and republishes it as one
 * progressive, range-addressable response. The Android MediaPlayer path is
 * unchanged: it still opens `/v1/tracks/:id/stream` and still receives seekable
 * progressive audio with an exact `Content-Length`.
 */
export class TrackPlaybackService {
  /** Warm plans let a seek reuse a resolved playlist instead of re-resolving. */
  private readonly plans = new Map<string, ConcatenatedMediaPlan>();

  constructor(
    private readonly config: ProviderConfig,
    private readonly credentials: ProviderCredentialsService
  ) {}

  async openTrackStream(
    trackId: string,
    session: DeviceSession,
    rangeHeader?: string
  ): Promise<TrackStreamResult> {
    const safeTrackId = trackId.trim();
    if (!safeTrackId || safeTrackId.length > 160) {
      throw invalidRequest('A valid track id is required.');
    }
    const safeRange = validateRangeShape(rangeHeader);
    const config = requireProviderApiConfig(this.config);
    const apiOrigin = new URL(config.apiBaseUrl).origin;

    const cacheKey = `${session.sessionId}:${safeTrackId}`;
    const warmPlan = this.plans.get(cacheKey);
    if (warmPlan) {
      if (warmPlan.expiresAtMs > Date.now()) {
        return serveFromPlan(warmPlan, safeRange, config.requestTimeoutMs);
      }
      // A stale plan holds one URL per part, so drop it rather than let it
      // linger for the life of the process while it is re-resolved.
      this.plans.delete(cacheKey);
    }

    const accessToken = await this.credentials.getAccessToken(session);
    const streamPayload = await this.readStreamCatalogue(safeTrackId, accessToken, config, apiOrigin);
    const selection = selectVariant(streamPayload, apiOrigin);

    if (!selection.isHls) {
      // Legacy progressive stream, retained for compatibility if SoundCloud
      // ever serves it again. Upstream owns Range for this shape.
      return this.proxyProgressiveStream(selection.url, accessToken, safeRange, config.requestTimeoutMs);
    }

    const plan = await this.resolveHlsPlan(selection, accessToken, config.requestTimeoutMs);
    this.plans.set(cacheKey, plan);
    return serveFromPlan(plan, safeRange, config.requestTimeoutMs);
  }

  private async readStreamCatalogue(
    trackId: string,
    accessToken: string,
    config: ProviderConfig,
    apiOrigin: string
  ): Promise<Record<string, unknown>> {
    const streamsEndpoint = new URL(
      `/tracks/${encodeURIComponent(trackId)}/streams`,
      config.apiBaseUrl
    );
    if (streamsEndpoint.origin !== apiOrigin) {
      throw providerUpstreamError('Provider stream URL was outside the configured API host.');
    }

    const response = await timedFetch(
      streamsEndpoint,
      {
        headers: {
          Accept: 'application/json',
          Authorization: `Bearer ${accessToken}`
        }
      },
      config.requestTimeoutMs
    );
    if (!response.ok) {
      throw providerUpstreamError('Provider did not return playable track streams.');
    }
    const payload = await response.json().catch(() => null);
    if (!payload || typeof payload !== 'object') {
      throw providerUpstreamError('Provider returned an unreadable stream catalogue.');
    }
    return payload as Record<string, unknown>;
  }

  /**
   * Resolves the signed media playlist and measures every part so the
   * concatenated stream can advertise an exact length and answer seeks.
   * Playlist and part requests are unauthenticated: the delivery URLs are
   * already signed, and the OAuth bearer must never reach a CDN host.
   */
  private async resolveHlsPlan(
    selection: VariantSelection,
    accessToken: string,
    timeoutMs: number
  ): Promise<ConcatenatedMediaPlan> {
    const redirectResponse = await timedFetch(
      selection.url,
      {
        headers: { Accept: '*/*', Authorization: `Bearer ${accessToken}` },
        redirect: 'manual'
      },
      timeoutMs
    );
    const manifestUrl = await readManifestUrl(redirectResponse);
    if (!manifestUrl || !isApprovedMediaUrl(manifestUrl)) {
      throw providerUpstreamError('Provider media redirect used an unapproved host.');
    }

    const manifestResponse = await timedFetch(
      manifestUrl,
      { headers: { Accept: '*/*' } },
      timeoutMs
    );
    if (!manifestResponse.ok) {
      throw providerUpstreamError('Provider stream playlist was unavailable.');
    }
    const partUrls = parseMediaPlaylist(await manifestResponse.text(), manifestUrl);

    const sizes = await mapWithConcurrency(
      partUrls,
      PART_MEASUREMENT_CONCURRENCY,
      async (url) => {
        const head = await timedFetch(url, { method: 'HEAD', headers: { Accept: '*/*' } }, timeoutMs);
        if (!head.ok) {
          throw providerUpstreamError('Provider media part could not be measured.');
        }
        return Number(head.headers.get('content-length'));
      }
    );

    const parts = toMediaParts(partUrls, sizes);
    return {
      variant: selection.variant,
      contentType: selection.contentType,
      parts,
      totalBytes: parts.reduce((total, part) => total + part.size, 0),
      expiresAtMs: planExpiryMs([manifestUrl, ...partUrls], Date.now())
    };
  }

  private async proxyProgressiveStream(
    streamUrl: URL,
    accessToken: string,
    range: string | undefined,
    timeoutMs: number
  ): Promise<TrackStreamResult> {
    const authenticatedHeaders: Record<string, string> = {
      Accept: 'audio/*',
      Authorization: `Bearer ${accessToken}`
    };
    if (range) authenticatedHeaders.Range = range;
    const authenticatedResponse = await timedFetch(
      streamUrl,
      { headers: authenticatedHeaders, redirect: 'manual' },
      timeoutMs
    );
    if (authenticatedResponse.ok && authenticatedResponse.body) {
      return responseToStreamResult(authenticatedResponse);
    }
    if (authenticatedResponse.status < 300 || authenticatedResponse.status >= 400) {
      throw providerUpstreamError('Provider full-length stream was unavailable.');
    }
    const mediaUrl = await readManifestUrl(authenticatedResponse);
    if (!mediaUrl || !isApprovedMediaUrl(mediaUrl)) {
      throw providerUpstreamError('Provider media redirect used an unapproved host.');
    }

    const mediaHeaders: Record<string, string> = { Accept: 'audio/*' };
    if (range) mediaHeaders.Range = range;
    return responseToStreamResult(await timedFetch(
      mediaUrl,
      { headers: mediaHeaders, redirect: 'manual' },
      timeoutMs
    ));
  }
}

interface VariantSelection {
  variant: MediaVariantId;
  contentType: string;
  url: URL;
  isHls: boolean;
}

const selectVariant = (
  payload: Record<string, unknown>,
  apiOrigin: string
): VariantSelection => {
  for (const candidate of MEDIA_VARIANTS) {
    if (PREVIEW_STREAM_FIELDS.has(candidate.field)) continue;
    const value = payload[candidate.field];
    if (typeof value !== 'string') continue;
    const url = parseUrl(value);
    if (!url || url.origin !== apiOrigin) continue;
    return {
      variant: candidate.id,
      contentType: candidate.contentType,
      url,
      isHls: candidate.isHls
    };
  }
  throw providerUpstreamError('Provider did not return an approved full-length audio stream.');
};

const serveFromPlan = (
  plan: ConcatenatedMediaPlan,
  rangeHeader: string | undefined,
  timeoutMs: number
): TrackStreamResult => {
  const range = resolveByteRange(rangeHeader, plan.totalBytes);
  if (!range) {
    return {
      statusCode: 416,
      body: emptyStream(),
      headers: {
        'Cache-Control': 'private, no-store',
        'Accept-Ranges': 'bytes',
        'content-range': `bytes */${plan.totalBytes}`
      }
    };
  }

  const length = range.end - range.start + 1;
  const headers: Record<string, string> = {
    'Cache-Control': 'private, no-store',
    'Accept-Ranges': 'bytes',
    'content-type': plan.contentType,
    'content-length': String(length)
  };
  if (range.isPartial) {
    headers['content-range'] = `bytes ${range.start}-${range.end}/${plan.totalBytes}`;
  }

  const body = createConcatenatedStream(plan.parts, range, (url, partRange) => timedFetch(
    url,
    {
      headers: partRange ? { Accept: '*/*', Range: partRange } : { Accept: '*/*' },
      redirect: 'follow'
    },
    timeoutMs
  ));

  return {
    statusCode: range.isPartial ? 206 : 200,
    body: body as unknown as NodeReadableStream<Uint8Array>,
    headers
  };
};

const emptyStream = (): NodeReadableStream<Uint8Array> => new ReadableStream<Uint8Array>({
  start(controller) {
    controller.close();
  }
}) as unknown as NodeReadableStream<Uint8Array>;

const validateRangeShape = (value: string | undefined): string | undefined => {
  if (!value) return undefined;
  const trimmed = value.trim();
  if (!/^bytes=\d*-\d*$/.test(trimmed)) {
    throw invalidRequest('Only a single valid byte range is supported.');
  }
  return trimmed;
};

const readManifestUrl = async (response: Response): Promise<URL | undefined> => {
  const header = response.headers.get('location');
  if (header) return parseUrl(header);

  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('json')) return undefined;
  const payload = await response.json().catch(() => null) as {
    location?: unknown;
    url?: unknown;
  } | null;
  const candidate = payload?.location ?? payload?.url;
  return typeof candidate === 'string' ? parseUrl(candidate) : undefined;
};

const responseToStreamResult = (response: Response): TrackStreamResult => {
  if (!response.ok || !response.body) {
    throw providerUpstreamError('Provider media stream was unavailable.');
  }

  const headers: Record<string, string> = {
    'Cache-Control': 'private, no-store',
    'Accept-Ranges': response.headers.get('accept-ranges') ?? 'bytes'
  };
  for (const name of ['content-type', 'content-length', 'content-range']) {
    const value = response.headers.get(name);
    if (value) headers[name] = value;
  }
  return {
    statusCode: response.status,
    body: response.body as unknown as NodeReadableStream<Uint8Array>,
    headers
  };
};

const timedFetch = async (
  url: URL,
  init: RequestInit,
  timeoutMs: number
): Promise<Response> => {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') {
      throw providerUpstreamError('Provider stream request timed out.');
    }
    throw providerUpstreamError('Provider stream request failed.');
  } finally {
    clearTimeout(timeout);
  }
};
