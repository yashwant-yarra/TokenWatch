# Provider feasibility (Phase 0)

Fill this in from official documentation before building any real adapter. Every claim needs a
link. Anything not confirmed from official docs stays **UNVERIFIED**. Nothing here has been
verified yet; the app currently runs on sample data only.

| Provider | Candidate source | Auth | Tokens | Quota % | Reset times | Models | Cost | Rate limits | ToS allows 3rd-party? | Status |
|---|---|---|---|---|---|---|---|---|---|---|
| Claude | Subscription usage via OAuth | UNVERIFIED | | | | | | | UNVERIFIED | MOCK |
| Claude | API org usage/cost reports (admin key, backend only) | UNVERIFIED | | | | | | | | MOCK |
| Claude | Claude Code local telemetry (desktop companion) | n/a (local) | | | | | | | | MOCK |
| Codex | ChatGPT/Codex subscription usage via OAuth | UNVERIFIED | | | | | | | UNVERIFIED | MOCK |
| Codex | API org usage/cost (admin key, backend only) | UNVERIFIED | | | | | | | | MOCK |
| Codex | Codex CLI local session data (desktop companion) | n/a (local) | | | | | | | | MOCK |
| Gemini | Subscription quota via Google OAuth | UNVERIFIED | | | | | | | UNVERIFIED | MOCK |
| Gemini | Cloud billing export / monitoring (API usage) | UNVERIFIED | | | | | | | | MOCK |

## Enabling a real provider

1. Complete its rows above with links.
2. Add its usage page and extraction to `data/web/UsagePageSpec.kt`; payloads go through `SanitizedQuotaPayloadParser`.
3. Report honest `ProviderCapabilities`; the UI adapts automatically.
4. Add a fake page for it to `app/src/test/js/extractionScript.test.js`.
