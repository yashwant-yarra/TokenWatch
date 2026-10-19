// Runs the extraction script in UsagePageSpec.kt against fake provider pages.
// Usage: node app/src/test/js/extractionScript.test.js app/src/main/java/com/tokenwatch/data/web/UsagePageSpec.kt
const fs = require("fs");
const kt = fs.readFileSync(process.argv[2], "utf8");
const tpl = kt.split("private fun extractionScript(")[1].split(') = """')[1].split('""".trimIndent()')[0];
// Fills the Kotlin string templates the way Kotlin would: "\\b" in Kotlin source is \b in JS.
const fill = (e, value) => value ? e.replace(/\\\\/g, "\\").replace(/\$it/g, value) : "null";
const script = (sel, pat, plan, headingPlan) => tpl
  .replace(/\$\{planPattern\?\.let \{ "(.*)" \} \?: "null"\}/, (_, e) => fill(e, plan))
  .replace(/\$\{headingPlanPattern\?\.let \{ "(.*)" \} \?: "null"\}/, (_, e) => fill(e, headingPlan))
  .replace(/\$\{'\$'\}/g, "$").replace(/\$selector/g, sel).replace(/\$labelPattern/g, pat);

let body;
const el = (tag, ...kids) => {
  const n = { tag, kids, parentElement: null };
  kids.forEach(k => typeof k === "object" && (k.parentElement = n));
  Object.defineProperty(n, "innerText", { get: () => kids.map(k => typeof k === "string" ? k : k.innerText).join("\n") });
  return n;
};
const all = (n, tag, out = []) => { if (n.tag === tag) out.push(n); n.kids.forEach(k => typeof k === "object" && all(k, tag, out)); return out; };
const run = (root, sel, pat, plan, headingPlan) => {
  body = root;
  global.document = { body, querySelectorAll: s => all(body, s) };
  return JSON.parse(eval(script(sel, pat, plan, headingPlan)));
};
module.exports = { el, run };
if (require.main === module) {
  const assert = require("assert");
  const now = Date.now();
  // Claude: session not started yet (no reset line), weekly with a reset.
  const claude = el("body", el("main",
    el("section", el("div", "Plan usage limits", "Pro"), el("div", "Current session", "Starts when a message is sent", "0% used")),
    el("section", el("div", "Weekly limits", "Learn more about usage limits"), el("div", "All models", "Resets Tue 11:00 PM", "20% used")),
  ));
  const c = run(claude, "section", "current session|weekly", "Pro|Max");
  console.log(JSON.stringify(c));
  const session = c.windows.find(w => w.label === "Current session");
  const weekly = c.windows.find(w => w.label === "Weekly");
  assert.equal(session.percent, 0); assert.equal(session.resetsAt, null, "session must not borrow the weekly reset");
  assert.equal(weekly.percent, 20); assert.ok(weekly.resetsAt);
  // Claude: session in progress.
  const c2 = run(el("body", el("main",
    el("section", el("div", "Current session", "Resets in 4 hr 12 min", "12% used")),
    el("section", el("div", "Weekly limits"), el("div", "All models", "Resets Tue 11:00 PM", "20% used")),
  )), "section", "current session|weekly", "Pro|Max");
  const s2 = c2.windows.find(w => w.label === "Current session");
  assert.equal(s2.percent, 12);
  assert.ok(Math.abs(Date.parse(s2.resetsAt) - (now + (4 * 60 + 12) * 60000)) < 5000);
  // Claude (settings dialog, 2026-09): both windows in one block after a summary line, weekly labeled
  // "This week" with a full weekday, plan under the "Your usage" heading.
  const c3 = run(el("body", el("div", el("h2", "Your usage"), el("span", "Pro"),
    el("section", "Fresh week. 1% of your weekly limit used.", el("div", "Current session", "Resets at 3:00 PM", "4% used"),
      el("div", "This week", "Resets Wednesday 4:30 AM", "1% used")))),
    "section", "current session|weekly|this week", "Pro|Max", "your usage\\s+(pro|max)\\b");
  console.log(JSON.stringify(c3));
  assert.equal(c3.planName, "Pro");
  assert.equal(c3.windows.length, 2);
  const s3 = c3.windows.find(w => w.label === "Current session");
  assert.equal(s3.percent, 4);
  assert.equal(new Date(s3.resetsAt).getMinutes(), 0, "\"Resets at 3:00 PM\" is read");
  const w3 = c3.windows.find(w => w.label === "Weekly");
  assert.equal(w3.percent, 1);
  assert.equal(new Date(w3.resetsAt).getDay(), 3, "resets on a Wednesday");
  // Gemini: month-day reset without a year lands within the next year, not a year late.
  // Six days from today, written the way Gemini does ("Sep 24 4:37 PM"), so the test holds on any date.
  const soon = new Date(now + 6 * 864e5);
  const soonText = soon.toLocaleDateString("en-US", {month: "short", day: "numeric"}) + " 4:37 PM";
  const g = run(el("body", el("div", el("p", "Weekly limit"), el("p", "0% used"), el("p", "Resets " + soonText))), "p", "current usage|weekly limit");
  const gw = g.windows[0];
  console.log(JSON.stringify(g));
  const days = (Date.parse(gw.resetsAt) - now) / 864e5;
  assert.ok(days > 0 && days < 366, "reset " + gw.resetsAt + " should be within a year");
  assert.ok(days < 30, soonText + " is days away, got " + gw.resetsAt);
  // Gemini: the plan badge under "Usage limits" is read and shown as "Pro"; an upsell banner is not a plan.
  const geminiPlan = "usage limits\\s+(pro|ultra|plus)\\b"; // Same pattern as UsagePageSpec's Gemini entry.
  const gp = run(el("body", el("div", el("h1", "Usage limits"), el("span", "PRO")),
    el("div", el("p", "Current usage"), el("p", "0% used"), el("p", "Resets at 3:07 AM"))), "p", "current usage|weekly limit", null, geminiPlan);
  assert.equal(gp.planName, "Pro");
  const free = run(el("body", el("div", el("h1", "Usage limits"), el("span", "Upgrade to Pro")),
    el("div", el("p", "Current usage"), el("p", "0% used"), el("p", "Resets at 3:07 AM"))), "p", "current usage|weekly limit", null, geminiPlan);
  assert.equal(free.planName, null);
  // Codex (chatgpt.com/settings/usage, 2026-10): div blocks, "% left", resets in days and hours.
  const codexPage = el("body", el("main", el("section", el("div", "Plan limits", "Shared across Codex, Work, Workspace Agents, and ChatGPT for Excel."),
    el("div", el("div", el("div", "5-hour limit"), "Resets in 5h 0m", "100% left"), el("div", el("div", "Weekly limit"), "Resets in 5d 18h", "93% left"))),
    el("section", el("div", "Recent plan usage"), el("div", "5-hour", "Weekly", "100%"))));
  const x = run(codexPage, "div", "5-hour limit|weekly limit");
  console.log(JSON.stringify(x));
  assert.equal(x.windows.length, 2);
  const xs = x.windows.find(w => w.label === "Current session");
  const xw = x.windows.find(w => w.label === "Weekly");
  assert.equal(xs.percent, 100); assert.equal(xs.percentMeaning, "REMAINING");
  assert.ok(Math.abs(Date.parse(xs.resetsAt) - (now + 5 * 3600000)) < 5000);
  assert.equal(xw.percent, 93); assert.equal(xw.percentMeaning, "REMAINING");
  assert.ok(Math.abs(Date.parse(xw.resetsAt) - (now + (5 * 24 + 18) * 3600000)) < 5000, "\"in 5d 18h\" is read: " + xw.resetsAt);
  // Claude: plan read from its quota block.
  assert.equal(run(el("body", el("section", el("div", "Plan usage limits", "Max"), el("div", "Current session", "Resets in 1 hr", "5% used"))),
    "section", "current session|weekly", "Pro|Max").planName, "Max");
  console.log("OK");
}
