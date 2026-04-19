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
    // Local-debug sessions are not real provider-authenticated users, so the
    // default signed-in UI must not silently render debug rails as if they
    // were real personalized content. The dev-only `GET /v1/debug/content/feed`
    // route still returns the debug items for explicit playback regression
    // testing.
    if (this.credentials.isLocalDebugSession(session)) return emptyFeed();

    const accessToken = await this.credentials.getAccessToken(session);
    const json = await this.providerGet(this.config.feedPath, accessToken);
    return {
      generatedAtIso: new Date().toISOString(),
      items: extractItems(json).map((item) => normalizeMediaCard(item, 'track'))
    };
  }

  async search(query: string, session: DeviceSession): Promise<SearchPayload> {
    if (this.credentials.isLocalDebugSession(session)) return emptySearch(query);

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
    if (this.credentials.isLocalDebugSession(session)) return emptyLibrary();

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
}

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

const readId = (item: Record<string, unknown>): string => {
  const value = readString(item.id)
    ?? readString(item.urn)
    ?? readString(item.uri)
    ?? readString(item.permalink_url)
    ?? readString(item.title);
  return value ?? 'provider-item';
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

const emptyFeed = (): FeedPayload => ({
  generatedAtIso: new Date().toISOString(),
  items: []
});

const emptySearch = (query: string): SearchPayload => ({
  generatedAtIso: new Date().toISOString(),
  query: query.trim(),
  items: []
});

const emptyLibrary = (): LibraryPayload => ({
  generatedAtIso: new Date().toISOString(),
  sections: []
});

// Debug rails are retained for explicit dev-only fallback use (e.g. the
// `/v1/debug/content/*` routes), but are no longer returned from the normal
// content endpoints. See also: Entry 015 in WORKLOG.md.
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
