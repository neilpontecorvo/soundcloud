import { HttpApiError, providerUpstreamError } from '../errors/api-error.js';
import { ProviderCredentialsService } from '../provider/credentials-service.js';
import { ProviderConfig, requireProviderApiConfig } from '../provider/provider-config.js';
import { DeviceSession } from '../session/session-store.js';

export type MediaKind = 'track' | 'playlist' | 'station' | 'artist';

export interface MediaCard {
  id: string;
  kind: MediaKind;
  title: string;
  subtitle?: string;
  creatorName?: string;
  creatorAvatarUrl?: string | null;
  artworkUrl?: string | null;
  description?: string | null;
  waveformUrl?: string | null;
  durationMs?: number | null;
  durationText?: string | null;
  trackCount?: number | null;
  webUrl?: string | null;
  isPrivate?: boolean;
  creatorProfileUrl?: string | null;
}

export interface PlaylistDetailPayload {
  id: string;
  title: string;
  creatorName?: string;
  artworkUrl?: string | null;
  description?: string | null;
  durationMs?: number | null;
  durationText?: string | null;
  trackCount: number;
  webUrl?: string | null;
  tracks: MediaCard[];
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
  getPlaylistDetail(playlistId: string, session: DeviceSession): Promise<PlaylistDetailPayload>;
}

export class ProviderCatalogProvider implements CatalogProvider {
  constructor(
    private readonly config: ProviderConfig,
    private readonly credentials: ProviderCredentialsService
  ) {}

  async getFeed(session: DeviceSession): Promise<FeedPayload> {
    if (this.credentials.isLocalDebugSession(session)) return localDebugFeed();

    const accessToken = await this.credentials.getAccessToken(session);
    const feedPromise = this.providerGet(
      this.config.feedPath,
      accessToken,
      paginationParams(200)
    ).then(extractItems);
    const recentlyPlayedPromise = this.providerGet(
      this.config.recentlyPlayedPath,
      accessToken,
      paginationParams(200)
    ).then(extractItems);

    if (this.config.artistUrl) {
      const [feedItems, recentlyPlayedItems, artistHome] = await Promise.all([
        feedPromise,
        recentlyPlayedPromise,
        this.getArtistHome(accessToken)
      ]);
      const myFeed = uniqueProviderItems(feedItems)
        .map((item) => normalizeMediaCard(item, 'track'));
      const recentlyPlayed = uniqueProviderItems(recentlyPlayedItems)
        .map((item) => normalizeMediaCard(item, 'track'));
      const artistSections = artistHome.sections ?? [];
      const artistPlaylists = uniqueMediaCards([
        ...(artistSections.find((section) => section.id === 'playlists')?.items ?? []),
        ...(artistSections.find((section) => section.id === 'albums')?.items ?? [])
      ]);
      const artistTracks = artistSections.find((section) => section.id === 'tracks')?.items ?? [];
      const spotlight = artistSections
        .find((section) => section.id === 'top')
        ?.items.slice(0, 5) ?? [];
      const spotlightIds = new Set(spotlight.map((item) => item.id));
      const moreFromArtist = artistPlaylists.filter((item) => !spotlightIds.has(item.id));
      const ownMusic = moreFromArtist.length > 0 ? moreFromArtist : artistTracks;
      const sections = [
        { id: 'my-feed', title: 'My Feed', items: myFeed },
        { id: 'more-from-artist', title: 'More from ANELO [Unifi Music]', items: ownMusic },
        { id: 'artist-spotlight', title: 'ANELO Spotlight', items: spotlight },
        { id: 'recently-played', title: 'Recently Played', items: recentlyPlayed }
      ].filter((section) => section.items.length > 0);
      return {
        generatedAtIso: new Date().toISOString(),
        items: sections[0]?.items ?? [],
        sections
      };
    }

    const [feedItems, recentlyPlayedItems] = await Promise.all([
      feedPromise,
      recentlyPlayedPromise
    ]);
    const items = uniqueProviderItems(feedItems)
      .map((item) => normalizeMediaCard(item, 'track'));
    const recentlyPlayed = uniqueProviderItems(recentlyPlayedItems)
      .map((item) => normalizeMediaCard(item, 'track'));
    return {
      generatedAtIso: new Date().toISOString(),
      items,
      sections: [
        { id: 'my-feed', title: 'My Feed', items },
        { id: 'recently-played', title: 'Recently Played', items: recentlyPlayed }
      ].filter((section) => section.items.length > 0)
    };
  }

