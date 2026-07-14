import { HttpApiError, providerUpstreamError } from '../errors/api-error.js';
import { ProviderCredentialsService } from '../provider/credentials-service.js';
import { ProviderConfig, requireProviderApiConfig } from '../provider/provider-config.js';
import { DeviceSession } from '../session/session-store.js';

export type MediaKind = 'track' | 'playlist' | 'station';

export interface MediaCard {
  id: string;
  kind: MediaKind;
  title: string;
  subtitle?: string;
  creatorName?: string;
  artworkUrl?: string | null;
  durationText?: string | null;
  webUrl?: string | null;
}

export interface FeedPayload {
  generatedAtIso: string;
  items: MediaCard[];
  sections?: LibrarySection[];
}

export interface SearchPayload {
  generatedAtIso: string;
  query: string;
  items: MediaCard[];
}

export interface LibrarySection {
  id: string;
  title: string;
  items: MediaCard[];
}

export interface LibraryPayload {
  generatedAtIso: string;
  sections: LibrarySection[];
}

export interface CatalogProvider {
  getFeed(session: DeviceSession): Promise<FeedPayload>;
  search(query: string, session: DeviceSession): Promise<SearchPayload>;
  getLibrary(session: DeviceSession): Promise<LibraryPayload>;
}

export class ProviderCatalogProvider implements CatalogProvider {
  constructor(
    private readonly config: ProviderConfig,
    private readonly credentials: ProviderCredentialsService
  ) {}

  async getFeed(session: DeviceSession): Promise<FeedPayload> {
    if (this.credentials.isLocalDebugSession(session)) return localDebugFeed();

    const accessToken = await this.credentials.getAccessToken(session);
    if (this.config.artistUrl) {
      return this.getArtistHome(accessToken);
    }

    const json = await this.providerGet(this.config.feedPath, accessToken);
    return {
      generatedAtIso: new Date().toISOString(),
      items: extractItems(json).map((item) => normalizeMediaCard(item, 'track'))
    };
  }

  async search(query: string, session: DeviceSession): Promise<SearchPayload> {
    if (this.credentials.isLocalDebugSession(session)) return localDebugSearch(query);

    const accessToken = await this.credentials.getAccessToken(session);
    const params = new URLSearchParams();
    if (query.trim().length > 0) params.set('q', query.trim());
    params.set('limit', '20');

    const json = await this.providerGet(this.config.searchPath, accessToken, params);
    return {
      generatedAtIso: new Date().toISOString(),
      query: query.trim(),
      items: extractItems(json).map((item) => normalizeMediaCard(item, 'track'))
    };
  }

  async getLibrary(session: DeviceSession): Promise<LibraryPayload> {
    if (this.credentials.isLocalDebugSession(session)) return localDebugLibrary();

    const accessToken = await this.credentials.getAccessToken(session);
    const [tracks, playlists] = await Promise.all([
      this.providerGet(this.config.libraryTracksPath, accessToken),
      this.providerGet(this.config.libraryPlaylistsPath, accessToken)
    ]);

    return {
      generatedAtIso: new Date().toISOString(),
      sections: [
        {
          id: 'tracks',
          title: 'Saved Tracks',
          items: extractItems(tracks).map((item) => normalizeMediaCard(item, 'track'))
        },
        {
          id: 'playlists',
          title: 'Playlists',
          items: extractItems(playlists).map((item) => normalizeMediaCard(item, 'playlist'))
        }
      ]
    };
  }

