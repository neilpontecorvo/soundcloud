# WORKLOG — SoundCloud Fire TV Hybrid Client

## 1. Project Identity
- Project: Private SoundCloud-style Fire TV hybrid client
- Repo local path: `~/soundcloud`
- GitHub repo: `https://github.com/neilpontecorvo/soundcloud`
- Source of truth: local repo, mirrored to GitHub
- Platforms:
  - Android / Fire TV client
  - Node/TypeScript backend API
- Goal:
  - Native TV shell
  - Backend session/auth/content proxy
  - Hardened controlled WebView player surface
  - Working card-to-player playback flow on physical Fire TV

---

## 2. Stable Rules
- Work only in this repository.
- Do not switch repos.
- Do not use or reference `soundcloud-tv`.
- This is a private app. Do not imply official SoundCloud ownership, endorsement, or affiliation.
- Do not add downloader, ripping, offline capture, or prohibited functionality.
- Keep provider secrets, access tokens, and refresh tokens server-side only.
- Preserve deterministic D-pad behavior and TV-first UX.
- Do not weaken hardened WebView / controlled host / allowlist rules.
- Prefer minimal, production-shaped changes over broad rewrites.
- Update this file after every completed task.

---

## 3. Current Environment
### Mac host
- Backend LAN IP: `192.168.1.167`
- Backend local URL: `http://192.168.1.167:4000`
- Android SDK path: `$HOME/Library/Android/sdk`
- Java target: JDK 17

### Fire TV
- Device IP: `192.168.1.168`
- Important: disable VPN during debugging and runtime validation unless split tunneling / LAN allow is confirmed.
- Fire TV model observed in logs: `AFTKM`
- Fire TV WebView package observed in logs: `com.amazon.webview.chromium`

### Android app config
- `compileSdk = 34`
- `targetSdk = 34`
- `minSdk = 24`
- Debug build currently allows cleartext only for local host `192.168.1.167`

### Backend auth/testing notes
- Local dev uses `ENABLE_DEBUG_AUTH=true`
- Provider auth is server-side only
- SoundCloud/provider credentials should never be stored in this file

### Agent/session mode
- Preferred coding agent per cycle: **one only**
- Approval mode: prefer auto-accept edits / low-friction approval for the active coding agent
- Every agent must update `WORKLOG.md` as it finishes each step
- Every agent should commit code + `WORKLOG.md` together

### Local commands
#### Backend
```bash
cd ~/soundcloud
ENABLE_DEBUG_AUTH=true HOST=0.0.0.0 PORT=4000 npm --workspace @soundcloud-private/api start
```

#### Android build
```bash
cd ~/soundcloud/apps/firetv-client
export ANDROID_HOME="$HOME/Library/Android/sdk"
export JAVA_HOME="$([ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 17)"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
./gradlew :app:assembleDebug
```

#### Install APK
```bash
adb connect 192.168.1.168:5555
adb install -r ~/soundcloud/apps/firetv-client/app/build/outputs/apk/debug/app-debug.apk
```

#### Launch app
```bash
adb shell am start -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER -n com.neilpontecorvo.soundcloudfiretv/.app.MainActivity
```

#### App-specific logs only
```bash
adb logcat -c
adb logcat -v time -s MainActivity PlayerBridge
```

---

## 4. Do Not Repeat Past Failed Loops
- Do not use multiple coding agents in parallel on the same step.
- Do not trust old installed APKs; always rebuild and reinstall before runtime conclusions.
- Do not debug from broad Fire TV/Alexa/Appstore/system log noise.
- Ignore logs that do not contain app tags:
  - `MainActivity`
  - `WebPlayerHostController`
  - `HardenedWebViewClient`
  - `PlayerBridge`
- Do not reopen playback-runtime debugging unless playback breaks again.
- Do not assume summaries equal proof; physical device verification decides state.
- Do not test with the Fire TV VPN enabled unless local LAN access is explicitly confirmed.
- Do not let Player loading state leak into Home/Search/Library.
- Do not broaden scope in a recovery cycle; fix one concrete blocker only.

---

## 5. Current Verified State
### Completed enough
- Monorepo scaffold exists.
- Backend session/auth/content pipeline exists.
- Fire TV app builds locally.
- Fire TV can reach backend over LAN.
- Debug cleartext config for local Fire TV testing exists.
- Home, Search, and Library render and are navigable.
- Nav/focus visibility is usable on the TV display.
- Hardened WebView / controlled host model exists.
- Session/auth is verified working on Fire TV.
- Selected track metadata reaches Player.
- Player no longer globally hijacks all screens with `Connecting...`.
- Physical Fire TV now proves full playback path success.
- Global Play/Pause behavior is proven on device.
- Launcher visibility is restored on the Fire TV home screen.
- Custom TV banner is now visible on the Fire TV home screen.

### Latest device checkpoint
- Settings confirms:
  - Backend: `http://192.168.1.167:4000`
  - Session status: `authenticated`
  - Authenticated: `true`