  async search(query: string, session: DeviceSession): Promise<SearchPayload> {
    if (this.credentials.isLocalDebugSession(session)) return localDebugSearch(query);

    const accessToken = await this.credentials.getAccessToken(session);
    const params = paginationParams(200);
    if (query.trim().length > 0) params.set('q', query.trim());

    const [tracks, playlists, artists] = await Promise.all([
      this.providerGetAllPages(this.config.searchPath, accessToken, params),
      this.providerGetAllPages(this.config.searchPlaylistsPath, accessToken, params),
      this.providerGetAllPages(this.config.searchUsersPath, accessToken, params)
    ]);
    return {
      generatedAtIso: new Date().toISOString(),
      query: query.trim(),
      items: [
        ...tracks.map((item) => normalizeMediaCard(item, 'track')),
        ...playlists.map((item) => normalizeMediaCard(item, 'playlist')),
        ...artists.map((item) => normalizeMediaCard(item, 'artist'))
      ]
    };
  }

  async getLibrary(session: DeviceSession): Promise<LibraryPayload> {
    if (this.credentials.isLocalDebugSession(session)) return localDebugLibrary();

    const accessToken = await this.credentials.getAccessToken(session);
    if (this.config.artistUrl) {
      const artistHome = await this.getArtistHome(accessToken);
      return {
        generatedAtIso: artistHome.generatedAtIso,
        sections: artistHome.sections ?? []
      };
    }

    const [tracks, authoredPlaylists, likedPlaylists] = await Promise.all([
      this.providerGetAllPages(this.config.libraryTracksPath, accessToken, paginationParams(200)),
      this.providerGetAllPages(this.config.libraryPlaylistsPath, accessToken, paginationParams(200)),
      this.providerGetAllPages(this.config.libraryLikedPlaylistsPath, accessToken, paginationParams(200))
    ]);
    const playlistItems = uniqueProviderItems([...authoredPlaylists, ...likedPlaylists]);
    const albumItems = playlistItems.filter(isAlbumItem);
    const nonAlbumItems = playlistItems.filter((item) => !isAlbumItem(item));

    return {
      generatedAtIso: new Date().toISOString(),
      sections: [
        {
          id: 'tracks',
          title: 'Saved Tracks',
          items: tracks.map((item) => normalizeMediaCard(item, 'track'))
        },
        {
          id: 'playlists',
          title: 'Playlists',
          items: nonAlbumItems.map((item) => normalizeMediaCard(item, 'playlist'))
        },
        {
          id: 'albums',
          title: 'Albums',
          items: albumItems.map((item) => normalizeMediaCard(item, 'playlist'))
        }
      ].filter((section) => section.items.length > 0)
    };
  }

  async getPlaylistDetail(playlistId: string, session: DeviceSession): Promise<PlaylistDetailPayload> {
    if (this.credentials.isLocalDebugSession(session)) return localDebugPlaylistDetail(playlistId);

    const accessToken = await this.credentials.getAccessToken(session);
    const safeId = encodeURIComponent(playlistId);
    const metadataParams = new URLSearchParams({ show_tracks: 'false' });
    const tracksParams = new URLSearchParams({
      linked_partitioning: 'true',
      limit: '200'
    });
    const [metadataJson, trackItems] = await Promise.all([
      this.providerGet(`/playlists/${safeId}`, accessToken, metadataParams),
      this.providerGetAllPages(`/playlists/${safeId}/tracks`, accessToken, tracksParams)
    ]);
    const metadata = isRecord(metadataJson) ? metadataJson : {};
    const user = isRecord(metadata.user) ? metadata.user : undefined;
    const tracks = trackItems.map((item) => normalizeMediaCard(item, 'track'));
    const durationMs = readNumber(metadata.duration)
      ?? tracks.reduce((total, track) => total + (track.durationMs ?? 0), 0);
    const artwork = readString(metadata.artwork_url)
      ?? readString(metadata.artworkUrl)
      ?? tracks.find((track) => track.artworkUrl)?.artworkUrl
      ?? readString(user?.avatar_url);

    return {
      id: readId(metadata) || playlistId,
      title: readString(metadata.title) ?? 'Playlist',
      creatorName: readString(user?.username) ?? readString(user?.full_name),
      artworkUrl: highResolutionArtworkUrl(artwork),
      description: readString(metadata.description) ?? null,
      durationMs,
      durationText: formatDuration(durationMs),
      trackCount: tracks.length,
      webUrl: readString(metadata.permalink_url) ?? null,
      tracks
    };
  }

