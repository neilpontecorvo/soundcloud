# Architecture Overview

## Goals

- Fire TV first: predictable D-pad navigation and clear focus states.
- Hybrid playback: native shell + embedded WebView host.
- Future-ready: backend API gateway for OAuth, refresh, caching, and session mgmt.
- Security-conscious: hardened WebView with controlled host strategy.

## High-Level Modules

### apps/firetv-client

- `MainActivity`: root host, remote event dispatch, screen routing.
- `core/navigation`: simple screen router + nav model.
- `core/input`: remote key mapping (D-pad/select/back/play-pause/menu).
- `webview`: hardened WebView player host with controlled host boundary.
- `feature/*`: home, search, library, player, settings, diagnostics surfaces.
- `auth`: backend API-backed auth gateway and session state holder.
- `content`: lightweight repository for backend-fed Home/Search/Library screen data.

### services/api

Node/TypeScript service for:

- Device session bootstrap and polling.
- Server-side provider OAuth exchange and refresh handling.
- Local development token persistence with refresh rotation semantics.
- Feed/search/library provider proxy routes with normalized response DTOs.
- user session and cache adapters.

### packages/contracts

Shared TypeScript interfaces for request/response DTOs and event payloads.

### packages/ui-tv

Shared UI design tokens and focus style specs for TV surfaces.

### packages/web-player

Shared web-player integration contracts and constants.

## Fire TV Interaction Flow

1. `MainActivity` receives key events.
2. `RemoteInputHandler` maps key code => `RemoteAction`.
3. `FocusCoordinator` handles movement/action dispatch.
4. Current screen handles intent (navigate, play/pause, open menu, etc).
5. Player screen delegates to `WebPlayerHostController` for WebView actions.

## API-backed mode

- Client calls `services/api` for auth/session bootstrapping and backend-fed Home/Search/Library data.
- API service stores provider token context server-side only. The local development store is file-backed and should be replaced by managed encrypted persistence for production.
- Client sends only the backend session id for proxied requests.

## WebView Hardening

The WebView player host is secured with a controlled host strategy:

### Controlled Host Configuration

`WebViewHostConfig` defines the explicit boundary:
- **Entry URL**: The only URL loaded directly by `loadPlayer()`
- **Allowed Hosts**: Explicit allowlist of permitted navigation targets
- **Allowed Schemes**: HTTPS only in production

Default allowed hosts:
- `soundcloud.com`, `www.soundcloud.com`, `m.soundcloud.com` (primary)
- `sndcdn.com`, `a-v2.sndcdn.com`, `i1.sndcdn.com`, `widget.sndcdn.com` (CDN assets)

### Navigation Blocking

`HardenedWebViewClient` enforces the allowlist:
- Validates all navigation requests against configured hosts
- Blocks requests to unauthorized origins
- Logs blocked attempts for diagnostics (without exposing sensitive data)
- Enforces SSL certificate validation (no bypass)

### WebView Settings

Production-safe settings applied:
- JavaScript: enabled (required for player)
- DOM storage: enabled (required for player state)
- File access: **disabled**
- Content provider access: **disabled**
- Mixed content: **blocked**
- Geolocation: **disabled**
- Safe browsing: **enabled** (API 26+)
- WebView debugging: **debug builds only**

### JS Bridge Boundary

`PlayerBridge` provides a minimal native-to-web communication interface:
- Explicit command methods (play, pause, next, previous)
- Input validation and sanitization
- No generic eval-style surfaces
- Structured for future integration (not attached by default)

### Diagnostics

Settings/diagnostics screen displays WebView state:
- Current controlled host
- Current loaded URL (sanitized)
- Last blocked navigation and reason
- Last WebView error
- Hardening status