- Selecting `Local Debug Track` reaches Player with selected metadata visible.
- Controlled WebView player host loads and remains on the controlled `data:` document.
- Bootstrap stages fire:
  - `pre-api-inline`
  - `widget-api-onload`
  - `post-api-inline`
- SoundCloud widget binds successfully.
- Widget reports `player ready`.
- Debug play invocation fires.
- Widget reports current sound state:
  - `{"hasSound":true,"id":"293","title":"Flickermood","user":"Forss"}`
- Widget reports `widget_event_fired=play`.
- Audio playback is confirmed working on physical Fire TV.
- Play/Pause behavior is confirmed working after moving focus away from the top navigation area.
- App tile is visible on the Fire TV home screen.
- Custom banner is visible on the Fire TV home screen.

### Runtime interpretation
This confirms:
- backend is not the blocker
- session/auth is not the blocker
- card selection/handoff is not the blocker
- WebView lifecycle/recomposition churn is not the blocker
- CSP / malformed document / baseUrl / URL-encoding issues are not the blocker
- playback path is proven end-to-end on device
- prior focus-scoped Play/Pause behavior is resolved
- launcher visibility and banner packaging are resolved

### Current active issues
1. **Real provider OAuth/device-pairing requires physical-device validation**
   - Backend pairing endpoints and Fire TV LOGIN_REQUIRED primary sign-in UI are implemented.
   - Repo now has a one-command provider-auth preflight: `npm run preflight:firetv-provider-auth`.
   - API typecheck/build, Android build, and local pairing-route smoke checks pass.
   - Latest preflight reaches the Fire TV over LAN/TCP, but ADB is not authorized yet: `adb connect 192.168.1.168:5555` returns `failed to authenticate` and `adb devices` lists `192.168.1.168:5555	unauthorized`.
   - A real provider sign-in still needs provider credentials configured on the backend, backend `/health` running on the LAN URL, ADB authorization on the Fire TV, and an on-device pairing/callback validation pass.

2. **Fast Forward / Rewind unsupported by design**
   - FF/REW currently do nothing meaningful.
   - Current implementation intentionally treats them as unsupported no-ops because no reliable seek/jump bridge contract exists yet.
   - This is expected behavior, not a regression.

3. **Next / Previous should be kept under runtime validation**
   - Command path exists.
   - End-to-end behavior should still be confirmed across more than one playable item / queue state.

4. **Cleanup / polish remains**
   - Manifest/lint cleanup
   - deprecated API cleanup
   - UI polish
   - search UX polish
   - optional native overlay/player refinement

---

## 6. Completion Estimate
### Internal prototype
- Overall completion: **97%**

### Polished private-use app
- Overall completion: **86%**

### Module estimate
- Backend/session/auth/content: **92%**
- TV shell/nav/cards/focus: **92%**
- Player runtime on physical Fire TV: **96%**
- Media transport integration: **88%**
- Launcher/banner packaging: **95%**
- Final cleanup/polish: **55–65%**

### Estimated time remaining
- Stable working prototype if scope stays frozen: **0–2 focused hours**
- Additional polish and cleanup after that: **6–12 hours**

---

## 7. Completed Work
### Repo / workflow
- Local repo is canonical.
- GitHub mirror exists.
- Patch/export drift issues were previously resolved.

### Backend
- Session bootstrap/auth/content proxy implemented.
- Debug auth flow exists for local testing.
- API checks/build passed.

### Android / Fire TV
- Build passes with Gradle.
- LAN host changed from emulator alias to real Mac IP.
- Debug cleartext allowed for local testing host only.
- Home/Search/Library evolved from text panels to usable prototype UI.
- Hardened WebView boundary and controlled host approach added.
- Player lifecycle was reworked to avoid global loading.
- Player remains idle when opened without selection.
- Card selection routes selected metadata into Player.
- Physical Fire TV now proves real playback success.
- Two-region Player layout is in place for playback surface + native queue.
- Top-level privacy-page escape is blocked; controlled document remains active through READY and PLAY.
- Media transport command path was wired for:
  - Play
  - Pause
  - Play/Pause toggle
  - Next
  - Previous
- Play/Pause interception was moved out of the prior focus-scoped path and is now validated working on device.
- FF/REW logging path was added as unsupported no-op behavior.
- Launcher/banner packaging was fixed by removing the conflicting shape wrapper and wiring a real PNG banner.
- App icon metadata was restored so the launcher tile surfaces correctly.
- Fire TV home screen now shows the app tile and custom banner.

### Audit results
- `npm run check:api` passed.
- `npm --workspace @soundcloud-private/api run build` passed.
- `npm audit` passed with 0 vulnerabilities.
- `./gradlew :app:assembleDebug` passed.
- APK badging verified launcher activity, icon, and banner wiring.

---

