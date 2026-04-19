# Roadmap

## Project Status Summary

- Phase 1: Complete
- Phase 2: Functionally Complete
- Phase 3: Functionally Complete / Validation Proven
- Phase 4: Active

Current primary focus:
- polish, cleanup, and remaining transport validation (`Next` / `Previous`)

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

### Still Open But Non-Blocking
- [ ] Broader runtime validation for Next/Previous under real queue/list state
- [ ] Decide whether FF/REW should remain intentionally unsupported or gain a real seek/jump contract

Result:
- One working path is now proven:
  - Home → select track → Player → ready → playing

---

## Phase 4 — Polish (Current)

### UI / UX
- [ ] Refine card rail density for 4K displays
- [ ] Improve focus styling consistency
- [ ] Improve Search UX

### Player
- [ ] Native overlay controls polish
- [ ] Playback state sync refinement
- [ ] Better error messaging
- [ ] Confirm Next/Previous behavior on device with real queue/list conditions

### Code Quality
- [ ] Fix Android lint issues
- [ ] Remove deprecated APIs
- [ ] Clean unused resources and strings

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
- [ ] Optional seek/jump contract for FF/REW

---

## Key Rule Going Forward

Do NOT reopen solved runtime branches unless they regress.

Currently proven:

> **Working path:**
> Home → select track → Player → ready → playing

That gate is now complete. Remaining work is polish, cleanup, and optional control expansion.
