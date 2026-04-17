export type ApiErrorCode =
  | 'invalid_request'
  | 'invalid_session'
  | 'provider_not_configured'
  | 'provider_exchange_failed'
  | 'provider_refresh_failed'
  | 'provider_upstream_error'
  | 'debug_route_disabled'
  | 'internal_error';

export class HttpApiError extends Error {
  constructor(
    public readonly statusCode: number,
    public readonly error: ApiErrorCode,
    message: string,
    public readonly details: Record<string, unknown> = {}
  ) {
    super(message);
  }

  toBody(): Record<string, unknown> {
    return {
      error: this.error,
      message: this.message,
      ...this.details
    };
  }
}

export const invalidRequest = (message: string): HttpApiError => (
  new HttpApiError(400, 'invalid_request', message)
);

export const invalidSession = (
  message: string,
  details: Record<string, unknown> = {}
): HttpApiError => new HttpApiError(401, 'invalid_session', message, details);

export const providerNotConfigured = (message = 'Provider OAuth is not configured.'): HttpApiError => (
  new HttpApiError(501, 'provider_not_configured', message)
);

export const providerExchangeFailed = (message = 'Provider authorization code exchange failed.'): HttpApiError => (
  new HttpApiError(502, 'provider_exchange_failed', message)
);

export const providerRefreshFailed = (message = 'Provider refresh failed.'): HttpApiError => (
  new HttpApiError(502, 'provider_refresh_failed', message)
);

export const providerUpstreamError = (message = 'Provider API request failed.'): HttpApiError => (
  new HttpApiError(502, 'provider_upstream_error', message)
);

export const debugRouteDisabled = (message = 'Debug authentication is disabled.'): HttpApiError => (
  new HttpApiError(403, 'debug_route_disabled', message)
);