  private async providerGet(
    path: string,
    accessToken: string,
    params?: URLSearchParams
  ): Promise<unknown> {
    const config = requireProviderApiConfig(this.config);
    const url = new URL(path, config.apiBaseUrl);
    params?.forEach((value, key) => url.searchParams.set(key, value));

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), config.requestTimeoutMs);

    try {
      const response = await fetch(url, {
        headers: {
          Accept: 'application/json',
          Authorization: `Bearer ${accessToken}`
        },
        signal: controller.signal
      });

      if (!response.ok) {
        throw providerUpstreamError();
      }

      return await response.json();
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') {
        throw providerUpstreamError('Provider API request timed out.');
      }

      if (error instanceof HttpApiError) {
        throw error;
      }

      throw providerUpstreamError();
    } finally {
      clearTimeout(timeout);
    }
  }

  private async getArtistHome(accessToken: string): Promise<FeedPayload> {
    const artist = await this.resolveArtist(accessToken);
    const artistId = readString(artist.id);
    if (!artistId) {
      throw providerUpstreamError('Provider artist page did not include a usable user id.');
    }

    const [tracksJson, playlistsJson] = await Promise.all([
      this.providerGet(`/users/${encodeURIComponent(artistId)}/tracks`, accessToken, limitParams(50)),
      this.providerGet(`/users/${encodeURIComponent(artistId)}/playlists`, accessToken, limitParams(50))
    ]);

    const trackItems = extractItems(tracksJson);
    const playlistItems = extractItems(playlistsJson);
    const tracks = trackItems.map((item) => normalizeMediaCard(item, 'track'));
    const albumItems = playlistItems.filter(isAlbumItem);
    const playlistOnlyItems = playlistItems.filter((item) => !isAlbumItem(item));
    const albums = albumItems.map((item) => normalizeMediaCard(item, 'playlist'));
    const playlists = playlistOnlyItems.map((item) => normalizeMediaCard(item, 'playlist'));
    const topTracks = trackItems
      .sort(compareByPopularity)
      .slice(0, 5)
      .map((item) => normalizeMediaCard(item, 'track'));

    const sections = [
      { id: 'top-tracks', title: 'Top 5', items: topTracks },
      { id: 'playlists', title: 'Playlists', items: playlists },
      { id: 'albums', title: 'Albums', items: albums },
      { id: 'tracks', title: 'Tracks', items: tracks }
    ].filter((section) => section.items.length > 0);

    return {
      generatedAtIso: new Date().toISOString(),
      items: sections[0]?.items ?? [],
      sections
    };
  }

  private async resolveArtist(accessToken: string): Promise<Record<string, unknown>> {
    const config = requireProviderApiConfig(this.config);
    const params = new URLSearchParams();
    params.set('url', this.config.artistUrl ?? '');
    const url = new URL(config.resolvePath, config.apiBaseUrl);
    params.forEach((value, key) => url.searchParams.set(key, value));

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), config.requestTimeoutMs);

    try {
      const response = await fetch(url, {
        headers: {
          Accept: 'application/json',
          Authorization: `Bearer ${accessToken}`
        },
        signal: controller.signal
      });

      if (!response.ok) {
        throw providerUpstreamError();
      }

      const json = await response.json();
      return isRecord(json) ? json : {};
    } finally {
      clearTimeout(timeout);
    }
  }
}

const limitParams = (limit: number): URLSearchParams => {
  const params = new URLSearchParams();
  params.set('limit', String(limit));
  return params;
};

const extractItems = (payload: unknown): Record<string, unknown>[] => {
  if (Array.isArray(payload)) return payload.filter(isRecord);
  if (!isRecord(payload)) return [];

  const candidates = [
    payload.collection,
    payload.items,
    payload.tracks,
    payload.playlists,
    payload.data,
    payload.results
  ];

  for (const candidate of candidates) {
    if (Array.isArray(candidate)) return candidate.filter(isRecord);
  }

  return [];
};

const normalizeMediaCard = (
  item: Record<string, unknown>,
  fallbackKind: MediaKind
): MediaCard => {
  const media = unwrapMediaItem(item);
  const user = isRecord(media.user) ? media.user : undefined;
  const kind = readKind(media.kind, fallbackKind);
  const title = readString(media.title) ?? readString(media.name) ?? 'Untitled';
  const description = readString(media.description) ?? readString(media.genre);

  return {
    id: readId(media),
    kind,
    title,
    subtitle: description,
    creatorName: readString(user?.username) ?? readString(user?.full_name) ?? readString(media.creatorName),
    artworkUrl: readString(media.artwork_url) ?? readString(media.artworkUrl) ?? readString(user?.avatar_url) ?? null,
    durationText: durationText(media.duration),
    webUrl: readString(media.permalink_url) ?? readString(media.webUrl) ?? null
  };
};

