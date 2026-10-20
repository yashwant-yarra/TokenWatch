---
name: TokenWatch
description: Every quota window is a sand glass on a graphite or porcelain bench.
colors:
  bench-ground-dark: "#0B0C0D"
  bench-raised-dark: "#16181A"
  bench-raised-high-dark: "#202326"
  ink-dark: "#ECEEF0"
  ink-muted-dark: "#8E949A"
  glass-rim-dark: "#6E747A"
  hairline-dark: "#24272A"
  alert-dark: "#FF5468"
  alert-slip-dark: "#3A1419"
  on-alert-slip-dark: "#FFD6DC"
  bench-ground-light: "#ECEEEC"
  bench-raised-light: "#F7F8F7"
  bench-raised-high-light: "#DFE2DF"
  ink-light: "#1C1A17"
  ink-muted-light: "#5C564D"
  glass-rim-light: "#8D877E"
  hairline-light: "#CBCEC9"
  alert-light: "#CF2A40"
  alert-slip-light: "#FADADF"
  on-alert-slip-light: "#5C0C1A"
  sand-claude-dark: "#E08B6B"
  sand-claude-light: "#C15F3C"
  sand-codex-dark: "#3CCB9C"
  sand-codex-light: "#0A7F62"
  sand-gemini-dark: "#6FA8FF"
  sand-gemini-light: "#2F6FD6"
  glass-cap-dark: "#2E3236"
  glass-cap-light: "#4A453E"
typography:
  numeral:
    fontFamily: "Barlow Condensed"
    fontSize: "40sp"
    fontWeight: 600
    lineHeight: "42sp"
    letterSpacing: "-0.5sp"
    fontFeature: "tnum"
  headline-medium:
    fontFamily: "Barlow Condensed"
    fontSize: "30sp"
    fontWeight: 600
    lineHeight: "34sp"
    letterSpacing: "-0.2sp"
    fontFeature: "tnum"
  headline-small:
    fontFamily: "Barlow Condensed"
    fontSize: "24sp"
    fontWeight: 600
    lineHeight: "28sp"
    letterSpacing: "-0.1sp"
    fontFeature: "tnum"
  title-large:
    fontFamily: "Barlow Condensed"
    fontSize: "22sp"
    fontWeight: 600
    lineHeight: "26sp"
    fontFeature: "tnum"
  title-medium:
    fontFamily: "Inter"
    fontSize: "16sp"
    fontWeight: 600
    lineHeight: "22sp"
    letterSpacing: "-0.1sp"
    fontFeature: "tnum"
  body-large:
    fontFamily: "Inter"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
    fontFeature: "tnum"
  body-medium:
    fontFamily: "Inter"
    fontSize: "14sp"
    fontWeight: 400
    lineHeight: "20sp"
    fontFeature: "tnum"
  body-small:
    fontFamily: "Inter"
    fontSize: "12sp"
    fontWeight: 400
    lineHeight: "16sp"
    fontFeature: "tnum"
  label-large:
    fontFamily: "Inter"
    fontSize: "14sp"
    fontWeight: 500
    lineHeight: "20sp"
    fontFeature: "tnum"
  label-medium:
    fontFamily: "Inter"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: "16sp"
    fontFeature: "tnum"
rounded:
  hair: "2dp"
  plate: "3dp"
  slip: "4dp"
  widget: "20dp"
spacing:
  gutter: "20dp"
  section-top: "18dp"
  section-bottom: "26dp"
  stack: "14dp"
  rack-gap: "16dp"
  glass-gap: "12dp"
  shelf-bottom: "24dp"
