# Fire TV Client

Native Kotlin Android TV / Fire TV shell with remote-first UX and embedded WebView player host.

## Prerequisites

- Java 17
- Android SDK (API 34) + build tools
- ADB for sideloading

## Build

```bash
./gradlew :app:assembleDebug
```

The debug build points at `http://10.0.2.2:4000` by default. For a physical Fire TV device, pass a reachable backend URL:

```bash
./gradlew :app:assembleDebug -PapiBaseUrl=http://<LAN_HOST_IP>:4000
```

## Notes

- Optimized for 1080p TV layout.
- Deterministic D-pad navigation via `FocusCoordinator`.
- Remote commands mapped via `RemoteInputHandler`.
- Session bootstrap and polling are API-backed; provider OAuth remains backend-only TODO work.
