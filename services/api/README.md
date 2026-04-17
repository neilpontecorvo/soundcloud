# API Service

Thin backend service for:

- Device session bootstrap and polling
- Server-side provider OAuth token exchange + refresh
- Server-side provider token persistence for local development
- Backend-normalized feed/search/library proxy responses
- caching and session management

## Run

```bash
npm install
npm run dev
```

## Provider Configuration

Provider credentials stay on the API service. The Fire TV client only receives
backend session state and normalized content responses.

Required for provider OAuth exchange and authenticated content proxy routes:

```bash
PROVIDER_CLIENT_ID=...
PROVIDER_CLIENT_SECRET=...
PROVIDER_REDIRECT_URI=...
```

Optional provider settings:

```bash
PROVIDER_TOKEN_URL=https://secure.soundcloud.com/oauth/token
PROVIDER_API_BASE_URL=https://api.soundcloud.com
PROVIDER_FEED_PATH=/me/activities
PROVIDER_SEARCH_PATH=/tracks
PROVIDER_LIBRARY_TRACKS_PATH=/me/likes/tracks
PROVIDER_LIBRARY_PLAYLISTS_PATH=/me/playlists
PROVIDER_TOKEN_STORE_PATH=.local/provider-token-store.json
PROVIDER_REQUEST_TIMEOUT_MS=8000
```

The default local token store is a JSON file under `.local/`, which is ignored
by git and written with owner-only file permissions. Production deployments
should replace this with a managed encrypted persistence layer.

## Endpoints

- `GET /health`
- `POST /v1/device/bootstrap`
- `GET /v1/session/:sessionId`
- `POST /v1/auth/exchange`
- `POST /v1/auth/refresh`
- `GET /v1/feed`
- `GET /v1/search?q=<query>`
- `GET /v1/library`

Auth exchange and refresh require provider configuration and never expose provider
credentials or tokens to the Android client.

Content proxy routes require an authenticated backend session via the
`X-Session-Id` header. Feed, search, and library responses are fetched through
provider-backed adapters and normalized before returning to the client.
