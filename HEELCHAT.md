# HeelChat Mobile

A Tar Heel rebrand of [garfiec/Librechat-Mobile](https://github.com/garfiec/Librechat-Mobile)
(Kotlin Multiplatform, Android + iOS) that talks to a self-hosted **HeelChat** (LibreChat)
backend running UNC PromptLab's models. Carolina blue `#4B9CD3`, UNC navy `#13294B`.

Upstream is tracked as the `upstream` remote — `git fetch upstream && git merge upstream/develop`
pulls new features; re-apply this doc's small set of changes if they conflict.

## What's changed from upstream

**Branding** (display only — package/bundle IDs and the `librechat` deep-link scheme are
left untouched so OAuth + builds keep working):
- App name → **HeelChat**: `app/src/main/res/values/strings.xml` (`app_name`),
  `app/src/main/AndroidManifest.xml` (`android:label`), `iosApp/iosApp/Info.plist`
  (`CFBundleDisplayName` + the 4 permission usage strings).
- Accent → **Carolina blue**: `core/ui/.../theme/AccentColors.kt` — `DefaultAccentSeed = Color(0xFF4B9CD3)`,
  with UNC navy added to the picker. The whole Material 3 scheme is generated from this seed.
- Onboarding/a11y strings ("Connect to HeelChat", logo content-desc) across all 10 locales.
  (The backend **version-mismatch** message keeps saying "LibreChat" — it refers to the
  underlying server version, which really is LibreChat.)

**Live PromptLab balance** — the app already had a balance feature pointing at LibreChat's
own `/api/balance` (which the HeelChat backend disables on purpose). Re-pointed it at the
**proxy's real balance**:
- `core/network/.../api/BalanceApi.kt` — now GETs `http://<server-host>:8788/balance`
  (the PromptLab proxy, same host as the server, port 8788) instead of `/api/balance`.
- `core/model/.../Balance.kt` — added `refillAmount` (the daily ceiling).
- `feature/settings/.../BalanceSection.kt` (+ `SettingsUiState`, `AccountDelegate`,
  `AccountSettingsScreen`) — shows `used / total (pct%)` in **Account Settings** (matching
  PromptLab's "tap your profile → see balance" UX). The unlimited mini doesn't move it.

> Not yet built/verified: these edits follow the codebase patterns and the Koin wiring is
> confirmed (`ServerUrlProvider` is bound; `singleOf(::BalanceApi)` resolves it; JSON has
> `ignoreUnknownKeys`), but they have **not** been compiled — build in Android Studio to confirm.

## Connecting to your HeelChat backend

The phone can't reach `localhost` — it needs the PC's HeelChat server (`:3080`) AND the
proxy (`:8788`, for the balance) reachable over the network. Two ways:

- **Same WiFi:** in the app's onboarding, enter `http://192.168.1.219:3080` (your PC's LAN IP).
  Open both ports through Windows Firewall (inbound TCP 3080 + 8788) so the phone can reach them.
- **Anywhere (recommended): Tailscale.** Install it on the PC and phone, sign into the same
  tailnet, then point the app at `http://<pc-tailscale-ip>:3080`. Nothing is exposed publicly,
  and both ports are reachable on the tailnet with no firewall changes.

Backend prerequisites (already set in the HeelChat `.env`):
- `NON_BROWSER_VIOLATION_SCORE=0` — the native app isn't a browser; this stops ban-point accrual.
- The PromptLab **proxy must be running on `:8788`** (it holds the warm SSO session), and the
  HeelChat Docker stack up on `:3080`.

Log in with your HeelChat user. New chats default to the unlimited mini (server-side `modelSpecs`),
and Account Settings shows your real PromptLab balance.

## Build

**Android** (free — sideload the APK):
```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # signed release (configure keystore.properties)
```
JDK 21, compileSdk 36, minSdk 26. Install the APK directly or via Obtainium.

**iOS** (needs an Apple-Silicon Mac + Xcode 15+, and an Apple Developer account for TestFlight):
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
open iosApp/iosApp.xcodeproj   # set your Team under Signing & Capabilities, ⌘R
```
