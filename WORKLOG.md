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
1. **Real provider OAuth/device-pairing is paused**
   - Backend pairing endpoints and Fire TV LOGIN_REQUIRED primary sign-in UI are implemented.
   - Repo now has a one-command provider-auth preflight: `npm run preflight:firetv-provider-auth`.
   - API typecheck/build, Android build, and local pairing-route smoke checks pass.
   - Latest preflight reaches the Fire TV over LAN/TCP and ADB is authorized: `adb devices` lists `192.168.1.168:5555	device`.
   - Backend `/health` is reachable on `http://192.168.1.167:4000`, and the rebuilt APK launches to the provider pairing screen on the Fire TV.
   - A real provider sign-in still needs provider credentials configured in the running backend process and an on-device pairing/callback validation pass.

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
Keep Sites migration and real-provider OAuth configuration paused. The local-debug Fire TV experience is the active verified path:
- run the API with `ENABLE_DEBUG_AUTH=true HOST=0.0.0.0 PORT=4000`
- local-debug Home, Library, and Search return their fixtures through the normal content endpoints
- selecting `Local Debug Track` plays Flickermood by Forss
- session restoration returns to populated Home

Do not begin Sites migration, production OAuth configuration, UI polish, Next/Previous, or FF/REW work without a new scoped task.

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
