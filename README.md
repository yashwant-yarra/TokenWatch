# TokenWatch

Kotlin, Jetpack Compose, Material 3, MVVM, Hilt, Room, DataStore, Navigation Compose.
Private Android app that reads subscription quota from the signed-in Claude, Codex, and Gemini
usage pages. Extracted percentages and reset times stay on-device in Room.

## Run

1. Open this folder in Android Studio (Ladybug or newer) and let Gradle sync. JDK 17+.
2. Run the `app` configuration on a device or emulator with Android 8.0 (API 26) or newer.

Command line: `./gradlew :app:installDebug` and `./gradlew :app:testDebugUnitTest`.

## Connect an account

Open Accounts, select a provider, and sign in inside its private in-app web session. The app reads
only quota percentages, reset times, the plan label, and the signed-in account email where the page
shows one (Codex, Gemini). These stay on the device. It does not read or export passwords,
cookies, tokens, page HTML, or conversation content. Usage is read when you sign in or tap
refresh, and, if you turn on background refresh in Settings, on a schedule using the sign-in saved in
the app. Settings can also notify you when a session or weekly quota reaches a chosen percent.
Provider markup changes may require an app update.

## Structure

- `domain/` pure Kotlin: models, repository interfaces, aggregations
- `data/` Room cache (single source of truth), validated web extraction, repositories, settings
- `core/` theme, formatting, shared components (quota rows, banners, states)
- `feature/` dashboard, provider detail, connections, settings, web session (screen + view model each)
- `navigation/` bottom-bar nav and routes
- `di/` Hilt modules

## Honesty rules built in

- Every usage field is nullable; missing data shows "unavailable", never a made-up number.
- Quota windows are a list from the provider, not fixed 5-hour/weekly fields.
- Reasoning tokens are shown but never added to totals (usually a subset of output).
- Subscription cost is labeled "API-equivalent value", not spend.
- Scraped values are partial, private-use data and are never presented as official API telemetry.
