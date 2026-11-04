# MarketData: getting started

MarketData runs on **your own accounts**. You bring a Zerodha Kite Connect key for market data, and optionally a Claude or Gemini key for the AI tab. Your keys stay on your phone, stored encrypted. They are never sent to us.

## What each tab needs

| Tab | Needs |
| --- | --- |
| Login | Nothing. This is where you enter your keys. |
| Data (historical download) | Kite login on the **paid** Kite Connect plan |
| Live (live quotes) | Kite login. Live prices need the **paid** Kite Connect plan |
| Scan | A downloaded file (from the Data tab), or a Kite login on the paid plan |
| Options | Kite login |
| AI | A Claude **or** Gemini key (optional). Optional Kite login if you want live prices included |
| Files | Nothing |

Zerodha's free "Personal" API plan does not include market data. If you use it, Live and Data will tell you that your plan does not include the data.

## 1. Get your Kite Connect key (needed for market data)

1. You need a Zerodha trading account. Sign in at **developers.kite.trade**.
2. Create a new app. For the **Redirect URL** enter exactly: `http://127.0.0.1`
3. Open the app's details and copy the **API key** and **API secret**.
4. In MarketData, open the **Login** tab, paste the API key and API secret, and tap **LOGIN WITH KITE**.
5. Sign in with your Zerodha details on the Kite page that opens. You will return to the app, and it says "Connected".

Kite logins last **one day**. If a screen says your login has expired, open the Login tab and tap **REFRESH TOKEN**.

After you log in, tap **REFRESH INSTRUMENT LIST** once. This loads the list of stocks the app can look up.

## 2. Get a Claude key (optional, for the AI tab)

1. Go to **console.anthropic.com** and sign in.
2. Add a little credit under Billing (the API is pay as you go).
3. Open **API keys**, choose **Create key**, and copy it. It starts with `sk-ant-`.
4. Paste it under **AI AGENT KEYS** on the Login tab.

## 3. Get a Gemini key (optional, for the AI tab)

1. Go to **aistudio.google.com** and sign in with your Google account.
2. Choose **Get API key**, then create a key, and copy it. It starts with `AIza`.
3. Paste it under **AI AGENT KEYS** on the Login tab.

You only need one of the two AI keys.

## Good to know

- Live prices update while NSE is open: Monday to Friday, 9:15 AM to 3:30 PM IST. Outside those hours you see the last session's values.
- Downloaded files are saved to **Download/MarketData** on your phone by default.
- Pattern signals and AI answers are for information and education only. They are not investment advice.
- Never share your API secret or keys with anyone. If you think one was exposed, create a new key.
