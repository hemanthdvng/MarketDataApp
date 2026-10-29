# Prompt to paste into a new AI chat

Copy everything inside the box and add your request at the end.

```
You are helping me maintain MarketData, an Android app (Kotlin, Jetpack Compose,
Material 3) that is published on Google Play. The code is at
github.com/hemanthdvng/MarketDataApp. I work ONLY from my phone: no PC, no
Android Studio. You can't assume I can run commands locally.

Read these first: README.md, docs/RELEASING.md, docs/PLAY_RELEASE.md,
PRIVACY_POLICY.md.

What the app does: downloads NSE historical data via Zerodha Kite Connect to CSV,
live quotes, option chain, a pattern scanner (with an optional foreground service
for background scans), and a beta AI Agent that uses the user's own Claude or
Gemini API key. Users bring their own keys. Market data needs the paid Kite
Connect plan. The developer runs no server and collects no data.

Rules:
1. Work on a new branch and open a pull request into main. Never push to main.
2. You may not be able to compile. GitHub Actions builds every pull request, so
   keep changes small, check imports and signatures carefully, and tell me to
   check the PR's build result before merging. Never claim it compiles unless
   a build proved it.
3. Do not change applicationId (com.marketdata.app). Do not change versionCode
   (CI sets it). Bump versionName for each release.
4. Never commit keystores, passwords or API keys.
5. Keep the dark theme (black background, blue accent, monospace). Keep bottom
   navigation labels short enough to stay on one line.
6. Before each release, re-check the Claude and Gemini model IDs in
   app/src/main/java/com/marketdata/app/data/models/Models.kt against the
   providers' official docs.
7. The app gives market information and AI commentary. Keep the "not investment
   advice, not affiliated with NSE or Zerodha" wording. Do not put "Nifty",
   "NSE", "Zerodha" or "Kite" in the app name or icon.
8. If a change affects permissions, ads, data sent to third parties, or the
   foreground service, tell me which Play Console forms (Data safety, Ads,
   Foreground service) and which part of PRIVACY_POLICY.md must be updated.
9. Explain steps for a phone browser, in plain language. Give me exact text to
   paste when Play Console needs it.

When done, give me: what changed, the pull request link, what to check in the
build, and the exact steps to release it (follow docs/RELEASING.md).

My request: <write what you want here>
```
