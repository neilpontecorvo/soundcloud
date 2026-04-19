# Architecture Overview

## 1. Architectural Goals

- **Fire TV first:** predictable D-pad navigation, strong focus visibility, and deterministic screen transitions
- **Hybrid playback:** native Fire TV shell with a controlled embedded WebView player surface
- **Backend-mediated provider access:** session bootstrap, OAuth/token handling, and normalized content proxy routes remain server-side
- **Security-conscious:** hardened WebView boundary, explicit allowlists, no provider secrets or tokens on device
- **Incremental runtime validation:** Home / Search / Library / Player should be verifiable independently on the physical Fire TV target

---

## 2. Current Architectural State

The architecture is now beyond the prior runtime-blocker stage.

What is already functioning end-to-end enough to be considered established:

- Native Fire TV shell with top navigation and focused remote movement
- Backend session bootstrap and polling through the API service
- Local debug session authentication for Fire TV validation
- Backend-proxied and normalized content for:
  - feed
  - search
  - library
- Card-based Home / Search / Library UI on device
- Controlled WebView player host architecture
- Player selection handoff from content cards into the Player screen
- End-to-end playback on physical Fire TV
- Global Play/Pause handling on physical Fire TV
- Fire TV launcher/home tile visibility with custom banner

**Current architectural posture**

The core runtime architecture is now proven. The remaining work is not a foundational architecture question. It is mostly:

- validation of remaining transport behaviors (`Next` / `Previous`)
- optional `FF/REW` design expansion
- lint/cleanup/polish

So the architecture is now in a stable state rather than a blocked state.

---

## 3. High-Level Modules

## apps/firetv-client

Primary Android / Fire TV app module.

Important responsibilities:

- Root activity and screen routing
- D-pad / remote input mapping
- Focus coordination
- Session and auth state display
- Backend-fed content UI
- Controlled Player screen lifecycle
- Settings / diagnostics actions
- Launcher-facing app presentation (icon/banner wiring)

Key logical areas:

- `MainActivity`
  - root host
  - top nav
  - screen routing
  - selected content handoff to Player
  - player state rendering
  - activity-level media key interception path
- `core/navigation`
  - screen router
  - focus coordination
  - reusable screen rendering helpers
- `core/input`
  - remote key handling
  - D-pad / select / back / play-pause / menu mapping
- `auth`
  - backend API-backed session/auth gateway
  - session bootstrap / poll / debug auth interactions
  - `SessionPersistence` (SharedPreferences) for the last known `sessionId`
  - `restoreOrBootstrap()` entry point used on launch to silently re-validate
    a persisted session before falling through to a fresh bootstrap
- `content`
  - backend-fed repository for Home / Search / Library data
- `feature/home`
- `feature/search`
- `feature/library`
- `feature/player`
- `feature/settings`
- `feature/diagnostics`
- `webview`
  - controlled host config
  - hardened WebView client
  - player bridge
  - player host controller

## services/api

Node.js / TypeScript API service responsible for:

- device session bootstrap and polling
- server-side provider OAuth exchange and refresh
- local-development token persistence
- file-backed persistence of authenticated device sessions
  (`services/api/data/sessions.json` by default, configurable via
  `SESSION_STORE_PATH`) so a backend restart does not invalidate paired
  devices; only `authenticated` sessions are flushed to disk, and writes are
  atomic via tmp-file + rename
- feed/search/library proxy routes
- session validation and auth guards
- debug-only local auth completion path for Fire TV testing

## packages/contracts

Shared TypeScript DTO/event definitions for:

- session/auth payloads
- content proxy responses
- player and integration contracts where applicable

## packages/ui-tv

Shared UI tokens / patterns for Fire TV surfaces.

## packages/web-player

Shared constants/contracts related to controlled web-player integration.

---

## 4. Runtime Flow

## 4.1 App Launch

1. `MainActivity` launches
2. `ApiBackedAuthGateway.restoreOrBootstrap()` runs before content UI is shown:
   - `SessionPersistence` (SharedPreferences) provides any previously stored
     `sessionId`
   - if present, the gateway re-validates against `GET /v1/session/:id`:
     - `authenticated` → state transitions to `AUTHENTICATED`, app routes
       straight to Home
     - any other status (`awaiting_auth` / `expired` / `error`) or a `401
       invalid_session` → persisted id is cleared and a fresh bootstrap is
       issued
     - non-401 network/parse errors → persisted id is kept, state surfaces
       `ERROR` so the UI does not hang in `BOOTSTRAPPING`
   - if no id is persisted, a fresh bootstrap is issued
3. Backend session state is the single source of truth for whether the device
   is authenticated; the device never trusts the persisted id on its own
4. `LOGIN_REQUIRED` is the normal unauthenticated state and drives a dedicated
   screen with a debug-auth fallback button; it is not an error state
5. Home / Search / Library can render without requiring Player startup
6. **Player WebView must not initialize globally on launch**
7. launcher metadata (icon/banner) should already be resolved by the APK/manifest path, not by runtime code

## 4.2 Navigation / Input

