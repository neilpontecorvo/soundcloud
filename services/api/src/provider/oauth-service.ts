import {
  providerExchangeFailed,
  providerRefreshFailed
} from '../errors/api-error.js';
import { ProviderConfig, requireProviderOAuthConfig } from './provider-config.js';

export interface ProviderTokenSet {
  accessToken: string;
  refreshToken?: string;
  tokenType: string;
  scope?: string;
  accessTokenExpiresAtIso?: string;
}

interface ProviderTokenResponse {
  access_token?: unknown;
  refresh_token?: unknown;
  token_type?: unknown;
  scope?: unknown;
  expires_in?: unknown;
}

export class ProviderOAuthService {
  constructor(private readonly config: ProviderConfig) {}

  createAuthorizationUrl(state: string): string {
    const config = requireProviderOAuthConfig(this.config);
    const url = new URL(config.authorizeUrl);
    url.searchParams.set('client_id', config.clientId);
    url.searchParams.set('redirect_uri', config.redirectUri);
    url.searchParams.set('response_type', 'code');
    url.searchParams.set('state', state);
    if (config.oauthScope) {
      url.searchParams.set('scope', config.oauthScope);
    }
    return url.toString();
  }

  async exchangeAuthorizationCode(authorizationCode: string): Promise<ProviderTokenSet> {
    const config = requireProviderOAuthConfig(this.config);
    const body = new URLSearchParams({
      grant_type: 'authorization_code',
      code: authorizationCode,
      redirect_uri: config.redirectUri,
      client_id: config.clientId,
      client_secret: config.clientSecret
    });

    return this.requestToken(body, 'exchange');
  }

  async refreshAccessToken(refreshToken: string): Promise<ProviderTokenSet> {
    const config = requireProviderOAuthConfig(this.config);
    const body = new URLSearchParams({
      grant_type: 'refresh_token',
      refresh_token: refreshToken,
      client_id: config.clientId,
      client_secret: config.clientSecret
    });

    return this.requestToken(body, 'refresh');
  }

  private async requestToken(
    body: URLSearchParams,
    operation: 'exchange' | 'refresh'
  ): Promise<ProviderTokenSet> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.config.requestTimeoutMs);

    try {
      const response = await fetch(this.config.tokenUrl, {
        method: 'POST',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/x-www-form-urlencoded'
        },
        body,
        signal: controller.signal
      });

      if (!response.ok) {
        throw operation === 'exchange' ? providerExchangeFailed() : providerRefreshFailed();
      }

      return normalizeTokenResponse(await response.json() as ProviderTokenResponse, operation);
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') {
        throw operation === 'exchange'
          ? providerExchangeFailed('Provider authorization code exchange timed out.')
          : providerRefreshFailed('Provider refresh timed out.');
      }

      if (operation === 'exchange') {
        throw providerExchangeFailed();
      }

      throw providerRefreshFailed();
    } finally {
      clearTimeout(timeout);
    }
  }
}

const normalizeTokenResponse = (
  response: ProviderTokenResponse,
  operation: 'exchange' | 'refresh'
): ProviderTokenSet => {
  if (typeof response.access_token !== 'string' || response.access_token.length === 0) {
    throw operation === 'exchange' ? providerExchangeFailed() : providerRefreshFailed();
  }

  return {
    accessToken: response.access_token,
    refreshToken: typeof response.refresh_token === 'string' && response.refresh_token.length > 0
      ? response.refresh_token
      : undefined,
    tokenType: typeof response.token_type === 'string' && response.token_type.length > 0
      ? response.token_type
      : 'Bearer',
    scope: typeof response.scope === 'string' ? response.scope : undefined,
    accessTokenExpiresAtIso: typeof response.expires_in === 'number'
      ? new Date(Date.now() + response.expires_in * 1000).toISOString()
      : undefined
  };
};
