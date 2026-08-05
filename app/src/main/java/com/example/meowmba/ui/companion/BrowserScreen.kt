package com.example.meowmba.ui.companion

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.meowmba.core.browser.WebViewBridge

/**
 * The browser meow can drive (swipe left from the face). A plain WebView with
 * a URL bar; DOM helper functions are injected on every page load so agent
 * tools can click, type, scroll, and screenshot.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(initialUrl: String = "https://www.google.com") {
    var urlText by remember { mutableStateOf(initialUrl) }
    var currentUrl by remember { mutableStateOf(initialUrl) }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = urlText,
            onValueChange = { urlText = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            placeholder = { Text("Address", color = Color.White.copy(alpha = 0.6f)) },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    // Video-heavy sites (YouTube) render a black player in a
                    // bare WebView unless playback can start and mixed content
                    // loads; a modern UA keeps them off the "unsupported" path.
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.databaseEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            view.evaluateJavascript(WebViewBridge.DOM_HELPERS, null)
                            urlText = url
                            currentUrl = url
                        }
                    }
                    WebViewBridge.attach(this)
                    loadUrl(initialUrl)
                }
            },
            onRelease = { view -> WebViewBridge.detach(view) },
        )
    }
    // The URL bar commits on focus loss; agent tools navigate via WebViewBridge.
    LaunchedEffect(currentUrl) { }
}