1. remote input enters through input handling layers
2. D-pad/select/back continue through focus/navigation logic
3. transport keys are intercepted at the activity level before depending on a focused child view
4. transport dispatch is still gated by player state/screen rules
5. focused control or card receives non-transport navigation events
6. selection either:
   - navigates to another screen, or
   - selects playable content for Player

## 4.3 Content Flow

1. Android client calls backend API using backend session ID only
2. API validates session
3. API uses provider-backed server-side adapters
4. API returns normalized content DTOs
5. Home / Search / Library render cards from normalized backend responses

## 4.4 Player Flow (verified)

1. user selects a playable card
2. selected item is stored as the current playable target
3. app navigates to Player
4. `WebPlayerHostController` resolves a controlled embeddable/widget target
5. controlled host entry loads inside hardened WebView
6. widget/player runtime initializes
7. `PlayerBridge` reports:
   - loading
   - ready
   - play/pause state
   - metadata
   - errors
8. native player shell reflects those states
9. playback begins on physical Fire TV

## 4.5 Launcher Flow (verified)

1. application package exposes a `MAIN` + `LEANBACK_LAUNCHER` entry activity
2. launcher-facing metadata includes both icon and banner
3. packaged resources resolve to valid drawable assets
4. Fire TV surfaces the app tile on the home screen
5. Fire TV displays the custom banner on the home screen tile

---

## 5. API-Backed Mode

The client is intentionally thin with respect to provider integration.

Rules:

- Android client never stores provider secrets
- Android client never receives refresh tokens
- Android client talks to backend using backend session ID
- All provider exchange/refresh/content access happens server-side

Server-side responsibilities:

- bootstrap and poll session state
- handle provider token exchange/refresh
- persist local-development token state
- proxy and normalize feed/search/library content
- expose debug-only local auth route in non-production

This remains the correct architecture and should not be moved onto the device.

---

## 6. WebView Hardening Model

The WebView player host is constrained by a controlled boundary.

## 6.1 Controlled Host Strategy

`WebViewHostConfig` defines:

- **Entry URL**: the only direct load target for the controlled player host
- **Allowed Hosts**: explicit allowlist of permitted navigation/runtime domains
- **Allowed Schemes**: restricted scheme policy
- **Resolved widget/embed URL**: derived from selected content and used inside the controlled host flow

## 6.2 Navigation Blocking

`HardenedWebViewClient` enforces:

- explicit host validation
- blocking of unauthorized navigations
- diagnostics logging for blocked attempts
- no arbitrary browsing behavior
- SSL validation with no bypass behavior

## 6.3 WebView Settings

Production-safe settings include:

- JavaScript enabled only because player runtime requires it
- DOM storage enabled for player state/runtime needs
- File access disabled
- Content provider access disabled
- Mixed content blocked
- Geolocation disabled
- Safe browsing enabled where supported
- WebView debugging enabled only in debug builds

## 6.4 JS Bridge Boundary

`PlayerBridge` should remain minimal.

Allowed responsibilities:
- explicit playback-related events
- explicit command methods only where needed
- structured metadata / loading / error callback reporting

Disallowed responsibilities:
- generic eval surfaces
- arbitrary command execution
- provider token access
- broad page control unrelated to player behavior

---

## 7. Diagnostics Architecture

Diagnostics / Settings are allowed to surface technical state that is intentionally hidden from the main browsing UI.

Useful diagnostics include:

- backend URL
- session ID
- session/auth status
- controlled host URL
- last blocked navigation
- last WebView error
- selected content ID/title/webUrl
- resolved widget/embed URL
- first bridge event received
- transport command traces
- timeout/failure reason when applicable

Important distinction:

- Home / Search / Library should feel like media surfaces
- Settings / Diagnostics can remain technical

---

## 8. Current Architectural Risk Areas

## 8.1 Remaining transport scope
Most important open runtime-validation area.

Observed/known:
- Play/Pause is now proven on device
- command path exists for Next/Previous
- FF/REW are still intentionally unsupported

Risk categories:
- queue-dependent behavior for Next/Previous may need additional validation
- future seek/jump support would require a real bridge contract rather than synthetic no-op handling

## 8.2 Stale installed APK risk
Device testing can still be misleading if the installed APK does not match local repo state.

## 8.3 VPN interference
A VPN on the Fire TV can interfere with:
- backend LAN access
- local debug session behavior
- provider/widget asset loading

---

## 9. What Should Not Change Right Now

Avoid broad changes to:

- backend architecture
- auth/session architecture
- Home / Search / Library shell structure
- overall navigation shell
- compliance rules
- provider-secret placement
- hardened WebView boundary

The shortest path forward is incremental polish on a proven architecture, not architectural rewrite.

---

## 10. Recommended Immediate Focus

Single current architectural objective:

> Keep the proven path stable and finish the remaining polish/validation tasks.

Practical near-term focus:

- validate `Next` / `Previous` under real queue/list conditions
- decide future `FF/REW` behavior
- complete lint/deprecation/resource cleanup
- refine UI/player polish without disturbing the solved playback/runtime path

The following path is already proven and should be treated as the architecture gate that is now complete:

> `select card -> Player -> controlled host -> bridge ready -> playable state`
