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
- [ ] OAuth exchange via backend service (client secret never on device)
- [ ] Secure refresh token rotation and provider-backed token lifecycle
- [ ] API proxy endpoints for feed/search/library access
- [ ] Hardened WebView bridge and CSP strategy

## Phase 3

- [ ] Native card rails populated from API-proxied content
- [ ] Playback state sync with native overlay controls
- [ ] telemetry and crash diagnostics
- [ ] end-to-end integration tests on Fire TV targets
