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
- The complete 1920 × 1080 redesign is installed and validated on the AFTKM Fire TV.
- Home and Library populate their required four rails with real provider content and exact Spotlight selection.
- Complete paginated Library/Search results remain accessible beyond the first viewport.
- Playlist and album detail expose complete independently scrolling track lists; selecting any focused row opens and plays that exact track in Player.
- Player waveform selection, minute-scale held-D-pad scanning, description-panel scrolling, and the header navigation ceiling are physically verified.
- Public tracks and full-length account-owned private tracks play successfully; a tested private item reports 210,051 ms (3:30), not the provider's 29-second preview.
- The display remains awake while the app is in use.
- The final installed APK declares the supplied 1280 × 720 launcher artwork and `SOUNDCLOUD` label; Fire OS cache refresh is pending the user's final restart.

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
1. **Launcher cache refresh pending user restart**
   - The final APK is installed with the supplied 1280 × 720 artwork mapped as both icon and banner and with launcher label `SOUNDCLOUD`.
   - APK badging resolves every icon density to `tv_banner.png`; Fire OS still shows its cached prior tile until the planned device restart.

2. **Non-blocking maintenance**
   - Android's legacy fullscreen system-UI flags emit deprecation warnings but compile, lint, and run correctly on the AFTKM target.
   - An Amazon Appstore-only 1920 × 1080 background image is unnecessary for this private sideloaded app and cannot be injected into the launcher from the APK manifest.

---

## 6. Completion Estimate
### Internal prototype
- Overall completion: **100% for the requested scope**

### Polished private-use app
- Overall completion: **98%**

### Module estimate
- Backend/session/auth/content: **99%**
- TV shell/nav/cards/focus: **99%**
- Player runtime on physical Fire TV: **99%**
- Media transport integration: **98%**
- Launcher/banner packaging: **100% pending cache refresh**
- Final cleanup/polish: **95%**

### Estimated time remaining
- Requested implementation: **complete**
- Optional maintenance/distribution work: **not part of this goal**

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
- Provider pagination, exact Spotlight normalization, complete collection detail, and full-length private-track stream proxy are implemented server-side.

### Android / Fire TV
- Build passes with Gradle.
- LAN host changed from emulator alias to real Mac IP.
- Debug cleartext allowed for local testing host only.
- Home/Search/Library evolved from text panels to usable prototype UI.
- Home, Library, Search, Playlist/Album Detail, Player, and the mini-player now use the completed 1920 × 1080 reference-frame redesign.
- Complete Library/Search results and independent horizontal rails are accessible by D-pad.
- Playlist and album track tables share the same deterministic focus/queue behavior.
- Focused waveforms support minute-scale D-pad scanning with held-key acceleration; media FF/REW and visible controls use bounded ±10-second seeking.
- Player description overflow scrolls inside its fixed panel.
- Account-owned private tracks play at full duration through native Android playback without exposing provider tokens to the device or CDN.
- The app requests screen-on behavior while in use.
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
- FF/REW and focused-waveform seek paths are functional and bounded.
- Launcher/banner packaging was fixed by removing the conflicting shape wrapper and wiring a real PNG banner.
- App icon metadata was restored so the launcher tile surfaces correctly.
- Fire TV home screen now shows the app tile and custom banner.

### Audit results
- `npm run check:api` passed.
- `npm --workspace @soundcloud-private/api run build` passed.
- `npm audit` currently reports four pre-existing dependency advisories (one low, three moderate); no task-related package was added.
- `./gradlew :app:assembleDebug` passed.
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passed.
- APK badging verified launcher activity, icon, and banner wiring.

---

## 8. Remaining Tasks
### Critical path
1. No remaining critical-path item for the requested redesign.
2. Restart the Fire TV once to refresh its cached launcher label/artwork.

### Secondary tasks
- Replace deprecated fullscreen system-UI flags during a future platform-maintenance pass.
- Telemetry/crash diagnostics hooks
- Optional Amazon Appstore listing assets only if distribution becomes a future goal.

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
- FF/REW are functional bounded seek commands; waveform D-pad scanning uses longer minute-scale steps.

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
The requested UI implementation goal is committed and pushed. Restart the Fire TV once so Fire OS refreshes the installed `SOUNDCLOUD` launcher label and supplied icon/banner from its cache. Future work requires a new scoped task.

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
- Export real provider OAuth env, restart the backend, rerun `npm run preflight:firetv-provider-auth`, then complete the displayed Fire TV provider pairing URL/code.

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

### Entry 020
- Status: validation pass completed; app rebuilt, installed, and launched; provider sign-in still blocked by missing backend OAuth env.
- Summary: Continued the Fire TV provider-auth launch path from Entry 019. ADB authorization is now fixed, the API and APK build cleanly, the backend is reachable over the LAN HTTP URL, and the app is visibly launched on the Fire TV sign-in screen. The current blocker is only provider OAuth configuration in the running backend process.
- Commands run:
  - `npm run check:api`
  - `npm --workspace @soundcloud-private/api run build`
  - `./gradlew :app:assembleDebug -PapiBaseUrl=http://192.168.1.167:4000`
  - `curl -fsS http://127.0.0.1:4000/health`
  - `curl -fsS http://192.168.1.167:4000/health`
  - `adb connect 192.168.1.168:5555`
  - `adb install -r apps/firetv-client/app/build/outputs/apk/debug/app-debug.apk`
  - `adb shell am start -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER -n com.neilpontecorvo.soundcloudfiretv/.app.MainActivity`
  - `npm run preflight:firetv-provider-auth`
  - `curl -i 'http://192.168.1.167:4000/v1/auth/start?user_code=LL32-7X4W'`
  - `curl -i 'http://192.168.1.167:4000/v1/auth/start?user_code=JGUS-3GZK'`
  - `adb exec-out screencap -p > /tmp/soundcloud-firetv-current.png`
- What passed:
  - API typecheck passed.
  - API build passed.
  - Android debug build passed.
  - Backend `/health` returned HTTP 200 on both localhost and `http://192.168.1.167:4000`.
  - ADB is now authorized: `adb devices` lists `192.168.1.168:5555	device`.
  - APK reinstall succeeded.
  - App launch succeeded.
  - Fire TV screenshot shows the app on `Sign In Required` with `Start Provider Sign In` focused and code `JGUS-3GZK` displayed at `http://192.168.1.167:4000/v1/auth/start?user_code=JGUS-3GZK`.
  - Pairing pages for both `LL32-7X4W` and `JGUS-3GZK` rendered, proving the pairing codes are recognized by the running backend.
- What failed / blocked:
  - `/v1/auth/start` for both codes returned `501 provider_not_configured`.
  - `npm run preflight:firetv-provider-auth` now passes LAN backend, Fire TV route, ping, TCP 5555, ADB connect, and ADB device checks, but still fails because `PROVIDER_CLIENT_ID`, `PROVIDER_CLIENT_SECRET`, `PROVIDER_REDIRECT_URI`, and `PROVIDER_AUTH_PUBLIC_BASE_URL` are missing from the shell.
  - The currently persisted real-provider token record is from 2026-04-20 and is marked `expired`; the only persisted authenticated session is `local_debug`, so there is no current restorable provider session for the Fire TV to reuse.
- Regressions: none observed in build/install/launch. No source code was changed in this entry.
- Exact next step:
  1. Restart the backend with real provider env:
     `PROVIDER_CLIENT_ID=<real> PROVIDER_CLIENT_SECRET=<real> PROVIDER_AUTH_PUBLIC_BASE_URL=http://192.168.1.167:4000 PROVIDER_REDIRECT_URI=http://192.168.1.167:4000/v1/auth/callback ENABLE_DEBUG_AUTH=false HOST=0.0.0.0 PORT=4000 npm --workspace @soundcloud-private/api start`
  2. In the same env, rerun `npm run preflight:firetv-provider-auth`.
  3. Use the fresh code displayed on the TV at `http://192.168.1.167:4000/v1/auth/start?user_code=<code>` and complete provider authorization.

