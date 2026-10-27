# Releasing MarketDataApp on Google Play

## 0. Before anything (cannot be changed later)
- `applicationId` is `com.marketdata.app`. It is permanent once uploaded. Pick a
  more unique one now if you want (e.g. `io.github.hemanthdvng.marketdata`).
- Avoid "Nifty", "NSE", "Zerodha" or "Kite" in the Play title and icon. They are
  third-party trademarks and Play's impersonation policy can reject the listing.
  You may mention them in the description ("works with your Zerodha Kite Connect
  account"). The in-app header currently says NIFTYBOT; consider renaming it too.

## 1. Create the upload key (one time, keep it safe)
On a phone (Termux) or any machine with Java:

    keytool -genkeypair -v -storetype JKS -keystore upload.jks \
      -alias upload -keyalg RSA -keysize 2048 -validity 10000

Back up `upload.jks` and its passwords somewhere private (never in the repo).
Then in GitHub: repo -> Settings -> Secrets and variables -> Actions, add:

| Secret | Value |
|---|---|
| UPLOAD_KEYSTORE_BASE64 | `base64 -w0 upload.jks` output |
| UPLOAD_STORE_PASSWORD | keystore password |
| UPLOAD_KEY_ALIAS | `upload` |
| UPLOAD_KEY_PASSWORD | key password |

Run the **Build APK and AAB** workflow. Download `MarketDataApp-release-aab`
from the run's Artifacts. Enrol in **Play App Signing** when you first upload
(default for new apps).

## 2. Play Console setup
1. Create a developer account (one-time USD 25 fee, identity verification).
2. Create app -> name, language, App, Free, accept declarations.
3. Fill **App content**:
   - Privacy policy URL: use the GitHub page for `PRIVACY_POLICY.md`
     (`https://github.com/hemanthdvng/MarketDataApp/blob/main/PRIVACY_POLICY.md`)
     or enable GitHub Pages.
   - Data safety: the app itself collects nothing, but data you enter goes to
     Zerodha and to Anthropic/Google when you use the AI tab. Declare that
     honestly (financial info, app activity/messages shared with third-party
     services, encrypted in transit).
   - Financial features declaration: select the trading/market data option and
     state it provides market data and information tools and does not offer
     loans, trades or advice.
   - Foreground service declaration: `specialUse`, with the justification
     already in the manifest, plus a short screen recording of switching the
     background scanner on and the notification it shows.
   - App access: reviewers cannot log in without Kite Connect credentials. See
     "Reviewer access" below.
   - Ads: none. Content rating questionnaire. Target audience: 18+.
4. Store listing: short and full description, 512x512 icon, 1024x500 feature
   graphic, at least 2 phone screenshots. Keep the not-financial-advice line
   in the description.

## 3. Testing track (required for new personal accounts)
Personal accounts created after 13 Nov 2023 must run a **closed test** with at
least **12 testers opted in for 14 continuous days** before production access.

1. Testing -> Closed testing -> create track -> upload the AAB.
2. Testers tab: create an email list (or a Google Group) of testers.
3. Copy the opt-in link from the track. Testers must open it, accept, and
   install from Play. Gmail accounts only.
4. Keep >= 12 opted in for 14 days (recruit ~20; some drop out).
5. Dashboard -> **Apply for production**, answer the three questionnaires
   (about the test, the app, readiness). Google usually answers within about a
   week.
6. Production -> create release -> same or newer AAB -> roll out.

Each new upload needs a higher versionCode; CI does this automatically from the
GitHub run number.

## 4. Reviewer access and tester friction (biggest risk)
The app needs a personal Kite Connect API key (and optionally Claude/Gemini
keys), so a stranger from LinkedIn cannot try it. Both testers and Play's
reviewers hit this wall. Recommended before the closed test: add a **Demo mode**
that loads bundled sample CSV/quotes so the Scanner, Files and Agent screens can
be tried without credentials. In Play Console -> App access, say that
full features need your own Kite key and describe Demo mode.

## 5. LinkedIn tester post (draft)
> I built an Android app for Indian market data: download NSE historical data
> via Kite Connect, live quotes, a pattern scanner with backtested stats, and an
> AI chat on top. I need 12+ testers for Google Play's 14-day closed test.
> If you have an Android phone and a Gmail account, comment or DM your Gmail and
> I will add you. Takes 2 minutes: opt in, install, open it for a few days, send
> feedback. Not financial advice, information tool only.

Only collect Gmail addresses people volunteer, and keep them private.