  private async providerGet(
    path: string,
    accessToken: string,
    params?: URLSearchParams
  ): Promise<unknown> {
    const config = requireProviderApiConfig(this.config);
    const url = new URL(path, config.apiBaseUrl);
    if (url.origin !== new URL(config.apiBaseUrl).origin) {
      throw providerUpstreamError('Provider pagination URL was outside the configured API host.');
    }
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

  private async providerGetAllPages(
    path: string,
    accessToken: string,
    params?: URLSearchParams
  ): Promise<Record<string, unknown>[]> {
    const items: Record<string, unknown>[] = [];
    let nextPath: string | undefined = path;
    let nextParams = params;
    let pageCount = 0;

    while (nextPath && pageCount < 20) {
      const payload = await this.providerGet(nextPath, accessToken, nextParams);
      items.push(...extractItems(payload));
      nextPath = isRecord(payload) ? readString(payload.next_href) : undefined;
      nextParams = undefined;
      pageCount += 1;
    }

    return items;
  }

  private async getArtistHome(accessToken: string): Promise<FeedPayload> {
    const artist = await this.resolveArtist(accessToken);
    const artistId = readString(artist.id);
    if (!artistId) {
      throw providerUpstreamError('Provider artist page did not include a usable user id.');
    }

    const [trackItems, playlistItems] = await Promise.all([
      this.providerGetAllPages(
        `/users/${encodeURIComponent(artistId)}/tracks`,
        accessToken,
        paginationParams(200)
      ),
      this.providerGetAllPages(
        `/users/${encodeURIComponent(artistId)}/playlists`,
        accessToken,
        paginationParams(200)
      )
    ]);

    const tracks = trackItems.map((item) => normalizeMediaCard(item, 'track'));
    const albumItems = playlistItems.filter(isAlbumItem);
    const playlistOnlyItems = playlistItems.filter((item) => !isAlbumItem(item));
    const albums = albumItems.map((item) => normalizeMediaCard(item, 'playlist'));
    const playlists = playlistOnlyItems.map((item) => normalizeMediaCard(item, 'playlist'));
    const catalogItems = [...trackItems, ...playlistOnlyItems, ...albumItems];
    const itemsById = new Map(catalogItems.map((item) => [readId(unwrapMediaItem(item)), item]));
    const configuredSpotlightItems = this.config.artistSpotlightIds
      .map((id) => itemsById.get(id))
      .filter((item): item is Record<string, unknown> => item !== undefined);
    const popularityFallback = [...trackItems, ...playlistOnlyItems]
      .sort(compareByPopularity)
      .filter((item) => !configuredSpotlightItems.some((selected) => readId(selected) === readId(item)));
    const topItems = [...configuredSpotlightItems, ...popularityFallback]
      .slice(0, 5)
      .map((item) => normalizeMediaCard(item, 'track'));

    const sections = [
      { id: 'top', title: 'Spotlight', items: topItems },
      { id: 'tracks', title: 'Tracks', items: tracks },
      { id: 'playlists', title: 'Playlists', items: playlists },
      { id: 'albums', title: 'Albums', items: albums }
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

const paginationParams = (limit: number): URLSearchParams => {
  const params = new URLSearchParams();
  params.set('limit', String(limit));
  params.set('linked_partitioning', 'true');
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

const uniqueProviderItems = (items: Record<string, unknown>[]): Record<string, unknown>[] => {
  const seen = new Set<string>();
  return items.filter((item) => {
    const key = readId(unwrapMediaItem(item));
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
};

const uniqueMediaCards = (items: MediaCard[]): MediaCard[] => {
  const seen = new Set<string>();
  return items.filter((item) => {
    const key = `${item.kind}:${item.id}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
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
  const creatorAvatarUrl = highResolutionArtworkUrl(readString(user?.avatar_url));
  const artworkUrl = highResolutionArtworkUrl(
    readString(media.artwork_url) ?? readString(media.artworkUrl) ?? creatorAvatarUrl
  );
  const durationMs = readNumber(media.duration) ?? null;

  return {
    id: readId(media),
    kind,
    title,
    subtitle: description,
    creatorName: readString(user?.username) ?? readString(user?.full_name) ?? readString(media.creatorName),
    creatorAvatarUrl,
    artworkUrl,
    description: readString(media.description) ?? null,
    waveformUrl: readString(media.waveform_url) ?? readString(media.waveformUrl) ?? null,
    durationMs,
    durationText: formatDuration(durationMs),
    trackCount: readNumber(media.track_count) ?? null,
    webUrl: readString(media.permalink_url) ?? readString(media.webUrl) ?? null,
    isPrivate: readString(media.sharing)?.toLowerCase() === 'private',
    creatorProfileUrl: readString(user?.permalink_url) ?? null
  };
};

const unwrapMediaItem = (item: Record<string, unknown>): Record<string, unknown> => {
  const nested = [item.origin, item.track, item.playlist].find(isRecord);
  return nested ?? item;
};

const readKind = (value: unknown, fallback: MediaKind): MediaKind => {
  if (value === 'playlist') return 'playlist';
  if (value === 'station') return 'station';
  if (value === 'track') return 'track';
  if (value === 'user' || value === 'artist') return 'artist';
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

const formatDuration = (value: number | null | undefined): string | null => {
  if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) return null;
  const totalSeconds = Math.round(value / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = String(totalSeconds % 60).padStart(2, '0');
  return `${minutes}:${seconds}`;
};

const highResolutionArtworkUrl = (value: string | null | undefined): string | null => {
  if (!value) return null;
  if (!/^https:\/\/i\d+\.sndcdn\.com\//i.test(value)) return value;
  return value.replace(/-(?:large|t\d+x\d+)\.(jpg|jpeg|png)(?:\?.*)?$/i, '-t500x500.$1');
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
    description: 'Development-only backend auth validation item',
    artworkUrl: null,
    durationMs: 60_000,
    durationText: '1:00',
    webUrl: 'https://soundcloud.com/forss/flickermood'
  },
  {
    id: 'local-debug-playlist',
    kind: 'playlist',
    title: 'Local Debug Playlist',
    subtitle: 'Development-only library validation item',
    creatorName: 'Private Test Session',
    description: 'Development-only library validation item',
    artworkUrl: null,
    durationMs: 60_000,
    trackCount: 2,
    durationText: null,
    webUrl: 'https://soundcloud.com/forss/flickermood'
  }
];

export const localDebugFeed = (): FeedPayload => ({
  generatedAtIso: new Date().toISOString(),
  items: localDebugItems,
  sections: [
    { id: 'my-feed', title: 'My Feed', items: localDebugItems.slice(0, 1) },
    { id: 'more-from-artist', title: 'More from ANELO [Unifi Music]', items: localDebugItems.slice(1, 2) },
    { id: 'artist-spotlight', title: 'ANELO Spotlight', items: localDebugItems.slice(1, 2) },
    { id: 'recently-played', title: 'Recently Played', items: localDebugItems.slice(0, 1) }
  ]
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

export const localDebugPlaylistDetail = (playlistId: string): PlaylistDetailPayload => ({
  id: playlistId,
  title: 'Local Debug Playlist',
  creatorName: 'Private Test Session',
  description: 'Development-only playlist detail validation.',
  artworkUrl: null,
  durationMs: 120_000,
  durationText: '2:00',
  trackCount: 2,
  webUrl: 'https://soundcloud.com/forss/flickermood',
  tracks: [0, 1].map((index) => ({
    ...localDebugItems[0],
    id: `local-debug-track-${index + 1}`,
    title: `Local Debug Track ${index + 1}`
  }))
});