### Entry 021
- Status: validation pass completed; fresh APK launch confirmed; provider sign-in remains blocked by missing backend OAuth env.
- Summary: Continued the provider-auth launch path with the new Fire TV pairing code `HLUZ-DV8B`. The app is foregrounded on the Fire TV, the displayed code matches the UI hierarchy, and the backend recognizes the route but cannot redirect to the provider because OAuth credentials are not configured in the running API process.
- Commands run:
  - `npm run check:api`
  - `npm --workspace @soundcloud-private/api run build`
  - `./gradlew :app:assembleDebug -PapiBaseUrl=http://192.168.1.167:4000`
  - `npm run preflight:firetv-provider-auth`
  - `adb connect 192.168.1.168:5555`
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk`
  - `adb shell am start -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER -n com.neilpontecorvo.soundcloudfiretv/.app.MainActivity`
  - `adb shell dumpsys activity activities`
  - `adb shell uiautomator dump /sdcard/soundcloud-window.xml`
  - `curl -i 'http://127.0.0.1:4000/v1/auth/start?user_code=HLUZ-DV8B'`
- What passed:
  - API typecheck passed.
  - API build passed.
  - Android debug build passed after rerunning outside the sandbox because Gradle's local file-lock socket was sandbox-blocked.
  - Backend `/health` over the LAN URL passed in preflight.
  - Fire TV route, ping, TCP 5555, ADB connect, and ADB device checks all passed.
  - `adb devices` lists `192.168.1.168:5555	device`.
  - APK reinstall succeeded.
  - App launch succeeded; `dumpsys activity` shows `com.neilpontecorvo.soundcloudfiretv/.app.MainActivity` as the resumed/focused activity.
  - UI hierarchy shows `Sign In Required`, the displayed URL `http://192.168.1.167:4000/v1/auth/start?user_code=HLUZ-DV8B`, code `HLUZ-DV8B`, and `START PROVIDER SIGN IN` focused.
- What failed / blocked:
  - `curl -i 'http://127.0.0.1:4000/v1/auth/start?user_code=HLUZ-DV8B'` returned `501 provider_not_configured` with message `Provider OAuth requires PROVIDER_CLIENT_ID, PROVIDER_CLIENT_SECRET, and PROVIDER_REDIRECT_URI.`
  - `npm run preflight:firetv-provider-auth` fails only provider-env checks: `PROVIDER_CLIENT_ID`, `PROVIDER_CLIENT_SECRET`, `PROVIDER_REDIRECT_URI`, and `PROVIDER_AUTH_PUBLIC_BASE_URL` are missing from this shell.
  - The in-app Browser surface requested through `@Browser` was not available in this Codex session, so the pairing URL could not be visually driven there; the direct HTTP result above is the authoritative backend outcome.
- Regressions: none observed in build/install/launch. No source code was changed in this entry.
- Exact next step:
  1. Restart the backend with real provider env:
     `PROVIDER_CLIENT_ID=<real> PROVIDER_CLIENT_SECRET=<real> PROVIDER_AUTH_PUBLIC_BASE_URL=http://192.168.1.167:4000 PROVIDER_REDIRECT_URI=http://192.168.1.167:4000/v1/auth/callback ENABLE_DEBUG_AUTH=false HOST=0.0.0.0 PORT=4000 npm --workspace @soundcloud-private/api start`
  2. In the same env, rerun `npm run preflight:firetv-provider-auth`.
  3. Start provider sign-in again on the TV, use the fresh displayed code, complete provider authorization, and confirm Home/Search/Library load provider-backed content.

### Entry 022
- Status: implemented and locally validated; physical provider callback still blocked by missing backend OAuth env.
- Summary: Added server-side recovery for previously authenticated provider sessions that have refresh tokens. This addresses the "it previously worked, why authorize again?" path: an expired provider access token is now treated as recoverable on API startup, and a temporary missing-OAuth-env failure no longer permanently re-expires that stored provider session.
- Changes:
  - `services/api/src/provider/credentials-service.ts`: added `restoreStoredSession(...)` to revive expired provider sessions that still have a refresh token, giving them a short backend recovery window while preserving the old access-token expiry so the next content access still refreshes server-side.
  - `services/api/src/provider/credentials-service.ts`: changed `refreshSession(...)` so `provider_not_configured` is propagated without rewriting the persisted provider session to `expired`.
  - `services/api/src/provider/provider-runtime.ts`: routes provider token-store startup restoration through `restoreStoredSession(...)`.
- Commands run:
  - `npm run check:api`
  - `npm --workspace @soundcloud-private/api run build`
  - Direct module smoke: expired provider session with refresh token restores as `authenticated`, writes `authenticated` back to the temp session/token stores, gets a future backend `expiresAtIso`, and keeps `accessTokenExpiresAtIso` expired so refresh is still required before real provider content.
  - Direct module smoke: missing provider OAuth env returns `provider_not_configured` during token access and leaves the temp provider session `authenticated` rather than re-expiring it.
  - `adb shell uiautomator dump /sdcard/soundcloud-window.xml` confirmed the Fire TV is still foregrounded on `Sign In Required` with code `HLUZ-DV8B` and `START PROVIDER SIGN IN` focused.
  - `curl -i 'http://127.0.0.1:4000/v1/auth/start?user_code=HLUZ-DV8B'` still returns `501 provider_not_configured` from the currently running backend process.
- What passed:
  - API typecheck passed.
  - API build passed.
  - Both isolated recovery smoke checks passed against rebuilt `dist` output.
  - Fire TV launch/UI state remains correct and shows the latest code `HLUZ-DV8B`.
- What failed / blocked:
  - The running backend process is still `node dist/index.js` without `PROVIDER_CLIENT_ID`, `PROVIDER_CLIENT_SECRET`, `PROVIDER_REDIRECT_URI`, or `PROVIDER_AUTH_PUBLIC_BASE_URL`, so real provider authorization cannot complete yet.
  - The new recovery code is built locally but will not affect the live `:4000` process until that backend is restarted from the rebuilt `dist`.
- Exact next step:
  1. Restart the backend from rebuilt `dist` with real provider env:
     `PROVIDER_CLIENT_ID=<real> PROVIDER_CLIENT_SECRET=<real> PROVIDER_AUTH_PUBLIC_BASE_URL=http://192.168.1.167:4000 PROVIDER_REDIRECT_URI=http://192.168.1.167:4000/v1/auth/callback ENABLE_DEBUG_AUTH=false HOST=0.0.0.0 PORT=4000 npm --workspace @soundcloud-private/api start`
  2. Rerun `npm run preflight:firetv-provider-auth`.
  3. Use the fresh code on the TV and complete the provider callback. If the old provider refresh token is still valid, the app should no longer require a full account re-authorization after the backend restarts with env.

### Entry 023
- Status: launched current APK and validated the Fire TV HTTP auth address; provider redirect remains blocked by missing real OAuth client credentials.
- Summary: Restarted the API from rebuilt `dist` on the Mac LAN address and reinstalled/relaunched the current Fire TV debug APK. The stale `HLUZ-DV8B` / `M9JL-7ETD` codes were no longer valid after backend restart/TTL expiry, but the relaunched app generated fresh code `6FVH-BEMC`.
- Commands run:
  - `HOST=0.0.0.0 PORT=4000 ENABLE_DEBUG_AUTH=false PROVIDER_AUTH_PUBLIC_BASE_URL=http://192.168.1.167:4000 PROVIDER_REDIRECT_URI=http://192.168.1.167:4000/v1/auth/callback npm --workspace @soundcloud-private/api start`
  - `curl -s -i http://127.0.0.1:4000/health`
  - `curl -s -i 'http://127.0.0.1:4000/v1/auth/start?user_code=HLUZ-DV8B'`
  - `npm run preflight:firetv-provider-auth`
  - `curl -s -i -X POST http://127.0.0.1:4000/v1/device/bootstrap -H 'Content-Type: application/json' -d '{"deviceName":"codex-smoke","appVersion":"test"}'`
  - `curl -s -i 'http://127.0.0.1:4000/v1/auth/start?user_code=LJ2V-ZKD9'`
  - `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:assembleDebug -PapiBaseUrl=http://192.168.1.167:4000` from `apps/firetv-client`
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk` from `apps/firetv-client`
  - `adb shell am force-stop com.neilpontecorvo.soundcloudfiretv`
  - `adb shell am start -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER -n com.neilpontecorvo.soundcloudfiretv/.app.MainActivity`
  - `adb shell uiautomator dump /sdcard/soundcloud-window.xml`
  - `curl -s -i 'http://127.0.0.1:4000/v1/auth/start?user_code=6FVH-BEMC'`
- What passed:
  - Backend health returned HTTP 200 on `127.0.0.1:4000` after relaunch.
  - Direct backend bootstrap generated live code `LJ2V-ZKD9`; that URL reached `501 provider_not_configured`, proving the pairing store and `/v1/auth/start` path are working when the code is current.
  - Android debug build completed successfully from `apps/firetv-client`.
  - APK reinstall succeeded.
  - Fire TV app relaunched and UI dump confirmed `Sign In Required` with current code `6FVH-BEMC` and URL `http://192.168.1.167:4000/v1/auth/start?user_code=6FVH-BEMC`.
  - The current TV code `6FVH-BEMC` reached `501 provider_not_configured`, so the TV-displayed HTTP auth address is now backed by the running API process.
