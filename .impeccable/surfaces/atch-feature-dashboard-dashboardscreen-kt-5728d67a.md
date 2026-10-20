---
version: 1
slug: "atch-feature-dashboard-dashboardscreen-kt-5728d67a"
primary_target: "app/src/main/java/com/tokenwatch/feature/dashboard/DashboardScreen.kt"
related_targets: ["app/src/main/java/com/tokenwatch/feature/providerdetail/ProviderDetailScreen.kt","app/src/main/java/com/tokenwatch/feature/connections/ConnectionsScreen.kt","app/src/main/java/com/tokenwatch/feature/settings/SettingsScreen.kt","app/src/main/java/com/tokenwatch/feature/widget/UsageWidgets.kt"]
---

Scope: everything the user sees: Usage, provider detail, Accounts, Settings, sign-in screen chrome, navigation, dialogs, feedback, transitions, home-screen widgets, launcher and notification icons. Visitor mode: Operate. User pins (2026-10-19): playful and premium; no stock Android/Material element anywhere visible.
Audience and job: the developer, glancing at a phone beside the keyboard, often in the evening: "am I about to hit a limit?"
Constraints: system Back and predictive back, edge-to-edge, 48dp targets and accessibility roles on every drawn control; honesty rules from PRODUCT.md; light and dark both first-class (Settings offers System/Light/Dark).

## Direction contract

THESIS: Every quota window is a sand glass: sand in the upper bulb is what's left, sand below is what's used, and refreshing turns the glass. It refuses the category default of a card per provider with a horizontal progress bar.

OWN-WORLD: Graphite ground (#0B0C0D dark, neutral, no warm cast / porcelain #ECEEEC light). Glasses are premium blown glass: hairline rim, a faint body tint, a curved highlight streak on each bulb, a soft contact shadow, solid dark-metal caps. Sand is grained (fine speckle), heaped in the lower bulb and dished in the upper, in each provider's color (Claude terracotta, Codex OpenAI green, Gemini blue: each provider's brand hue); one alert rose red held for alerts only. Barlow Condensed numerals (one size for every percent) that roll when values change, Inter for prose. Every control is drawn here: square-shouldered toggles, inverted-plate scale selectors with a sliding plate, a ruled scale slider with a needle, ink plate buttons, a bench bar with a sliding rule, bench-slip dialogs, authored hairline glyphs, an ink-wash press. No cards.

STORY: The visitor sees which window is closest to empty, how much is left, and when it comes back, then pulls to turn the glasses and re-read.

FIRST VIEWPORT: Title row "TokenWatch" with a one-line verdict under it ("Codex weekly is lowest: 7% left"). Below, a rack sorted by least left: the riskiest provider's glasses drawn tall (about 150dp), each with its percent-left numeral and reset under it; other providers follow as short glasses in ruled rows. Pull-to-refresh is the primary action; the drawn bench bar (three glyph tabs with a sliding rule) sits at the bottom.

FORM: The Running Glass, candidate 7 of 7 on the ordered list (cockpit, split-flap, warning lamps, parking meter, tide table, transit signage, hourglass). Seed key 19d73605. Raises: graduation ticks and alert line (Crouwel grid specimen); fixed rack of ruled rows (event catalog); errata slip in the one alert color (acetate manual); one numeral size, rank by weight and order (timetable rack). Signature interactions: sand grains fall live through every running glass and the sand surface tilts with the phone (gravity sensor); pulling to refresh tips a glass that flips with a spring and a haptic tick at the threshold, and each re-reading provider's glasses spin a full sprung turn; controls answer with springs and haptic ticks. Remove animations stills every one of these.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
