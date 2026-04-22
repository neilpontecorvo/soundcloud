# Private SoundCloud Fire TV Hybrid Client

A private, sideloaded **Amazon Fire TV** client that combines:

- A native **Kotlin Android TV / Fire TV shell** for deterministic D-pad UX and focus control
- A backend **Node.js + TypeScript API** for session bootstrap, provider OAuth, token handling, and normalized content proxy routes
- A hardened **WebView-based controlled player host** for hybrid playback
- Backend-fed **Home / Search / Library** screens with selectable content cards

> This project is **not affiliated with, endorsed by, or provided by SoundCloud**. Do not use SoundCloud branding, names, or iconography in a way that implies official ownership.

## Current Status

**The app is now in a workable internal stage. Core runtime is proven. Remaining work is mostly UI/navigation polish and a small set of transport/cleanup tasks.**

What is currently verified working on physical Fire TV:

- Native Fire TV shell with deterministic remote navigation
- Backend session bootstrap and polling
- File-backed session persistence on the API service so authenticated sessions survive a backend restart
- Automatic session restore on app launch using backend-authoritative re-validation of the persisted `sessionId`
- `LOGIN_REQUIRED` as the normal unauthenticated state
- Debug-only local auth completion exposed as an explicit fallback button on the `LOGIN_REQUIRED` screen
- Provider-backed backend proxy routes for:
  - `GET /v1/feed`
  - `GET /v1/search`
  - `GET /v1/library`
- Home / Search / Library screens consuming backend-normalized content
- Hardened WebView boundary with:
  - controlled host strategy
  - allowlisted hosts
  - minimal JS bridge surface
  - production-safe WebView settings
  - locked top-level navigation to the controlled injected document
- Base64 `loadData(...)` controlled player host on physical Fire TV
- Widget bootstrap chain on device:
  - `pre-api-inline`
  - `widget-api-onload`
  - `post-api-inline`
  - `player ready`
- End-to-end playback from card selection to audible playback on Fire TV
- Global Play/Pause handling on the physical Fire TV remote
- Fire TV launcher visibility
- Fire TV custom banner visibility on the home screen tile
- Local backend access from Fire TV over LAN
- Two-region Player layout with top playback surface and bottom native queue list

## Current Product Reality

The app is no longer blocked on playback/runtime fundamentals.

Current remaining work is concentrated in these areas:

1. **TV-first UI polish**
   - convert the main menu/navigation into the intended vertical TV layout
   - tighten focus styling consistency
   - improve 4K spacing/density

2. **Navigation behavior fixes**
   - keep selection movement deterministic left/right across cards, playlists, and components
   - ensure the focus indicator moves between items rather than rails/cards visually shifting into a static selection state
   - continue validating playlist/component navigation paths

3. **Transport validation**
   - confirm `Next` / `Previous` behavior under real queue/list conditions
   - keep `Fast Forward` / `Rewind` unsupported unless a real seek/jump contract is intentionally added later

4. **Cleanup**
   - lint / manifest cleanup
   - deprecated API cleanup
   - low-risk resource/string cleanup
   - search UX polish

## Monorepo Layout

```text
soundcloud/
├─ docs/
├─ apps/
│  └─ firetv-client/
├─ services/
│  └─ api/
├─ packages/
│  ├─ contracts/
│  ├─ ui-tv/
│  └─ web-player/
└─ infra/
```

## Tech Stack

- **Client:** Kotlin, Android TV / Fire TV APIs, Gradle Kotlin DSL
- **Player Host:** Android `WebView`
- **Backend Service:** Node.js, TypeScript, Express
- **Shared Contracts:** TypeScript package for API DTOs/events

## Prerequisites

- Java 17
- Android SDK + platform tools
- Node.js 20+
- Amazon Fire TV device or Android TV / Fire TV test target

## Local Development

### 1) Start the Backend

Run from the repo root:

```bash
cd ~/soundcloud
ENABLE_DEBUG_AUTH=true HOST=0.0.0.0 PORT=4000 npm --workspace @soundcloud-private/api start
```

Health check:

```bash
curl http://localhost:4000/health
```

For physical Fire TV validation on the local network, the backend host has been tested at:

```text
http://192.168.1.167:4000
```

### 2) Build the Fire TV Client

```bash
cd ~/soundcloud/apps/firetv-client
export ANDROID_HOME="$HOME/Library/Android/sdk"
export JAVA_HOME="$([ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 17)"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
./gradlew :app:assembleDebug
```

### 3) Install to Fire TV

```bash
adb connect <FIRE_TV_IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch:

```bash
adb shell am start -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER -n com.neilpontecorvo.soundcloudfiretv/.app.MainActivity
```

### 4) Useful Device Logs

For app-only runtime logs:

```bash
adb logcat -c
adb logcat -v time -s MainActivity WebPlayerHostController HardenedWebViewClient PlayerBridge
```

## Backend Service Summary

The backend API is responsible for:

- device session bootstrap and polling
- provider OAuth exchange and refresh
- server-side token persistence for local development
- file-backed authenticated session persistence
- normalized feed/search/library proxy responses
- session validation and cache behavior

Debug-only local auth completion exists for Fire TV validation:

```bash
curl -X POST http://localhost:4000/v1/debug/authenticate-session \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"<session_id>"}'
```

This route is local-development only and is disabled in production.

## Remote Controls

- **D-pad:** deterministic focus movement
- **Center / Select:** activate focused element
- **Back:** app back / screen back
- **Play / Pause:** transport command routed to player module and validated on physical Fire TV
- **Next / Previous:** command path exists and remains under runtime validation
- **Fast Forward / Rewind:** intentionally unsupported no-op behavior until a real seek/jump contract exists
- **Menu:** settings / context hook

## Current Scope

### Verified complete enough
- Fire TV native shell
- backend session/auth/content pipeline
- backend-normalized content loading
- selectable card-based Home / Search / Library UI
- hardened WebView architecture
- diagnostics/settings screen
- local debug auth path for device testing
- end-to-end playback validation from selected card -> Player -> ready/playing state
- global Play/Pause handling on device
- launcher presence and banner visibility on Fire TV home screen
- session persistence + silent restore flow

### Remaining scope
- vertical TV navigation/menu refinement
- deterministic left/right item navigation cleanup
- broader validation for queue/playlist navigation paths
- validate `Next` / `Previous` more broadly
- optional `FF/REW` seek contract later
- runtime polish and cleanup

## Important Compliance Notes

- No downloading, ripping, stream capture, offline capture, or ad-stripping flows
- Provider secrets and provider tokens stay on the backend
- The Android client should only use backend session ids and normalized API responses
- Debug authentication is local-development only and must never be treated as production auth
- Do not imply official SoundCloud ownership or endorsement

## Reference Docs

- `docs/index.md`
- `services/api/README.md`
- `docs/architecture.md`
- `docs/roadmap.md`
- `WORKLOG.md`