## 8. Remaining Tasks
### Critical path
1. Validate Next/Previous behavior against real queue/list state on device.
2. Decide whether FF/REW should remain unsupported or gain a real seek/jump contract later.
3. Fix TV lint manifest issues:
   - `android.hardware.touchscreen` should be optional
   - clean debug manifest overlay TV lint behavior
4. Remove deprecated `saveFormData`.
5. Clean hardcoded strings / unused resources / low-risk warnings.

### Secondary tasks
- Improve Search results experience
- Native overlay/player polish
- Better error messaging
- Telemetry/crash diagnostics hooks
- Optional richer queue behavior/state sync refinement

---

## 9. Known Issues / Side Notes
- Fire TV VPN may interfere with local LAN/backend/widget access.
- Installed APK may lag behind local repo unless explicitly rebuilt/reinstalled.
- Many prior log captures were Fire TV/Alexa/system noise, not app traces.
- Only these log tags matter for focused debugging:
  - `MainActivity`
  - `PlayerBridge`
- Current local backend should be started from repo root, not home directory.
- Keep `AGENTS.md` and `.claude/` status in mind if they remain uncommitted.
- FF/REW are not regressions at this stage; they are intentionally unsupported.

---

## 10. Official / Useful Reference URLs
- Android TV app setup: `https://developer.android.com/training/tv/start/start`
- Android network security config: `https://developer.android.com/privacy-and-security/security-config`
- adb docs: `https://developer.android.com/tools/adb`
- Gradle command-line reference: `https://docs.gradle.org/current/userguide/command_line_interface.html`
- npm audit docs: `https://docs.npmjs.com/cli/v10/commands/npm-audit`
- SoundCloud API docs: `https://developers.soundcloud.com/docs/api/guide`
- SoundCloud widget API: `https://developers.soundcloud.com/docs/api/html5-widget`
- Claude Code memory/docs: `https://docs.anthropic.com/en/docs/claude-code/memory`
- OpenAI Codex usage guidance: `https://openai.com/business/guides-and-resources/how-openai-uses-codex/`

---

## 11. Current Next Step
**Single-task focus:**
Treat playback, Play/Pause transport, launcher visibility, banner, session restore, LOGIN_REQUIRED, and empty local_debug signed-in UI as solved. The next task is physical-device validation of the real provider OAuth pairing flow added in Entry 017:
- start the backend on LAN with real provider OAuth env vars, including `PROVIDER_AUTH_PUBLIC_BASE_URL=http://192.168.1.167:4000` and `PROVIDER_REDIRECT_URI=http://192.168.1.167:4000/v1/auth/callback`
- in a second shell with the same provider env, run `npm run preflight:firetv-provider-auth`
- only after the preflight passes, rebuild/reinstall the APK with `-PapiBaseUrl=http://192.168.1.167:4000`
- launch on Fire TV with VPN disabled / LAN confirmed
- use the LOGIN_REQUIRED primary provider sign-in path
- complete the browser provider callback
- confirm relaunch restores the real provider session and Home/Library/Search use provider-backed content

Do not reopen playback-runtime debugging unless playback itself stops working again.

---

## 12. Resume Protocol For Any Agent
When resuming work:

1. Read `AGENTS.md`
2. Read this `WORKLOG.md`
3. Work on only the task in **Current Next Step**
4. Do not broaden scope
5. Run the required validation
6. Update this file before stopping
7. Commit code + `WORKLOG.md` together

---

## 13. Session Handoff Template
### Last completed task
- Verified end-to-end playback, global Play/Pause handling, launcher visibility, and banner visibility on physical Fire TV

### Active agent
- [Codex / Claude Code / other]

### Approval mode
- [auto-accept / on-request / manual]

### Start SHA
- [fill in]

### End SHA
- [fill in]

### What changed
- [fill in]

### Files changed
- [fill in]

### Commands run
- [fill in]

### What passed
- [fill in]

### What failed
- [fill in]

### Remaining blocker
- No major blocker; only validation/polish tasks remain

### Exact next step
- Validate Next/Previous behavior on device and continue cleanup/polish tasks.

---

## 14. Running Work Log
### Entry 001
- Status: completed
- Summary: repo scaffold + backend/session/auth/content foundation established
- Result: backend and shell foundation exist

### Entry 002
- Status: completed
- Summary: LAN backend routing fixed for physical Fire TV
- Result: backend reachable at `192.168.1.167:4000`

### Entry 003
- Status: completed
- Summary: cleartext debug config added for local Fire TV development
- Result: local backend access no longer blocked by Android cleartext policy

### Entry 004
- Status: completed
- Summary: Home/Library moved from text panels toward cards/rails; focus visibility improved
- Result: shell is now usable as a prototype

### Entry 005
- Status: completed
- Summary: hardened WebView / controlled host strategy added
- Result: player boundary architecture exists

### Entry 006
- Status: completed
- Summary: Player loading no longer leaks globally into all screens
- Result: Player opens only around selected content / idle state path

### Entry 007
- Status: completed
- Summary: Selected content now reaches Player on physical Fire TV
- Result: Player shows selected track metadata and no longer fails at the old generic global connection stage

