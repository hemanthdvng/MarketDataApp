# MarketDataApp (Nifty Bot)

Android app for downloading NSE historical market data via Kite Connect API, viewing live quotes, and getting AI-powered stock analysis using Claude or Gemini.

## Features
- Kite Connect OAuth login (WebView-based, 127.0.0.1 redirect)
- Historical data download: single/multi stock, indices, Nifty 50, Nifty 100
- Intervals: 1min to weekly, with OI data support
- Auto-chunked downloads to handle Kite's date range limits
- Live on-demand quotes
- CSV export to user-selected folder (SAF)
- AI Agent chat (Claude or Gemini) with live market data context
- Local file browser for downloaded CSVs

## Setup
1. Open in Android Studio (or build via GitHub Actions)
2. On first launch, enter your Kite API Key + Secret (Settings/Login tab)
3. Tap "Login with Kite" to authenticate
4. Enter Claude/Gemini API keys for the AI Agent tab

## Build
GitHub Actions automatically builds a release APK on every push to `main`.
Download the APK from the Actions tab → latest run → Artifacts.
