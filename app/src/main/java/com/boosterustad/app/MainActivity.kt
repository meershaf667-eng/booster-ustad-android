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

        // Booster URL: user can change it in Settings (saved on phone).
        // Default = Render URL; for home WiFi use http://<laptop-ip>:3000
        val prefs = getSharedPreferences("booster_ustad", MODE_PRIVATE)
        val boosterUrl = prefs.getString("booster_url", getString(R.string.booster_url))
            ?: getString(R.string.booster_url)
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
            if (item.itemId == R.id.tab_settings) {
                showUrlSettings()
                return@setOnItemSelectedListener false
            }
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

    /** Settings dialog: change the Booster Ustad server URL (e.g. home WiFi laptop IP). */
    private fun showUrlSettings() {
        val prefs = getSharedPreferences("booster_ustad", MODE_PRIVATE)
        val current = prefs.getString("booster_url", getString(R.string.booster_url))
        val input = android.widget.EditText(this).apply {
            setText(current)
            hint = "http://192.168.1.10:3000"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Booster Ustad URL")
            .setMessage("Apne laptop ka address likhein.\nGhar WiFi: http://192.168.1.X:3000\n(Jahan X aapke laptop ka number hai)")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                var url = input.text.toString().trim().trimEnd('/')
                if (url.isNotEmpty()) {
                    if (!url.startsWith("http")) url = "http://$url"
                    prefs.edit().putString("booster_url", url).apply()
                    webViews[R.id.tab_booster]?.loadUrl(url)
                    showTab(R.id.tab_booster)
                    findViewById<BottomNavigationView>(R.id.bottom_nav).selectedItemId = R.id.tab_booster
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        webViews.values.forEach { it.onResume() }
    }
}