### Entry 008
- Status: completed
- Summary: Controlled WebView bootstrap/READY path proven on Fire TV
- Result: WebView/widget/bridge readiness path verified on device

### Entry 009
- Status: completed
- Summary: End-to-end playback verified on physical Fire TV
- Result: Player loads selected content, reaches READY, invokes play, reports current sound state, fires PLAY event, and audio playback is confirmed working on device.

### Entry 010
- Status: completed
- Summary: Media transport bridge command path added for core controls
- Result: Play, Pause, Play/Pause toggle, Next, and Previous now dispatch through the player bridge; FF/REW intentionally remain unsupported no-ops pending a real seek/jump contract.

### Entry 011
- Status: completed
- Summary: Global Play/Pause handling fixed and validated on physical Fire TV
- Result: prior focus-scoped transport behavior is resolved; Play/Pause works after focus moves away from the top navigation region.

### Entry 012
- Status: completed
- Summary: Launcher/banner packaging fixed and validated on physical Fire TV
- Result: app appears on the Fire TV home screen again and the custom banner is visible on the tile.

### Entry 013
- Status: completed (build, typecheck, and on-device validation all passed)
- Summary: Session persistence + explicit LOGIN_REQUIRED auth phase added across Android client and API service.
- Changes:
  - AppScreen.kt: added LOGIN_REQUIRED screen to the navigation enum.
  - SessionPersistence.kt (new): SharedPreferences-backed wrapper storing only the sessionId.
  - AuthGateway.kt: added restoreOrBootstrap() to the gateway contract.
  - ApiBackedAuthGateway.kt: wired SessionPersistence and implemented restoreOrBootstrap() so a persisted sessionId is re-validated on launch before falling back to bootstrap.
  - MainActivity.kt: routes startup by auth phase, renders the LOGIN_REQUIRED screen with a debug re-auth button.
  - activity_main.xml: layout adjusted to accommodate the LOGIN_REQUIRED state.
  - services/api/src/session/session-store.ts: file-backed atomic JSON persistence for authenticated sessions (replaces prior in-memory-only store).
- Commands run: `./gradlew :app:assembleDebug` (BUILD SUCCESSFUL in 3m 25s); `npm run check` in services/api (tsc --noEmit clean).
- Passed: Android debug APK builds green; API service typechecks clean.
- Failed: none.
- Remaining: none for this entry.
- Result: client now has an explicit unauthenticated phase and API sessions survive backend restarts; ready for device validation.

### Entry 014
- Status: completed (on-device validation)
- Summary: Validated Entry 013 session-persistence + LOGIN_REQUIRED flow on physical Fire TV (AFTKM @ 192.168.1.168) against rebuilt API service.
- Commands run:
  - `./gradlew :app:assembleDebug` (reused APK from Entry 013, BUILD SUCCESSFUL).
  - Rebuilt API: `npm --workspace @soundcloud-private/api run build`.
  - Wiped backend state: `rm -f services/api/data/sessions.json services/api/.local/provider-token-store.json`, restarted `node dist/index.js` with `ENABLE_DEBUG_AUTH=true`.
  - `adb uninstall com.neilpontecorvo.soundcloudfiretv && adb install -r app-debug.apk && adb shell am start ... MainActivity`.
  - `adb shell input keyevent 23` (DPAD_CENTER to click "Use Debug Session").
  - `adb shell input keyevent KEYCODE_MEDIA_PLAY_PAUSE` (x2 for pause/play regression).
- What passed:
  1. Fresh-install cold start: app lands on LOGIN_REQUIRED with "Sign In Required / Private Cloud TV / Ready to sign in." and focuses "Use Debug Session" (debug route is now a fallback button, not the default happy path).
  2. Debug auth → HOME: clicking Use Debug Session authenticates and navigates to HOME ("Cloud Player" title, Latest rail populated). Backend `sessions.json` picks up the authenticated session; device `shared_prefs/session_persistence.xml` holds `last_session_id`.
  3. Silent restore: `force-stop` + relaunch with valid persisted sessionId goes straight to HOME without flashing LOGIN_REQUIRED.
  4. Invalid-session fallback: wiping backend `sessions.json` + `provider-token-store.json` and restarting the API, then relaunching the app, correctly routes to LOGIN_REQUIRED, clears the stale sessionId from `shared_prefs` (confirmed empty `<map/>`), and completes a fresh bootstrap ("Ready to sign in.").
  5. Playback intact: selecting `Local Debug Track` reaches Player, widget fires `ready` → `play`, sound resolves to `Flickermood` by `Forss`, and `isPaused=false` after debug play invocation.
  6. Play/Pause key: global MEDIA_PLAY_PAUSE keycode still consumed at the Activity level (`Transport dispatch: command=toggle`) and widget responds with `pause` then `play` events across two presses.
