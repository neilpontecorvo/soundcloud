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
- Fire TV OS/build version: add here when captured from device settings.

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
adb logcat -v time -s MainActivity WebPlayerHostController HardenedWebViewClient PlayerBridge
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
- Do not work on styling, docs, or new features while the Player success path is unresolved.
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
- Home and Library render selectable cards.
- Search has a focused input field.
- Nav/focus visibility is usable on the 70" 4K display.
- Hardened WebView / controlled host model exists.
- Session/auth is verified working on Fire TV.
- Selected track metadata now reaches Player.
- Player no longer globally hijacks all screens with `Connecting...`.

### Latest device checkpoint
- Settings confirms:
  - Backend: `http://192.168.1.167:4000`
  - Session status: `authenticated`
  - Authenticated: `true`
- Selecting `Local Debug Track` from Library reaches Player with selected metadata visible.
- Current Player failure is explicit and narrow:
  - first visible failure: `Player did not report ready within 15 seconds.`
  - Player then falls back to `IDLE`
  - selected track metadata remains visible
- This confirms:
  - backend is not the blocker
  - session/auth is not the blocker
  - card selection/handoff is not the blocker
- Remaining blocker is the controlled WebView/widget/bridge readiness path on physical Fire TV.

### Current blocker
- Player runtime on physical Fire TV still does not report `ready` after a selected playable target reaches Player.
- Logs now prove the handoff path is happening:
  - selected playable content logged
  - player load start logged
  - resolved controlled widget URL logged (`https://w.soundcloud.com/player/`)
  - synthetic `data://null` error is ignored as non-fatal
  - then a 15-second timeout occurs with no `ready` callback
- Current unresolved zone is now only one of:
  - widget/embed URL generation
  - required widget asset host availability/allowlist
  - widget JS init on Amazon WebView
  - `PlayerBridge` never receiving the first ready/play event

---

## 6. Completion Estimate
### Internal prototype
- Overall completion: **82%**

### Polished private-use app
- Overall completion: **65%**

### Module estimate
- Backend/session/auth/content: **90%**
- TV shell/nav/cards/focus: **80%**
- Player runtime on physical Fire TV: **45–50%**
- Final cleanup/polish: **35–45%**

### Estimated time remaining
- Stable working prototype if scope stays frozen: **3–6 focused hours**
- Additional polish and cleanup after that: **10–18 hours**

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
- Player now remains idle when opened without selection.
- Card selection now routes selected metadata into Player.
- Player timeout behavior now shows a precise native error instead of an indefinite generic loading state.

### Audit results
- `npm run check:api` passed.
- `npm --workspace @soundcloud-private/api run build` passed.
- `npm audit` passed with 0 vulnerabilities.
- `./gradlew :app:assembleDebug` passed.
- `./gradlew :app:lintDebug` failed on TV manifest issues only.

---

## 8. Remaining Tasks
### Critical path
1. Verify latest APK is installed on Fire TV before each runtime conclusion.
2. Disable VPN on Fire TV during validation.
3. Capture one clean app-only log trace for:
   - selecting `Local Debug Track`
   - Player opening
   - first bridge event OR timeout
4. Determine why the widget never reports ready on Fire TV:
   - verify final widget/embed URL shape
   - verify widget script/assets actually load on Amazon WebView
   - verify all required hosts are allowlisted
   - verify `PlayerBridge` callback wiring receives the first ready/play event
5. Fix only the Player runtime path until one real card-to-player success path works on the physical Fire TV.

### Secondary tasks
- Fix TV lint manifest issues:
  - `android.hardware.touchscreen` should be optional
  - clean debug manifest overlay TV lint behavior
- Remove deprecated `saveFormData`
- Clean hardcoded strings / unused resources / low-risk warnings
- Improve Search results experience
- Final player/native overlay polish

---

## 9. Known Issues / Side Notes
- Fire TV VPN may interfere with local LAN/backend/widget access.
- Installed APK may lag behind local repo unless explicitly rebuilt/reinstalled.
- Many prior log captures were Fire TV/Alexa/system noise, not app traces.
- Only these log tags matter for player debugging:
  - `MainActivity`
  - `WebPlayerHostController`
  - `HardenedWebViewClient`
  - `PlayerBridge`
- Current local backend should be started from repo root, not home directory.
- Keep `AGENTS.md` and `.claude/` status in mind if they remain uncommitted.

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
Prove one real end-to-end Player readiness path on device:

`Selected card -> Player -> first ready/play callback OR precise widget/bridge failure cause`

Do not work on unrelated styling, new features, or docs until that is proven.

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
- [fill in]

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
- [fill in]

### Exact next step
- [fill in]

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
- Status: in progress
- Summary: Player widget/bridge readiness on Fire TV remains the main blocker
- Result: unresolved; selected item reaches Player, but no `ready` callback arrives before the 15-second timeout

### Entry 009
- Status: instrumentation added; device validation pending
- Summary: Added three narrow diagnostics to make the silent Player-readiness failure visible on device, without weakening the hardened allowlist or changing UX.
  1. `HardenedWebViewClient.shouldInterceptRequest` — blocked subresources now log at `Log.w` with the blocked host, so allowlist-miss on any `sndcdn.com` subdomain used by the widget will appear under tag `HardenedWebViewClient` in the standard app-only logcat filter. Previously `Log.d` + missing host field made blocks invisible.
  2. `MainActivity` player WebView — attached a `WebChromeClient.onConsoleMessage` handler that forwards widget JS console output (including CSP violations, uncaught errors, `SC.Widget missing` path) to logcat under tag `MainActivity` as `WebConsole[LEVEL] ...`. Previously no console channel existed; widget-side failures were invisible.
  3. `WebPlayerHostController.buildControlledPlayerHtml` — injected two sentinel `console.log` lines ("inline player script started" and "bindWidget entered; SC=... SC.Widget=...") so we can distinguish (a) inline script never ran (CSP/parse issue), (b) script ran but widget API never loaded (allowlisted host unreachable / blocked), (c) script + API both loaded but READY never fired (widget init stalled).