- What failed / blocked:
  - `HLUZ-DV8B` and `M9JL-7ETD` returned `404 Sign-in code expired` because awaiting-auth pairings are short-lived and not persisted across backend restart/TTL expiry.
  - Preflight now passes LAN backend health, Fire TV route, ping, TCP 5555, ADB connect, and ADB device, but still fails shell env checks for `PROVIDER_CLIENT_ID`, `PROVIDER_CLIENT_SECRET`, `PROVIDER_REDIRECT_URI`, and `PROVIDER_AUTH_PUBLIC_BASE_URL`. The running backend was started with public base/callback env, but the preflight command still needs those env vars in its own shell.
  - Real provider authorization cannot redirect until `PROVIDER_CLIENT_ID` and `PROVIDER_CLIENT_SECRET` are provided to the backend process.
- Exact next step:
  1. Restart the API with all real provider env in the same shell, including `PROVIDER_CLIENT_ID` and `PROVIDER_CLIENT_SECRET`.
  2. Relaunch the app or use the current displayed fresh code before its 10-minute TTL expires.
  3. Open the TV-displayed URL and complete the provider callback.

### Entry 024
- Status: implemented, installed, and physically validated on Fire TV; provider redirect remains blocked by missing real OAuth credentials.
- Summary: Fixed stale login-screen primary action text. The Fire TV login body was already refreshing as auth state changed, but the primary button was created with a generated id and did not refresh after the state moved into `awaiting_auth`. The button now has a stable resource id and updates with the login body, so awaiting-auth displays `CHECK SIGN-IN STATUS` instead of stale `START PROVIDER SIGN IN`.
- Changes:
  - `apps/firetv-client/app/src/main/res/values/strings.xml`: added stable id resource `login_primary_action`.
  - `apps/firetv-client/app/src/main/java/com/neilpontecorvo/soundcloudfiretv/app/MainActivity.kt`: uses `R.id.login_primary_action`, centralizes login primary action label selection, and refreshes the button text alongside `panelBody`.
- Commands run:
  - `git diff --check`
  - `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:assembleDebug -PapiBaseUrl=http://192.168.1.167:4000`
  - `HOST=0.0.0.0 PORT=4000 ENABLE_DEBUG_AUTH=false PROVIDER_AUTH_PUBLIC_BASE_URL=http://192.168.1.167:4000 PROVIDER_REDIRECT_URI=http://192.168.1.167:4000/v1/auth/callback npm --workspace @soundcloud-private/api start`
  - `adb install -r app/build/outputs/apk/debug/app-debug.apk`
  - `adb shell am force-stop com.neilpontecorvo.soundcloudfiretv`
  - `adb shell am start -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER -n com.neilpontecorvo.soundcloudfiretv/.app.MainActivity`
  - `adb shell uiautomator dump /sdcard/soundcloud-window.xml`
  - `adb exec-out cat /sdcard/soundcloud-window.xml`
  - `curl -s -i 'http://127.0.0.1:4000/v1/auth/start?user_code=YHCJ-UQ2D'`
- What passed:
  - Android debug build passed.
  - APK reinstall succeeded.
  - Fire TV UI dump showed `CHECK SIGN-IN STATUS` with resource id `com.neilpontecorvo.soundcloudfiretv:id/login_primary_action` while displaying code `YHCJ-UQ2D`.
  - The TV-generated code `YHCJ-UQ2D` reached the running backend and returned `501 provider_not_configured`, proving the code was live and the remaining stop is OAuth configuration.
- What failed / blocked:
  - Real provider redirect still cannot complete until the backend is restarted with real `PROVIDER_CLIENT_ID` and `PROVIDER_CLIENT_SECRET`.

### Entry 025
- Status: implemented and validated on the rebuilt backend; real provider redirect remains blocked by missing OAuth credentials.
- Summary: Made the human-opened provider auth start URL render an HTML configuration page when provider OAuth env is missing. Previously `/v1/auth/start?user_code=<code>` fell through to the global JSON error handler for `provider_not_configured`, which was correct for API clients but poor for the phone/computer browser flow launched from the Fire TV screen.
- Changes:
  - `services/api/src/routes/auth.ts`: catches `provider_not_configured` from `providerOAuthService.createAuthorizationUrl(...)` inside `/auth/start` and renders the existing styled `messagePage(...)` with the missing env names.
  - Kept the global error handler unchanged so machine-facing API routes still return JSON errors.
- Commands run:
  - `npm run check:api`
  - `npm --workspace @soundcloud-private/api run build`
  - `git diff --check`
  - Restarted rebuilt API on `HOST=0.0.0.0 PORT=4000`.
  - `curl -s -i -X POST http://127.0.0.1:4000/v1/device/bootstrap -H 'Content-Type: application/json' -d '{"deviceName":"codex-html-smoke","appVersion":"test"}'`
  - `curl -s -i 'http://127.0.0.1:4000/v1/auth/start?user_code=LRJ5-SN68'`
  - `curl -s -i http://127.0.0.1:4000/v1/feed`
- What passed:
  - API typecheck passed.
  - API build passed.
  - Fresh bootstrap generated live code `LRJ5-SN68`.
  - `/v1/auth/start?user_code=LRJ5-SN68` returned HTTP 501 with `Content-Type: text/html` and the title `Provider sign-in is not configured`.
  - `/v1/feed` without a session still returned HTTP 401 with `Content-Type: application/json`, confirming the HTML fallback is route-specific.
- What failed / blocked:
  - Real provider redirect still cannot complete until the backend is restarted with real `PROVIDER_CLIENT_ID` and `PROVIDER_CLIENT_SECRET`.

### Entry 026
- Status: preflight narrowed to the two real OAuth credential blockers.
- Summary: Reran the provider-auth preflight outside the sandbox with `ANDROID_HOME`, `PROVIDER_AUTH_PUBLIC_BASE_URL`, and `PROVIDER_REDIRECT_URI` set. This removed the earlier sandbox-only LAN/ADB failures and proved the remaining authorization blocks are exactly the missing provider client id and client secret.
- Commands run:
  - `HOST=0.0.0.0 PORT=4000 ENABLE_DEBUG_AUTH=false PROVIDER_AUTH_PUBLIC_BASE_URL=http://192.168.1.167:4000 PROVIDER_REDIRECT_URI=http://192.168.1.167:4000/v1/auth/callback npm --workspace @soundcloud-private/api start`
  - `ANDROID_HOME=$HOME/Library/Android/sdk PATH=$ANDROID_HOME/platform-tools:$PATH PROVIDER_AUTH_PUBLIC_BASE_URL=http://192.168.1.167:4000 PROVIDER_REDIRECT_URI=http://192.168.1.167:4000/v1/auth/callback npm run preflight:firetv-provider-auth`
- What passed:
  - Backend URL is LAN-routable.
  - Backend health over LAN returned HTTP 200.
  - `PROVIDER_REDIRECT_URI` is set and aligned with `http://192.168.1.167:4000/v1/auth/callback`.
  - `PROVIDER_AUTH_PUBLIC_BASE_URL` is set and aligned with `http://192.168.1.167:4000`.
  - Mac route to Fire TV IP uses `en1`.
  - Fire TV ping replied from `192.168.1.168`.
  - Fire TV ADB TCP port `192.168.1.168:5555` accepted a connection.
  - ADB executable resolved to `/Users/neilpontecorvo/Library/Android/sdk/platform-tools/adb`.
  - ADB connect/device listing passed for `192.168.1.168:5555`.
- What failed / blocked:
  - `PROVIDER_CLIENT_ID` is missing or empty.
  - `PROVIDER_CLIENT_SECRET` is missing or empty.
- Exact next step:
  1. Restart the backend with the real `PROVIDER_CLIENT_ID` and `PROVIDER_CLIENT_SECRET` in addition to the already validated public base/callback env.
  2. Rerun the same preflight command with those two env vars present.
  3. Relaunch the TV app, use the fresh displayed code before the 10-minute TTL expires, and complete provider authorization in the browser.

### Entry 027
- Status: debug build/install/launch and local-debug authentication validation completed on physical Fire TV; one Mac self-LAN health-check anomaly remains documented below.
- Summary: Rebuilt the current working tree with an explicitly pinned Java 17 runtime and the configured Android SDK, reinstalled the resulting debug APK on the authorized Fire TV, launched `com.neilpontecorvo.soundcloudfiretv/.app.MainActivity`, recovered an unauthenticated session through the existing local debug flow, and verified that a force-stop/relaunch restores directly to the signed-in Home state.
- Start SHA: `7b9775e`.
- Device: `192.168.1.168:5555`, model `AFTKM` / product `karat`.
- Commands run:
  - `curl http://192.168.1.167:4000/health` with explicit connect/total timeouts.
  - `curl http://127.0.0.1:4000/health`.
  - `$HOME/Library/Android/sdk/platform-tools/adb connect 192.168.1.168:5555` and `adb devices -l`.
  - Java runtime discovery via `java -XshowSettings:properties -version`; qualifying build pinned to `/usr/local/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home`.
  - `./gradlew --no-daemon -Dorg.gradle.java.home=<JDK17_HOME> :app:assembleDebug -PapiBaseUrl=http://192.168.1.167:4000` with `ANDROID_HOME=$HOME/Library/Android/sdk`.
  - `adb install -r apps/firetv-client/app/build/outputs/apk/debug/app-debug.apk`.
  - `adb shell am start -n com.neilpontecorvo.soundcloudfiretv/.app.MainActivity`.
  - Deterministic D-pad selection of the existing `USE DEBUG FALLBACK` flow.
  - Focused log captures only: `adb logcat -d -v time -s MainActivity WebPlayerHostController HardenedWebViewClient PlayerBridge`.
  - Final `force-stop` + relaunch and UI-hierarchy verification.