- What failed: nothing observed during validation.
- Regressions: none to playback, launcher, banner, or Play/Pause.
- Remaining blocker: none for this validation pass.
- Next step: optional cleanup — purge old pre-Entry-013 authenticated session rows from `services/api/data/sessions.json` on startup (they currently get re-hydrated from `provider-token-store.json` even when `data/sessions.json` is deleted). Out of scope for this entry.

### Entry 015
- Status: completed (API build + API typecheck + Android assembleDebug all passed; local curl smoke test confirmed the new content behavior; on-device validation still pending)
- Summary: Stopped silently serving `Local Debug Track` / `Local Debug Playlist` as the default signed-in Home/Library/Search content. Real provider-backed content is now the default path for authenticated sessions; debug rails are only reachable via explicit dev-only routes.
- Motivation: prior to this entry, any authenticated session whose provider credentials had `source: 'local_debug'` was routed inside `ProviderCatalogProvider` straight to the `localDebug*` helpers, so Home/Library/Search always rendered the debug items — the "signed-in experience" was structurally the debug experience, which blocked Phase 4 content work.
- Changes:
  - `services/api/src/content/catalog-provider.ts`:
    - `getFeed` / `search` / `getLibrary` now return an empty payload (rather than `localDebugFeed/Search/Library`) when `credentials.isLocalDebugSession(session)` is true. Real provider sessions are unchanged — they continue to hit `providerGet(...)` against the upstream API.
    - Added `emptyFeed()` / `emptySearch(query)` / `emptyLibrary()` helpers.
    - Exported `localDebugItems`, `localDebugFeed`, `localDebugSearch`, `localDebugLibrary` so the new debug-only routes can reuse them without duplicating the fixture.
  - `services/api/src/routes/debug.ts`:
    - Added `GET /v1/debug/content/feed`, `/v1/debug/content/search`, `/v1/debug/content/library`.
    - Added a `requireDebugEnabled` middleware so the whole debug surface is off when `ENABLE_DEBUG_AUTH=false` or `NODE_ENV=production`.
    - Added a `requireLocalDebugSession` middleware that rejects with `invalid_session` unless the caller's session is actually a local-debug one — real provider sessions can never accidentally surface debug rails through these routes.
    - The existing `POST /v1/debug/authenticate-session` is untouched and still guarded inline.
  - No Android client changes. `ContentRepository` already maps `items.isEmpty() → ContentLoadState.Empty`, and `MainActivity.handleContentState` already renders "No content available" (Home) and "Library is empty" (Library) for that state, so the signed-in UI now shows a clean empty state for local-debug sessions without any client-side code change. Hardened WebView, allowlist, session persistence, LOGIN_REQUIRED, launcher/banner, and global Play/Pause behavior are all unchanged.
- Validation:
  - `npm --workspace @soundcloud-private/api run check` → clean.
  - `npm --workspace @soundcloud-private/api run build` → clean (`tsc -p tsconfig.json`, no errors).
  - `./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL` (APK produced, no new lint regressions).
  - Local curl smoke test against `dist/index.js` on port 4099 with an isolated `SESSION_STORE_PATH` + `PROVIDER_TOKEN_STORE_PATH` and `ENABLE_DEBUG_AUTH=true`:
    - `POST /v1/device/bootstrap` → session id issued.
    - `POST /v1/debug/authenticate-session` → session flips to `authenticated` with `source=local_debug`.
    - `GET /v1/feed` → `{"items":[]}` (previously returned Local Debug Track + Local Debug Playlist).
    - `GET /v1/library` → `{"sections":[]}` (previously returned the `debug-local` section).
    - `GET /v1/search?q=flicker` → `{"items":[]}`.
    - `GET /v1/debug/content/feed` → the two debug items (Local Debug Track + Local Debug Playlist, webUrl `https://soundcloud.com/forss/flickermood`).
    - `GET /v1/debug/content/library` → the `debug-local` section.
    - `GET /v1/debug/content/search?q=flicker` → filtered debug items (empty for `flicker` because the substring filter matches title/subtitle/creator/kind, none of which contain "flicker"; `?q=` with an empty query or `?q=local` would return both).
    - `GET /v1/debug/content/feed` with no `x-session-id` → `401 invalid_session` (guard works).
- What passed: both builds, typecheck, and the full curl matrix above.
- What failed: nothing.
- Known effects (not regressions):
  - On Fire TV with a local-debug session, Home will now render "No content available" and Library "Library is empty" instead of a populated Latest rail. This is the intended outcome — it's the "clean signed-in empty/error state" called out in the task goals.
  - The on-device playback smoke test from Entry 014 (selecting Local Debug Track from Home) can no longer be reproduced by picking a card out of Home, because Home is empty for debug sessions. The underlying playback path is unchanged; the debug items can still be reached through `/v1/debug/content/feed` (or by curl) to re-prove playback if needed. Adding a small "Load debug rails" button in the Diagnostics screen that hits `/v1/debug/content/feed` is a natural follow-up, but was intentionally left out of this entry to keep scope narrow (no new UI features).
