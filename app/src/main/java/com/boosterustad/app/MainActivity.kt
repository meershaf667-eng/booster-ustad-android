package com.boosterustad.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Booster Ustad — one app, five tabs.
 * Each tab is a WebView that keeps its own state, so Facebook/Gmail stay logged in.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webViews: Map<Int, WebView>
    private var currentTab: Int = R.id.tab_booster
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    companion object {
        const val FILE_CHOOSER_REQUEST = 1001
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Cookies persist logins (Facebook, Gmail) between app restarts.
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        val boosterUrl = getString(R.string.booster_url)
        val tabs = mapOf(
            R.id.tab_booster to Pair(findViewById<WebView>(R.id.web_booster), boosterUrl),
            R.id.tab_facebook to Pair(findViewById<WebView>(R.id.web_facebook), "https://m.facebook.com"),
            R.id.tab_google to Pair(findViewById<WebView>(R.id.web_google), "https://www.google.com"),
            R.id.tab_gmail to Pair(findViewById<WebView>(R.id.web_gmail), "https://mail.google.com"),
            R.id.tab_playstore to Pair(findViewById<WebView>(R.id.web_playstore), "https://play.google.com/store")
        )

        webViews = tabs.mapValues { (_, pair) ->
            val (wv, url) = pair
            setupWebView(wv)
            if (wv.url == null) wv.loadUrl(url)
            wv
        }

        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        showTab(currentTab)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = webViews[currentTab]
                if (wv != null && wv.canGoBack()) wv.goBack() else finish()
            }
        })
    }

    private fun showTab(tabId: Int) {
        currentTab = tabId
        webViews.forEach { (id, wv) ->
            wv.visibility = if (id == tabId) WebView.VISIBLE else WebView.GONE
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(wv: WebView) {
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return false // keep every link inside the app
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            // File uploads (Gallery photos/videos) inside the Booster tab.
            override fun onShowFileChooser(
                view: WebView, filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                val intent = fileChooserParams.createIntent()
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST)
                } catch (e: Exception) {
                    fileChooserCallback = null
                    return false
                }
                return true
            }
        }
    }

    @Deprecated("Used for the file chooser result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST) {
            val results = if (resultCode == Activity.RESULT_OK) {
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            } else null
            fileChooserCallback?.onReceiveValue(results)
            fileChooserCallback = null
        }
    }

    override fun onPause() {
        super.onPause()
        webViews.values.forEach { it.onPause() }
    }

    override fun onResume() {
        super.onResume()
        webViews.values.forEach { it.onResume() }
    }
}