- What passed:
  - Local API `/health` returned HTTP 200 with `{"service":"soundcloud-private-api","status":"ok",...}` on `127.0.0.1:4000`; the Node process was listening on `*:4000`.
  - ADB connected without an authorization prompt; `adb devices -l` listed `192.168.1.168:5555 device`.
  - The qualifying Gradle build used OpenJDK `17.0.18` for both the launcher and single-use daemon and ended `BUILD SUCCESSFUL`.
  - APK installation returned `Success`; installed artifact SHA-256 was `1b82c85ca73d239369edf3fb7779f3fa95961571ba9c5ffbddb308ecef5c49d3`.
  - MainActivity launched and was the resumed activity.
  - Initial launch showed the expected unauthenticated screen. The existing debug fallback first recovered bootstrap and obtained a live pairing/session state, then authenticated successfully on the second selection.
  - After debug authentication, the app reached `ANELO on SoundCloud` Home with the expected local-debug signed-in empty state, `No content available`.
  - A force-stop/relaunch returned directly to that signed-in Home state, proving Fire TV-to-backend connectivity and persisted-session restoration.
  - App-specific log captures contained no crash or error from the allowed tags. Player/WebView tags were quiet because playback was not opened in this scoped task.
- What did not pass / observation:
  - Requests from the Mac to its own LAN address `http://192.168.1.167:4000/health` completed the TCP handshake but timed out without HTTP response bytes. This differs from the localhost HTTP 200 result. It did not block the physical Fire TV: the app successfully bootstrapped, debug-authenticated, loaded signed-in Home, and restored its session through the same LAN API base URL.
- Source changes: none made in this entry. Existing uncommitted Android/API/config changes were present before the task and were preserved without modification.
- Commit: none created because this entry made no source changes; `WORKLOG.md` remains an intentional worklog-only update in the dirty working tree.

### Entry 028
- Status: corrective live-state verification completed after the user reported that the app was not running.
- Summary: The earlier `mResumedActivity` evidence was insufficient by itself. A fresh `am start -W` cold launch initially produced a black Fire TV framebuffer even though Android returned `Status: ok` and marked MainActivity resumed. After approximately 10–15 seconds, the actual framebuffer and UI hierarchy rendered the signed-in Home screen with `No content available`.
- Commands run:
  - `adb connect 192.168.1.168:5555` and `adb devices -l`.
  - `adb shell pidof com.neilpontecorvo.soundcloudfiretv`.
  - Focused activity/window and display-power checks.
  - `adb shell am force-stop com.neilpontecorvo.soundcloudfiretv`.
  - `adb shell am start -W -n com.neilpontecorvo.soundcloudfiretv/.app.MainActivity`.
  - Fire TV framebuffer captures plus `uiautomator` hierarchy verification.
  - App-specific logs only: `MainActivity`, `WebPlayerHostController`, `HardenedWebViewClient`, and `PlayerBridge`.
- What passed:
  - ADB remained authorized as `device`.
  - Cold launch returned `Status: ok`, `LaunchState: COLD`, and completed in 1.985 seconds at the activity-manager level.
  - The app process remained alive and MainActivity remained the resumed activity.
  - The display was awake and powered on.
  - After the startup delay, framebuffer and hierarchy both showed `ANELO on SoundCloud`, Home focused, and the expected local-debug empty state `No content available`.
  - No crash/error was emitted by the four allowed app tags.
- Corrected interpretation:
  - The app is currently running and visible, but a cold launch can present a transient black screen before content is rendered. The final `No content available` screen is the intentionally empty local-debug feed behavior from Entry 015, not evidence that the process failed to launch.
  - If the intended meaning of "not running" is that playable content is absent, that is a separate content/auth task: local-debug Home is intentionally empty, while real provider content still requires the provider-auth path.
- Source changes: none.
- Commit: none created because there were no source changes.

### Entry 029
- Status: completed; focused tests, required builds, and physical Fire TV playback validation passed.
- Summary: Restored the Entry 014 local-debug experience through the normal feed/library/search endpoints without removing the Entry 015 explicit debug routes or changing later provider-pairing, session-restoration, WebView hardening, launcher/banner, or transport implementations.
- Scoped changes:
  - `services/api/src/content/catalog-provider.ts`: authenticated local-debug sessions now receive `localDebugFeed()`, `localDebugLibrary()`, and `localDebugSearch()` from the normal content provider when the credentials service confirms local-debug mode is enabled.
  - `services/api/test/catalog-provider.test.ts`: added focused coverage for enabled local-debug fixtures, disabled persisted local-debug denial, and the unchanged real-provider adapter path.
  - `services/api/package.json`: added the API test command.
  - Kept `/v1/debug/content/feed`, `/v1/debug/content/library`, and `/v1/debug/content/search` unchanged and available only behind their existing debug/session guards.
- Security behavior verified:
  - `ProviderCredentialsService.isLocalDebugSession()` returns true only when local-debug credentials are enabled and the persisted token source is `local_debug`.
  - With debug credentials disabled, a persisted local-debug session cannot fall through to fixtures; normal feed/library/search calls reject it as `invalid_session`.
  - Real-provider sessions still obtain their provider access token and call the configured provider adapter.
- Automated validation:
  - `git diff --check` passed.
  - `npm --workspace @soundcloud-private/api test` passed all 3 tests.
  - `npm run check:api` passed.
  - `npm --workspace @soundcloud-private/api run build` passed.
  - Android `:app:assembleDebug -PapiBaseUrl=http://192.168.1.167:4000` passed with OpenJDK 17.0.18 and `$HOME/Library/Android/sdk`.
- Runtime setup:
  - Restarted the backend with `ENABLE_DEBUG_AUTH=true HOST=0.0.0.0 PORT=4000 npm --workspace @soundcloud-private/api start`.
  - Backend `/health` returned HTTP 200 on localhost.
  - ADB listed `192.168.1.168:5555 device` (AFTKM).
  - Reinstalled the rebuilt APK with `adb install -r`, cleared app data for a fresh auth pass, and launched MainActivity.
- Physical Fire TV results:
  1. Local debug authentication succeeded through the existing debug fallback; Settings showed an authenticated session with backend `http://192.168.1.167:4000`.
  2. Home rendered `Latest`, `Local Debug Track`, and `Local Debug Playlist`.
  3. Library rendered `Local Debug Session` with both fixtures.
  4. Search for `local` rendered both fixtures. The first device request reported one transient read timeout; the rebuilt API returned the correct search response directly, and the on-device retry succeeded.
  5. Selecting `Local Debug Track` opened Player and reached `widget_event_fired=ready`, `debug_play_invoked=true`, and `widget_event_fired=play`.
  6. Player reported `{"hasSound":true,"id":"293","title":"Flickermood","user":"Forss"}` and `widget_is_paused_after_debug_play=false`.
  7. Audible playback was confirmed by the user at the physical TV.
  8. Global MEDIA_PLAY_PAUSE produced Activity-level `Transport dispatch: command=toggle`, widget `pause`, then a second toggle and widget `play`.
  9. Force-stop/relaunch restored the authenticated session directly to populated Home with both debug fixtures.
- Logs: captured only `MainActivity`, `WebPlayerHostController`, `HardenedWebViewClient`, and `PlayerBridge`.
- Regressions observed: none in provider routing, pairing UI, LOGIN_REQUIRED, session restoration, hardened controlled WebView, launcher/banner, or global Play/Pause.
- Scope held: no Sites migration, production provider OAuth configuration, UI polish, Next/Previous, or FF/REW work was performed.

### Entry 030
- Status: implemented, built, installed, and OS-level behavior validated; timed user observation pending.
- Summary: Prevented Fire TV ambient/screensaver activation during active playback by tying `FLAG_KEEP_SCREEN_ON` to the existing native playback-state bridge.
- Scoped change:
  - `MainActivity.kt` requests `FLAG_KEEP_SCREEN_ON` only while Player reports active playback with no error.
  - The flag is cleared on pause and when the Player/WebView host is released; no wake-lock permission or WebView/playback architecture change was added.
