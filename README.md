# Private SoundCloud Fire TV Hybrid Client

A private, sideloaded **Amazon Fire TV** client that combines:

- A native **Kotlin Android TV / Fire TV shell** for deterministic D-pad UX and playback controls.
- An embedded **WebView SoundCloud player host** for MVP playback.
- A modular monorepo layout for a future API-backed auth/session architecture.

> This project is **not affiliated with or endorsed by SoundCloud**. Do not use SoundCloud trademarks or branding in a way that implies official ownership.

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


- **Client:** Kotlin, Android TV/Fire TV APIs, Gradle Kotlin DSL
- **Player Host:** Android `WebView`
- **Backend Placeholder:** Node.js + TypeScript + Express
- **Shared Contracts:** TypeScript package for API DTOs/events

## Prerequisites

- Java 17 (recommended for Android Gradle Plugin 8.x)
- Android SDK + platform tools for Fire TV deployment
- Node.js 20+ for backend placeholder

## Quick Start

### 1) Fire TV Client

```bash
cd apps/firetv-client
./gradlew tasks
```

Build debug APK:

```bash
./gradlew :app:assembleDebug
```

### 2) Sideload to Fire TV

1. Enable **Developer Options** on Fire TV device.
2. Turn on **ADB Debugging** and **Apps from Unknown Sources**.
3. Connect device and install:

```bash
adb connect <FIRE_TV_IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Launch from Fire TV app list.

### 3) Backend Placeholder

```bash
cd services/api
npm install
npm run dev
```

Health endpoint:

```bash
curl http://localhost:4000/health
```

## Controls (Remote)

- **D-pad:** deterministic focus movement
- **Center/Select:** activate focused element
- **Back:** app back stack / screen back
- **Play/Pause:** transport command routed to player module
- **Menu:** open context/settings hook

## MVP Scope (Phase 1)

- Native app shell: Home, Search, Library, Player, Settings
- Focus manager abstraction + remote input handler
- WebView-based player host screen
- Settings + diagnostics screen (reload, clear cookies/session, app info)
- Backend placeholder for future auth/session proxy

## Important Compliance Notes

- No downloading, ripping, or offline capture flows.
- TODO markers are limited to real external integration needs (OAuth credentials, token exchange endpoints).
- Avoid fake branding or implying official SoundCloud ownership.

See:
- `docs/architecture.md`
- `docs/roadmap.md`
- `docs/compliance-notes.md`
