# API Service

Thin backend service scaffold for:

- Device session bootstrap and polling
- OAuth token exchange + refresh
- Backend-normalized feed/search/library proxy responses
- caching and session management

## Run

```bash
npm install
npm run dev
```

## Endpoints

- `GET /health`
- `POST /v1/device/bootstrap`
- `GET /v1/session/:sessionId`
- `POST /v1/auth/exchange`
- `POST /v1/auth/refresh`
- `GET /v1/feed`
- `GET /v1/search?q=<query>`
- `GET /v1/library`

Device bootstrap, session polling, and content proxy routes are scaffold-backed for client integration. OAuth exchange and refresh endpoints are wired but return provider-not-configured errors until server-side provider credentials and token storage are implemented.

Content proxy routes require a valid backend session via the `X-Session-Id` header. Provider-backed feed/search/library adapters are intentionally not implemented yet.