- Automated/device validation:
  - `git diff --check` passed.
  - Android `:app:assembleDebug -PapiBaseUrl=http://192.168.1.167:4000` passed with Java 17 and `$HOME/Library/Android/sdk`.
  - Rebuilt APK installation returned `Success`; MainActivity cold-launched and restored the authenticated populated Home.
  - Selecting the focused provider track reached widget `ready` and `play`.
  - On widget `play`, MainActivity logged `keepScreenOn=true` and Fire OS reported `KEEP_SCREEN_ON` on the app window.
  - On MEDIA_PLAY_PAUSE, widget `pause` logged `keepScreenOn=false` and the app window no longer contained `KEEP_SCREEN_ON`; resuming playback restored both the widget `play` event and the window flag.
  - Fire TV settings reported `screen_off_timeout=900000` (15 minutes) and `sleep_timeout=1200000` (20 minutes).
- Logs: captured only `MainActivity`, `WebPlayerHostController`, `HardenedWebViewClient`, and `PlayerBridge`.
- Pending validation: user is leaving playback active beyond the prior screensaver interval to confirm the screen remains on. Commit is intentionally deferred until that physical observation is reported.
- Existing unrelated dirty-tree changes were preserved and are not part of this scoped fix.

### Entry 031
- Status: Phase 0 provider-configuration checkpoint completed; registered-callback dashboard comparison pending after Codex restart.
- Scope: Executed only the project-unique Phase 0 checks requested by the user. Existing debug-authentication, APK-build, and physical playback evidence was not redundantly repeated.
- Baseline: branch `main`, start commit `2a15c11f7c91362c1333892d4307d9312ed3e138`.
- Secret handling:
  - Used the existing ignored `.env.firetv.local` configuration without copying, modifying, printing, or committing any provider values.
  - No provider credential, access token, refresh token, or redirect URI value was written to this worklog.
- Runtime env verification:
  - `PROVIDER_CLIENT_ID`: defined.
  - `PROVIDER_CLIENT_SECRET`: defined.
  - `PROVIDER_REDIRECT_URI`: defined.
  - `npm run start:firetv-api` sourced the existing local env file, built the API, and delegated to `npm --workspace @soundcloud-private/api start`.
  - The workspace API process started successfully and its live `/v1/auth/start` path generated a provider authorization redirect, proving the running process received all three required provider variables.
- Provider callback verification:
  - SoundCloud's authorization endpoint returned HTTP 200 for the live authorization request and did not report an invalid client or redirect-URI mismatch.
  - A strict read-only comparison against the callback URI shown in the authenticated SoundCloud application dashboard is still pending because the available browser session was not authenticated and Codex must be restarted for full screen-recording permission.
- Validation:
  - API TypeScript build passed before launch.
  - Live API bootstrap returned the expected success status.
  - No APK/device/playback loop was rerun because the user explicitly limited this Phase 0 pass to checks that are new or unique to the project.
- Commit: deferred until the pending dashboard comparison is completed; pre-existing unrelated dirty-tree changes remain preserved.

### Entry 032
- Status: repository handoff review and validation completed; reviewed changes prepared for commit and push to `origin/main`.
- Repository: `https://github.com/neilpontecorvo/soundcloud.git`.
- Branch/baseline:
  - Branch: `main`.
  - Local start commit: `2a15c11f7c91362c1333892d4307d9312ed3e138`.
  - After `git fetch --prune origin`, divergence was zero origin-only commits and one local-only commit.
  - No unexpected remote commits or conflicts were present.
- Reviewed handoff scope:
  - Preserved the previously unpushed local-debug content restoration commit and its API tests.
  - Preserved the local Fire TV API launcher, ignored env template, and provider-auth preflight script.
  - Preserved provider artist-home resolution and grouped Top 5, Playlists, Albums, and Tracks feed sections.
  - Preserved Android feed-section parsing, artwork propagation/loading, TV card/focus layout updates, player artwork/waveform layout, and the playback-only screen-wake request.
  - Preserved the existing `AGENTS.md`, documentation, resources, and worklog updates after reviewing their diffs.
  - No WebView hardening, controlled-host restrictions, allowlists, SSL behavior, or token boundaries were weakened.
- Secret handling:
  - `.env`, `.env.*`, server-side token storage, and session storage paths are ignored.
  - The real local env file remained ignored and was not staged or committed.
  - No sensitive-looking filenames are tracked.
  - Targeted credential-pattern scanning and Gitleaks both passed across the complete push delta, including the prior local commit and the reviewed untracked files.
- Validation:
  - `git diff --check` passed.
  - `bash -n scripts/start-firetv-api.sh` passed.
  - `node --check scripts/firetv-provider-auth-preflight.mjs` passed.
  - `npm --workspace @soundcloud-private/api test` passed all 3 tests.
  - `npm run check:api` passed.
  - `npm --workspace @soundcloud-private/api run build` passed.
  - Android `:app:assembleDebug` passed with pinned OpenJDK 17.0.18 and the configured Android SDK.
  - The existing deprecated `saveFormData` warning remains; no new build failure was introduced.
- Pending runtime observations carried into the handoff:
  - The long-duration physical observation for playback screen-wake behavior remains pending from Entry 030, although flag behavior was already validated at the OS level.
  - The strict authenticated SoundCloud dashboard comparison for the registered callback remains pending from Entry 031; live authorization did not report a callback mismatch.
- Staging rule: only the explicitly reviewed project files from this checkpoint are intended for the handoff commit; ignored env and runtime state must remain excluded.

### Entry 033
- Status: real-provider authorization and physical Fire TV success path validated; strict dashboard-text callback comparison remains unverified.
- Scope held:
  - Continued only Entry 031's pending provider-auth validation.
  - Did not repeat debug-auth implementation, playback implementation, APK build/install, pairing implementation, styling, or feature work.
- Provider configuration and preflight:
  - Started the API with `npm run start:firetv-api`; the API TypeScript build passed, eight persisted sessions loaded, and the service listened on `0.0.0.0:4000` in development mode.
  - Ran `npm run preflight:firetv-provider-auth` with the existing ignored `.env.firetv.local` loaded into the process.
  - All preflight checks passed, including required provider env presence, LAN backend health/routing, local callback construction, Fire TV reachability, ADB connectivity, and device enumeration.
  - The callback URI value was redacted from captured command output and was not written to this worklog.
  - The authenticated SoundCloud developer dashboard value was not separately inspected. Browser control failed before page access, and the user explicitly reported that they had not performed a separate dashboard comparison.
  - SoundCloud authorization accepted the configured callback and completed it successfully, which operationally proves the callback was accepted for this application, but this is not recorded as a byte-for-byte dashboard inspection.
- Physical Fire TV state:
  - Relaunched the currently installed APK without rebuilding or reinstalling it.
  - The first relaunch restored the prior real-provider session directly to populated Home content, independently confirming that the previously persisted provider session was still valid at the start of this pass.
  - Cleared only the Fire TV app's local data to require a genuinely fresh pairing session; no APK, backend implementation, or provider configuration was modified.
  - Relaunched MainActivity and confirmed a fresh pairing URL and code are displayed with the normal automatic-continuation message.
- Phone handoff:
  - `adb devices -l` exposed only the Fire TV target; the Galaxy S26+ is not connected or authorized as an Android debug target, so the pairing URL could not be remotely opened on the phone from this environment.
  - The user opened the displayed pairing URL on the Galaxy S26+, completed SoundCloud authorization, and confirmed the provider callback completed operationally.
- Physical provider-auth results:
  1. Fire TV automatically left the pairing screen after callback completion and rendered authenticated Home without a manual poll or debug-auth fallback.
  2. Home loaded real provider artwork and populated Top 5, Playlists, Albums, and Tracks sections.
  3. Search for `anelo` returned 20 real provider tracks through the authenticated API and rendered multiple artwork-backed results on the Fire TV Search screen.
  4. Library loaded real Saved Tracks and Playlists content.
  5. Selecting a real provider track opened Player, loaded the SoundCloud widget, advanced playback time, and reported `PLAYING`.
  6. Global `KEYCODE_MEDIA_PLAY_PAUSE` dispatched the existing transport toggle, produced the widget `pause` event, changed the native Player state to `PAUSED`, and cleared the playback screen-wake request.
  7. A second global Play/Pause toggle produced the widget `play` event, returned the native state to `PLAYING`, and restored the playback screen-wake request.
  8. Force-stop and cold relaunch destroyed the active Player/WebView, restored the persisted authenticated session without re-pairing, and repopulated real provider Home.
- Security and scope:
  - No callback URI, provider credential, provider token, refresh token, pairing code, or session identifier was written to this worklog.
  - No `.env` file, APK, authentication implementation, pairing implementation, playback implementation, WebView hardening, controlled-host rule, or allowlist was changed.
- Remaining evidence gap: directly inspect the authenticated SoundCloud developer dashboard and compare its registered callback byte-for-byte with the ignored local env value without printing or recording either value.

