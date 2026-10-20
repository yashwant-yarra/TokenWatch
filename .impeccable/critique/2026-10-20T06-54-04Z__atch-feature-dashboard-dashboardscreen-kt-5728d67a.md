---
target: whole app (dashboard surface)
total_score: 28
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 3
target_identity: "file:C:\\Users\\yarra\\Documents\\TokenWatch\\app\\src\\main\\java\\com\\tokenwatch\\feature\\dashboard\\DashboardScreen.kt"
target_fingerprint: "sha256:18cea9514f21b0c5d05bbc01e841b5cb0ec0a9f1d8329e86319286185cfa51fc"
target_path: "C:\\Users\\yarra\\Documents\\TokenWatch\\app\\src\\main\\java\\com\\tokenwatch\\feature\\dashboard\\DashboardScreen.kt"
timestamp: 2026-10-20T06-54-04Z
slug: atch-feature-dashboard-dashboardscreen-kt-5728d67a
closed: true
---
# Critique: TokenWatch app (dashboard surface + related screens)
Method: dual-agent (A: design review, B: detector). Detector scanned 0 readable files (Kotlin unsupported); evidence is the design review of 14 device captures, spot-checked in code.

## Design Health Score
| # | Heuristic | Score | Key Issue |
|---|---|---|---|
| 1 | Visibility of System Status | 3 | Detail header lacks last-read time |
| 2 | Match System / Real World | 3 | Glasses speak % left, charts speak % used |
| 3 | User Control and Freedom | 3 | Refresh only via hidden pull gesture |
| 4 | Consistency and Standards | 2 | Left/used polarity flip; alert red everywhere; Codex "Connected" vs plan |
| 5 | Error Prevention | 3 | 1-minute background interval allowed |
| 6 | Recognition Rather Than Recall | 3 | Alert line and rim ticks unexplained |
| 7 | Flexibility and Efficiency | 3 | No tap-to-refresh, no per-provider thresholds |
| 8 | Aesthetic and Minimalist Design | 3 | Six red dashes on all-clear; History reset-line noise |
| 9 | Error Recovery | 3 | Errata slip + kept reading are good |
| 10 | Help and Documentation | 2 | No first-use explanation of the glass |
| **Total** | | **28/40** | **Good** |

## Priority Issues
- [P1] Alert line on every glass breaks the Held-Back Alert Rule; rose #FF5468 vs Claude sand #E08B6B is 1.2:1 so it vanishes on Claude glasses. Fix: rim-grey line until near/past, then rose; bench-coloured halo. (/impeccable quieter) — UsageViews.kt:100, SandGlass.kt:303
- [P1] Polarity flip: charts plot % used, everything else % left. Fix: plot % left, summaries in "left". (/impeccable clarify) — HistoryChart.kt, UsageHistory.kt
- [P1] Verdict lacks reset time and all-clear tone. Fix: "All clear · lowest is X, N% left, resets in Th." / "X is past your line: N% left, back in T." (/impeccable clarify) — DashboardScreen.kt:167-174
- [P2] Settings alerts block overloaded: two 5-option selectors + redundant preview glasses + two toggles. Fix: 4 options, drop/merge previews, sub-heading for reset notifications. (/impeccable distill)
- [P2] History chart noise: full-height dashed reset lines (8 in 24h) + alert line; idle 0% line invisible on axis; no axis unit. Fix: baseline reset ticks, lift baseline, "% left" unit. (/impeccable quieter)

## Persona Red Flags
- Alex: pull-only refresh; reset time not in verdict; no per-provider thresholds.
- Sam: 11sp chart labels (HistoryChart.kt:151); rim ticks 3.05:1 on porcelain; alert-light 4.44:1; Claude sand vs alert by hue only.
- Casey: Sign out link directly under each account row.
- Evening desk dev: six red dashes read as alarm; perpetual grain motion; lead glasses ~45% of viewport when all clear.

## Minor Observations
- DESIGN.md documents 3 bench-bar tabs; app ships 4 (History).
- Detail header drops "read X ago".
- "Delete cached usage" also deletes history; label should say so.
- Full emails in 16sp ink on Accounts.
- "100% left" tight in compact half-columns.
- "18 hours ago" on 24 h axis is by design (first reading) but reads oddly.

## Questions
1. Should the lead glass be tall when all is clear, so size becomes the alarm?
2. Could History be drawn as the glass's own memory instead of a generic line chart?
3. Past the line, should the reset time become the hero number?
