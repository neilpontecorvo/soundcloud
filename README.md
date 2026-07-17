# Private SoundCloud Fire TV Hybrid Client

A private, sideloaded **Amazon Fire TV** client that combines:

- A native **Kotlin Android TV / Fire TV shell** for deterministic D-pad UX and focus control
- A backend **Node.js + TypeScript API** for session bootstrap, provider OAuth, token handling, and normalized content proxy routes
- A hardened **WebView-based controlled player host** for hybrid playback
- Backend-fed **Home / Search / Library** screens with selectable content cards

> This project is **not affiliated with, endorsed by, or provided by SoundCloud**. Do not use SoundCloud branding, names, or iconography in a way that implies official ownership.

## Current Status

**The 1920 × 1080 Fire TV redesign is implemented and validated on the physical Fire TV. The private-use app now has complete provider-backed browsing, collection detail, seeking, and public/private playback paths.**

What is currently verified working on physical Fire TV:

- Native 1920 × 1080 reference-frame shell with deterministic remote navigation
- Fixed five-destination header and persistent bottom mini-player
- Backend session bootstrap and polling
- File-backed session persistence on the API service so authenticated sessions survive a backend restart
- Automatic session restore on app launch using backend-authoritative re-validation of the persisted `sessionId`
- `LOGIN_REQUIRED` as the normal unauthenticated state
- Debug-only local auth completion exposed as an explicit fallback button on the `LOGIN_REQUIRED` screen
- Provider-backed backend proxy routes for:
  - `GET /v1/feed`
  - `GET /v1/search`
  - `GET /v1/library`
- Home rails for My Feed, More from ANELO, Spotlight, and Recently Played
- Library rails for the exact Spotlight selection, Tracks, Playlists, and Albums
- Complete paginated Library and Search results with independent horizontal rails
- Playlist and album detail using the same accessible, complete track-table behavior
- Player waveform focus, minute-scale D-pad scanning, internal description scrolling, and real queue context
- Server-side authenticated playback proxy for the account owner's private tracks; provider credentials never reach Android or the media CDN
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
- Screen-wake protection while the app is in use
- Fire TV launcher icon/banner mapped to the packaged 1280 × 720 artwork and launcher label `SOUNDCLOUD`

## Current Product Reality

The requested private-use success paths are complete on the installed APK: real provider authentication and session restoration, full Home/Library/Search navigation, playlist and album track selection, public playback, full-length private-track playback, waveform seeking, and global transport control.

Remaining work is non-blocking maintenance: replace deprecated fullscreen APIs when raising the platform baseline, prepare Appstore-only listing assets only if distribution is ever desired, and keep provider/API compatibility under routine validation.

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

For the real provider-auth device pass, export the provider OAuth environment,
start the backend on the LAN URL, then run the preflight before rebuilding or
installing:

```bash
cd ~/soundcloud
export PROVIDER_CLIENT_ID=<real>
export PROVIDER_CLIENT_SECRET=<real>
export PROVIDER_AUTH_PUBLIC_BASE_URL=http://192.168.1.167:4000
export PROVIDER_REDIRECT_URI=http://192.168.1.167:4000/v1/auth/callback

ENABLE_DEBUG_AUTH=false HOST=0.0.0.0 PORT=4000 npm --workspace @soundcloud-private/api start
```

In another shell with the same provider env:

```bash
cd ~/soundcloud
export PROVIDER_CLIENT_ID=<real>
export PROVIDER_CLIENT_SECRET=<real>
export PROVIDER_AUTH_PUBLIC_BASE_URL=http://192.168.1.167:4000
export PROVIDER_REDIRECT_URI=http://192.168.1.167:4000/v1/auth/callback
npm run preflight:firetv-provider-auth
```

The preflight checks that the backend LAN URL belongs to this Mac, `/health`
responds on that URL, provider OAuth env is present and URL-aligned, and
`192.168.1.168:5555` is reachable through ADB. Do not rebuild/install until it
passes.

### 2) Build the Fire TV Client

```bash
cd ~/soundcloud/apps/firetv-client
export ANDROID_HOME="$HOME/Library/Android/sdk"
export JAVA_HOME="$([ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 17)"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
./gradlew :app:assembleDebug
```

For the provider-auth pass, keep the APK pointed at the same preflighted LAN
backend URL:

```bash
./gradlew :app:assembleDebug -PapiBaseUrl=http://192.168.1.167:4000
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
- normalized feed/search/library proxy responses with provider pagination
- normalized complete playlist/album detail and queue metadata
- authenticated private-track stream resolution and Range-aware media proxying
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
- **Next / Previous:** move through the active playlist/album queue
- **Fast Forward / Rewind:** seek by 10 seconds through the bounded playback-specific bridge
- **D-pad Left / Right on a focused waveform:** scan by one minute and accelerate while held
- **Menu:** settings / context hook

## Current Scope

### Verified complete
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
- 1920 × 1080 Home/Library/Search/Playlist/Player redesign
- complete playlist and album queues with selectable tracks
- full-length account-owned private-track playback
- waveform seek and synchronized mini-player progress

### Remaining scope
- non-blocking deprecated fullscreen API cleanup
- optional Appstore listing assets only if distribution is ever desired
- routine provider/API compatibility maintenance

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
