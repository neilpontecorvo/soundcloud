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
1. **Fast Forward / Rewind unsupported by design**
   - FF/REW currently do nothing meaningful.
   - Current implementation intentionally treats them as unsupported no-ops because no reliable seek/jump bridge contract exists yet.
   - This is expected behavior, not a regression.

2. **Next / Previous should be kept under runtime validation**
   - Command path exists.
   - End-to-end behavior should still be confirmed across more than one playable item / queue state.

3. **Cleanup / polish remains**
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
Treat playback, Play/Pause transport, launcher visibility, and banner as solved. Move forward with validation/polish tasks only:
- confirm Next/Previous behavior under real queue conditions
- decide future FF/REW behavior
- clean lint/deprecations/resources

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