### Entry 034
- Status: UI/UX redesign preflight and first implementation pass completed; physical-device visual/runtime iteration in progress.
- Task title: Implement the New SoundCloud Fire TV UI/UX End-to-End.
- Active agent: Codex (single coding agent).
- Approval mode: low-friction/auto-accept for in-scope repository edits and validation.
- Start time: 2026-07-17 02:55:24 EDT.
- Branch: `main`.
- Starting SHA: `da306562d9b2dcf2481548cb1871ec7b70f9476e`.
- Pre-existing uncommitted changes: none; `git status --short --branch` reported `main...origin/main` with a clean worktree before design assets were copied.
- Design references found and inspected:
  - `01 — Home(6).png`
  - `02 — Library(6).png`
  - `03 — Playlist detail(6).png`
  - `04 — Search(7).png`
  - `05 — Player + queue(9).png`
  - `SoundCloud Fire TV — UX Redesign(8).fig`
  - `SoundCloud Fire TV — UX Redesign(6).pdf`
  - All supplied references were copied without modification into `docs/design/firetv-ux-redesign/`; each PNG is confirmed as 1920 × 1080.
- Runtime display baseline: Fire TV reports physical size 3840 × 2160, override size 1920 × 1080, and physical density 320. ADB is authorized at `192.168.1.168:5555` after the on-device authorization prompt was accepted.
- Baseline automated validation:
  - `npm run check:api`: passed.
  - `npm --workspace @soundcloud-private/api run build`: passed.
  - `npm --workspace @soundcloud-private/api test`: passed, 3/3 tests.
  - `npm test`: not available at repository root (`Missing script: test`); the API workspace test command above is the repository's actual test suite.
  - `npm audit`: completed with exit 1 due to four pre-existing dependency advisories (one low, three moderate).
  - Android `./gradlew test`: passed; no Android unit-test sources existed at baseline.
  - Android `./gradlew lint`: failed at baseline with 5 errors and 35 warnings. The errors are the debug-manifest TV declarations, missing explicit optional touchscreen declaration, and an unguarded API-26 `WebView.getCurrentWebViewPackage()` call.
  - Android `:app:assembleDebug -PapiBaseUrl=http://192.168.1.167:4000`: passed using pinned OpenJDK 17.0.18.
- Exact task scope: replace/refactor the native Fire TV presentation layer for Home, Library, Playlist Detail, Search, Player, and the persistent mini-player to match the supplied 1920 × 1080 references; use real normalized provider data/artwork; preserve deterministic D-pad navigation, auth/session behavior, playback, launcher/banner, and hardened controlled-WebView rules; add the smallest required playlist/progress/transport data extensions; validate with automated checks and the final rebuilt/reinstalled APK on the physical Fire TV; capture comparison screenshots; update documentation; commit and push code plus this worklog together.
- Compaction checkpoint:
  - Implemented the centralized 1920 × 1080 reference-frame shell, fixed header/mini-player, exact six-column card grid geometry, fixed Search controls/results viewport, real Playlist Detail endpoint/table/queue, native Player waveform/description layout, high-resolution artwork normalization/cache, bounded widget progress reporting, and bounded absolute seek command.
  - API typecheck passes; API tests pass 4/4, including real-provider playlist normalization and debug-session isolation.
  - Android unit tests pass for scale, six-column focus mapping, Search normalization, queue bounds, seek clamping, and artwork fallback.
  - Android lint now passes after fixing the touchscreen declaration and prior unguarded API usage; the debug-overlay-only TV detector false positives are narrowly annotated at the overlay manifest root while the merged manifest retains the real launcher/Leanback declarations.
  - Android debug assembly passes with pinned OpenJDK 17.0.18 and LAN API base URL.
  - Live normalized provider validation confirms t500x500 artwork, waveform metadata, real playlist tracks, and a multi-item playlist response; no provider credential or token was printed or moved to Android.

### Entry 035
- Status: completed, committed, pushed, and remotely verified.
- Task title: Implement the New SoundCloud Fire TV UI/UX End-to-End.
- Active agent: Codex (single coding agent).
- Approval mode: low-friction/auto-accept for in-scope repository edits and validation.
- Start SHA: `da306562d9b2dcf2481548cb1871ec7b70f9476e`.
- End/implementation commit SHA: `5ed6e034c90b7065c24f76df7ad19965adf65be3`.
- Branch: `main`.
- Design references used: all five 1920 × 1080 PNG exports plus the supplied Figma and PDF under `docs/design/firetv-ux-redesign/`.
- Implementation summary:
  - Replaced the presentation layer with a centralized 1920 × 1080 reference-frame shell, fixed five-button header, page-specific nested scroll regions, real-artwork cards, and persistent bottom mini-player.
  - Home now renders My Feed, More from ANELO, ANELO Spotlight, and Recently Played from normalized provider data; unavailable personalized SoundCloud mixes fall back to more of the account owner's music.
  - Library now renders the exact five-item Spotlight selection and complete paginated Tracks, Playlists, and Albums rails. Each rail scrolls independently left/right and the main surface moves vertically without crossing the header ceiling.
  - Search preserves its empty pre-query state, fixed input/action controls, complete paginated results, and deterministic six-column grid navigation.
  - Added complete real collection detail loading and shared Playlist/Album Detail behavior: fixed artwork/waveform/transport column, independently scrolling track table, explicit high-contrast selected row, exact selected-track activation, active queue construction, and correct `PLAYLIST`/`ALBUM` labeling.
  - Player now shows real artwork, waveform/progress, scrollable provider description/tracklisting, synchronized mini-player state, visible ±10-second controls, queue Previous/Next, and focused-waveform D-pad scanning by one minute with held-key acceleration.
  - Added a narrow bounded seek bridge and progress callbacks without broadening the hardened WebView host, navigation allowlist, SSL policy, or JavaScript interface.
  - Added server-side full-length private-track stream resolution/proxying. Android receives only the backend session ID; provider OAuth is never serialized to Android and is removed before the request reaches SoundCloud media CDN hosts. Range requests are preserved.
  - Kept the display awake while the private app is in use; the final foreground app window reports `KEEP_SCREEN_ON`.
  - Replaced the Fire TV launcher icon/banner resources with the user-supplied 1280 × 720 PNG and changed the installed launcher label to `SOUNDCLOUD`.
- Exact task files changed/added:
  - `.gitignore`
  - `README.md`
  - `WORKLOG.md`
  - `apps/firetv-client/app/build.gradle.kts`
  - `apps/firetv-client/app/src/debug/AndroidManifest.xml`
  - `apps/firetv-client/app/src/main/AndroidManifest.xml`
  - `apps/firetv-client/app/src/main/java/com/neilpontecorvo/soundcloudfiretv/app/MainActivity.kt`
  - `apps/firetv-client/app/src/main/java/com/neilpontecorvo/soundcloudfiretv/content/ContentRepository.kt`
  - `apps/firetv-client/app/src/main/java/com/neilpontecorvo/soundcloudfiretv/core/navigation/AppScreen.kt`
  - `apps/firetv-client/app/src/main/java/com/neilpontecorvo/soundcloudfiretv/core/navigation/ScreenRenderer.kt`
  - `apps/firetv-client/app/src/main/java/com/neilpontecorvo/soundcloudfiretv/network/DeviceSessionApiClient.kt`
  - `apps/firetv-client/app/src/main/java/com/neilpontecorvo/soundcloudfiretv/ui/TvArtworkLoader.kt`
  - `apps/firetv-client/app/src/main/java/com/neilpontecorvo/soundcloudfiretv/ui/TvDesign.kt`
  - `apps/firetv-client/app/src/main/java/com/neilpontecorvo/soundcloudfiretv/ui/TvInteractionRules.kt`
  - `apps/firetv-client/app/src/main/java/com/neilpontecorvo/soundcloudfiretv/ui/TvWaveformView.kt`
  - `apps/firetv-client/app/src/main/java/com/neilpontecorvo/soundcloudfiretv/webview/PlayerBridge.kt`
  - `apps/firetv-client/app/src/main/java/com/neilpontecorvo/soundcloudfiretv/webview/WebPlayerHostController.kt`
  - `apps/firetv-client/app/src/main/res/drawable-nodpi/tv_banner.png`
  - `apps/firetv-client/app/src/main/res/drawable-xhdpi/tv_banner.png`
  - `apps/firetv-client/app/src/main/res/layout/activity_main.xml`
  - `apps/firetv-client/app/src/main/res/values/strings.xml`
  - `apps/firetv-client/app/src/test/java/com/neilpontecorvo/soundcloudfiretv/ui/TvInteractionRulesTest.kt`
  - `docs/architecture.md`
  - `docs/roadmap.md`
  - all seven supplied reference assets under `docs/design/firetv-ux-redesign/`
  - `packages/contracts/src/index.ts`
  - `services/api/src/content/catalog-provider.ts`
  - `services/api/src/content/track-playback-service.ts`
  - `services/api/src/provider/provider-config.ts`
  - `services/api/src/routes/content.ts`
  - `services/api/test/catalog-provider.test.ts`
  - `services/api/test/track-playback-service.test.ts`
