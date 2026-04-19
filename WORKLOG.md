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
- Selecting `Local Debug Track` and `Local Debug Playlist` from Library reaches Player with selected metadata visible.
- Physical Fire TV now proves the controlled player readiness path:
  - controlled `data:` document loads and remains active
  - bootstrap stages fire (`pre-api-inline`, `widget-api-onload`, `post-api-inline`)
  - `player ready` is received natively
  - prior privacy-page takeover is blocked
- This confirms:
  - backend is not the blocker
  - session/auth is not the blocker
  - card selection/handoff is not the blocker
  - WebView lifecycle/recomposition churn is no longer the blocker
  - CSP / malformed document / baseUrl / URL-encoding issues are no longer the blocker

### Current blocker
- Player runtime on physical Fire TV now reaches `ready`, but actual playback still does not start.
- Latest device traces show:
  - `player ready` fires
  - controlled document stays active
  - no `JS -> Native: playback state changed: isPlaying=true`
  - no `JS -> Native: track changed: id=..., title=...`
- Current unresolved zone is now narrowed to:
  - explicit widget play not being triggered after READY
  - final content-specific widget URL being wrong/incomplete
  - widget state remaining paused / unresolved even after READY

---

## 6. Completion Estimate
### Internal prototype
- Overall completion: **88%**

### Polished private-use app
- Overall completion: **72%**

### Module estimate
- Backend/session/auth/content: **92%**
- TV shell/nav/cards/focus: **85%**
- Player runtime on physical Fire TV: **70–75%**
- Final cleanup/polish: **40–50%**

### Estimated time remaining
- Stable working prototype if scope stays frozen: **2–4 focused hours**
- Additional polish and cleanup after that: **8–16 hours**

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
- Player readiness path is now proven on physical Fire TV.
- Two-region Player layout is in place for playback surface + native queue.
- Top-level privacy-page escape is blocked; controlled document remains active through READY.

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
   - selecting `Local Debug Track` or `Local Debug Playlist`
   - `player ready`
   - first post-READY state transition
4. Prove why READY does not transition into PLAY:
   - log the exact final iframe `src` inserted into the HTML
   - trigger a one-shot debug `widget.play()` after READY
   - log `widget.isPaused(...)` and `widget.getCurrentSound(...)`
   - confirm whether PLAY callback ever fires
5. Fix only the Player runtime path until one real card-to-player success path works on the physical Fire TV.

### Secondary tasks
- Confirm two-region Player layout behavior on device with multi-item sections
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


### Entry 023
- Status: post-READY playback diagnostic scope defined; device trace confirms readiness path remains stable
- Summary: Device validation after Entry 022 proved the privacy-page escape is fixed. The controlled `data:` document remains active through bootstrap and READY. `player ready` continues to fire, but playback still does not transition into `isPlaying=true`, and no `track changed` event arrives.
- Evidence from latest device run:
  - `Page finished: data://null injected=true controlledActive=true`
  - `DocSnapshot ... globals={"hasSC":true,"hasWidget":true,"hasNative":true,"hasHost":true}`
  - `JS -> Native: player ready`
  - repeated `loading state changed: isLoading=false` after READY
  - no `playback state changed: isPlaying=true`
  - no `track changed: id=..., title=...`
- Interpretation: readiness/bootstrap/navigation lockdown are now verified. The active unresolved zone is post-READY playback start. Current leading hypotheses are:
  1. widget play is not being triggered
  2. the final content-specific widget URL inserted into the iframe is still wrong or incomplete
- Next targeted pass (diagnostic only):
  - log the exact final iframe `src` actually inserted into the HTML
  - trigger a one-shot debug `widget.play()` after READY
  - log `widget.isPaused(...)`
  - log `widget.getCurrentSound(...)`
  - log whether PLAY callback ever fires
- Do not change in that pass unless new evidence reopens them:
  - allowlist
  - CSP
  - navigation lockdown
  - layout
- Build state at handoff: latest APK installs and reaches READY on Fire TV; next cycle is post-READY play diagnostics only.