- No allowlist change, no UI change, no bridge surface change, no backend change. Pure instrumentation.
- Build: `./gradlew :app:assembleDebug` PASSED.
- Pending (device): install APK, disable VPN on Fire TV, `adb logcat -c` then run app-only filter, select `Local Debug Track`, capture the 15-second window.
- Expected evidence from next device run (one of):
  - `HardenedWebViewClient` warns `Blocked subresource host='...sndcdn.com' ...` → allowlist is the root cause; next step is a scoped allowlist update (e.g., suffix match for `sndcdn.com`) reviewed against the "do not weaken" rule.
  - `WebConsole[ERROR] Refused to ...` CSP/mixed-content line → tighten or correct CSP `meta` in the controlled host HTML.
  - `fire-tv: inline player script started` absent → inline script never ran; investigate CSP `'unsafe-inline'` on Amazon WebView.
  - Sentinels + API both present but no READY → widget postMessage / iframe origin issue on Amazon WebView.
- Docs check: README.md / docs/architecture.md / docs/roadmap.md / services/api/README.md not edited this cycle — the user's scope constraint was "work only on the current blocker." Doc alignment review deferred until Player success path is green.

### Entry 010
- Status: document-lifecycle instrumentation added; device validation pending
- Summary: Entry 009 sentinels (WebConsole + inline console.log) did not appear in the on-device trace. Failure is pre-widget: it occurs before inline bootstrap execution. Added targeted instrumentation to expose the top-level document state instead of the widget state.
- Changes:
  - `MainActivity.snapshotPlayerDom(source)` — called from `onPageFinished`, uses `WebView.evaluateJavascript` to log `document.readyState`, `location.href`, `document.documentElement.outerHTML` length + first 500 chars, and presence of `window.SC`, `window.SC.Widget`, `window.NativePlayer`, `window.FireTvPlayerHost`. Tagged `MainActivity` as `DocSnapshot[onPageFinished] ...`.
  - `MainActivity.logWebViewEnvironment()` — logs `Build.MANUFACTURER`, `Build.MODEL`, SDK int, and the installed WebView implementation package+version via `WebView.getCurrentWebViewPackage()`. Runs once per session before Player load.
  - `MainActivity` — logs `Post-load webView.url snapshot` immediately after `loadPlayer` returns, and explicitly logs `Player load method: loadDataWithBaseURL(baseUrl=...)` so the load pathway cannot be misread from logs alone.
  - `HardenedWebViewClient.onPageStarted` / `onPageFinished` — escalated from `Log.d` to `Log.i` so page lifecycle is unmistakable in the app-only trace.
  - `PlayerBridge.reportBootstrap(stage)` — new native-side JS interface method (early beacon, runs before SC.Widget access). Logged at `Log.i` on `PlayerBridge`. The inline HTML now calls `reportBootstrap('pre-api-inline')` before the external widget API script, `reportBootstrap('widget-api-onload')` / `reportBootstrap('widget-api-onerror')` on the api.js script tag, and `reportBootstrap('post-api-inline')` after. This separates "inline HTML executed" from "external widget API loaded" from "bindWidget reached."
  - Deduplicated the previously duplicate `<script src="w.soundcloud.com/player/api.js">` tag — now single-sourced with onload/onerror handlers.
  - `PlayerBridge.reportLoadingState` escalated `Log.d` -> `Log.i` so the first JS->native hop is visible.
- No allowlist change, no UI change, no backend change, no bridge surface widening (beyond one new informational method).
- Build: `./gradlew :app:assembleDebug` PASSED (14s incremental).
- Pending (device): install APK, disable VPN on Fire TV, `adb logcat -c`, filter to `MainActivity WebPlayerHostController HardenedWebViewClient PlayerBridge`, select `Local Debug Track`, capture trace.
- Expected decision tree from the next device trace:
  1. `Page finished` + `DocSnapshot outerHTML=` shows the full native-owned HTML we built → top-level document is what we expected; failure is inside inline script / CSP. Fix: strip or relax CSP meta.
  2. `Page finished` + `DocSnapshot outerHTML=` shows a redirected SoundCloud page (login, 404, generic page) → `loadDataWithBaseURL` is not behaving as intended on Amazon WebView under this baseUrl, or the baseUrl is being treated as the top-level URL. Fix: change the baseUrl to a non-navigable synthetic scheme or use `about:blank` as baseUrl.
  3. `Page finished` never fires → WebView stalled on a subresource or SSL error. Fix via `onLoadError` / SSL surface.
  4. `DocSnapshot globals=` shows `hasNative:false` → bridge never attached on this document; `addJavascriptInterface` timing issue relative to `loadDataWithBaseURL`.
  5. `reportBootstrap('pre-api-inline')` fires but no `widget-api-onload` → external `w.soundcloud.com/player/api.js` is being fetched and either blocked by `shouldInterceptRequest` (look for blocked-subresource warn) or returning non-script content.
