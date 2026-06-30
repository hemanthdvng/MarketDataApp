package com.marketdata.app.util

object NiftySymbols {

    val NIFTY_50 = listOf(
        "ADANIENT", "ADANIPORTS", "APOLLOHOSP", "ASIANPAINT", "AXISBANK",
        "BAJAJ-AUTO", "BAJAJFINSV", "BAJFINANCE", "BPCL", "BHARTIARTL",
        "BRITANNIA", "CIPLA", "COALINDIA", "DIVISLAB", "DRREDDY",
        "EICHERMOT", "GRASIM", "HCLTECH", "HDFCBANK", "HDFCLIFE",
        "HEROMOTOCO", "HINDALCO", "HINDUNILVR", "ICICIBANK", "INDUSINDBK",
        "INFY", "ITC", "JSWSTEEL", "KOTAKBANK", "LT",
        "M&M", "MARUTI", "NESTLEIND", "NTPC", "ONGC",
        "POWERGRID", "RELIANCE", "SBILIFE", "SBIN", "SHRIRAMFIN",
        "SUNPHARMA", "TATACONSUM", "TATAMOTORS", "TATASTEEL", "TCS",
        "TECHM", "TITAN", "ULTRACEMCO", "WIPRO", "ZOMATO"
    )

    val NIFTY_100_EXTRA = listOf(
        "ABB", "AMBUJACEM", "AUROPHARMA", "BANDHANBNK", "BANKBARODA",
        "BEL", "BERGEPAINT", "BOSCHLTD", "CANBK", "CHOLAFIN",
        "COLPAL", "DABUR", "DMART", "FEDERALBNK", "GAIL",
        "GODREJCP", "HAVELLS", "IDFCFIRSTB", "INDHOTEL", "INDUSTOWER",
        "IOC", "IRCTC", "JINDALSTEL", "JUBLFOOD", "LICHSGFIN",
        "LUPIN", "MARICO", "MAXHEALTH", "MCDOWELL-N", "MPHASIS",
        "MUTHOOTFIN", "NAUKRI", "OBEROIRLTY", "OFSS", "PAGEIND",
        "PERSISTENT", "PETRONET", "PIDILITIND", "PIIND", "PNB",
        "POLYCAB", "RECLTD", "SAIL", "SIEMENS", "SRF",
        "TATACOMM", "TRENT", "TVSMOTOR", "UPL", "VEDL"
    )

    val NIFTY_100 = NIFTY_50 + NIFTY_100_EXTRA

    // Instrument tokens for indices (NSE segment)
    val INDEX_TOKENS = mapOf(
        "NIFTY 50" to 256265L,
        "NIFTY BANK" to 260105L,
        "NIFTY IT" to 259849L,
        "NIFTY MIDCAP 50" to 288009L,
        "NIFTY NEXT 50" to 270857L,
        "INDIA VIX" to 264969L,
        "NIFTY AUTO" to 261889L,
        "NIFTY FMCG" to 261121L,
        "NIFTY METAL" to 262921L,
        "NIFTY PHARMA" to 261641L
    )

    // Max days per request for each interval
    val INTERVAL_DAY_LIMITS = mapOf(
        "minute" to 60,
        "3minute" to 100,
        "5minute" to 100,
        "10minute" to 100,
        "15minute" to 200,
        "30minute" to 200,
        "60minute" to 400,
        "day" to 2000,
        "week" to 2000
    )

    val INTERVALS = listOf(
        Triple("1 Min", "minute", "1min"),
        Triple("3 Min", "3minute", "3min"),
        Triple("5 Min", "5minute", "5min"),
        Triple("10 Min", "10minute", "10min"),
        Triple("15 Min", "15minute", "15min"),
        Triple("30 Min", "30minute", "30min"),
        Triple("1 Hour", "60minute", "1h"),
        Triple("Day", "day", "day"),
        Triple("Week", "week", "week")   // aggregated from day
    )
}