- Remaining blocker: none for this backend change. Real provider content cannot actually be exercised on device yet, because no real provider OAuth flow has been wired to Fire TV — Entry 013/014 only validated the local-debug path. Reaching real personalized feed/library on the TV itself still depends on either a real device-auth pairing flow or a provisioned provider token being attached to a real session.
- Next step: run the device regression described below (section "On-device regression steps"); afterward, the natural follow-up entries are (a) optionally exposing a dev-only "Load debug rails" diagnostics action so playback can be re-proven without curl, and (b) real provider OAuth pairing so Home can render actual personalized content.

On-device regression steps (to run from `~/soundcloud`):

```bash
# 1. Rebuild the backend and restart it with debug auth enabled
npm --workspace @soundcloud-private/api run build
ENABLE_DEBUG_AUTH=true HOST=0.0.0.0 PORT=4000 npm --workspace @soundcloud-private/api start &

# 2. Reinstall the Fire TV APK (no Kotlin changed, but reinstall for clean state)
cd apps/firetv-client
export ANDROID_HOME="$HOME/Library/Android/sdk"
export JAVA_HOME="$([ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 17)"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
./gradlew :app:assembleDebug
adb connect <FIRE_TV_IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER \
  -n com.neilpontecorvo.soundcloudfiretv/.app.MainActivity
```

Expected on-device outcomes:

1. Cold launch with no persisted session → LOGIN_REQUIRED (unchanged from Entry 014).
2. Click "Use Debug Session" → transitions to Home, but Home renders "No content available" (Latest rail is gone). Library renders "Library is empty". Search with any query shows its normal "No results for ..." body.
3. Force-stop + relaunch → still skips LOGIN_REQUIRED and returns straight to Home (session persistence intact); Home still empty for local-debug.
4. From a dev laptop, `curl http://127.0.0.1:4000/v1/debug/content/feed -H "x-session-id: <SID>"` still returns Local Debug Track / Local Debug Playlist — proves the debug rail is preserved as an explicit fallback, not a silent default.

### Entry 016
- Status: completed (on-device validation of Entry 015)
- Summary: Validated Entry 015's "no debug rails in the default signed-in UI" behavior on physical Fire TV against the rebuilt API service.
- Device: Fire TV (AFTKM), same LAN target used in Entry 014.
- What passed:
  1. Home empty-state: after clicking "Use Debug Session" on the LOGIN_REQUIRED screen, Home rendered "No content available" instead of the previous `Latest` rail with `Local Debug Track` / `Local Debug Playlist`. This is the core behavior change from Entry 015 and is confirmed working on device.
  2. Library empty-state: navigating to Library with the same debug session active rendered "Library is empty" (no `Local Debug Session` section).
  3. Silent restore into empty signed-in state: force-stop + relaunch skipped LOGIN_REQUIRED and returned straight to the empty Home. Session persistence from Entry 013/014 is intact; the empty-Home change does not regress the restore path.
- What failed: nothing observed.
- Regressions: none. LOGIN_REQUIRED, session persistence / restore, launcher visibility, banner, hardened WebView boundary, and global Play/Pause behavior are all untouched by Entry 015 and continue to work as validated in Entry 014.
- Remaining blocker: none for this validation pass. On-device verification of `/v1/debug/content/feed` returning Local Debug rails was not re-run in this pass (the server-side curl matrix in Entry 015 already confirmed it with the fixture; no on-device surface reads from that route yet).
- Next step: optional follow-ups — (a) add a dev-only "Load debug rails" action in the Diagnostics screen that hits `/v1/debug/content/feed` so the debug playback regression from Entry 014 can be re-run without curl; (b) wire a real provider-OAuth device pairing flow so an authenticated Fire TV user can see actual personalized content on Home/Library instead of an empty signed-in state. Both are separate entries.

### Entry 017
- Status: implemented; local validation passed; physical Fire TV provider validation blocked by device reachability.
- Summary: Added the first real provider OAuth pairing path for Fire TV. LOGIN_REQUIRED now has a real provider sign-in primary action; debug auth remains a debug-only fallback button.
- Changes:
  - Backend:
    - Added short-lived provider auth pairing codes and CSRF state tracking in `services/api/src/provider/auth-pairing-store.ts`.
    - `POST /v1/device/bootstrap` now returns `verificationUri`, `verificationUriComplete`, and `userCode` for TV pairing.
    - Added `GET /v1/auth/pair` for browser code entry.
    - Added `GET /v1/auth/start?user_code=<code>` to validate the TV code and redirect to the provider authorization URL.
    - Added `GET /v1/auth/callback` to exchange the provider authorization code server-side and mark the original backend session authenticated.
    - Added provider config for `PROVIDER_AUTHORIZE_URL`, `PROVIDER_OAUTH_SCOPE`, and `PROVIDER_AUTH_PUBLIC_BASE_URL`.
    - Updated API contract and README docs for the pairing response/flow.
  - Android:
    - Added `verificationUriComplete` to bootstrap DTO/state.
    - LOGIN_REQUIRED now focuses a primary `Start Provider Sign In` / `Check Sign-In Status` action.
    - The sign-in screen displays the backend pairing URL and user code, then polls session status until the backend reports `authenticated`.
    - Debug auth is still available only in debug builds as `Use Debug Fallback`, and is no longer the focused/primary sign-in path.
