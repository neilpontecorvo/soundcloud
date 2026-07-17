# Roadmap

## Project Status Summary

- Phase 1: Complete
- Phase 2: Functionally Complete
- Phase 3: Functionally Complete / Validation Proven
- Phase 4: Complete for the private-use target

Current primary focus:
- routine provider/platform compatibility maintenance

---

## Phase 1 — Foundation (Complete)

- [x] Monorepo scaffold
- [x] Fire TV starter app shell
- [x] D-pad / focus abstraction
- [x] Initial WebView player host screen
- [x] Settings + diagnostics shell
- [x] Backend placeholder service

Result:
- Base app structure and navigation system established

---

## Phase 2 — Backend + Integration (Complete)

### Session / Auth
- [x] Device session bootstrap
- [x] Session polling
- [x] Debug-only local authentication flow
- [x] Backend-only OAuth exchange (no client secrets on device)
- [x] Refresh token handling and persistence (server-side)

### Content
- [x] API proxy routes:
  - /v1/feed
  - /v1/search
  - /v1/library
- [x] Provider-backed content adapters
- [x] Normalized DTO responses
- [x] Android client consumption of backend content

### Client Integration
- [x] Home screen (card-based)
- [x] Library screen (card-based)
- [x] Search screen (input + results)
- [x] Content selection -> Player navigation

### WebView / Security
- [x] Controlled host strategy
- [x] HardenedWebViewClient allowlist enforcement
- [x] Production-safe WebView settings
- [x] Minimal JS bridge (PlayerBridge)
- [x] Diagnostics surface for WebView + session

Result:
- Full backend + client integration achieved
- Content loads on device and is selectable
- Player receives selected content

---

## Phase 3 — Runtime Validation (Proven)

### Primary Objective
> Achieve a fully working end-to-end playback path on physical Fire TV

### Verified State
- [x] Session authenticated on device
- [x] Backend reachable over LAN
- [x] Home / Search / Library usable
- [x] Cards selectable with proper focus
- [x] Selected content reaches Player
- [x] Controlled WebView host loads
- [x] PlayerBridge attaches to WebView
- [x] Player becomes `ready`
- [x] Playback starts on Fire TV
- [x] Audio playback works on physical device
- [x] Play/Pause works on device outside the old focus-scoped top-nav limitation
- [x] App appears on the Fire TV launcher/home surface
- [x] Banner appears on the Fire TV launcher/home tile

### Runtime Issues Resolved During Phase 3
- [x] WebView/widget/bridge readiness path on Fire TV
- [x] End-to-end playback invocation
- [x] Global Play/Pause interception path
- [x] Launcher/banner packaging and visibility
- [x] File-backed session persistence on the API so paired devices survive a
      backend restart (only authenticated sessions flushed; atomic write)
- [x] Automatic session restore on app launch via
      `ApiBackedAuthGateway.restoreOrBootstrap()` with backend-authoritative
      re-validation (Entry 013 / Entry 014)
- [x] `LOGIN_REQUIRED` as the normal unauthenticated state (not an error);
      debug auth reduced to an explicit fallback button

### Completed Transport Expansion
- [x] Queue-aware Next/Previous behavior
- [x] Bounded ±10-second media-key seek
- [x] Minute-scale focused-waveform scanning with held-key acceleration

Result:
- One working path is now proven:
  - Home → select track → Player → ready → playing

---

## Phase 4 — Polish (Current)

### UI / UX
- [x] Implement centralized 1920 × 1080 reference-frame layout
- [x] Implement fixed header and persistent mini-player
- [x] Implement complete Home/Library/Search rails and deterministic nested scrolling
- [x] Implement shared Playlist/Album Detail with complete selectable track queues
- [x] Validate high-contrast focus styling on physical Fire TV

### Player
- [x] Implement native waveform and internally scrollable description panels
- [x] Implement bounded seek and minute-scale held-D-pad scanning
- [x] Preserve global Play/Pause and queue-aware Next/Previous
- [x] Add full-length private-track playback through the authenticated backend proxy
- [x] Keep the display awake while the private app is in use

### Code Quality
- [x] Fix task-related Android lint issues
- [x] Add Android focus/grid/search/queue/artwork unit tests
- [x] Add API pagination, playlist normalization, and private-stream tests
- [ ] Remove deprecated APIs
- [ ] Clean low-risk unused resources and strings during routine maintenance

### Reliability
- [ ] Add telemetry hooks
- [ ] Add crash diagnostics
- [ ] Add end-to-end Fire TV regression checklist

---

## Phase 5 — Hardening (Optional / Future)

- [ ] Replace local token store with encrypted persistence
- [ ] Add environment-based config separation
- [ ] Improve CSP enforcement for controlled host
- [ ] Expand diagnostics for remote debugging
- [ ] Continue compatibility testing for the existing seek contract when provider behavior changes

---

## Key Rule Going Forward

Do NOT reopen solved runtime branches unless they regress.

Currently proven:

> **Working path:**
> Home/Library/Search → select track or collection → complete queue/detail → Player → seek/play

That gate is complete for public and account-owned private tracks. Remaining work is non-blocking maintenance and optional distribution preparation.
