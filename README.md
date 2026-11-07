# TokenWatch

Kotlin, Jetpack Compose, Material 3, MVVM, Hilt, Room, DataStore, Navigation Compose.
Private Android app that reads subscription quota from the signed-in Claude, Codex, Gemini, and
GitHub Copilot usage pages. Extracted percentages and reset times stay on-device in Room.

Current version: **1.3.0**. Signed APKs and release notes are on the
[releases page](https://github.com/yashwant-yarra/TokenWatch/releases).

## Run

1. Open this folder in Android Studio (Ladybug or newer) and let Gradle sync. JDK 17+.
2. Run the `app` configuration on a device or emulator with Android 8.0 (API 26) or newer.

Command line: `./gradlew :app:installDebug` and `./gradlew :app:testDebugUnitTest`.
Release builds (`:app:assembleRelease`, `:app:bundleRelease` for Play) need `keystore.properties`;
see `keystore.properties.example`. Debug and release builds are signed with different keys, so
switching a phone from one to the other means uninstalling first, which clears the app's data.

## Connect an account

Open Accounts, select a provider, and sign in inside its private in-app web session. The app reads
only quota percentages, reset times, the plan label, and the signed-in account email where the page
shows one. These stay on the device. It does not read or export passwords,
cookies, tokens, page HTML, or conversation content. Usage is read when you sign in or tap
refresh, and, if you turn on background refresh in Settings, on a schedule using the sign-in saved in
the app. Settings can also notify you when a session or weekly quota reaches a chosen percent;
monthly windows (Copilot's AI credits) use the weekly limit. If a provider signs you out, its
connection shows as expired with a Reconnect button. Provider markup changes may require an app update.

## History, widgets, and tile

- **History** keeps every reading on the device for 35 days and charts session windows or
  weekly & monthly windows for all providers.
- **Home-screen widgets**: "Current session", "Weekly & monthly", and "Session & weekly" (one row,
  resizable in width: session as the glass, weekly as a bar under it). Widgets draw your alert line
  and turn red past it.
- **Quick settings tile** showing the window closest to empty; tap it to re-read every connected provider.

## Structure

- `domain/` pure Kotlin: models, repository interfaces, aggregations
- `data/` Room cache (single source of truth), validated web extraction, repositories, settings
- `core/` theme, formatting, shared components (quota rows, banners, states)
- `feature/` dashboard, provider detail, history, connections, settings, web session (screen + view
  model each), plus home-screen widgets and the quick settings tile
- `navigation/` bottom-bar nav and routes
- `di/` Hilt modules

## Honesty rules built in

- Every usage field is nullable; missing data shows "unavailable", never a made-up number.
- Quota windows are a list from the provider, not fixed 5-hour/weekly fields.
- Reasoning tokens are shown but never added to totals (usually a subset of output).
- Subscription cost is labeled "API-equivalent value", not spend.
- Scraped values are partial, private-use data and are never presented as official API telemetry.