components:
  plate-button-filled:
    backgroundColor: "{colors.ink-dark}"
    textColor: "{colors.bench-ground-dark}"
    typography: "{typography.label-large}"
    rounded: "{rounded.plate}"
    padding: "12dp 22dp"
    height: "48dp"
  plate-button-outline:
    textColor: "{colors.ink-dark}"
    typography: "{typography.label-large}"
    rounded: "{rounded.plate}"
    padding: "12dp 22dp"
    height: "48dp"
  plate-button-quiet:
    textColor: "{colors.ink-dark}"
    typography: "{typography.label-large}"
    rounded: "{rounded.plate}"
    padding: "12dp 12dp"
    height: "48dp"
  scale-selector:
    textColor: "{colors.ink-dark}"
    typography: "{typography.label-large}"
    rounded: "{rounded.plate}"
    height: "48dp"
  scale-selector-selected:
    backgroundColor: "{colors.ink-dark}"
    textColor: "{colors.bench-ground-dark}"
  glass-toggle:
    rounded: "{rounded.plate}"
    width: "54dp"
    height: "32dp"
  glass-toggle-on:
    backgroundColor: "{colors.bench-raised-high-dark}"
  errata-slip:
    backgroundColor: "{colors.alert-slip-dark}"
    textColor: "{colors.on-alert-slip-dark}"
    typography: "{typography.body-small}"
    rounded: "{rounded.plate}"
    padding: "4dp 4dp 4dp 12dp"
  bench-dialog:
    backgroundColor: "{colors.bench-raised-dark}"
    textColor: "{colors.ink-dark}"
    rounded: "{rounded.slip}"
    padding: "24dp 12dp 12dp 24dp"
  bench-header:
    backgroundColor: "{colors.bench-ground-dark}"
    typography: "{typography.headline-medium}"
    height: "64dp"
  bench-bar:
    backgroundColor: "{colors.bench-ground-dark}"
    textColor: "{colors.ink-muted-dark}"
    typography: "{typography.label-medium}"
    height: "68dp"
  bench-bar-selected:
    textColor: "{colors.ink-dark}"
  glass-tall:
    width: "64dp"
    height: "148dp"
  glass-compact:
    width: "34dp"
    height: "72dp"
  glass-mini:
    width: "18dp"
    height: "34dp"
---

# Design System: TokenWatch

## Overview

**Creative North Star: "The Running Glass"**

TokenWatch is a bench of blown sand glasses. Each quota window the provider reports is one glass: sand in the upper bulb is what is left, sand in the lower bulb is what is used, and refreshing turns the glass. The bench is neutral graphite in dark mode (no warm cast) and cool porcelain in light mode; both are first-class. Glasses are drawn at runtime as premium objects (wall-gradient body, inner rim, curved highlight streaks, dark-metal caps, a soft contact shadow) and the sand is grained, dished in the upper bulb and heaped in the lower, in each provider's own color.

