# Roadmap

## Phase 1

- [x] Monorepo scaffold
- [x] Fire TV starter app shell
- [x] D-pad/focus abstraction
- [x] WebView player host screen
- [x] Settings + diagnostics shell
- [x] Backend placeholder service

## Phase 2 (Current)

- [x] Android client session bootstrap and polling through backend API
- [x] Settings/diagnostics surface for backend auth session state
- [x] Production-shaped exchange/refresh route wiring without provider credentials on device
- [x] API proxy routes for feed/search/library access
- [x] Native Home/Search/Library shells consume backend-normalized data
- [x] OAuth exchange via backend service (client secret never on device)
- [x] Server-side refresh token persistence and rotation handling
- [x] Provider-backed feed/search/library adapters behind backend proxy routes
- [ ] Hardened WebView bridge and CSP strategy

## Phase 3

- [ ] Native card rail polish for provider-backed API-proxied content
- [ ] Playback state sync with native overlay controls
- [ ] telemetry and crash diagnostics
- [ ] end-to-end integration tests on Fire TV targets
