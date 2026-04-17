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

export interface PlayerCommand {
  type: 'play_pause' | 'next' | 'previous';
}