### Entry 023
- Status: launcher banner consolidated to single direct PNG; device validation pending
- Summary: Device run after Entry 022 confirmed navigation lockdown + playback working + play/pause on device. Remaining blocker is Fire TV Launcher home tile — still showing default/incorrect tile instead of the banner. Audit per user's 7 focus areas identified two concrete problems.
- Root cause:
  - `res/drawable/tv_banner.xml` existed as a shape drawable (dark 320×180 rectangle with `#111111` fill) — a wrapper, not a bitmap.
  - `res/drawable-xhdpi/tv_banner.png` was the real image but only in the xhdpi bucket.
  - Unqualified `drawable/` is density-equivalent to mdpi. Fire TV models span tvdpi (Stick 2nd/3rd gen), hdpi (Stick 4K), and xhdpi (Stick 4K Max). On non-xhdpi Fire TV models, resource resolution falls through to the unqualified `drawable/` shape rectangle instead of the xhdpi PNG — rendering exactly the dark "default/incorrect tile" the user reported.
- Fix (resource only, per user directive "single direct bitmap banner resource path with no drawable alias/wrapper"):
  - Deleted `app/src/main/res/drawable/tv_banner.xml` (shape wrapper). This was the actual root cause of the default-tile fallback on non-xhdpi Fire TV models.
  - User replaced `tv_banner.png` with a correctly sized **320×180 px** asset matching Android TV Launcher spec exactly.
  - Final banner resource placement: identical 320×180 PNG in both `drawable-nodpi/tv_banner.png` and `drawable-xhdpi/tv_banner.png`. xhdpi wins on Fire TV Stick 4K Max; nodpi covers tvdpi/hdpi/mdpi devices. No shape wrapper in the lookup path.
- Manifest unchanged: `android:banner="@drawable/tv_banner"` at both `<application>` and `<activity>` for `LEANBACK_LAUNCHER`.
- Build: `./gradlew :app:assembleDebug` PASSED.
- APK verification via `aapt2 dump badging`:
  - `application: ... banner='res/drawable-xhdpi-v4/tv_banner.png'`
  - `leanback-launchable-activity: ... banner='res/drawable-xhdpi-v4/tv_banner.png'`
  - Both `res/drawable-xhdpi-v4/tv_banner.png` and `res/drawable-nodpi-v4/tv_banner.png` packaged in APK (identical bytes). Density-appropriate lookup is unambiguous for all Fire TV models.
- Pending (device): uninstall app, reboot Fire TV (or force-stop `com.amazon.tv.launcher`), reinstall APK. Fire TV Launcher caches banners aggressively — without a cache flush the old rectangle may persist.
- Success condition: after reinstall (and if needed, a Fire TV reboot or Launcher force-stop), the app tile on the Fire TV home row shows the intended banner image instead of the default/dark tile.

### Entry 024
- Status: launcher icon added as packaged vector resource; device validation pending
- Summary: Entry 023 device verification exposed a residual anomaly: `aapt2 dump badging` reported `icon=''` for both `<application>` and the leanback-launchable-activity even though the banner was correctly wired. Root cause: manifest referenced `@android:drawable/sym_def_app_icon` — a system-namespaced resource that is never packaged into the APK, so completeness-checking launchers see a missing icon.
- Fix (manifest + new resource, no playback/transport/navigation changes):
  - New `app/src/main/res/drawable/ic_launcher.xml` — vector drawable placeholder: rounded-corner square background `#FF7700` (user-selected default orange) with a centered white ▶ play-triangle glyph. Vector renders at any density so one resource covers all Fire TV models.
  - `AndroidManifest.xml` `<application>`: `android:icon="@drawable/ic_launcher"`, added `android:roundIcon="@drawable/ic_launcher"`.
  - `AndroidManifest.xml` `<activity>` (LEANBACK_LAUNCHER): added `android:icon="@drawable/ic_launcher"`.
- Build: `./gradlew :app:assembleDebug` PASSED (16s).
- APK verification via `aapt2 dump badging`:
  - `application: ... icon='res/drawable/ic_launcher.xml' banner='res/drawable-xhdpi-v4/tv_banner.png'`
  - `leanback-launchable-activity: ... icon='res/drawable/ic_launcher.xml' banner='res/drawable-xhdpi-v4/tv_banner.png'`
  - `application-icon-{120,160,240,320,480,640,65534,65535}:'res/drawable/ic_launcher.xml'` — resolves at every density bucket including anydpi.
- Pending (device): uninstall app, reboot Fire TV (or force-stop `com.amazon.tv.launcher`), reinstall APK.
- Known optional follow-up (not a bug, user preference): the vector is a generic placeholder. A branded PNG can be swapped in later by dropping `ic_launcher.png` into `mipmap-xhdpi/` (and/or `mipmap-nodpi/`) and changing the manifest reference to `@mipmap/ic_launcher`. No code change required.
- Success condition: Fire TV home row shows the banner tile, and Settings → Applications shows the orange-square ▶ icon instead of the Android default gear.