The feel is playful and premium: delightful to touch, expensive to look at. Play lives in physical motion (grains falling through every running glass, sand levelling with the phone's tilt, sprung flips, haptic ticks), never in decoration. Premium lives in restraint: one ink, hairline rules, one alert color held back, engraved condensed numerals. No stock Android or Material element is visible anywhere. Material 3 is invisible plumbing only (MaterialTheme as the token store, Scaffold layout, the PullToRefresh gesture, the Snackbar slot); every control the user sees is drawn by the app.

Density is a ruled rack, not a card grid. Sections and provider shelves are separated by one-pixel hairline rules on the bench; nothing floats in a container. Rank comes from glass size and order, never from shrinking a number.

**Key Characteristics:**
- Every quota window is a sand glass; percent left is the upper bulb, area-correct (sqrt mapping).
- One ink (the text color) does every control job; provider sands are the only hues; rose red means alert and nothing else.
- Barlow Condensed engraved face for titles and numerals; Inter for prose; tabular figures everywhere.
- Hairline rules instead of cards; flat bench with drawn objects on it.
- Springs and haptics on every control; Remove animations stills all of it.

## Colors

A neutral bench in two grounds (graphite and porcelain), one ink per ground, three provider sands, and one rose red held in reserve.

### Primary
- **Glass Ink** (`ink-dark` / `ink-light`): the text color and the app's only control color. Filled plate buttons, the selected scale-selector plate, the toggle block when on, the slider needle, the bench-bar rule and the focus ring are all ink. Mapped to Material `primary` and `onSurface`, so a "filled" control reads as an engraved plate rather than a brand color.

### Secondary
- **Provider Sands** (`sand-claude-*` terracotta, `sand-codex-*` OpenAI green, `sand-gemini-*` Gemini blue, each the provider's own brand hue): the sand inside a provider's glasses and nothing else. Each has a dark-ground and light-ground value tuned for contrast on its bench. Never used for text, controls or fills outside a glass.

### Tertiary
- **Alert Rose** (`alert-dark` / `alert-light`): the user's alert line (dashed, across and past the upper bulb), a percent numeral once its window is past the line, and the errata slip (`alert-slip-*` ground with `on-alert-slip-*` text), the one place it fills a surface. Mapped to Material `error`.

### Neutral
- **Graphite Bench** (`bench-ground-dark`) / **Porcelain Bench** (`bench-ground-light`): the screen ground, header and bench bar, window and splash.
- **Raised Bench** (`bench-raised-*`, `bench-raised-high-*`): dialog slips, the toggle's on-state well, and the widget ground in light mode.
- **Muted Ink** (`ink-muted-*`): secondary text, unselected bench-bar tabs, reset captions, chevrons.
- **Glass Rim** (`glass-rim-*`): glass outlines and graduations, toggle and selector borders, slider scale. Mapped to Material `outline`.
- **Hairline** (`hairline-*`): every Rule between sections and shelves, and the widget edge. Mapped to `outlineVariant`.
- **Glass Cap** (`glass-cap-*`): the solid metal caps a glass stands between.

### Named Rules
**The Held-Back Alert Rule.** The alert color appears only for alerts: the alert line, a past-the-line numeral, an errata slip. A screen with nothing past its line shows no alert red at all. It is rose, not vermilion, so it never reads as Claude's terracotta sand.

**The One Ink Rule.** Controls are ink on bench or bench on ink. No control takes a provider sand or a third hue.

**The Sand Is the Provider Rule.** A provider's color lives in its sand. Provider marks carry the provider's own artwork; the OpenAI mark is monochrome and follows the ink.

## Typography

**Display Font:** Barlow Condensed (Medium 500, SemiBold 600; bundled, OFL)
**Body Font:** Inter (Regular, Medium, SemiBold, Bold; bundled)

**Character:** Barlow Condensed is the engraved face of the glasses' numerals and scale marks: tall, narrow, instrument-like. Inter carries every sentence. All styles set `tnum` so percents and countdowns never shift as they update.

### Hierarchy
- **Numeral** (Barlow Condensed 600, 40sp/42sp, -0.5sp): every percent in the app, at one size. The lead (tall) glass sets it SemiBold; compact glasses set it Medium. It rolls vertically in the direction the value moved.
- **Headline** (Barlow Condensed 600, 30sp top-level / 24sp nested): screen titles in the bench header; 24sp also titles message states.
- **Title** (Barlow Condensed 600, 22sp/26sp): section headings, provider names on shelves, dialog titles.
- **Body** (Inter 400, 16/14/12sp): the dashboard verdict line (16sp), explanatory text (14sp), reset times, metadata and slip text (12sp).
- **Label** (Inter 500, 14sp/12sp): plate-button text (set SemiBold), window labels, selector cells, bench-bar tab labels, the "left" suffix beside a numeral.

### Named Rules
**The One Numeral Rule.** Every percent is 40sp Barlow Condensed. Rank is carried by weight, glass size and order; a number is never shrunk to make it less important.

**The Engraved Heading Rule.** Headings and numerals are Barlow Condensed; prose and controls are Inter. Never set a sentence in the condensed face.

## Layout

Single-column, phone-first, edge-to-edge. Scrolling content sits on a 20dp gutter left and right and ends 24dp above the bench bar. Screens are a header (64dp, below the status bar) then a list of ruled blocks.

- **Section:** a hairline rule, then 18dp top / 26dp bottom padding, with a 14dp stack between heading and content.
- **Dashboard rack:** a verdict sentence (20dp below), then one ruled shelf per provider, sorted least-left first. The first provider with a reading gets tall glasses (64x148dp, numeral under the glass); the rest get compact glasses (34x72dp, numeral to the right, 12dp gap). A provider's windows sit side by side, equal width, 16dp apart, in a fixed order so they never swap between reads. Shelf rows are 14dp vertical padding; a shelf ends 24dp below its glasses (12dp when an errata slip follows).
- **Accounts rows:** mini glasses (18x34dp) beside each account.
- **Settings:** alert-line preview glasses (26x48dp) show where the line will sit.
- **Pull to refresh:** pulled content drops by a 72dp gap (stretch up to 1.4x) so the turning glass sits in its own space instead of over a line.
- **Touch targets:** every drawn control is at least 48dp.

## Elevation & Depth

The bench is flat. There are no surface shadows, no cards and no elevated containers; depth belongs to the glasses as drawn objects. Each glass carries its own soft radial contact shadow on the bench (black at 55% dark / 18% light, fading out), a body gradient darker at the walls, a fainter inner rim for wall thickness, white highlight streaks (22% dark / 75% light) and a lit cap edge. Dialog slips separate from the bench by a raised tone and a hairline border, not a shadow. The bench bar separates by a hairline rule.

### Named Rules
**The Flat Bench Rule.** Screens, rows, sections and dialogs cast no shadow. Only the glass casts a shadow, because it is the only object standing on the bench.

## Shapes

Square-shouldered and nearly sharp. Controls use a 3dp radius (plate buttons, scale selector, toggle well, errata slip), 2dp for small inner parts (toggle block, glyph-button press area, slider needle head, bench-bar rule's lower corners), 4dp for dialog slips. The home-screen widget frame uses 20dp to sit with launcher widget shapes. Borders are hairlines (1dp, or 1.25dp when drawn on canvas). The recurring silhouette is the hourglass itself, which also forms the USAGE glyph, the loading indicator and the launcher icon.

## Components

### Buttons
Ink plates: engraved, firm, quiet.
- **Shape:** gently squared (3dp), minimum 48x48dp.
- **Filled:** ink ground with bench-colored SemiBold label, 22dp horizontal / 12dp vertical padding. One per view, for the main action.
- **Outline:** 1dp ink border at 55% alpha, ink label.
- **Quiet:** no border or fill, underlined ink label, 12dp horizontal padding. Used for minor actions, dialog dismiss, and the errata slip's action (in on-alert-slip ink).
- **Press / Focus:** the ink wash (ink at 9% alpha, sprung in and out) replaces the ripple; keyboard focus draws a 2dp ink ring. A press haptic fires on tap. Disabled is 38% alpha.
- **Glyph button:** a 48dp target holding one 24dp glyph, used for Back in the header.

### Toggles (Glass Toggle)
A square-shouldered lever, 54x32dp. A 3dp well with a rim hairline; a square block springs across (damping 0.55), stretches up to 35% mid-travel, and fills with ink as the well fills with raised bench. The whole row carries the toggle semantics.

### Scale Selector
A 48dp row of engraved cells inside a 3dp rim border, divided by rim hairlines at 50%. An inverted ink plate slides to the chosen cell on a spring (damping 0.62); the selected label turns bench-colored and SemiBold. Tick haptic on change; radio semantics.

### Scale Slider
A ruled scale (rim baseline with minor 6dp and major 12dp ticks every 5 steps), an ink track to the value, and an ink needle with a 12x9dp head. The needle springs (damping 0.6); a tick haptic fires on each step passed. Exposes progress and set-progress semantics.

### Navigation (Bench Bar and Bench Header)
- **Bench bar:** a 68dp bar on the bench, above a hairline rule, with three glyph tabs (Usage, Accounts, Settings). A 28x3dp ink rule springs to the chosen tab (damping 0.55); the selected glyph lifts 2dp and turns ink with a SemiBold label; unselected tabs are muted ink. Tick haptic on switch; tab semantics.
- **Bench header:** 64dp, engraved title (30sp top-level, 24sp with Back), drawn Back glyph button when nested.
- **Transitions:** screens slide a sixth of the width and settle on a spring with a short fade; Back reverses.

### Dialogs (Bench Dialog)
A bench slip: raised bench ground, 4dp corners, hairline rim border at 60%, engraved title, muted body, Quiet dismiss then Filled confirm. It springs up 48dp from the table, scaling from 94%.

### Errata Slip
A failed read hung under its glass: alert-slip ground, 3dp corners, ALERT glyph and on-alert-slip text, optional Quiet action ("Sign in"). The glass above keeps its last reading in a dashed outline with sand at half strength.

### Glyphs
The app's own hairline icon set drawn on canvas at a 24-unit grid with a 1.6dp round-capped stroke: BACK, CHEVRON, USAGE (an hourglass), ACCOUNTS, SETTINGS (a ruled scale with a needle), INFO, ALERT, CHECK. Tinted ink or muted ink; ALERT takes alert-slip ink inside a slip.

### Sand Glass (signature)
The core component, drawn by one shared routine for the app, the refresh indicator and the widgets.
- **Anatomy:** upper and lower bulbs as bezier paths, body gradient, outer rim hairline plus a 35% inner rim, two highlight streaks and a right rim light, solid caps with a lit edge, contact shadow.
- **Sand:** level follows area (sqrt mapping, so half the sand fills half the bulb); the upper surface is dished, the lower pile heaped; shaded darker at the walls and base, lifted at the crest, with fixed light and dark speckle. A thin stream and five accelerating grains fall while sand remains above and below.
- **Marks:** rim graduations at 25/50/75% left on the upper bulb; the user's alert line as a dashed rose-red line running across and past the bulb.
- **States:** stale or unreported draws a dashed outline and half-strength sand; sand settles to a new reading on a soft spring; while re-reading, the glass turns full sprung circles and always lands upright.
- **Motion host (Sand Bench):** one shared frame clock for every grain and the gravity sensor tilting the sand surface, clamped to 0.35 rad; both pause in the background and under Remove animations.
- **Sizes:** tall 64x148dp, compact 34x72dp, settings preview 26x48dp, mini 18x34dp, turning indicator 18x30dp.

### Turning Glass (refresh and loading)
A small ink glass. In pull-to-refresh it drops with the pull, tips toward a flip, ticks at the threshold, then keeps flipping with a spring while usage is re-read; a confirm haptic lands when the read finishes. Alone, it is the loading state.

## Do's and Don'ts

### Do:
- **Do** show every quota window as a sand glass drawn with the shared glass routine, sand in the provider's color.
- **Do** set every percent at the one 40sp Barlow Condensed numeral, with tabular figures, rolling in the direction it moved.
- **Do** separate groups with a 1dp hairline rule on the bench and a 20dp gutter.
- **Do** draw every visible control in the app's own vocabulary (plate buttons, glass toggle, scale selector, scale slider, bench bar, bench dialog, glyphs, ink wash) with 48dp targets and proper accessibility roles.
- **Do** answer controls with springs and haptic ticks, and check `motionEnabled()` so Remove animations stills grains, tilt, spins, flips and entrances.
- **Do** show stale readings as stale: dashed outline, half-strength sand, an errata slip.

### Don't:
- **Don't** let any stock Material or Android element show: no ripple, no stretch overscroll, no Material buttons, switches, sliders, segmented buttons, navigation bar, top app bar, dialogs or icon packs.
- **Don't** use cards, elevated containers or surface shadows; only the glass casts a shadow.
- **Don't** use the alert rose for anything but alerts.
- **Don't** shrink a numeral to signal lower rank; use glass size, weight and order.
- **Don't** fall back to a horizontal progress bar for usage.
- **Don't** give controls a provider sand or a brand hue; controls are ink.
