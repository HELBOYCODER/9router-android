package com.ninerouter.android.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.ninerouter.android.R

/**
 * Simple WebView wrapper that loads the local 9Router dashboard with a small
 * toolbar (copy URL, open in external browser, back). JavaScript is enabled
 * because the dashboard is a React/Next.js SPA that requires it.
 */
class DashboardActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_URL = "dashboard_url"
        fun newIntent(context: Context, url: String): Intent =
            Intent(context, DashboardActivity::class.java).putExtra(EXTRA_URL, url)
    }

    private var url: String = "http://127.0.0.1:20128/dashboard"
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        url = intent.getStringExtra(EXTRA_URL) ?: url

        setContentView(R.layout.activity_dashboard)
        webView = findViewById(R.id.webView)
        progress = findViewById(R.id.progress)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                return !(uri.scheme == "http" || uri.scheme == "https")
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }

        val copy = findViewById<View>(R.id.actionCopy)
        val open = findViewById<View>(R.id.actionOpen)
        val back = findViewById<View>(R.id.actionBack)

        copy.setOnClickListener {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("9Router dashboard", url))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }
        open.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webView.url ?: url)))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "No browser available", Toast.LENGTH_SHORT).show()
            }
        }
        back.setOnClickListener {
            if (webView.canGoBack()) webView.goBack() else finish()
        }

        if (savedInstanceState == null) {
            webView.loadUrl(url)
        }
    }

    @Deprecated("Deprecated in favor of the system back dispatching")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }
}
