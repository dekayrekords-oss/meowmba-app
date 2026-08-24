package com.example.meowmba.ui.companion

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.meowmba.core.browser.WebViewBridge

/**
 * Swipe-left agent browser: Meow builds HTML here and scrapes live pages.
 * DOM helpers inject on every load so the agent loop can click, type, inject, scrape.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    goal: String? = null,
    source: String = "",
    sourceKind: String = "html",
    initialUrl: String? = null,
) {
    val start = initialUrl ?: "workshop"
    var urlText by remember { mutableStateOf(if (start == "workshop") "" else start) }
    val focus = LocalFocusManager.current

    fun go(raw: String) {
        val t = raw.trim()
        if (t.isEmpty()) {
            WebViewBridge.loadWorkshop()
        } else {
            WebViewBridge.loadUrl(t)
        }
        focus.clearFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
    ) {
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Agent browser", color = Color.White, fontSize = 18.sp)
                Text(
                    if (goal.isNullOrBlank()) "Build a page or scrape a URL"
                    else goal,
                    color = Color(0xFF8B949E),
                    fontSize = 12.sp,
                    maxLines = 2,
                )
            }
            TextButton(onClick = {
                urlText = ""
                WebViewBridge.loadWorkshop()
            }) {
                Text("Workshop", color = Color(0xFF58A6FF), fontSize = 13.sp)
            }
        }
        OutlinedTextField(
            value = urlText,
            onValueChange = { urlText = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            placeholder = { Text("URL — then say scrape this page", color = Color.White.copy(alpha = 0.5f)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { go(urlText) }),
            shape = RoundedCornerShape(12.dp),
        )
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.databaseEnabled = false
                    if (android.os.Build.VERSION.SDK_INT >= 26) {
                        settings.safeBrowsingEnabled = true
                    }
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                        ): Boolean {
                            val next = request.url?.toString() ?: return true
                            if (!com.example.meowmba.core.security.CleartextPolicy.isSafeBrowseUrl(next)) {
                                return true
                            }
                            return false
                        }
                        override fun onPageFinished(view: WebView, url: String) {
                            view.evaluateJavascript(WebViewBridge.DOM_HELPERS, null)
                            if (!url.startsWith("data:") && url != "about:blank") {
                                urlText = url
                            }
                        }
                    }
                    WebViewBridge.attach(this)
                    if (start == "workshop") {
                        loadDataWithBaseURL(
                            "https://meow.local/workshop",
                            WebViewBridge.WORKSHOP_HTML,
                            "text/html",
                            "utf-8",
                            null,
                        )
                    } else {
                        loadUrl(WebViewBridge.normalizeUrl(start))
                    }
                }
            },
            onRelease = { view -> WebViewBridge.detach(view) },
        )
        LiveCodePanel(kind = sourceKind, code = source)
    }
}

@Composable
private fun LiveCodePanel(kind: String, code: String) {
    val label = when (kind) {
        "js" -> "Live JS"
        "scrape" -> "Scrape"
        "prompt" -> "Live prompt"
        "code" -> "Live code"
        "media" -> "Media"
        else -> "Live HTML"
    }
    val body = code.ifBlank {
        if (kind == "prompt") "Waiting for the model…"
        else "A first draft should land here in a moment."
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 280.dp)
            .background(Color(0xFF010409))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            "$label · ${if (code.isBlank()) "idle" else "${code.length} chars"}",
            color = Color(0xFF58A6FF),
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(4.dp))
        SelectionContainer {
            Text(
                text = body,
                color = Color(0xFFC9D1D9),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}
