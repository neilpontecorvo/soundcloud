import path from 'node:path';
import { providerNotConfigured } from '../errors/api-error.js';

export interface ProviderConfig {
  clientId?: string;
  clientSecret?: string;
  redirectUri?: string;
  tokenUrl: string;
  apiBaseUrl: string;
  feedPath: string;
  searchPath: string;
  libraryTracksPath: string;
  libraryPlaylistsPath: string;
  tokenStorePath: string;
  requestTimeoutMs: number;
}

export const readProviderConfig = (): ProviderConfig => ({
  clientId: readNonEmpty(process.env.PROVIDER_CLIENT_ID),
  clientSecret: readNonEmpty(process.env.PROVIDER_CLIENT_SECRET),
  redirectUri: readNonEmpty(process.env.PROVIDER_REDIRECT_URI),
  tokenUrl: process.env.PROVIDER_TOKEN_URL ?? 'https://secure.soundcloud.com/oauth/token',
  apiBaseUrl: process.env.PROVIDER_API_BASE_URL ?? 'https://api.soundcloud.com',
  feedPath: process.env.PROVIDER_FEED_PATH ?? '/me/activities',
  searchPath: process.env.PROVIDER_SEARCH_PATH ?? '/tracks',
  libraryTracksPath: process.env.PROVIDER_LIBRARY_TRACKS_PATH ?? '/me/likes/tracks',
  libraryPlaylistsPath: process.env.PROVIDER_LIBRARY_PLAYLISTS_PATH ?? '/me/playlists',
  tokenStorePath: process.env.PROVIDER_TOKEN_STORE_PATH ?? path.join(process.cwd(), '.local', 'provider-token-store.json'),
  requestTimeoutMs: Number(process.env.PROVIDER_REQUEST_TIMEOUT_MS ?? 8000)
});

export const requireProviderOAuthConfig = (config: ProviderConfig): Required<Pick<
  ProviderConfig,
  'clientId' | 'clientSecret' | 'redirectUri'
>> & ProviderConfig => {
  if (!config.clientId || !config.clientSecret || !config.redirectUri) {
    throw providerNotConfigured('Provider OAuth requires PROVIDER_CLIENT_ID, PROVIDER_CLIENT_SECRET, and PROVIDER_REDIRECT_URI.');
  }

  return config as Required<Pick<ProviderConfig, 'clientId' | 'clientSecret' | 'redirectUri'>> & ProviderConfig;
};

export const requireProviderApiConfig = (config: ProviderConfig): ProviderConfig => {
  requireProviderOAuthConfig(config);
  return config;
};

const readNonEmpty = (value: string | undefined): string | undefined => (
  value && value.trim().length > 0 ? value.trim() : undefined
);