- Commands run:
  - `npm run check:api`
  - `npm --workspace @soundcloud-private/api run build`
  - `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:assembleDebug`
  - Local API smoke server on port 4012 with dummy provider env vars.
  - `curl -s -X POST http://127.0.0.1:4012/v1/device/bootstrap -H 'Content-Type: application/json' -d '{"deviceName":"codex-smoke","appVersion":"0.1.0"}'`
  - `curl -s -I 'http://127.0.0.1:4012/v1/auth/start?user_code=PGJM-FETN'`
  - `curl -s 'http://127.0.0.1:4012/v1/auth/pair?user_code=PGJM-FETN'`
  - `curl -s http://127.0.0.1:4012/v1/session/e092a809-70e6-49a4-b394-076fe65f5bca`
  - `adb connect 192.168.1.168:5555`
- What passed:
  - API typecheck passed.
  - API build passed.
  - Android debug build passed.
  - Local bootstrap smoke returned an awaiting-auth session with a pairing URL, complete URL, and user code.
  - Local pairing page rendered a browser form with the code prefilled.
  - Local auth start redirected to `https://secure.soundcloud.com/authorize` with `client_id`, `redirect_uri`, `response_type=code`, and `state`.
  - Session polling stayed `awaiting_auth` before provider callback, as expected.
- What failed:
  - First local API server smoke attempt failed in the sandbox with `listen EPERM`; reran with approval and the server started successfully.
  - Physical Fire TV validation could not start because `adb connect 192.168.1.168:5555` failed with `No route to host`.
- Regressions: none observed in local/API/build validation. Playback, launcher/banner, Play/Pause, session restore, and content behavior were not changed directly, but still need the required physical-device regression pass after ADB/LAN access is restored.
- Remaining blocker: Fire TV device is unreachable over ADB/LAN from this session, and real provider credentials/callback must be configured before proving full provider-authenticated content on device.
- Exact next device step:
  1. Confirm Fire TV VPN is off and the Mac can route to `192.168.1.168`.
  2. Start backend from repo root with real provider env:
     `ENABLE_DEBUG_AUTH=false HOST=0.0.0.0 PORT=4000 PROVIDER_AUTH_PUBLIC_BASE_URL=http://192.168.1.167:4000 PROVIDER_REDIRECT_URI=http://192.168.1.167:4000/v1/auth/callback PROVIDER_CLIENT_ID=<real> PROVIDER_CLIENT_SECRET=<real> npm --workspace @soundcloud-private/api start`
  3. Rebuild/reinstall APK, launch the app, select `Start Provider Sign In`, open the displayed URL on a phone/laptop, complete provider authorization, and confirm the TV routes to Home with a non-local_debug authenticated session.

### Entry 018
- Status: implemented; local/sandbox and live LAN preflight runs completed; physical Fire TV provider validation still blocked by missing backend/env and ADB authorization.
- Summary: Added a repo-root one-command Fire TV provider-auth preflight so the next on-device sign-in pass checks backend LAN URL, provider OAuth env, route/LAN reachability, and ADB before rebuild/install.
- Changes:
  - Added `scripts/firetv-provider-auth-preflight.mjs`.
  - Added root npm script `preflight:firetv-provider-auth`.
  - Updated `README.md`, `services/api/README.md`, and `apps/firetv-client/README.md` with the provider-auth preflight sequence.
  - Updated this worklog's active issue and next-step handoff.
- Command:
  - `npm run preflight:firetv-provider-auth`
- What the preflight checks:
  - backend URL defaults to `http://192.168.1.167:4000` unless `FIRETV_BACKEND_URL` or `PROVIDER_AUTH_PUBLIC_BASE_URL` overrides it
  - backend URL is a LAN URL assigned to this Mac
  - `GET /health` responds over the LAN backend URL
  - `PROVIDER_CLIENT_ID`, `PROVIDER_CLIENT_SECRET`, `PROVIDER_REDIRECT_URI`, and `PROVIDER_AUTH_PUBLIC_BASE_URL` are set
  - `PROVIDER_AUTH_PUBLIC_BASE_URL` matches the backend LAN URL
  - `PROVIDER_REDIRECT_URI` matches `<backend>/v1/auth/callback`
  - Mac routing to `192.168.1.168`
  - ICMP/TCP reachability to `192.168.1.168:5555`
  - `adb connect 192.168.1.168:5555` and `adb devices`
- Local verification:
  - First sandbox run executed the command and proved script wiring, but LAN/socket operations were sandbox-blocked with `EPERM`.
  - Escalated live run executed successfully and produced real environment results.