const unwrapMediaItem = (item: Record<string, unknown>): Record<string, unknown> => {
  const origin = item.origin;
  return isRecord(origin) ? origin : item;
};

const readKind = (value: unknown, fallback: MediaKind): MediaKind => {
  if (value === 'playlist') return 'playlist';
  if (value === 'station') return 'station';
  if (value === 'track') return 'track';
  return fallback;
};

const isAlbumItem = (item: Record<string, unknown>): boolean => {
  const media = unwrapMediaItem(item);
  const markers = [
    readString(media.set_type),
    readString(media.playlist_type),
    readString(media.type),
    readString(media.kind),
    readString(media.description),
    readString(media.title)
  ].filter(Boolean).join(' ');
  return /\balbum\b/i.test(markers);
};

const readId = (item: Record<string, unknown>): string => {
  const value = readString(item.id)
    ?? readString(item.urn)
    ?? readString(item.uri)
    ?? readString(item.permalink_url)
    ?? readString(item.title);
  return value ?? 'provider-item';
};

const compareByPopularity = (
  a: Record<string, unknown>,
  b: Record<string, unknown>
): number => {
  const mediaA = unwrapMediaItem(a);
  const mediaB = unwrapMediaItem(b);
  return readMetric(mediaB) - readMetric(mediaA);
};

const readMetric = (item: Record<string, unknown>): number => (
  readNumber(item.playback_count)
  ?? readNumber(item.favoritings_count)
  ?? readNumber(item.likes_count)
  ?? 0
);

const readNumber = (value: unknown): number | undefined => {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string') {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return undefined;
};

const readString = (value: unknown): string | undefined => {
  if (typeof value === 'string' && value.trim().length > 0) return value.trim();
  if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  return undefined;
};

const durationText = (value: unknown): string | null => {
  if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) return null;
  const totalSeconds = Math.round(value / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = String(totalSeconds % 60).padStart(2, '0');
  return `${minutes}:${seconds}`;
};

const isRecord = (value: unknown): value is Record<string, unknown> => (
  typeof value === 'object' && value !== null && !Array.isArray(value)
);

// These fixtures are returned from normal content endpoints only when the
// credentials service confirms both an authenticated local_debug token source
// and that local-debug credentials are enabled for this runtime. The explicit
// `/v1/debug/content/*` routes continue to reuse the same fixtures.
export const localDebugItems: MediaCard[] = [
  {
    id: 'local-debug-track',
    kind: 'track',
    title: 'Local Debug Track',
    subtitle: 'Development-only backend auth validation item',
    creatorName: 'Private Test Session',
    artworkUrl: null,
    durationText: '1:00',
    webUrl: 'https://soundcloud.com/forss/flickermood'
  },
  {
    id: 'local-debug-playlist',
    kind: 'playlist',
    title: 'Local Debug Playlist',
    subtitle: 'Development-only library validation item',
    creatorName: 'Private Test Session',
    artworkUrl: null,
    durationText: null,
    webUrl: 'https://soundcloud.com/forss/flickermood'
  }
];

export const localDebugFeed = (): FeedPayload => ({
  generatedAtIso: new Date().toISOString(),
  items: localDebugItems
});

export const localDebugSearch = (query: string): SearchPayload => {
  const normalizedQuery = query.trim();
  const searchableQuery = normalizedQuery.toLocaleLowerCase();
  const items = searchableQuery.length === 0
    ? localDebugItems
    : localDebugItems.filter((item) => [
        item.title,
        item.subtitle,
        item.creatorName,
        item.kind
      ].join(' ').toLocaleLowerCase().includes(searchableQuery));

  return {
    generatedAtIso: new Date().toISOString(),
    query: normalizedQuery,
    items
  };
};

export const localDebugLibrary = (): LibraryPayload => ({
  generatedAtIso: new Date().toISOString(),
  sections: [
    {
      id: 'debug-local',
      title: 'Local Debug Session',
      items: localDebugItems
    }
  ]
});
