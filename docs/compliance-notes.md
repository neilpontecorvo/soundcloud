# Compliance Notes

## Positioning
This repository is for a private Amazon Fire TV client project for SoundCloud playback. It must not present itself as an official SoundCloud application.

## Rules
- Do not use official SoundCloud branding, names, or iconography in a way that implies endorsement.
- Do not implement downloading, stream ripping, offline capture, or ad stripping.
- Keep playback and authentication flows modular so provider integration can be swapped or limited later.
- Prefer embedded or web-hosted playback for MVP behavior over direct media extraction.
- Surface creator attribution wherever playback metadata is shown.

## Scope for MVP
- TV-first navigation
- Fire TV remote support
- WebView-hosted player shell
- Settings and diagnostics
- Backend API gateway for auth/session/content

## WebView Security Compliance

### Controlled Host Strategy
The WebView is restricted to a controlled host boundary:
- Entry URL is explicitly configured (not user-controllable)
- Navigation is limited to an explicit allowlist of approved hosts
- Any attempt to navigate outside the boundary is blocked and logged

### No Arbitrary URL Loading
- `loadPlayer()` only loads the configured entry URL
- Override URLs are rejected if they don't match the entry URL
- No URL bar, deep link handling, or user-provided URL input

### Hardened Settings
Production-safe WebView configuration:
- File access: disabled
- Content provider access: disabled
- Mixed content: blocked
- Geolocation: disabled
- WebView debugging: debug builds only
- Safe browsing: enabled (Android 8.0+)

### JS Bridge Boundary
- Minimal exposed surface area
- No generic eval or arbitrary command interfaces
- Input validation on all web-to-native calls
- Explicit command methods only

### Token Security
- Provider tokens (access, refresh) remain server-side only
- No sensitive credentials in WebView JavaScript context
- Client sends only backend session ID for API requests

### Local Debug Auth
- Debug session authentication exists only for local development and Fire TV validation.
- The debug route is disabled in production configuration.
- Debug credentials are server-side local markers only; they are not provider tokens and are never sent to Android.
