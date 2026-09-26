package com.tokenwatch.data.web

import com.tokenwatch.domain.model.AiProvider

data class UsagePageSpec(
    val provider: AiProvider,
    val url: String,
    val allowedHosts: Set<String>,
    val extractionScript: String,
    /** Where the visible sign-in screen starts. Defaults to the usage page. */
    val signInUrl: String = url,
) {
    /**
     * Cookie domains holding this provider's own sign-in. Shared Google and Apple sign-in is left
     * out so signing out of one provider doesn't sign you out of the others; Gemini's own sign-in
     * is the Google account, so it does include google.com.
     */
    val sessionDomains: Set<String> = when (provider) {
        AiProvider.CLAUDE -> setOf("claude.ai", "anthropic.com")
        AiProvider.CODEX -> setOf("chatgpt.com", "openai.com")
        AiProvider.GEMINI -> setOf("gemini.google.com", "google.com")
    }

    fun allows(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        val host = uri.host?.lowercase() ?: return@runCatching false
        uri.scheme == "https" && allowedHosts.any { host == it || host.endsWith(".$it") }
    }.getOrDefault(false)

    companion object {
        fun forProvider(provider: AiProvider): UsagePageSpec = when (provider) {
            AiProvider.CLAUDE -> UsagePageSpec(
                provider,
                "https://claude.ai/settings/usage",
                setOf("claude.ai", "auth.anthropic.com", "accounts.google.com", "appleid.apple.com"),
                // The plan ("Pro") sits under the "Your usage" heading, outside the quota block.
                extractionScript("section", "current session|weekly|this week", "Pro|Max", headingPlanPattern = "your usage\\s+(pro|max)\\b"),
            )
            AiProvider.CODEX -> UsagePageSpec(
                provider,
                "https://chatgpt.com/codex/cloud/settings/usage",
                // All of openai.com: sign-in passes through several of its subdomains, and blocking any of
                // them stalls the flow. ChatGPT's sign-in page also offers Google and Apple.
                setOf("chatgpt.com", "openai.com", "accounts.google.com", "appleid.apple.com"),
                extractionScript("article", "5 hour|5-hour|weekly"),
                // Signed out, the usage page falls back to the ChatGPT home page instead of login,
                // so sign-in starts at login and returns to usage afterwards.
                signInUrl = "https://chatgpt.com/auth/login?next=%2Fcodex%2Fcloud%2Fsettings%2Fusage",
            )
            AiProvider.GEMINI -> UsagePageSpec(
                provider,
                "https://gemini.google.com/usage",
                setOf("gemini.google.com", "accounts.google.com"),
                // The plan badge ("PRO") sits under the "Usage limits" heading, outside the quota blocks, so it's read
                // from the page text, anchored to that heading so an "Upgrade to Pro" banner can't pass for the plan.
                extractionScript("p", "current usage|weekly limit", headingPlanPattern = "usage limits\\s+(pro|ultra|plus)\\b"),
            )
        }

        private fun extractionScript(
            selector: String,
            labelPattern: String,
            planPattern: String? = null,
            headingPlanPattern: String? = null,
        ) = """
            (() => {
              const clean = value => (value || "").replace(/\s+/g, " ").trim();
              const resetInstant = value => {
                const text = clean(value).replace(/^resets?\s+/i, "");
                const relative = text.match(/^in\s+(?:(\d+)\s*h(?:r|ours?)?)?\s*(?:(\d+)\s*m(?:in(?:utes?)?)?)?/i);
                if (relative?.[0]?.trim() !== "in") {
                  const minutes = Number(relative?.[1] || 0) * 60 + Number(relative?.[2] || 0);
                  if (minutes) return new Date(Date.now() + minutes * 60000).toISOString();
                }
                const normalized = text.replace(/\bat\b/i, " ").trim();
                let parsed = Date.parse(normalized);
                if (!Number.isNaN(parsed)) {
                  if (!/\d{4}/.test(normalized)) {
                    // Without a year Chrome assumes 2001: use this year, or next year if that date has passed.
                    const date = new Date(parsed);
                    date.setFullYear(new Date().getFullYear());
                    if (date <= Date.now()) date.setFullYear(date.getFullYear() + 1);
                    parsed = date.getTime();
                  }
                  return new Date(parsed).toISOString();
                }
                const time = normalized.match(/^(?:(Sun|Mon|Tue|Wed|Thu|Fri|Sat)[a-z]*\s+)?(\d{1,2}):(\d{2})\s*(AM|PM)$/i);
                if (!time) return null;
                const now = new Date();
                const result = new Date(now);
                let hour = Number(time[2]) % 12 + (/pm/i.test(time[4]) ? 12 : 0);
                result.setHours(hour, Number(time[3]), 0, 0);
                if (time[1]) {
                  const days = ["sun", "mon", "tue", "wed", "thu", "fri", "sat"];
                  let offset = (days.indexOf(time[1].toLowerCase()) - now.getDay() + 7) % 7;
                  if (offset === 0 && result <= now) offset = 7;
                  result.setDate(result.getDate() + offset);
                } else if (result <= now) result.setDate(result.getDate() + 1);
                return result.toISOString();
              };
              const candidates = [...document.querySelectorAll("$selector")]
                .filter(node => /$labelPattern/i.test(clean(node.innerText)))
                .map(node => {
                  let current = node;
                  while (current && current !== document.body) {
                    const text = clean(current.innerText);
                    if (text.length <= 500 && /\d+(?:\.\d+)?\s*%\s*(used|remaining)/i.test(text) && /resets?/i.test(text)) {
                      return {node: current, text};
                    }
                    current = current.parentElement;
                  }
                  return null;
                })
                .filter(Boolean);
              const labelPattern = /current session|current usage|5[ -]hour(?: usage limit)?|weekly(?: usage)? limit|weekly|this week/gi;
              // A block can hold several windows, so read each window's values only from the text between
              // its label and the next label. Otherwise one window picks up another's percent or reset.
              // Smallest blocks first, so a window is read from the tightest block that holds it.
              const blocks = [...new Map(candidates.map(item => [item.node, item])).values()]
                .sort((a, b) => a.text.length - b.text.length);
              const windows = blocks.flatMap(block => {
                const labels = [...block.text.matchAll(labelPattern)];
                return labels.map((match, i) => ({label: match[0], text: block.text.slice(match.index + match[0].length, labels[i + 1]?.index ?? block.text.length)}));
              }).map(({label, text}) => {
                const percent = text.match(/(\d+(?:\.\d+)?)\s*%\s*(used|remaining)/i);
                const resetText = text.match(/resets?\s+(?:in\s+\d+\s*h(?:r|ours?)?\s*(?:\d+\s*m(?:in(?:utes?)?)?)?|(?:at\s+)?\d{1,2}:\d{2}\s*(?:AM|PM)|(?:Sun|Mon|Tue|Wed|Thu|Fri|Sat)[a-z]*\s+\d{1,2}:\d{2}\s*(?:AM|PM)|[A-Z][a-z]{2}\s+\d{1,2}(?:,\s*\d{4})?\s+(?:at\s+)?\d{1,2}:\d{2}\s*(?:AM|PM))/i)?.[0] || "";
                return {
                  windowId: label.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_|_${'$'}/g, ""),
                  label: /week/i.test(label) ? "Weekly" : "Current session",
                  windowType: /week/i.test(label) ? "WEEKLY" : "ROLLING_HOURS",
                  percent: percent ? Number(percent[1]) : null,
                  percentMeaning: percent ? percent[2].toUpperCase() : "USED",
                  resetsAt: resetInstant(resetText)
                };
              }).filter(window => window.percent !== null);
              const uniqueWindows = windows.filter((window, i) => windows.findIndex(other => other.windowId === window.windowId) === i);
              const rawPlan = ${planPattern?.let { "candidates.map(item => item.text.match(/\\b($it)\\b/i)?.[0]).find(Boolean)" } ?: "null"} ||
                ${headingPlanPattern?.let { "clean(document.body.innerText).match(/$it/i)?.[1]" } ?: "null"};
              // Pages write the plan as "PRO" or "Pro"; show it one way.
              const planName = rawPlan ? rawPlan.charAt(0).toUpperCase() + rawPlan.slice(1).toLowerCase() : null;
              return JSON.stringify({schemaVersion: 1, planName, windows: uniqueWindows});
            })()
        """.trimIndent()
    }
}
