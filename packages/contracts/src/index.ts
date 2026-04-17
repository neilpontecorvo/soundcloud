export type SessionStatus = 'awaiting_auth' | 'authenticated' | 'expired' | 'error';

export interface SessionRecord {
  sessionId: string;
  status: SessionStatus;
  expiresAtIso: string;
  authenticatedAtIso?: string | null;
  accessTokenExpiresAtIso?: string | null;
}

export interface DeviceBootstrapRequest {
  deviceName?: string;
  appVersion?: string;
}

export interface SessionBootstrapResponse {
  sessionId: string;
  status: SessionStatus;
  verificationUri?: string | null;
  userCode?: string | null;
  expiresAtIso: string;
  pollIntervalSeconds: number;
}

export interface AuthExchangeRequest {
  sessionId: string;
  authorizationCode: string;
}

export interface AuthRefreshRequest {
  sessionId: string;
}

export interface ApiError {
  error: string;
  message?: string;
  sessionId?: string;
  status?: SessionStatus;
}

export type CacheStatus = 'hit' | 'miss' | 'bypass';
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

export interface FeedResponse {
  generatedAtIso: string;
  cacheStatus?: CacheStatus;
  items: MediaCard[];
}

export interface SearchRequest {
  query?: string;
  limit?: number;
}

export interface SearchResponse {
  generatedAtIso: string;
  cacheStatus?: CacheStatus;
  query: string;
  items: MediaCard[];
}

export interface LibrarySection {
  id: string;
  title: string;
  items: MediaCard[];
}

export interface LibraryResponse {
  generatedAtIso: string;
  cacheStatus?: CacheStatus;
  sections: LibrarySection[];
}

export interface PlayerCommand {
  type: 'play_pause' | 'next' | 'previous';
}