- Backend/contract changes:
  - Added complete playlist-detail response and route, higher-quality artwork/waveform/private metadata, provider pagination helpers, deterministic four-row Home/Library assembly, and exact Spotlight handling.
  - Added private-track streaming route/service with authenticated provider stream discovery, SoundCloud delivery-host validation, redirect resolution, byte-range proxying, and private/no-store response policy.
  - Added tests for debug-session isolation, real-provider routing, Home composition, playlist normalization, pagination, private-stream credential stripping, and malicious redirect rejection.
- Final commands and results:
  - `npm run check:api`: passed.
  - `npm --workspace @soundcloud-private/api test`: passed 8/8 tests.
  - `npm --workspace @soundcloud-private/api run build`: passed.
  - `npm test`: expected exit 1 because the repository root has no `test` script; the actual API workspace suite above passed.
  - `npm audit --audit-level=low`: exit 1 for four pre-existing advisories (one low, three moderate); no dependency was added for this task.
  - `./gradlew test lint :app:assembleDebug -PapiBaseUrl=http://192.168.1.167:4000`: passed with pinned OpenJDK 17.0.18; only legacy fullscreen API deprecation warnings remain.
  - Final corrective `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`: passed after proper Album labeling was added.
  - `curl -fsS http://127.0.0.1:4000/health` and `curl -fsS http://192.168.1.167:4000/health`: both returned service status `ok`.
  - `adb install -r .../app-debug.apk`: returned `Success` for the final rebuilt APK.
  - APK badging: application label is `SOUNDCLOUD`; every declared icon density resolves to packaged `tv_banner.png`.
  - `git diff --check`: passed before handoff review.
- APK: `apps/firetv-client/app/build/outputs/apk/debug/app-debug.apk`.
- Fire TV: `192.168.1.168:5555`, model AFTKM, 1920 × 1080 override on a 3840 × 2160 physical display.
- Physical-device matrix result:
  - Cold launch/session restore, all header destinations, fixed mini-player, real artwork, Home/Library/Search loading, and header ceiling passed.
  - Home and Library required rails populate correctly; the user explicitly confirmed these pages and the ceiling behavior.
  - Complete results extend beyond the first viewport and independent rail movement works.
  - Playlist test loaded a real 173-track queue; focus visibly moved from row 1 to row 2, and Select opened/played the exact row-2 track.
  - Album test loaded a real two-track collection, displayed `ALBUM`, moved focus between rows, and opened the exact selected track through the shared queue behavior.
  - Queue media `NEXT` moved index 1 → 2 and `PREVIOUS` returned 2 → 1; widget ready/play events followed both transitions.
  - Media FF/REW dispatched functional bounded seek actions; Player/mini-player progress remained synchronized.
  - Player waveform focus/scanning and internal description scrolling were confirmed working by the user.
  - A private-track test prepared duration `210051` ms and rendered `3:30`, replacing the earlier 29-second preview response with the full authenticated stream.
  - Foreground window inspection reports `KEEP_SCREEN_ON`; no screensaver/sleep activation is expected while the app remains in use.
  - No crash, ANR, focus trap, top-level WebView navigation escape, credential exposure, or debug fixture leak was observed.
- Screenshot evidence (local and intentionally ignored from Git):
  - `artifacts/firetv-ui-validation/home-final.png`
  - `artifacts/firetv-ui-validation/library-nav.png`
  - `artifacts/firetv-ui-validation/search-empty.png`
  - `artifacts/firetv-ui-validation/search-results.png`
  - `artifacts/firetv-ui-validation/playlist-focus-strong-first.png`
  - `artifacts/firetv-ui-validation/playlist-focus-strong-second.png`
  - `artifacts/firetv-ui-validation/playlist-second-track-player.png`
  - `artifacts/firetv-ui-validation/album-detail-labeled-final.png`
  - `artifacts/firetv-ui-validation/private-track-full-length-playing.png`
  - `artifacts/firetv-ui-validation/final-transport-validation.png`
- Known limitations:
  - Fire OS still shows its cached prior launcher tile until the user restarts the Fire TV; the installed package metadata already resolves to the new icon/banner and `SOUNDCLOUD` label.
  - Legacy fullscreen flags compile and work on this Fire OS target but remain deprecated for a future platform-maintenance task.
  - The four npm audit advisories predate this task and require a separately scoped dependency upgrade/regression pass.
  - Appstore-only background/feature images are irrelevant to this private sideloaded app.
- Exact next step: the user can restart the Fire TV to refresh its launcher cache; no implementation work remains in this goal.
- Commit SHA and push verification:
  - Implementation commit: `5ed6e034c90b7065c24f76df7ad19965adf65be3` (`feat(firetv): implement 1920x1080 UX redesign`).
  - Push to `origin/main` succeeded (`da30656..5ed6e03`).
  - Post-push `git fetch --prune origin` succeeded.
  - Local `HEAD` and `origin/main` both resolved to `5ed6e034c90b7065c24f76df7ad19965adf65be3` with divergence `0 0`.
  - This exact handoff record is included in the immediate follow-up worklog-only commit.

### Entry 036
- Status: completed, validated on the physical Fire TV, committed.
- Task title: Private Track Playback Repair — restore full-length private-track playback against SoundCloud's current stream response.
- Active agent: Claude (single coding agent).
- Approval mode: low-friction/auto-accept for in-scope repository edits and validation.
- Start SHA: `56787c5aa82fdb32fe0615a24fe624648d018c62`.
- Branch: `main`.
- Reported symptom: Fire TV correctly identified the item as private, logged `PrivateTrackLoad: trackId=2379107141, backendProxy=true`, then failed with `PrivateTrackError: trackId=2379107141, what=1, extra=-2147483648`. `GET /v1/tracks/2379107141/stream` returned HTTP 502 `provider_upstream_error` / "Provider did not return an approved full-length HTTP stream."

#### Root cause
- `TrackPlaybackService` required `http_mp3_128_url` from `GET /tracks/{id}/streams`. SoundCloud no longer returns that field.
- Read-only inspection of the live authenticated response (no token or signed URL printed or logged) showed the current field set for private tracks `2379107141`, `2303298242` and `2381876697` is identical and contains only:
  - `hls_mp3_128_url` — `https` on `api.soundcloud.com`, query `[secret_token]`
  - `hls_aac_160_url` — `https` on `api.soundcloud.com`, query `[secret_token]`
  - `preview_mp3_128_url` — `https` on `api.soundcloud.com`, query `[secret_token]`
- The track object's `stream_url` now resolves to a `/preview` path only. `access=playable`, `sharing=private`.
- With no `http_mp3_128_url`, the selection check failed and every private track 502'd. The only full-length variants are HLS.

#### Stream-shape findings that drove the design (measured, not assumed)
- `hls_aac_160_url` → 302 → `playback.media-streaming.soundcloud.cloud` `playlist.m3u8`, `EXT-X-VERSION:7`, `EXT-X-MAP` init segment plus 25 `.m4s` fragments (fMP4, `styp`/`sidx`/`moof`/`mdat`).
- `hls_mp3_128_url` → 302 → `cf-hls-media.sndcdn.com` `playlist.m3u8`, `EXT-X-VERSION:6`, no init segment, 28 raw MPEG-1 Layer III segments.
- Both variants' parts report exact `Content-Length` via `HEAD` and support per-part `Range` (206), so an exact byte-offset map is buildable:
  - MP3: HEAD sum `3999868` == GET sum `3999868`; concatenation yields 9570 MPEG frames, **0 unparsed bytes**, decoded 249.99 s vs API duration 249920 ms.
  - AAC: HEAD sum `5049976` == GET sum `5049976`; concatenation parses cleanly to EOF.
- Both concatenations decode as valid full-length audio (`ffprobe`: mp3 249.920 s @128 kbps; aac 249.966 s @161 kbps).
- Manifest and part requests succeed **unauthenticated** — the delivery URLs are already signed — so no OAuth bearer is needed past `api.soundcloud.com`.

#### Implementation
- Added `services/api/src/content/hls-media-plan.ts`:
  - Quality-ordered variant ladder, preview fields explicitly excluded.
  - `parseMediaPlaylist` — rejects master playlists and encrypted playlists, resolves `EXT-X-MAP` plus every segment URI, and runs **each** resolved URI through the approved-media-host check (a playlist is untrusted input).
  - `toMediaParts` / `planExpiryMs` — byte-offset map from exact HEAD lengths; plan lifetime capped at a flat 5 minutes, and additionally bounded by the signed URL `expires` value minus a 60 s safety margin **when that parameter is present**. Note the MP3 delivery URLs carry only `Policy`/`Signature`/`Key-Pair-Id` and no `expires`, so on the default path only the 5 minute cap applies; the AAC `playback.media-streaming.soundcloud.cloud` URLs do carry `expires`.
  - `mapWithConcurrency` — part measurement is bounded to 12 in-flight HEAD requests while preserving playlist order.
  - `resolveByteRange` — full single-range semantics (`N-M`, `N-`, `-S`), returning undefined for unsatisfiable ranges.
  - `createConcatenatedStream` — streams only the overlapping parts, slices the boundary parts with their own byte range, prefetches one part deep, and never buffers the whole track.
