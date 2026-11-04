package com.marketdata.app.util

import android.content.Context
import com.marketdata.app.data.prefs.SecurePrefs

/**
 * Turns raw failures (HTTP codes, JSON bodies, exception names) into short
 * plain-language messages that say what happened and what to do next.
 * Every screen should show the result of these, never the raw text.
 */
/** An exception whose message is already written for the user. */
class FriendlyException(message: String, cause: Throwable? = null) : Exception(message, cause)

object FriendlyError {

    const val NOT_LOGGED_IN =
        "You are not logged in to Kite yet. Open the Login tab, enter your Kite API key and " +
            "secret, then tap LOGIN WITH KITE."

    const val SESSION_EXPIRED =
        "Your Kite login has expired (Kite logins last one day). Open the Login tab and tap " +
            "REFRESH TOKEN, then try again."

    const val PAID_PLAN_NEEDED =
        "Your Kite plan does not include this data. Live prices and historical candles need " +
            "Zerodha's paid Kite Connect plan (about Rs 500 per month). Without it this action " +
            "cannot work."

    const val NO_INTERNET =
        "Could not reach the internet. Check your connection and try again."

    /** Returns a message if no Kite login is saved, else null. Call before any Kite request. */
    fun kiteLoginProblem(context: Context): String? {
        val prefs = SecurePrefs.getInstance(context)
        return if (prefs.apiKey.isBlank() || prefs.accessToken.isBlank()) NOT_LOGGED_IN else null
    }

    /** True for problems that will repeat on every symbol, so a bulk job should stop at once. */
    fun isFatalForBulk(raw: String?): Boolean {
        val r = (raw ?: "").lowercase()
        return r.contains("not logged in") || r.contains("login has expired") ||
            r.contains("plan does not include") || r.contains("could not reach the internet") ||
            r.contains("too many requests")
    }

    /** Kite (api.kite.trade) failures. */
    fun kite(raw: String?): String {
        val text = raw?.trim().orEmpty()
        val r = text.lowercase()
        return when {
            text.isEmpty() -> "Something went wrong while contacting Kite. Please try again."
            // Already one of our own friendly messages.
            r.startsWith("you are not logged in") || r.startsWith("your kite") ||
                r.startsWith("could not reach") || r.startsWith("too many requests") -> text
            r.contains("empty `api_key`") || r.contains("empty `access_token`") ||
                r.contains("empty api_key") || r.contains("empty access_token") -> NOT_LOGGED_IN
            r.contains("tokenexception") || r.contains("incorrect `api_key` or `access_token`") ||
                r.contains("session expired") || r.contains("invalid session") -> SESSION_EXPIRED
            r.contains("permissionexception") || r.contains("insufficient permission") ->
                PAID_PLAN_NEEDED
            hasCode(r, 401) -> SESSION_EXPIRED
            hasCode(r, 403) -> SESSION_EXPIRED + " If it keeps failing after you log in again, " +
                "your plan may not include this data."
            hasCode(r, 429) || r.contains("too many requests") ->
                "Too many requests in a short time. Wait a minute and try again."
            hasCode(r, 400) && r.contains("input") ->
                "Kite did not accept that request. Check the symbol names and try again."
            hasCode(r, 500) || hasCode(r, 502) || hasCode(r, 503) || hasCode(r, 504) ->
                "Kite's servers are having trouble right now. Try again in a few minutes."
            looksOffline(r) -> NO_INTERNET
            r.contains("timeout") || r.contains("timed out") ->
                "Kite took too long to answer. Check your internet and try again."
            else -> "Something went wrong while contacting Kite. Please try again. If it keeps " +
                "happening, log in again on the Login tab."
        }
    }

    fun kite(e: Throwable): String =
        if (e is FriendlyException) e.message ?: kite(null as String?) else kite(e.message ?: e::class.java.simpleName)

    /** The friendly text if [e] already carries one, otherwise [fallback]. */
    fun orElse(e: Throwable, fallback: String): String =
        if (e is FriendlyException) e.message ?: fallback else fallback

    /** Kite login screen failures (wrong key/secret, closed login page, etc). */
    fun login(raw: String?): String {
        val r = raw.orEmpty().lowercase()
        return when {
            r.contains("checksum") || r.contains("invalid") || r.contains("incorrect") ||
                r.contains("inputexception") ->
                "Kite did not accept the login. Check your API key and API secret for typing " +
                    "mistakes, and make sure your Kite app's redirect URL is http://127.0.0.1"
            looksOffline(r) -> NO_INTERNET
            else -> "Login did not finish. Please try again. If it keeps failing, check that " +
                "your API key and secret are correct."
        }
    }

    /** Claude or Gemini failures. [provider] is "Claude" or "Gemini". */
    fun ai(raw: String?, provider: String): String {
        val text = raw?.trim().orEmpty()
        val r = text.lowercase()
        return when {
            r.contains("api key not set") || r.contains("no claude api key") || r.contains("no gemini api key") ->
                "No $provider key is saved. Open the Login tab, paste your $provider key under " +
                    "AI AGENT KEYS, then try again."
            hasCode(r, 401) || r.contains("authentication_error") || r.contains("api key not valid") ||
                r.contains("api_key_invalid") || hasCode(r, 403) || r.contains("permission_denied") ->
                "$provider did not accept your key. Open the Login tab and check that the key was " +
                    "pasted completely, with no spaces."
            hasCode(r, 429) || r.contains("rate_limit") || r.contains("resource_exhausted") ||
                r.contains("quota") ->
                "$provider says you have hit a limit (too many requests, or your credit or free " +
                    "quota is used up). Wait a little, or check your $provider account."
            hasCode(r, 404) || r.contains("not_found") || r.contains("model") && r.contains("not") ->
                "This AI model is not available for your key. Pick a different model at the top " +
                    "of the AI tab."
            hasCode(r, 529) || r.contains("overloaded") || hasCode(r, 503) || r.contains("unavailable") ->
                "$provider is very busy right now. Try again in a minute, or switch model."
            hasCode(r, 400) && (r.contains("credit") || r.contains("billing")) ->
                "Your $provider account has no credit left. Add credit in your $provider account."
            hasCode(r, 500) || hasCode(r, 502) || hasCode(r, 504) ->
                "$provider had a problem on its side. Try again in a minute."
            looksOffline(r) -> NO_INTERNET
            r.contains("timeout") || r.contains("timed out") ->
                "$provider took too long to answer. Try again, or ask a shorter question."
            r.contains("blocked the request") -> text // already worded for the user
            r.contains("returned no") || r.contains("empty response") -> text
            else -> "The AI could not answer right now. Please try again."
        }
    }

    fun ai(e: Throwable, provider: String): String =
        if (e is FriendlyException) e.message ?: ai(null as String?, provider)
        else ai(e.message ?: e::class.java.simpleName, provider)

    private fun hasCode(lower: String, code: Int): Boolean =
        lower.contains("http $code") || lower.contains("error $code") || lower.contains("code $code") ||
            lower.contains("\"code\":$code") || lower.contains("\"code\": $code")

    private fun looksOffline(lower: String): Boolean =
        lower.contains("unknownhost") || lower.contains("unable to resolve host") ||
            lower.contains("failed to connect") || lower.contains("network is unreachable") ||
            lower.contains("no address associated") || lower.contains("connectexception")
}
