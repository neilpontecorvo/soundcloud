# API Service Placeholder

Thin backend service scaffold for future:

- Device session bootstrap and polling
- OAuth token exchange + refresh
- API proxying
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

Device bootstrap and session polling are scaffold-backed for client integration. OAuth exchange and refresh endpoints are wired but return provider-not-configured errors until server-side provider credentials and token storage are implemented.
