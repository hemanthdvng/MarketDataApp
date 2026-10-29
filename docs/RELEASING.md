# Releasing a new version (phone-only routine)

Everything below works from a phone browser. The app is already on Google Play;
this is the loop for every update.

## 1. Make the change
- Work on a **branch**, never directly on `main` (an AI assistant such as Claude
  Code can do this; give it `docs/AI_HANDOFF_PROMPT.md`).
- Bump `versionName` in `app/build.gradle.kts` (for example `1.1` -> `1.2`).
  Do **not** touch `versionCode`: CI sets it from the GitHub run number, so it
  always goes up.
- Re-check AI model IDs in `data/models/Models.kt` against the Claude and Gemini
  docs. Providers retire old IDs and an old ID makes the AI tab fail.

## 2. Let GitHub build it
- Open a **pull request** into `main`. The workflow **Build APK and AAB** runs on
  every pull request.
- Open the PR's **Checks** tab. If it is red, the change does not compile.
  Fix it before merging (paste the error into your AI assistant).

## 3. Merge, then build the release
- Merge the PR. A fresh build runs on `main`.
- When it is green, open the run (**Actions** tab) and download the artifact
  **MarketDataApp-release-aab**. It is a zip; unzip it to get `app-release.aab`.
- Check the step **Restore upload keystore**. It must not say "using debug
  signing". If it does, the four signing secrets are missing and Play will
  reject the file.

## 4. Upload to Play Console
- **While the closed test is running:** Testing -> Closed testing -> Alpha ->
  Create new release.
- **After production access is granted:** Production -> Create new release.
- Upload the `.aab`, write release notes, tap Save, then send for review from
  Publishing overview. Updates to the same closed-test track should not restart
  the 14-day clock, but testers must stay opted in.

## 5. Re-check Play Console when behaviour changes
- Added ads or an ad SDK? Change **Ads** to Yes, declare the advertising ID, add
  the `AD_ID` permission, update Data safety, and update `PRIVACY_POLICY.md`.
- New permission, foreground service or data sent to a new third party? Update
  Data safety, the privacy policy, and the matching declarations.
- New store screenshots or description text: Grow users -> Store presence.

## Rules that must never be broken
- Never commit `upload.jks`, passwords or API keys. `.gitignore` blocks `*.jks`.
- Keep a private backup of the upload keystore and its passwords.
- `applicationId` (`com.marketdata.app`) can never change after release.
- Keep the in-app and store text free of NSE / Zerodha / Kite trademarks in the
  app name and icon. Mentioning "works with your Zerodha Kite Connect account"
  in the description is fine.
