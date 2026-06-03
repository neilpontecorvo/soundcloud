import { randomUUID } from 'node:crypto';
import {
  HttpApiError,
  invalidSession,
  providerRefreshFailed
} from '../errors/api-error.js';
import {
  DeviceSession,
  updateDeviceSession
} from '../session/session-store.js';
import { ProviderOAuthService, ProviderTokenSet } from './oauth-service.js';
import { FileProviderTokenStore, StoredProviderSession } from './token-store.js';

const REFRESH_SKEW_MS = 60 * 1000;

export class ProviderCredentialsService {
  constructor(
    private readonly oauthService: ProviderOAuthService,
    private readonly tokenStore: FileProviderTokenStore,
    private readonly allowLocalDebugCredentials = false
  ) {}

  storeExchange(session: DeviceSession, tokenSet: ProviderTokenSet): DeviceSession {
    const nextSession = authenticatedSession(session, tokenSet);
    this.tokenStore.save({
      session: nextSession,
      tokens: storedTokens(tokenSet)
    });
    return updateDeviceSession(session.sessionId, nextSession) ?? nextSession;
  }

  restoreStoredSession(record: StoredProviderSession): DeviceSession {
    if (
      record.tokens.source === 'provider'
      && record.tokens.refreshToken
      && record.session.status === 'expired'
    ) {
      const expiresAtIso = new Date(Date.now() + RECOVERED_SESSION_TTL_MS).toISOString();
      const recoverableSession: DeviceSession = {
        ...record.session,
        status: 'authenticated',
        expiresAtIso
      };
      this.tokenStore.save({
        ...record,
        session: recoverableSession
      });
      return recoverableSession;
    }

    return record.session;
  }

  async refreshSession(session: DeviceSession): Promise<DeviceSession> {
    const record = this.tokenStore.get(session.sessionId);
    if (!record?.tokens.refreshToken) {
      throw invalidSession('No provider refresh token is available for this session.', {
        sessionId: session.sessionId,
        status: session.status
      });
    }

    if (record.tokens.source === 'local_debug') {
      if (!this.allowLocalDebugCredentials) {
        throw invalidSession('Local debug credentials are not enabled.', {
          sessionId: session.sessionId,
          status: session.status
        });
      }

      return this.storeLocalDebugSession(session);
    }

    try {
      const tokenSet = await this.oauthService.refreshAccessToken(record.tokens.refreshToken);
      const nextTokenSet = {
        ...tokenSet,
        refreshToken: tokenSet.refreshToken ?? record.tokens.refreshToken
      };
      return this.storeExchange(session, nextTokenSet);
    } catch (error) {
      if (error instanceof HttpApiError && error.error === 'provider_not_configured') {
        throw error;
      }

      const expired = updateDeviceSession(session.sessionId, {
        status: 'expired'
      });
      if (expired) {
        this.tokenStore.save({
          ...record,
          session: expired
        });
      }
      throw error instanceof Error ? error : providerRefreshFailed();
    }
  }

  async getAccessToken(session: DeviceSession): Promise<string> {
    if (session.status !== 'authenticated') {
      throw invalidSession('An authenticated backend session is required.', {
        sessionId: session.sessionId,
        status: session.status
      });
    }

    const record = this.tokenStore.get(session.sessionId);
    if (!record) {
      throw invalidSession('No provider credentials are stored for this session.', {
        sessionId: session.sessionId,
        status: session.status
      });
    }

    if (record.tokens.source === 'local_debug') {
      if (!this.allowLocalDebugCredentials) {
        throw invalidSession('Local debug credentials are not enabled.', {
          sessionId: session.sessionId,
          status: session.status
        });
      }

      return record.tokens.accessToken;
    }

    if (shouldRefresh(record)) {
      const refreshedSession = await this.refreshSession(session);
      const refreshedRecord = this.tokenStore.get(refreshedSession.sessionId);
      if (!refreshedRecord) {
        throw invalidSession('No provider credentials are stored for this session.', {
          sessionId: session.sessionId,
          status: refreshedSession.status
        });
      }
      return refreshedRecord.tokens.accessToken;
    }

    return record.tokens.accessToken;
  }

  storeLocalDebugSession(session: DeviceSession): DeviceSession {
    if (!this.allowLocalDebugCredentials) {
      throw invalidSession('Local debug credentials are not enabled.', {
        sessionId: session.sessionId,
        status: session.status
      });
    }

    const now = new Date();
    const expiresAtIso = new Date(now.getTime() + DEBUG_SESSION_TTL_MS).toISOString();
    const nextSession: DeviceSession = {
      ...session,
      status: 'authenticated',
      authenticatedAtIso: session.authenticatedAtIso ?? now.toISOString(),
      accessTokenExpiresAtIso: expiresAtIso,
      expiresAtIso
    };

    this.tokenStore.save({
      session: nextSession,
      tokens: {
        accessToken: `local-debug-access-${randomUUID()}`,
        refreshToken: `local-debug-refresh-${randomUUID()}`,
        tokenType: 'Bearer',
        scope: 'local_debug',
        accessTokenExpiresAtIso: expiresAtIso,
        updatedAtIso: now.toISOString(),
        source: 'local_debug'
      }
    });

    return updateDeviceSession(session.sessionId, nextSession) ?? nextSession;
  }

  isLocalDebugSession(session: DeviceSession): boolean {
    return this.allowLocalDebugCredentials
      && this.tokenStore.get(session.sessionId)?.tokens.source === 'local_debug';
  }
}

const authenticatedSession = (
  session: DeviceSession,
  tokenSet: ProviderTokenSet
): DeviceSession => ({
  ...session,
  status: 'authenticated',
  authenticatedAtIso: session.authenticatedAtIso ?? new Date().toISOString(),
  accessTokenExpiresAtIso: tokenSet.accessTokenExpiresAtIso,
  expiresAtIso: tokenSet.accessTokenExpiresAtIso ?? session.expiresAtIso
});

const storedTokens = (tokenSet: ProviderTokenSet) => ({
  accessToken: tokenSet.accessToken,
  refreshToken: tokenSet.refreshToken,
  tokenType: tokenSet.tokenType,
  scope: tokenSet.scope,
  accessTokenExpiresAtIso: tokenSet.accessTokenExpiresAtIso,
  updatedAtIso: new Date().toISOString(),
  source: 'provider' as const
});

const DEBUG_SESSION_TTL_MS = 60 * 60 * 1000;
const RECOVERED_SESSION_TTL_MS = 10 * 60 * 1000;
const shouldRefresh = (record: StoredProviderSession): boolean => {
  const expiresAtIso = record.tokens.accessTokenExpiresAtIso;
  if (!expiresAtIso) return false;
  return Date.parse(expiresAtIso) <= Date.now() + REFRESH_SKEW_MS;
};
