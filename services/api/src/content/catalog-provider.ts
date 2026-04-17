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

const allItems: MediaCard[] = [
  {
    id: 'track-night-drive',
    kind: 'track',
    title: 'Night Drive Reference Mix',
    subtitle: 'Backend-normalized track card',
    creatorName: 'Independent Artist',
    durationText: '3:42',
    artworkUrl: null,
    webUrl: null
  },
  {
    id: 'track-low-light',
    kind: 'track',
    title: 'Low Light Session',
    subtitle: 'Recent listening scaffold',
    creatorName: 'Studio Project',
    durationText: '4:16',
    artworkUrl: null,
    webUrl: null
  },
  {
    id: 'playlist-tv-focus',
    kind: 'playlist',
    title: 'TV Focus Queue',
    subtitle: 'Playlist normalized by backend',
    creatorName: 'Private Library',
    durationText: '18 tracks',
    artworkUrl: null,
    webUrl: null
  },
  {
    id: 'station-evening',
    kind: 'station',
    title: 'Evening Discovery',
    subtitle: 'Station scaffold for provider adapter',
    creatorName: 'Recommendations',
    durationText: null,
    artworkUrl: null,
    webUrl: null
  }
];

export class ScaffoldCatalogProvider implements CatalogProvider {
  async getFeed(_session: DeviceSession): Promise<FeedPayload> {
    return {
      generatedAtIso: new Date().toISOString(),
      items: allItems
    };
  }

  async search(query: string, _session: DeviceSession): Promise<SearchPayload> {
    const normalizedQuery = query.trim();
    const searchableQuery = normalizedQuery.toLocaleLowerCase();
    const items = searchableQuery.length === 0
      ? allItems.slice(0, 3)
      : allItems.filter((item) => {
          const haystack = [
            item.title,
            item.subtitle,
            item.creatorName,
            item.kind
          ].join(' ').toLocaleLowerCase();
          return haystack.includes(searchableQuery);
        });

    return {
      generatedAtIso: new Date().toISOString(),
      query: normalizedQuery,
      items
    };
  }

  async getLibrary(_session: DeviceSession): Promise<LibraryPayload> {
    return {
      generatedAtIso: new Date().toISOString(),
      sections: [
        {
          id: 'recent',
          title: 'Recent',
          items: allItems.slice(0, 2)
        },
        {
          id: 'saved',
          title: 'Saved Playlists',
          items: allItems.filter((item) => item.kind === 'playlist')
        },
        {
          id: 'discovery',
          title: 'Discovery',
          items: allItems.filter((item) => item.kind === 'station')
        }
      ]
    };
  }
}
