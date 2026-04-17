# Architecture Overview

## Goals

- Fire TV first: predictable D-pad navigation and clear focus states.
- Hybrid playback: native shell + embedded WebView host.
- Future-ready: backend API gateway for OAuth, refresh, caching, and session mgmt.

## High-Level Modules

### apps/firetv-client

- `MainActivity`: root host, remote event dispatch, screen routing.
- `core/navigation`: simple screen router + nav model.
- `core/input`: remote key mapping (D-pad/select/back/play-pause/menu).
- `webview`: SoundCloud WebView host + cookie/session controls.
- `feature/*`: home, search, library, player, settings, diagnostics surfaces.
- `auth`: backend API-backed auth gateway and session state holder.
- `content`: lightweight repository for backend-fed Home/Search/Library screen data.

### services/api

Node/TypeScript service placeholder for:

- OAuth token exchange + refresh (future).
- Feed/search/library proxy route scaffolds with normalized response DTOs.
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

## Future API-backed mode

- Client calls `services/api` for auth/session bootstrapping and backend-fed Home/Search/Library data.
- API service stores encrypted refresh/session context (implementation TBD).
- Client receives short-lived session token for proxied requests.
