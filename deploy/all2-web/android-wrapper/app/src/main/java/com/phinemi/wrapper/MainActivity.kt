package com.phinemi.wrapper

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val startUrl = "https://phinemi.live/k1"
    private val remoteAdConfigUrl = "https://phinemi.live/k1/api/mobile-ads-config"
    private var bannerAdUnitId = BuildConfig.ADMOB_BANNER_ID
    private var interstitialAdUnitId = BuildConfig.ADMOB_INTERSTITIAL_ID
    private var appOpenAdUnitId = BuildConfig.ADMOB_APP_OPEN_ID

    private lateinit var webView: WebView
    private lateinit var bannerAdView: AdView
    private lateinit var fullScreenContainer: FrameLayout

    private enum class FullscreenAdFormat {
        INTERSTITIAL,
        APP_OPEN;

        companion object {
            fun fromValue(value: String?): FullscreenAdFormat {
                return if (String(value ?: "").trim().equals("APP_OPEN", ignoreCase = true)) {
                    APP_OPEN
                } else {
                    INTERSTITIAL
                }
            }
        }
    }

    private var runtimeFullscreenAdFormat = FullscreenAdFormat.fromValue(BuildConfig.ADMOB_FULLSCREEN_FORMAT)
    private var isAdMobEnabled = true
    private var autoShowAppOpenOnStart = BuildConfig.ADMOB_AUTO_SHOW_APP_OPEN_ON_START
    private var interstitialAd: InterstitialAd? = null
    private var appOpenAd: AppOpenAd? = null
    private var isLoadingInterstitial = false
    private var isLoadingAppOpen = false
    private var isShowingFullScreenAd = false
    private val handler = Handler(Looper.getMainLooper())
    private var bannerRefreshTask: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        bannerAdView = findViewById(R.id.bannerAdView)
        fullScreenContainer = findViewById(R.id.fullScreenContainer)

        MobileAds.initialize(this)
        setupWebView()
        setupBanner()
        preloadConfiguredFullscreenAd()
        fetchRemoteAdConfig()

        webView.loadUrl(startUrl)

        if (runtimeFullscreenAdFormat == FullscreenAdFormat.APP_OPEN &&
            autoShowAppOpenOnStart
        ) {
            handler.postDelayed({ showConfiguredFullscreenAd() }, 1_200L)
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                return openInsideWebView(url)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url.isNullOrBlank()) return false
                return openInsideWebView(url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            private var customView: View? = null
            private var customViewCallback: CustomViewCallback? = null

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) return
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                fullScreenContainer.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                fullScreenContainer.visibility = View.VISIBLE
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }

            override fun onHideCustomView() {
                customView?.let { fullScreenContainer.removeView(it) }
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                fullScreenContainer.visibility = View.GONE
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }

            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: android.webkit.JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message ?: "")
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: android.webkit.JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message ?: "")
                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }
        }

        webView.addJavascriptInterface(AndroidInterface(), "AndroidInterface")
    }

    private fun openInsideWebView(url: String): Boolean {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            webView.loadUrl(url)
            return true
        }
        return false
    }

    private fun setupBanner() {
        bannerAdView.adUnitId = bannerAdUnitId
        loadBanner()

        bannerRefreshTask = object : Runnable {
            override fun run() {
                loadBanner()
                handler.postDelayed(this, 60_000L)
            }
        }
        handler.postDelayed(bannerRefreshTask!!, 60_000L)
    }

    private fun loadBanner() {
        if (!isAdMobEnabled || bannerAdUnitId.isBlank()) {
            bannerAdView.visibility = View.GONE
            return
        }
        bannerAdView.visibility = View.VISIBLE
        bannerAdView.adUnitId = bannerAdUnitId
        bannerAdView.loadAd(AdRequest.Builder().build())
    }

    private fun preloadConfiguredFullscreenAd() {
        if (!isAdMobEnabled) return
        when (runtimeFullscreenAdFormat) {
            FullscreenAdFormat.INTERSTITIAL -> loadInterstitial()
            FullscreenAdFormat.APP_OPEN -> loadAppOpenAd()
        }
    }

    private fun loadInterstitial() {
        if (!isAdMobEnabled || interstitialAdUnitId.isBlank()) return
        if (isLoadingInterstitial) return
        isLoadingInterstitial = true
        InterstitialAd.load(
            this,
            interstitialAdUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoadingInterstitial = false
                    interstitialAd = ad
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdShowedFullScreenContent() {
                            isShowingFullScreenAd = true
                            interstitialAd = null
                        }

                        override fun onAdDismissedFullScreenContent() {
                            isShowingFullScreenAd = false
                            loadInterstitial()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            isShowingFullScreenAd = false
                            interstitialAd = null
                            loadInterstitial()
                        }
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingInterstitial = false
                    interstitialAd = null
                    handler.postDelayed({ loadInterstitial() }, 30_000L)
                }
            }
        )
    }

    private fun loadAppOpenAd() {
        if (!isAdMobEnabled || appOpenAdUnitId.isBlank()) return
        if (isLoadingAppOpen) return
        isLoadingAppOpen = true
        AppOpenAd.load(
            this,
            appOpenAdUnitId,
            AdRequest.Builder().build(),
            AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    isLoadingAppOpen = false
                    appOpenAd = ad
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdShowedFullScreenContent() {
                            isShowingFullScreenAd = true
                            appOpenAd = null
                        }

                        override fun onAdDismissedFullScreenContent() {
                            isShowingFullScreenAd = false
                            loadAppOpenAd()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            isShowingFullScreenAd = false
                            appOpenAd = null
                            loadAppOpenAd()
                        }
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingAppOpen = false
                    appOpenAd = null
                    handler.postDelayed({ loadAppOpenAd() }, 30_000L)
                }
            }
        )
    }

    private fun showInterstitialIfReady() {
        if (!isAdMobEnabled || interstitialAdUnitId.isBlank()) return
        if (isShowingFullScreenAd) return
        interstitialAd?.show(this) ?: loadInterstitial()
    }

    private fun showAppOpenIfReady() {
        if (!isAdMobEnabled || appOpenAdUnitId.isBlank()) return
        if (isShowingFullScreenAd) return
        appOpenAd?.show(this) ?: loadAppOpenAd()
    }

    private fun showConfiguredFullscreenAd() {
        when (runtimeFullscreenAdFormat) {
            FullscreenAdFormat.INTERSTITIAL -> showInterstitialIfReady()
            FullscreenAdFormat.APP_OPEN -> showAppOpenIfReady()
        }
    }

    private fun setFullscreenAdFormat(format: String) {
        runtimeFullscreenAdFormat = FullscreenAdFormat.fromValue(format)
        interstitialAd = null
        appOpenAd = null
        isLoadingInterstitial = false
        isLoadingAppOpen = false
        preloadConfiguredFullscreenAd()
    }

    private fun fetchRemoteAdConfig() {
        Thread {
            try {
                val connection = (URL(remoteAdConfigUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    useCaches = false
                }
                val code = connection.responseCode
                if (code !in 200..299) {
                    connection.disconnect()
                    return@Thread
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val root = JSONObject(body)
                val cfg = root.optJSONObject("config") ?: return@Thread
                val enabled = cfg.optBoolean("enabled", true)
                val bannerId = cfg.optString("banner_ad_unit_id", bannerAdUnitId).trim()
                val interstitialId = cfg.optString("interstitial_ad_unit_id", interstitialAdUnitId).trim()
                val appOpenId = cfg.optString("app_open_ad_unit_id", appOpenAdUnitId).trim()
                val fullscreenFormat = cfg.optString("fullscreen_format", runtimeFullscreenAdFormat.name)
                val autoShowAppOpen = cfg.optBoolean("auto_show_app_open_on_start", autoShowAppOpenOnStart)

                runOnUiThread {
                    applyRemoteAdConfig(
                        enabled = enabled,
                        bannerId = bannerId,
                        interstitialId = interstitialId,
                        appOpenId = appOpenId,
                        fullscreenFormat = fullscreenFormat,
                        autoShowAppOpen = autoShowAppOpen
                    )
                }
            } catch (_: Exception) {
                // Keep local build config values when remote config is unavailable.
            }
        }.start()
    }

    private fun applyRemoteAdConfig(
        enabled: Boolean,
        bannerId: String,
        interstitialId: String,
        appOpenId: String,
        fullscreenFormat: String,
        autoShowAppOpen: Boolean
    ) {
        val prevEnabled = isAdMobEnabled
        val prevBanner = bannerAdUnitId
        val prevInterstitial = interstitialAdUnitId
        val prevAppOpen = appOpenAdUnitId
        val prevFormat = runtimeFullscreenAdFormat

        isAdMobEnabled = enabled
        autoShowAppOpenOnStart = autoShowAppOpen

        if (bannerId.isNotBlank()) bannerAdUnitId = bannerId
        if (interstitialId.isNotBlank()) interstitialAdUnitId = interstitialId
        if (appOpenId.isNotBlank()) appOpenAdUnitId = appOpenId
        runtimeFullscreenAdFormat = FullscreenAdFormat.fromValue(fullscreenFormat)

        if (!isAdMobEnabled) {
            bannerAdView.visibility = View.GONE
            interstitialAd = null
            appOpenAd = null
            isLoadingInterstitial = false
            isLoadingAppOpen = false
            return
        }

        if (!prevEnabled || prevBanner != bannerAdUnitId) {
            loadBanner()
        }

        val fullscreenChanged =
            !prevEnabled ||
                prevInterstitial != interstitialAdUnitId ||
                prevAppOpen != appOpenAdUnitId ||
                prevFormat != runtimeFullscreenAdFormat
        if (fullscreenChanged) {
            interstitialAd = null
            appOpenAd = null
            isLoadingInterstitial = false
            isLoadingAppOpen = false
            preloadConfiguredFullscreenAd()
        }
    }

    inner class AndroidInterface {
        @JavascriptInterface
        fun showFullscreenAd() {
            runOnUiThread { showConfiguredFullscreenAd() }
        }

        @JavascriptInterface
        fun showInterstitial() {
            // Backward compatibility with older web scripts.
            runOnUiThread { showConfiguredFullscreenAd() }
        }

        @JavascriptInterface
        fun showAppOpenAd() {
            runOnUiThread { showAppOpenIfReady() }
        }

        @JavascriptInterface
        fun setFullscreenAdFormat(format: String) {
            runOnUiThread { setFullscreenAdFormat(format) }
        }

        @JavascriptInterface
        fun setBannerVisible(visible: Boolean) {
            runOnUiThread {
                bannerAdView.visibility = if (visible) View.VISIBLE else View.GONE
            }
        }

        @JavascriptInterface
        fun reloadBanner() {
            runOnUiThread { loadBanner() }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        bannerAdView.resume()
    }

    override fun onPause() {
        bannerAdView.pause()
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        bannerRefreshTask?.let(handler::removeCallbacks)
        bannerRefreshTask = null
        interstitialAd = null
        appOpenAd = null
        isLoadingInterstitial = false
        isLoadingAppOpen = false
        isShowingFullScreenAd = false

        bannerAdView.destroy()
        webView.removeJavascriptInterface("AndroidInterface")
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = null
        webView.destroy()

        super.onDestroy()
    }
}