- Live preflight result:
  - Passed:
    - backend URL `http://192.168.1.167:4000` is assigned to this Mac
    - Mac route to Fire TV uses `interface: en1`
    - Fire TV ping replied from `192.168.1.168`
    - TCP connect to `192.168.1.168:5555` succeeded
    - `adb` executable is available
  - Failed:
    - backend `/health` on `http://192.168.1.167:4000` returned `ECONNREFUSED` because the backend was not running
    - provider OAuth env was missing from the shell
    - `adb connect 192.168.1.168:5555` failed with `failed to authenticate to 192.168.1.168:5555`
    - `adb devices` listed `192.168.1.168:5555	unauthorized`
- Regressions: none; this is a script/docs/worklog-only change.
- Remaining blocker: Fire TV LAN reachability is now present, but ADB still needs to be authorized on the Fire TV. Provider credentials also need to be exported and the backend needs to be running before the preflight can pass.
- Exact next device step:
  1. Accept/refresh the ADB debugging authorization prompt on the Fire TV so `adb devices` shows `192.168.1.168:5555	device` instead of `unauthorized`.
  2. Start backend from repo root with real provider env:
     `ENABLE_DEBUG_AUTH=false HOST=0.0.0.0 PORT=4000 PROVIDER_AUTH_PUBLIC_BASE_URL=http://192.168.1.167:4000 PROVIDER_REDIRECT_URI=http://192.168.1.167:4000/v1/auth/callback PROVIDER_CLIENT_ID=<real> PROVIDER_CLIENT_SECRET=<real> npm --workspace @soundcloud-private/api start`
  3. In another shell with the same provider env, run `npm run preflight:firetv-provider-auth`.
  4. Only if preflight passes, rebuild/install:
     `cd apps/firetv-client && ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:assembleDebug -PapiBaseUrl=http://192.168.1.167:4000 && adb connect 192.168.1.168:5555 && adb install -r app/build/outputs/apk/debug/app-debug.apk`
  5. Launch the app, select `Start Provider Sign In`, open the displayed URL on a phone/laptop, complete provider authorization, and confirm the TV routes to Home with a non-local_debug authenticated session.

### Entry 019
- Status: implemented; API typecheck passed; provider auth execution reached TV pairing screen but cannot complete without provider credentials.
- Summary: Executed the provider-auth path far enough for the Fire TV to bootstrap and display a pairing URL/code. While doing that, fixed the API server so the documented `HOST=0.0.0.0` setting is actually honored by `app.listen`.
- Changes:
  - `services/api/src/config/env.ts`: added `host` to API env, defaulting to `127.0.0.1`.
  - `services/api/src/index.ts`: now calls `app.listen(env.port, env.host, ...)` and logs `host:port`.
- Commands run:
  - `npm run preflight:firetv-provider-auth`
  - `PROVIDER_AUTH_PUBLIC_BASE_URL=http://192.168.1.167:4000 PROVIDER_REDIRECT_URI=http://192.168.1.167:4000/v1/auth/callback ENABLE_DEBUG_AUTH=false HOST=0.0.0.0 PORT=4000 npm --workspace @soundcloud-private/api start`
  - `curl -s -I 'http://127.0.0.1:4000/v1/auth/start?user_code=P7Y4-N4YC'`
  - `curl -s 'http://127.0.0.1:4000/v1/auth/start?user_code=P7Y4-N4YC'`
  - `npm --workspace @soundcloud-private/api run build`
  - `npm run check:api`
- What passed:
  - Fire TV progressed from backend connection failure to the pairing screen.
  - Pairing URL/code displayed on TV: `http://192.168.1.167:4000/v1/auth/start?user_code=P7Y4-N4YC`, code `P7Y4-N4YC`.
  - API typecheck passed after the host-bind source change.
- What failed / blocked:
  - The pairing URL currently returns `501 provider_not_configured` because this shell has no `PROVIDER_CLIENT_ID` or `PROVIDER_CLIENT_SECRET`, and no repo `.env` file with those values was found.
  - Preflight still reported ADB as `unauthorized` until the Fire TV accepts the ADB debugging prompt.
- Regressions: none observed. This change only makes the documented API `HOST` setting explicit.
- Exact next step:
  1. Export real `PROVIDER_CLIENT_ID` and `PROVIDER_CLIENT_SECRET` along with `PROVIDER_AUTH_PUBLIC_BASE_URL=http://192.168.1.167:4000` and `PROVIDER_REDIRECT_URI=http://192.168.1.167:4000/v1/auth/callback`.
  2. Restart the backend with `ENABLE_DEBUG_AUTH=false HOST=0.0.0.0 PORT=4000 npm --workspace @soundcloud-private/api start`.
  3. Accept the Fire TV ADB authorization prompt so `adb devices` shows `192.168.1.168:5555	device`.
  4. Rerun `npm run preflight:firetv-provider-auth`.
  5. Start provider sign-in again on the TV and open the new displayed URL/code.
