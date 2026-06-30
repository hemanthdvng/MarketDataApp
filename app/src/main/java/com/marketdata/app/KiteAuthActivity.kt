package com.marketdata.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils

class KiteAuthActivity : Activity() {

    companion object {
        const val EXTRA_API_KEY = "api_key"
        const val RESULT_REQUEST_TOKEN = "request_token"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val apiKey = intent.getStringExtra(EXTRA_API_KEY) ?: run {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
        }
        layout.addView(progress)

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 12; Pixel) AppleWebKit/537.36"

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    progress.visibility = android.view.View.GONE
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false

                    // Kite redirects to 127.0.0.1 with request_token
                    if (url.startsWith("http://127.0.0.1")) {
                        val uri = android.net.Uri.parse(url)
                        val requestToken = uri.getQueryParameter("request_token")
                        val status = uri.getQueryParameter("status")

                        if (status == "success" && !requestToken.isNullOrEmpty()) {
                            val resultIntent = Intent()
                            resultIntent.putExtra(RESULT_REQUEST_TOKEN, requestToken)
                            setResult(RESULT_OK, resultIntent)
                        } else {
                            setResult(RESULT_CANCELED)
                        }
                        finish()
                        return true
                    }
                    return false
                }
            }
        }

        layout.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(layout)

        val loginUrl = "https://kite.zerodha.com/connect/login?api_key=$apiKey&v=3"
        webView.loadUrl(loginUrl)
    }
}
