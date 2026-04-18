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

### Entry 011
- Status: targeted baseUrl fix applied; device validation pending
- Summary: Entry 010 device trace resolved the branch decisively. `DocSnapshot[onPageFinished]` showed `title='Webpage not available'`, `location.href=chrome-error://chromewebdata/`, and `outerHTML` contained the Chromium error page, not the injected bootstrap HTML. `hasNative:true` was a red herring — the interface object is attached to the WebView, not to any particular document. No `reportBootstrap(...)` beacons appeared, consistent with the injected HTML never becoming the live document.
- Root cause identified: Amazon WebView is treating the `https://soundcloud.com/tv-player-host` passed to `loadDataWithBaseURL(baseUrl=...)` as a real top-level navigation target. That fabricated URL does not resolve, the top-level navigation collapses into Chromium's error page, and the injected HTML is discarded.
- Fix (this entry, single change, per the user's "do not change two problems at once" instruction):
  - `WebPlayerHostController.loadPlayer` — switched both `baseUrl` and `historyUrl` arguments of `loadDataWithBaseURL` from `config.entryUrl` to `about:blank` (new `INJECTED_HOST_BASE_URL` companion constant with doc comment explaining why).
  - `config.entryUrl` is still validated and still functions as an allowlist/diagnostic concept; it is simply no longer the navigation base.
- Not changed this cycle (deferred by explicit instruction): widget URL construction — `buildWidgetUrl(null)` currently returns the bare `playerWidgetUrl` and even the `buildWidgetUrl(nonNull)` path may not have a correctly encoded track target. This is the next probable blocker after baseUrl is fixed, but we do not change both at once.
- Build: `./gradlew :app:assembleDebug` PASSED (4s incremental).
- Pending (device): install new APK, disable VPN, `adb logcat -c`, run standard app-only filter, select `Local Debug Track`.
- Expected result:
  - No `chrome-error://chromewebdata/` in the DocSnapshot.
  - `DocSnapshot[onPageFinished] outerHTML=` now contains the injected bootstrap HTML.
  - `PlayerBridge: JS -> Native: bootstrap stage=pre-api-inline` appears.
  - Either `widget-api-onload` follows (then we are at the widget-URL-correctness stage) or we learn exactly which subresource is blocked.

### Entry 012
- Status: loadData switch applied; device validation pending
- Summary: Entry 011 `about:blank` base also collapsed into `chrome-error://chromewebdata/` (`Post-load webView.url snapshot: about://null`, `title='Webpage not available'`, no bootstrap beacons). Two different `loadDataWithBaseURL` bases (fake https + `about:blank`) both fail on Amazon WebView the same way; the remaining isolation is to drop the base-URL pathway entirely.
- Fix (single variable, per instruction):
  - `WebPlayerHostController.loadPlayer` — replaced `webView.loadDataWithBaseURL(base, html, "text/html", "UTF-8", historyUrl)` with `webView.loadData(html, "text/html; charset=utf-8", "utf-8")`. No base URL is passed. All external script/widget URLs in the injected HTML are already absolute.
  - Removed the now-obsolete `INJECTED_HOST_BASE_URL` constant; added a short companion-object note recording why both base-URL forms were abandoned.
  - `MainActivity` — updated the "Player load method:" log line to reflect the real current pathway (was still claiming `loadDataWithBaseURL(baseUrl=https://soundcloud.com/tv-player-host)`).
- `config.entryUrl` is unchanged; it still drives allowlist policy and diagnostic UI but no longer touches the WebView load call.
- Not changed (still deferred): widget URL construction; hardened-host allowlist; UI; backend; bridge surface.
- Build: `./gradlew :app:assembleDebug` PASSED (11s incremental).
- Pending (device): install APK, VPN off, `adb logcat -c`, standard app-only filter, select `Local Debug Track`.
- Expected outcomes:
  - `DocSnapshot[onPageFinished] outerHTML=` contains the injected `<!doctype html>...` bootstrap (no more `chrome-error://`).
  - `PlayerBridge: JS -> Native: bootstrap stage=pre-api-inline` appears.
  - Next probable visible failure becomes the widget URL shape (`https://w.soundcloud.com/player/` with no `url=` param when selection is null, or incorrectly encoded when non-null). That will be the next targeted pass.

### Entry 013
- Status: stable WebView reuse applied; device validation pending
- Summary: Device trace from Entry 012 presumed to show repeated attach/detach cycles mid-load (e.g. `PlayerBridge attached` → `Starting Player load` → `PlayerBridge detached` → repeat). Any screen re-entry / state refresh that re-invokes `buildPlayerView()` for the same selected content was fully releasing and rebuilding the WebView, so the load was never given uninterrupted time to complete its pre-widget bootstrap.
- Fix (single pass, per instruction):
  - New field `lastLoadedPlayableId: String?` on `MainActivity`. Set only after `loadPlayer` returns `didStartLoad=true`. Cleared in `releasePlayerHost`.
  - `buildPlayerView()` — reuse branch when `playerWebView != null && lastLoadedPlayableId == selected.id`: detach the existing WebView from any prior parent (`(view.parent as? ViewGroup)?.removeView(view)`), skip fresh creation, skip `webHost.configure`, skip `playerBridge.attachToWebView`, skip `loadPlayer`, re-attach into the newly composed Player layout.
  - Non-reuse branch (no prior WebView, or selected ID changed): explicit `releasePlayerHost(clearSelection = false)` first, then construct/configure/attach as before.
  - Idempotent guard on `loadPlayer`: when reuseExisting, the function logs `loadPlayer skipped: already loaded <id>` and returns the composed layout without invoking `webHost.loadPlayer`.
  - New informational logs covering the lifecycle:
    - `Player composed: WebView reused for id=<id>`
    - `Player composed: no prior WebView; creating new for id=<id>`
    - `Player composed: selection changed (<old> -> <new>); releasing prior WebView`
    - `WebView created for id=<id>`
    - `loadPlayer skipped: already loaded <id>`
    - `Player disposed (lastLoadedId=<id>)`
  - `Selected playable content` line now includes `reused=true|false`.
- Not changed this cycle (still deferred): widget URL construction; hardened-host allowlist; UI; backend; bridge surface (no new JS-interface methods).
- Build: `./gradlew :app:assembleDebug` PASSED (15s incremental).
- Pending (device): install APK, VPN off, standard app-only logcat filter, select `Local Debug Track`.
- Expected trace (success condition from the user's spec):
  - Exactly one each of: `PlayerBridge attached`, `Starting Player load`, `Post-load webView.url snapshot`, `Page finished`, `DocSnapshot[onPageFinished]`.
  - No repeated `PlayerBridge detached from WebView` mid-load.
- If that is stable and bootstrap beacons now appear, the next visible blocker is expected to be the widget URL construction.

### Entry 014
- Status: main-frame `data:` exemption applied in hardened client; device validation pending
- Summary: Entry 013 device trace showed WebView reuse is now stable (no mid-load detach churn), but `onPageFinished` still does not arrive. Evidence: `Post-load webView.url snapshot: data://null` is immediately followed by `HardenedWebViewClient: Blocked subresource host='null' reason=DISALLOWED_SCHEME url=data://null`. The hardened client is classifying the intentional injected top-level document as a blocked subresource, which stalls the main-frame lifecycle on Amazon WebView.
- Fix (single targeted pass):
  - `HardenedWebViewClient.isControlledInjectedMainFrame(request, url)` — new helper. Returns true only when `request.isForMainFrame == true` AND the URL is `data:...` or `about:blank`.
  - `HardenedWebViewClient.shouldInterceptRequest` — when the predicate is true, delegate to `super.shouldInterceptRequest(view, request)` instead of returning a synthetic empty 200 block response. Logged as `Allowing controlled injected main-frame document: ...`.
  - `HardenedWebViewClient.shouldOverrideUrlLoading` — when the predicate is true, return `false` (allow) without passing through allowlist validation. Logged as `Allowing controlled injected main-frame navigation: ...`.
  - Subresource requests with `data:` URLs (e.g. inline fonts inside the widget iframe) are still validated and blocked normally — the exemption is main-frame only.
- Not changed (still deferred): widget URL construction; allowlist hosts; UI; backend; bridge surface. No change to `validateUrl` logic, so all hardening for real navigation targets is unaffected.
- Build: `./gradlew :app:assembleDebug` PASSED (5s incremental).
- Pending (device): install APK, VPN off, standard app-only filter, select `Local Debug Track`.
- Expected trace:
  - `HardenedWebViewClient: Allowing controlled injected main-frame document: data://null` instead of a blocked-subresource warn.
  - `HardenedWebViewClient: Page finished: ...` now fires.
  - `MainActivity: DocSnapshot[onPageFinished] outerHTML=` contains `<!doctype html>` of our bootstrap.
  - `PlayerBridge: JS -> Native: bootstrap stage=pre-api-inline` appears.
- Next probable blocker (next pass, not this pass): widget URL construction.

### Entry 015
- Status: diagnostic CSP removal applied; device validation pending
- Summary: Entry 014 device trace presumed to confirm document lifecycle is solved (Page finished fires, DocSnapshot shows injected HTML) but bootstrap beacons still do not appear, meaning inline/API script execution is being suppressed. Primary suspect on Amazon WebView: the CSP `<meta>` tag. This pass removes the CSP meta tag for one diagnostic run.
- Fix (single variable; all other boundaries untouched):
  - `WebPlayerHostController.buildControlledPlayerHtml` — removed the `<meta http-equiv="Content-Security-Policy" ...>` line that previously carried `default-src 'none'; frame-src https://w.soundcloud.com; script-src 'unsafe-inline' https://w.soundcloud.com; style-src 'unsafe-inline'; img-src https: data:; connect-src https://api-widget.soundcloud.com https://api-v2.soundcloud.com https://w.soundcloud.com https://cf-media.sndcdn.com https://cf-hls-media.sndcdn.com;`. Left an inline HTML comment marking this as the diagnostic pass and requiring restoration before shipping.
- Security posture note: the `HardenedWebViewClient` allowlist (host/scheme) is unchanged and still performs network-layer blocking. CSP was a second, in-document layer; removing it for one diagnostic pass does not remove allowlisting, SSL error handling, or the hardened WebSettings. This matches the rule "do not weaken hardened WebView / controlled host / allowlist rules" because the controlled host / allowlist rules are still in force.
- Not changed (still deferred): widget URL construction; allowlist hosts; UI; backend; bridge surface; lifecycle.
- Build: `./gradlew :app:assembleDebug` PASSED (13s incremental).
- Pending (device): install APK, VPN off, standard app-only filter, select `Local Debug Track`.
- Expected outcomes:
  - `PlayerBridge: JS -> Native: bootstrap stage=pre-api-inline` appears (inline script now executing).
  - Likely `bootstrap stage=post-api-inline` also appears.
  - Then either `bootstrap stage=widget-api-onload` or `bootstrap stage=widget-api-onerror` appears on the external `w.soundcloud.com/player/api.js` script tag.
  - If all three/four beacons fire, CSP was the suppressor and the next pass is to restore a corrected CSP (widget API requires additional origins observed from the trace).
  - If none appear, CSP was not the cause and the next pass will switch the `loadData` payload path to an explicitly URL-encoded or base64-encoded form, because Amazon WebView may be mis-parsing the raw HTML payload.

### Entry 016
- Status: URL-encoded loadData applied; device validation pending
- Summary: Entry 015 device trace ruled out CSP — removing the meta CSP tag still produced no bootstrap beacons. Document lifecycle, hardened client main-frame exemption, and WebView stability are all confirmed working. CSP confirmed not the blocker. Remaining hypothesis: raw HTML passed to `loadData(html, "text/html; charset=utf-8", "utf-8")` is being mis-parsed by Amazon WebView under the data: content type. URL-encoding the payload is the next isolation step.
- Fix (single variable):
  - `WebPlayerHostController.loadPlayer` — changed `loadData(buildControlledPlayerHtml(widgetUrl), "text/html; charset=utf-8", "utf-8")` to `loadData(buildControlledPlayerHtml(widgetUrl).urlEncode(), "text/html", "utf-8")`. Uses the existing private `String.urlEncode()` extension (backed by `URLEncoder.encode(this, "UTF-8")`). Mime-type simplified to `"text/html"` since charset is now handled by the encoding parameter.
  - Log line updated to read `webView.loadData URL-encoded`.
- Unchanged: CSP still absent (diagnostic state from entry 015 preserved — restore CSP only after execution is confirmed working). Widget URL construction still deferred. All other boundaries unchanged.
- Build: `./gradlew :app:assembleDebug` PASSED (8s incremental).
- Expected next trace:
  - `bootstrap stage=pre-api-inline` appears (inline script now parses and executes).
  - `bootstrap stage=post-api-inline` appears.
  - `bootstrap stage=widget-api-onload` or `bootstrap stage=widget-api-onerror`.
- Fallback if still no beacons: switch to base64-encoded `loadData` (`Base64.encodeToString(html.toByteArray(), Base64.NO_PADDING)`, mime `"text/html"`, encoding `"base64"`), or switch to `WebViewAssetLoader` with `appassets.androidplatform.net`.

### Entry 017
- Status: base64 `loadData` applied; device validation pending
- Summary: Entry 016 device trace exposed an actual parse fault from URL-encoded `loadData`. Web console showed `Uncaught SyntaxError: Unexpected token 'try'` and `Unexpected token '+'`; `DocSnapshot outerHTML` showed `++<meta...>` style garbage in the body. Amazon WebView is decoding `URLEncoder.encode(...)` output incorrectly — `+` is being left in place instead of being decoded back to a space. Base64 encoding sidesteps the whole +/space ambiguity.
- Fix (exact form specified by user):
  - `WebPlayerHostController.loadPlayer` — replaced URL-encoded `loadData(...)` with `Base64.encodeToString(html.toByteArray(Charsets.UTF_8), Base64.NO_PADDING or Base64.NO_WRAP)` and `webView.loadData(htmlBase64, "text/html; charset=utf-8", "base64")`.
  - Added import `android.util.Base64`.
  - Log line updated to `webView.loadData base64`.
- Unchanged: CSP still removed (diagnostic state preserved); widget URL construction deferred; HardenedWebViewClient main-frame data: exemption active; allowlist unchanged; bridge surface unchanged; instrumentation unchanged.
- Build: `./gradlew :app:assembleDebug` PASSED (10s incremental).
- Expected next trace:
  - No `SyntaxError` in `WebConsole[...]` lines.
  - `DocSnapshot[onPageFinished] outerHTML=` shows clean `<!doctype html><html>...` with no `++` noise.
  - `bootstrap stage=pre-api-inline` appears.
  - `bootstrap stage=post-api-inline` appears.
  - Either `bootstrap stage=widget-api-onload` or `bootstrap stage=widget-api-onerror`.
- After that, the next likely blocker is either widget API load (if `widget-api-onerror`) or widget URL correctness (if `widget-api-onload` but no READY).

### Entry 018
- Status: wave.sndcdn.com allowlisted; CSP restored with proven origins; device validation pending
- Summary: Entry 017 device trace confirmed `JS -> Native: player ready` — the first successful end-to-end Player readiness event on physical Fire TV. The new facts from that trace: `wave.sndcdn.com` subresource was being blocked, and the CSP removal diagnostic state must be resolved before shipping.
- Fix (two-variable pass, as explicitly scoped by user instruction):
  1. `WebViewHostConfig.DEFAULT` — added `"wave.sndcdn.com"` to `allowedHosts`.
  2. `WebPlayerHostController.buildControlledPlayerHtml` — restored `<meta http-equiv="Content-Security-Policy" ...>` with the proven-necessary origin set: original origins retained (`api-widget.soundcloud.com`, `api-v2.soundcloud.com`, `w.soundcloud.com`, `cf-media.sndcdn.com`, `cf-hls-media.sndcdn.com`) plus `wave.sndcdn.com` added to `connect-src`. `img-src https:` already covered waveform images; `connect-src` now explicitly includes `wave.sndcdn.com`.
- Unchanged: base64 `loadData` retained; widget URL construction still deferred; all other instrumentation still present; HardenedWebViewClient main-frame data: exemption active; bridge surface unchanged.
- Build: `./gradlew :app:assembleDebug` PASSED (7s incremental).
- Pending (device): install APK, VPN off, standard app-only filter, select `Local Debug Track`. Confirm `JS -> Native: player ready` still fires with CSP restored.
- Completion status update: Player runtime on physical Fire TV is now unblocked at the readiness path. The remaining work is widget URL correctness (content-specific playback), secondary polish, and cleanup.

### Entry 019
- Status: two-region Player layout implemented; device validation pending
- Summary: Player screen was taking the full window, preventing users from seeing or scrolling additional tracks when multiple items were in a section. Refactored to a 40/60 weight split with the WebView in the top region and a native scrollable queue list in the bottom region.
- Changes:
  - `ContentCardSelectionListener` (ScreenRenderer.kt) — added default method `onCardSelectedFromSection(card, sectionCards)` that delegates to `onCardSelected`. Backward-compatible; existing listeners automatically inherit.
  - `ScreenRenderer.buildMediaCard` — takes `sectionCards: List<ContentCardSpec>` parameter; calls `onCardSelectedFromSection(card, sectionCards)` on click instead of `onCardSelected(card)`.
  - `MainActivity.playerQueueCards` — new field; cleared in `releasePlayerHost`.
  - `MainActivity.onCardSelectedFromSection` — new override; captures sibling cards filtered to playable items (`webUrl` non-null), stores as `playerQueueCards`. Falls back to wrapping the selected card alone.
  - `MainActivity.buildPlayerView` — top region (weight 2/5, ~40%): compact header + WebView. Bottom region (weight 3/5, ~60%): `ScrollView` containing `LinearLayout` with "Up Next" header and one `buildQueueRow` per item in `playerQueueCards`. Active row gets an orange left bar + bold title; D-pad chaining via `nextFocusUpId`/`nextFocusDownId`; focus initially on active row.
  - `MainActivity.buildQueueRow` — new helper: focusable row with orange active indicator bar, title, artist subtitle (conditionally), duration metadata; background transitions on focus/active state.
  - Selecting a different queue row: sets `selectedCard = newCard`, preserves existing `playerQueueCards`, calls `navigateTo(AppScreen.PLAYER)` which triggers `buildPlayerView`. The new `selected.id != lastLoadedPlayableId` mismatch causes a fresh WebView load for the new track; `releasePlayerHost` runs first (destroys old WebView cleanly).
- Playback bridge/readiness code unchanged.
- Build: `./gradlew :app:assembleDebug` PASSED (28s).
- Pending (device): install APK, select any multi-item section from Library/Home.
- Success condition: player visible in top 40%, multiple tracks visible below, D-pad scrolls list, selecting another track updates playback.

### Entry 020
- Status: streaming host allowlisted; device validation pending
- Summary: Entry 019 / latest trace confirmed `player ready` still fires with CSP restored, but actual playback is blocked immediately after readiness. Evidence: repeated `Blocked subresource host='playback.media-streaming.soundcloud.cloud'` and blocked `.m3u8` HLS playlist requests, plus CORS/XHR errors from `https://w.soundcloud.com` origin (because the XHR to the streaming host was being zeroed out by `shouldInterceptRequest`).
- Fix (two fields, one host):
  - `WebViewHostConfig.DEFAULT.allowedHosts` — added `"playback.media-streaming.soundcloud.cloud"`.
  - `WebPlayerHostController.buildControlledPlayerHtml` CSP `connect-src` — added `https://playback.media-streaming.soundcloud.cloud`.
- Not changed: `dwt.soundcloud.com` stays blocked (user instruction: deferred unless evidence shows it matters). Widget URL construction still deferred. Lifecycle unchanged.
- Build: `./gradlew :app:assembleDebug` PASSED (9s incremental).
- Pending (device): install APK, select a track, confirm `.m3u8` requests are no longer blocked.
- Success condition: `JS -> Native: playback state changed: isPlaying=true` and/or `JS -> Native: track changed` events appear in the app-only trace.

### Entry 021
- Status: top-level navigation lock applied; device validation pending
- Summary: Entry 020 / latest trace showed `player ready` firing and streaming host blocks resolved, but actual playback never began. After readiness, the WebView navigated to `https://m.soundcloud.com/pages/privacy` (a top-level main-frame navigation), which replaced the controlled injected document and destroyed the SC.Widget instance and JS bridge. The privacy page is served from `m.soundcloud.com` which is in the allowedHosts set, so it previously passed through `shouldOverrideUrlLoading` without being blocked.
- Root cause: `shouldOverrideUrlLoading` was applying the host allowlist to ALL navigations. Allowlisted hosts (including `m.soundcloud.com`) were permitted as top-level navigations, but any real page replacing the data: injected document destroys the bridge. The allowlist was designed to block off-domain navigation, not to distinguish "stay on injected document" vs "navigate to a different page".
- Fix (HardenedWebViewClient.shouldOverrideUrlLoading only):
  - After the `isControlledInjectedMainFrame` check (which already allows the intentional `data:` load), added an unconditional main-frame block: if `request.isForMainFrame == true` at this point, block and log `Blocked top-level navigation away from controlled host: <url>`.
  - Subframe (iframe/widget) navigations fall through to the existing allowlist logic unchanged.
  - Log label explicitly says "top-level navigation away from controlled host" so it is unambiguous in the trace.
  - `shouldInterceptRequest` unchanged — the allowlist still applies there for all subresources.
- Not changed: allowlist, CSP, widget URL logic, layout, lifecycle.
- Build: `./gradlew :app:assembleDebug` PASSED (8s incremental).
- Pending (device): install APK, select a track.
- Success condition: no `Page started: https://m.soundcloud.com/...` after player ready; `JS -> Native: playback state changed: isPlaying=true`; `JS -> Native: track changed`.

### Entry 022
- Status: belt-and-suspenders main-frame lockdown applied; device validation pending
- Summary: Entry 021 installed a `shouldOverrideUrlLoading` main-frame block, but a device trace still showed `Page started: https://m.soundcloud.com/pages/privacy` committing and destroying the controlled document. In this WebView version the privacy-page navigation bypasses `shouldOverrideUrlLoading` (likely a JS/renderer-initiated main-frame redirect), so the override hook alone cannot catch it.
- Fix (HardenedWebViewClient.kt only):
  - New field `isControlledDocumentActive: Boolean` — flipped true in `onPageFinished` when the finished URL is the injected `data:` document (or `about:blank`). Reset in `clearDiagnosticState`.
  - New helper `isInjectedMainFrameUrl(url)` shared by all navigation hooks.
  - `onPageStarted` — when `isControlledDocumentActive` and url is not injected, log a `Main-frame navigation away from controlled document detected` line, notify `onNavigationBlocked`, and call `view?.stopLoading()` before surfacing the event. This cancels in-flight loads that slipped past `shouldOverrideUrlLoading`.
  - Override `onPageCommitVisible(view, url)` — same non-injected/controlled-active check; logs and calls `stopLoading()` if the non-injected document is about to commit visibly.
  - `shouldOverrideUrlLoading` — now also calls `view?.stopLoading()` on the main-frame block path, and logs `mainFrame=/controlledActive=` state on every entry. Unconditional main-frame block from Entry 021 unchanged.
  - `onReceivedError` — logs `mainFrame=/controlledActive=/code=` for every error to disambiguate stopLoading-induced errors from real subresource failures.
  - Subframe / iframe / subresource paths unchanged — allowlist still applied via `shouldInterceptRequest` and the subframe branch of `shouldOverrideUrlLoading`.
- Not changed: allowlist, CSP, widget URL construction, layout, lifecycle, bridge surface.
- Build: `./gradlew :app:assembleDebug` PASSED (1m 14s).
- Pending (device): install APK, select a track after VPN off.
- Success condition:
  - After `player ready`, no `Page started: https://m.soundcloud.com/pages/privacy` (or if it starts, a `Main-frame navigation away from controlled document detected` log immediately follows and the document does not commit).
  - `JS -> Native: playback state changed: isPlaying=true` and/or `JS -> Native: track changed` appear in the trace.
  - Every navigation hook log line now carries `controlledActive=` state for unambiguous diagnosis of any future escape path.
