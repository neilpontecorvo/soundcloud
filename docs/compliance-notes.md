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
- Optional backend placeholder only
