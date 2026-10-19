# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Primarily the developer themselves: one person paying for AI coding/chat subscriptions (Claude, Codex, Gemini) who wants to know, at a glance, how much of each plan's session and weekly limit is left and when it resets. Strangers are a secondary audience; personal daily use comes first.

## Product Purpose

TokenWatch reads the quota a provider already shows on its own signed-in usage page (percent used or left, reset time, plan label) and puts all providers in one place, with optional alerts and home-screen widgets. Success is never being surprised by a limit and never having to open three websites to check.

## Positioning

It reads the numbers each provider shows its own signed-in user, on-device, instead of estimating from API logs or token counts. Nothing leaves the phone.

## Operating Context

- Checked in short glances: opening the app, a home-screen widget, or an alert notification at a chosen percent.
- Usage is read when the user signs in or taps refresh, and optionally on a background schedule using the saved web sign-in.
- Sign-in happens inside a private in-app web view per provider. Google sign-in is often refused inside embedded web views, so email sign-in is the usual path.
- Provider pages change without notice (Codex moved to `chatgpt.com/settings/usage` in 2026-10), so a provider can show stale data plus a failure until the reader is updated.

## Capabilities and Constraints

- Screens: Usage dashboard, provider detail, Accounts (connect, manage, sign out), Settings (background refresh, alert thresholds), the sign-in web view, and two home-screen widgets (session and weekly).
- Stack: Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore, Navigation Compose; minSdk 26, targetSdk 36.
- Quota windows are a list from the provider, not fixed 5-hour/weekly fields; some providers have no reset time until a session starts.
- Every usage field is nullable.
- Distribution: personal / sideload for now; no Play launch is planned.
- Undecided: whether privacy messaging, the widgets, or a stock Material You feel are binding for a redesign.

## Brand Commitments

- Name: TokenWatch.
- Playful and premium (user requirement, 2026-10-19): the app should feel delightful to touch and expensive to look at.
- No stock Android or Material elements anywhere the user can see (user requirement, 2026-10-19): controls, bars, dialogs, icons, press feedback, transitions, widgets and the launcher icon are all drawn in the app's own world. Material may stay only as invisible plumbing (theme tokens, layout, gesture handling); system Back, accessibility roles and 48dp touch targets are kept.
- Provider marks for Claude, Codex (OpenAI) and Gemini are used to identify providers (`res/drawable/ic_provider_*.xml`); the OpenAI mark is monochrome and follows the text color.

## Evidence on Hand

- Real usage data from the developer's own accounts on the connected phone.
- No testimonials, user counts, store ratings, or press exist; none may be invented.

## Product Principles

1. Honesty over completeness: missing data shows "unavailable", never a made-up number; values are never presented as official API telemetry.
2. Answer "can I keep going, and until when?" faster than opening the provider's site.
3. The provider's words win: quota windows, plan names and labels come from the page, never hardcoded.
4. Private by construction: usage stays on-device; passwords, cookies, tokens and page content are never read or exported.
5. Stale is shown as stale: when a read fails, keep the last values and say so plainly.
