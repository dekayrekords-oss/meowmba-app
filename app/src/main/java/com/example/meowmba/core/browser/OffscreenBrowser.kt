package com.example.meowmba.core.browser

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.meowmba.core.security.CleartextPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A WebView the agent drives that the user never sees.
 *
 * The swipe-left browser panel was removed because it swallowed every
 * utterance while it was open and covered the face. The agent still needs a
 * real browser (render JS, click, read the DOM) — so the engine moved here,
 * attached to the decor view at 1×1 and INVISIBLE. It is laid out and renders
 * (which a GONE view would not), but nothing is drawn over the companion.
 *
 * Everything hops to [Dispatchers.Main] because WebView is main-thread only.
 */
class OffscreenBrowser(private val context: Context) {

    @Volatile
    private var webView: WebView? = null

    @Volatile
    private var lastUrl: String = ""

    /** Attaches the hidden engine. Safe to call repeatedly; only binds once. */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun attach(): Boolean = withContext(Dispatchers.Main) {
        if (webView != null) return@withContext true
        // Offscreen WebViews must be created on the main thread. Using the
        // application context here is deliberate: the ViewModel only has that.
        // A WebView created this way can still load pages and run JS even
        // without being added to a window — enough for the agent's
        // navigate/read/click/type tools. No decor-view hack needed.
        val view = WebView(context.applicationContext).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = WebSettings.LOAD_DEFAULT
                // A hidden browser has no business advertising itself as one.
                userAgentString = "${settings.userAgentString} MeowmbaAgent/1.0"
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: android.webkit.WebResourceRequest,
                ): Boolean = !CleartextPolicy.isSafeBrowseUrl(request.url?.toString().orEmpty())

                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(WebViewBridge.DOM_HELPERS, null)
                    lastUrl = url
                }
            }
            webChromeClient = WebChromeClient()
        }
        webView = view
        WebViewBridge.attach(view)
        true
    }

    fun detach() {
        webView?.let { view ->
            WebViewBridge.detach(view)
            runCatching { view.destroy() }
        }
        webView = null
    }

    val currentUrl: String get() = lastUrl

    /** Loads [url]. Suspends until the page reports finished (or 20s times out). */
    suspend fun navigate(url: String): String {
        if (!attach()) return "error: no window to attach the browser to"
        var target = WebViewBridge.normalizeUrl(url)
        // Single-word queries like "yahu" (Whisper heard "yahoo" as "yahu") are not
        // URLs — treat them as a web search so the browser actually shows something
        // rather than failing on https://yahu.
        val rawHost = target.removePrefix("https://").removePrefix("http://").substringBefore("/")
        if (!rawHost.contains('.') && !rawHost.contains(' ') && rawHost.length <= 30) {
            target = "https://www.google.com/search?q=" +
                java.net.URLEncoder.encode(url, "UTF-8")
        }
        if (!CleartextPolicy.isSafeBrowseUrl(target)) {
            return "error: refused to open $target (blocked by browse policy)"
        }
        // The user asked for visible Chrome — open the same URL there so they see it.
        openInChrome(target)
        return withContext(Dispatchers.Main) {
            val view = webView ?: return@withContext "error: browser not attached"
            suspendCancellableCoroutine { cont ->
                view.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView,
                        request: android.webkit.WebResourceRequest,
                    ): Boolean = !CleartextPolicy.isSafeBrowseUrl(request.url?.toString().orEmpty())

                    override fun onPageFinished(v: WebView, url: String) {
                        v.evaluateJavascript(WebViewBridge.DOM_HELPERS, null)
                        lastUrl = url
                        if (cont.isActive) cont.resume("loaded: $url")
                    }

                    @Deprecated("deprecated in API 24 but required for older devices")
                    override fun onReceivedError(
                        v: WebView,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?,
                    ) {
                        if (cont.isActive) {
                            cont.resume("error: $description ($errorCode) at $failingUrl")
                        }
                    }
                }
                view.loadUrl(target)
                // A page that never finishes must not hang the agent forever.
                view.postDelayed({
                    if (cont.isActive) cont.resume("loaded (timeout): $target")
                }, LOAD_TIMEOUT_MS)
            }
        }
    }

    /**
     * Readable text of the page. Uses the existing `__meow.readable()` helper
     * (title + headings + paragraphs) rather than raw innerText — innerText
     * drags in nav chrome and cookie banners.
     */
    suspend fun readText(): String = eval("window.__meow.readable()")

    /** Links on the page, as "text -> href" lines. */
    suspend fun links(): String = eval("window.__meow.scrape()")

    /** Full DOM, capped so a huge page cannot flood a prompt. */
    suspend fun html(maxChars: Int = 12_000): String =
        eval("window.__meow.pageHtml()").let { out ->
            if (out.length > maxChars) out.take(maxChars) + "…[truncated]" else out
        }

    /**
     * Click by CSS selector, or by text with `tag=text` (e.g. `button=Sign in`)
     * — the helper already falls back to text matching when the selector
     * contains `=`.
     */
    suspend fun click(selector: String): String =
        eval("window.__meow.click(${js(selector)})")

    suspend fun type(selector: String, text: String): String =
        eval("window.__meow.type(${js(selector)}, ${js(text)})")

    suspend fun scroll(dy: Int): String =
        eval("window.__meow.scroll($dy)")

    suspend fun eval(js: String): String {
        if (!attach()) return "error: no window to attach the browser to"
        return withContext(Dispatchers.Main) {
            val view = webView ?: return@withContext "error: browser not attached"
            suspendCancellableCoroutine { cont ->
                try {
                    view.evaluateJavascript(js) { value ->
                        if (cont.isActive) cont.resume(WebViewBridge.decodeJsValue(value))
                    }
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
        }
    }

    /** Single quotes a value for inline JS. Escapes what would break out. */
    private fun js(value: String): String = "'" +
        value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n") + "'"

    private fun openInChrome(url: String) {
        try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url),
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            // Prefer Chrome, but any browser will do — don't fail if Chrome is missing.
            val chrome = listOf(
                "com.android.chrome",
                "com.chrome.beta",
                "com.chrome.dev",
                "com.google.android.apps.chrome",
            ).firstOrNull { pkg ->
                runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
            }
            if (chrome != null) intent.setPackage(chrome)
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val LOAD_TIMEOUT_MS = 20_000L
    }
}