- Rewrote `services/api/src/content/track-playback-service.ts` to resolve the HLS media playlist server-side and republish it to the Fire TV as **one progressive, range-addressable response**:
  - No `Range` → 200 with exact `Content-Length` + `Accept-Ranges: bytes`.
  - `Range` → 206 with exact `Content-Range`; range past EOF → 416 with `bytes */total`.
  - Warm plan cache keyed by session + track so a seek reuses the resolved playlist instead of re-resolving; a stale entry is dropped on the miss path rather than held for the life of the process.
  - Legacy `http_mp3_128_url` progressive path retained unchanged for compatibility.
- The Android client was **not modified**. `ensureNativePlayerHost` still opens `/v1/tracks/:id/stream` with only the opaque `X-Session-Id` and still receives seekable progressive audio.

#### Variant ordering decision (empirical)
- `hls_aac_160` is the higher-bitrate stream, so it was implemented first in the ladder and tested on the device.
- On the physical AFTKM the AAC fMP4 prepared but MediaPlayer read only the first fragment: `PrivateTrackPrepared: trackId=2380056603, durationMs=10008` for a 3:52 track, and the UI pinned at `0:10 / 0:10` and would not play through. Native MediaPlayer cannot derive total duration from the chained per-fragment `sidx` layout.
- The ladder was therefore reordered to `hls_mp3_128` → `hls_aac_160` → `http_mp3_128`: highest-quality **supported** variant first. The MP3 concatenation is a plain constant-bitrate MPEG stream, which the same MediaPlayer path handles correctly. AAC remains the fallback for tracks where SoundCloud omits the MP3 variant. The reason is recorded in a comment on `MEDIA_VARIANTS`.

#### Security model (preserved)
- `PROVIDER_CLIENT_SECRET` and access/refresh tokens remain server-side; the Fire TV still receives only its opaque backend session id.
- The OAuth bearer is sent **only** to `api.soundcloud.com`. Manifest, HEAD and part requests carry no `Authorization` header — asserted in tests.
- Approved-host validation is unchanged in scope (`*.sndcdn.com`, `*.soundcloud.cloud`) and is now additionally applied to the redirect target, every segment URI and the `EXT-X-MAP` URI.
- No signed URL, `secret_token`, `Policy`, `Signature`, `Key-Pair-Id` or bearer is echoed to the client — asserted in tests. `Cache-Control: private, no-store` retained.
- WebView allowlists, controlled-host rules, SSL policy and the JavaScript interface were not touched.

#### Files changed/added
- `services/api/src/content/hls-media-plan.ts` (added)
- `services/api/src/content/track-playback-service.ts`
- `services/api/test/track-playback-service.test.ts`
- `WORKLOG.md`

#### Tests added/updated
- Current HLS response republished as one progressive response with exact `Content-Length`, init segment leading.
- MP3/HLS preferred over AAC/HLS, with the device reason recorded; only the MP3 variant is resolved.
- AAC/HLS used as the fallback when the MP3 variant is absent.
- Range requests answered from the byte map: single-part slice, three-part spanning slice, `bytes=0-` → 206, suffix range, and only overlapping parts fetched.
- Range past EOF → 416; malformed range → `invalid_request`.
- Preview-only stream response rejected instead of played.
- Segment on an unapproved host rejected; `EXT-X-MAP` on an unapproved host rejected; redirect to an unapproved host rejected.
- Part without an exact length rejected rather than mis-declared.
- Credential stripping and signed-URL non-disclosure.
- A 400-segment playlist is measured with at most 12 concurrent HEAD requests, still in parallel and still in playlist order.
- Warm plan serves seeks without re-resolving.
- Legacy progressive proxy behaviour retained (pre-existing test kept).

#### Validation results
- `npm --workspace @soundcloud-private/api test`: passed 20/20.
- `npm run check:api`: passed.
- `npm --workspace @soundcloud-private/api run build`: passed.
- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PapiBaseUrl=http://192.168.1.167:4000` with pinned OpenJDK 17.0.18: BUILD SUCCESSFUL.
- `adb install -r .../app-debug.apk`: `Success`.
- Backend restarted via `~/Desktop/Start-SoundCloud-FireTV.command` with `.env.firetv.local`; `curl /health` returned `ok` on both `127.0.0.1:4000` and `192.168.1.167:4000`.
- Live endpoint checks against the running backend:
  - `2379107141`: 200, `audio/mpeg`, `content-length: 3999868`, body exactly 3999868 bytes, ffprobe 249.920 s (API 249920 ms).
  - `2380056603`: 200, `audio/mpeg`, `content-length: 3718163`, ffprobe 232.320 s (API 232320 ms).
  - `Range: bytes=0-` → 206 `bytes 0-5049975/5049976`; `Range: bytes=2000000-2000999` → 206, 1000 bytes, byte-identical to the same offset of the whole file; `Range: bytes=99999999-` → 416 `bytes */5049976`.
  - Response headers contained no `secret_token`, `Signature`, `Policy`, `Key-Pair-Id` or bearer.

#### Physical Fire TV results (192.168.1.168:5555, AFTKM, 1920 × 1080)
- Track `2379107141` ("Lets Werk (1)"): `PrivateTrackLoad` → `PrivateTrackPrepared: durationMs=249966`; Player showed `4:09` and played through. This is the exact track from the bug report; the previous failure was `PrivateTrackError what=1 extra=-2147483648`.
- Second private track `2380056600` ("ANELO - Touch Me (Afro House Edit)"): `PrivateTrackPrepared: durationMs=216999`; Player showed `3:36`, PLAYING, labelled `PRIVATE ACCOUNT TRACK`.
- Full duration: confirmed on both (249966 ms vs API 249920 ms; 216999 ms vs API 216960 ms).
- Play/Pause: PLAYING → PAUSED → PLAYING confirmed via media keys with the mini-player state synchronized.
- Seek: deterministic paused test moved 2:26 → 2:16 (REWIND, −10 s) → 2:26 (FAST_FORWARD, +10 s). Seeking during playback also advanced correctly and playback continued from the new position.
- Next/Previous: queue index moved 6 → 7 → 6 → 3 with the correct track loading at each step.
- Public track still plays: `NEXT` reached public track `2360118929` ("ANELO PONTECORVO LIVE JULY 10th 2026"), which loaded through the unchanged WebView widget path and played (`1:56:38` duration). No `PrivateTrackLoad` was emitted for it, confirming the public path is untouched.
- No `PrivateTrackError`, `PrivateTrackSetupFailed`, FATAL exception or ANR appeared in the device log during the session.
- The user independently confirmed on the device that private tracks play and that seek works.

#### Screenshot evidence (local, under gitignored `artifacts/`)
- `artifacts/private-track-repair/11-target-prepared.png`
- `artifacts/private-track-repair/12-playing.png`
- `artifacts/private-track-repair/13-seek-forward.png`
- `artifacts/private-track-repair/17-seek-paused.png`
- `artifacts/private-track-repair/18-next.png`
- `artifacts/private-track-repair/19-second-private.png`

#### Longest-track check
- The Library Tracks rail contains `2283620006` ("ANELO LIVE STUDIO MIX PART 1 - MARCH 2026"), duration 5997871 ms (~100 minutes), which resolves to roughly 660 segments. The first implementation measured every part in a single unbounded `Promise.all`, so that track opened hundreds of simultaneous CDN sockets and any one HEAD exceeding the 8 s request timeout would have 502'd the whole stream.
- Measurement is now bounded to 12 in-flight HEAD requests. Verified against the running backend: cold resolve returned HTTP 206 with time-to-first-byte 3.50 s, warm resolve 0.03 s, and a full read returned HTTP 200 with `content-length: 95966353` (~96 MB) matching the bytes received.

#### Known limitations / follow-ups
- Resolving a plan costs one `HEAD` per part (28 for a 4 minute track, ~660 for the 100 minute set), bounded to 12 concurrent. It happens once per session+track within the plan TTL.
- If a signed URL expires mid-body on an unusually long stream, the current behaviour is a failed part rather than a mid-stream re-resolve. The 5 minute plan TTL is bounded well inside the signature lifetime to avoid this.
- `hls_aac_160` is higher quality but cannot be used until the client can read fMP4 duration; moving to a player with proper fMP4 support (or server-side remuxing) would be a separately scoped task.
- The four pre-existing npm audit advisories still predate this task and remain out of scope.

#### Out of scope and untouched
- OAuth/pairing, session persistence, Home, Library, Search, public-track widget playback, launcher/banner, D-pad behaviour, UI layout and WebView hardening were not modified.
