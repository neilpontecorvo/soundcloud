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

For the local Fire TV provider-auth flow, use the repo-root launcher instead of
manually exporting every variable each time:

```bash
cd /Users/neilpontecorvo/soundcloud
npm run start:firetv-api
```

The launcher binds the API to `0.0.0.0:4000`, derives the LAN callback URL for
the Fire TV, reuses the local session/token stores, builds the API, and starts
the `@soundcloud-private/api` workspace. It reads optional overrides from
`.env.firetv.local`, which is ignored by git. Keep provider secrets there or in
your shell environment; do not commit them.

Use the tracked template to create the ignored local env file:

```bash
cp config/firetv-api.env.example .env.firetv.local
```

Then edit `.env.firetv.local` and replace `PROVIDER_CLIENT_ID` and
`PROVIDER_CLIENT_SECRET`. After browser authorization completes, provider
access/refresh tokens are written server-side to
`services/api/.local/provider-token-store.json`.

## Provider Configuration

Provider credentials stay on the API service. The Fire TV client only receives
backend session state and normalized content responses.

Required for provider OAuth exchange and authenticated content proxy routes:

```bash
PROVIDER_CLIENT_ID=...
PROVIDER_CLIENT_SECRET=...
PROVIDER_REDIRECT_URI=...
```

For Fire TV provider-auth pairing on the local LAN, also set
`PROVIDER_AUTH_PUBLIC_BASE_URL` to the Mac backend URL and keep
`PROVIDER_REDIRECT_URI` aligned with its callback path:

```bash
PROVIDER_AUTH_PUBLIC_BASE_URL=http://192.168.1.167:4000
PROVIDER_REDIRECT_URI=http://192.168.1.167:4000/v1/auth/callback
```

Before rebuilding or installing the APK for the on-device sign-in pass, run:

```bash
npm run preflight:firetv-provider-auth
```

That command checks the backend LAN URL, `/health`, provider OAuth environment,
Mac-to-Fire-TV route, `192.168.1.168:5555`, and `adb connect`.

Optional provider settings:

```bash
ENABLE_DEBUG_AUTH=true
PROVIDER_AUTHORIZE_URL=https://secure.soundcloud.com/authorize
PROVIDER_AUTH_PUBLIC_BASE_URL=http://192.168.1.167:4000
PROVIDER_OAUTH_SCOPE=...
PROVIDER_TOKEN_URL=https://secure.soundcloud.com/oauth/token
PROVIDER_API_BASE_URL=https://api.soundcloud.com
PROVIDER_FEED_PATH=/me/activities
PROVIDER_SEARCH_PATH=/tracks
PROVIDER_LIBRARY_TRACKS_PATH=/me/likes/tracks
PROVIDER_LIBRARY_PLAYLISTS_PATH=/me/playlists
PROVIDER_TOKEN_STORE_PATH=.local/provider-token-store.json
PROVIDER_REQUEST_TIMEOUT_MS=8000
```

`ENABLE_DEBUG_AUTH` is enabled by default outside `NODE_ENV=production` and can
be disabled locally with `ENABLE_DEBUG_AUTH=false`. It is always disabled when
`NODE_ENV=production`.

The default local token store is a JSON file under `.local/`, which is ignored
by git and written with owner-only file permissions. Production deployments
should replace this with a managed encrypted persistence layer.

## Endpoints

- `GET /health`
- `POST /v1/device/bootstrap`
- `GET /v1/session/:sessionId`
- `GET /v1/auth/pair`
- `GET /v1/auth/start?user_code=<code>`
- `GET /v1/auth/callback`
- `POST /v1/auth/exchange`
- `POST /v1/auth/refresh`
- `POST /v1/debug/authenticate-session` (local development only)
- `GET /v1/feed`
- `GET /v1/search?q=<query>`
- `GET /v1/library`

Auth exchange and refresh require provider configuration and never expose provider
credentials or tokens to the Android client.

Fire TV sign-in uses a server-side pairing flow. `POST /v1/device/bootstrap`
creates a short-lived user code and URL for the TV. The user opens that URL on
another device, the API redirects to the provider authorization page, and the
provider callback exchanges the authorization code server-side before marking
the original backend session authenticated. The Fire TV only polls backend
session state; provider tokens never leave the API service.

Content proxy routes require an authenticated backend session via the
`X-Session-Id` header. Feed, search, and library responses are fetched through
provider-backed adapters and normalized before returning to the client.

## Local Development Auth Completion

Debug builds can promote an existing backend session without provider OAuth:

```bash
curl -X POST http://localhost:4000/v1/debug/authenticate-session \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"<session_id>"}'
```

This route is for local Fire TV validation only. It seeds server-side local
debug credentials and returns the normal session response shape so guarded
content routes can be exercised without moving provider secrets or tokens to
the Android client. It is disabled in production.
