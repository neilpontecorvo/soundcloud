import { invalidRequest, providerUpstreamError } from '../errors/api-error.js';
import { ProviderCredentialsService } from '../provider/credentials-service.js';
import { ProviderConfig, requireProviderApiConfig } from '../provider/provider-config.js';
import { DeviceSession } from '../session/session-store.js';
import type { ReadableStream as NodeReadableStream } from 'node:stream/web';

export interface TrackStreamResult {
  statusCode: number;
  body: NodeReadableStream<Uint8Array>;
  headers: Record<string, string>;
}

/**
 * Resolves and proxies SoundCloud audio while keeping provider credentials on
 * the backend. The Fire TV authenticates only with its opaque app session.
 */
export class TrackPlaybackService {
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
    const safeRange = validateRange(rangeHeader);
    const accessToken = await this.credentials.getAccessToken(session);
    const config = requireProviderApiConfig(this.config);
    const streamsEndpoint = new URL(
      `/tracks/${encodeURIComponent(safeTrackId)}/streams`,
      config.apiBaseUrl
    );
    const apiOrigin = new URL(config.apiBaseUrl).origin;
    if (streamsEndpoint.origin !== apiOrigin) {
      throw providerUpstreamError('Provider stream URL was outside the configured API host.');
    }

    const streamsResponse = await timedFetch(
      streamsEndpoint,
      {
        headers: {
          Accept: 'application/json',
          Authorization: `Bearer ${accessToken}`
        },
      },
      config.requestTimeoutMs
    );
    if (!streamsResponse.ok) {
      throw providerUpstreamError('Provider did not return playable track streams.');
    }
    const streamPayload = await streamsResponse.json().catch(() => null) as {
      http_mp3_128_url?: unknown;
    } | null;
    const authenticatedStreamUrl = typeof streamPayload?.http_mp3_128_url === 'string'
      ? parseUrl(streamPayload.http_mp3_128_url)
      : undefined;
    if (!authenticatedStreamUrl || authenticatedStreamUrl.origin !== apiOrigin) {
      throw providerUpstreamError('Provider did not return an approved full-length HTTP stream.');
    }

    const mediaHeaders: Record<string, string> = { Accept: 'audio/*' };
    if (safeRange) mediaHeaders.Range = safeRange;
    const authenticatedHeaders: Record<string, string> = {
      Accept: 'application/json',
      Authorization: `Bearer ${accessToken}`
    };
    if (safeRange) authenticatedHeaders.Range = safeRange;
    const authenticatedResponse = await timedFetch(
      authenticatedStreamUrl,
      {
        headers: authenticatedHeaders,
        redirect: 'manual'
      },
      config.requestTimeoutMs
    );
    if (authenticatedResponse.ok && authenticatedResponse.body) {
      return responseToStreamResult(authenticatedResponse);
    }
    if (authenticatedResponse.status < 300 || authenticatedResponse.status >= 400) {
      throw providerUpstreamError('Provider full-length stream was unavailable.');
    }
    const mediaUrl = await readRedirectUrl(authenticatedResponse);
    if (!mediaUrl || !isApprovedMediaUrl(mediaUrl)) {
      throw providerUpstreamError('Provider media redirect used an unapproved host.');
    }
    return responseToStreamResult(await timedFetch(
      mediaUrl,
      { headers: mediaHeaders, redirect: 'manual' },
      config.requestTimeoutMs
    ));
  }
}

const validateRange = (value: string | undefined): string | undefined => {
  if (!value) return undefined;
  const trimmed = value.trim();
  if (!/^bytes=\d*-\d*$/.test(trimmed)) {
    throw invalidRequest('Only a single valid byte range is supported.');
  }
  return trimmed;
};

const readRedirectUrl = async (response: Response): Promise<URL | undefined> => {
  const header = response.headers.get('location');
  if (header) return parseUrl(header);

  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('json')) return undefined;
  const payload = await response.json().catch(() => null) as { location?: unknown } | null;
  return typeof payload?.location === 'string' ? parseUrl(payload.location) : undefined;
};

const parseUrl = (value: string): URL | undefined => {
  try {
    return new URL(value);
  } catch {
    return undefined;
  }
};

const isApprovedMediaUrl = (url: URL): boolean => {
  if (url.protocol !== 'https:') return false;
  const host = url.hostname.toLowerCase();
  return host === 'sndcdn.com'
    || host.endsWith('.sndcdn.com')
    || host === 'soundcloud.cloud'
    || host.endsWith('.soundcloud.cloud');
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
