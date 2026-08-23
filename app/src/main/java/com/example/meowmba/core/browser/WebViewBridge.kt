package com.example.meowmba.core.browser

import android.annotation.SuppressLint
import android.webkit.WebView
import java.io.File
import java.lang.ref.WeakReference

/**
 * Bridge between the agent tools and the single live WebView. Tools schedule
 * javascript on the UI thread through here; the browser screen registers its
 * WebView when composed and clears it when disposed.
 */
object WebViewBridge {
    private var viewRef = WeakReference<WebView?>(null)

    fun attach(view: WebView) {
        viewRef = WeakReference(view)
    }

    fun detach(view: WebView) {
        if (viewRef.get() === view) viewRef = WeakReference(null)
    }

    val isAttached: Boolean get() = viewRef.get() != null

    fun loadUrl(url: String) {
        val view = viewRef.get() ?: return
        view.post { view.loadUrl(normalizeUrl(url)) }
    }

    fun loadWorkshop() {
        loadHtml(WORKSHOP_HTML)
    }

    /** Replace the document. Used for first-draft pages and local media. */
    fun loadHtml(html: String) {
        val view = viewRef.get() ?: return
        view.post {
            view.loadDataWithBaseURL(
                "https://meow.local/workshop",
                html,
                "text/html",
                "utf-8",
                null,
            )
        }
    }

    fun normalizeUrl(raw: String): String {
        val t = raw.trim()
        val lower = t.lowercase()
        if (lower.startsWith("javascript:") ||
            lower.startsWith("file:") ||
            lower.startsWith("content:") ||
            lower.startsWith("intent:")
        ) {
            return "about:blank"
        }
        if (t.startsWith("http://") || t.startsWith("https://") || t.startsWith("data:")) {
            return if (com.example.meowmba.core.security.CleartextPolicy.isSafeBrowseUrl(t)) {
                t
            } else {
                t.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
            }
        }
        if (t.startsWith("www.")) return "https://$t"
        return "https://$t"
    }

    /** Runs JS on the UI thread, returns the result string via callback. */
    @SuppressLint("SetJavaScriptEnabled")
    fun evaluate(script: String, onResult: (String) -> Unit) {
        val view = viewRef.get()
        if (view == null) {
            onResult("ERROR: no browser is open (swipe left on the face first)")
            return
        }
        view.post {
            view.evaluateJavascript(script) { value ->
                onResult(decodeJsValue(value))
            }
        }
    }

    /** Renders the current page into a PNG for the vision pipeline. */
    fun screenshot(dir: File, onResult: (String) -> Unit) {
        val view = viewRef.get()
        if (view == null) {
            onResult("ERROR: no browser is open")
            return
        }
        view.post {
            runCatching {
                val bmp = android.graphics.Bitmap.createBitmap(
                    view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                view.draw(canvas)
                val file = File(dir, "web_screenshot_${System.currentTimeMillis()}.png")
                file.outputStream().use {
                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it)
                }
                bmp.recycle()
                onResult(file.absolutePath)
            }.getOrElse { onResult("ERROR: screenshot failed: ${it.message}") }
        }
    }

    /** Renders the current page into in-memory JPEG bytes (vision feed). */
    fun captureJpeg(quality: Int = 80, onResult: (ByteArray?) -> Unit) {
        val view = viewRef.get()
        if (view == null) {
            onResult(null)
            return
        }
        view.post {
            runCatching {
                val bmp = android.graphics.Bitmap.createBitmap(
                    view.width.coerceAtLeast(1), view.height.coerceAtLeast(1),
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                view.draw(canvas)
                val out = java.io.ByteArrayOutputStream()
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
                bmp.recycle()
                onResult(out.toByteArray())
            }.getOrElse { onResult(null) }
        }
    }

    /**
     * evaluateJavascript returns a JSON-encoded value (a quoted string with \n,
     * \" and \uXXXX escapes). Trimming the quotes alone leaves those escapes
     * literal, which corrupted saved pages (they rendered as a black/garbage
     * canvas). Decode properly; fall back to the old trim if parsing fails.
     */
    internal fun decodeJsValue(value: String?): String {
        if (value == null) return "null"
        val trimmed = value.trim()
        if (trimmed == "null") return "null"
        if (!trimmed.startsWith("\"") || !trimmed.endsWith("\"") || trimmed.length < 2)
            return trimmed
        return unescapeJsonString(trimmed.substring(1, trimmed.length - 1))
    }

    /** Decodes \n \t \r \" \\ \/ and \uXXXX inside a JSON string body. */
    internal fun unescapeJsonString(body: String): String {
        val sb = StringBuilder(body.length)
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length) {
                when (val n = body[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append('\u000C'); i += 2 }
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'u' -> {
                        if (i + 5 < body.length + 1) {
                            val hex = body.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16)
                            if (code != null) { sb.append(code.toChar()); i += 6 } else { sb.append(c); i++ }
                        } else { sb.append(c); i++ }
                    }
                    else -> { sb.append(c); i++ }
                }
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    /** DOM helpers injected on every page load. */
    const val DOM_HELPERS = """
(function(){
  if (window.__meow) return;
  window.__meow = {
    find: function(sel) {
      try { return document.querySelector(sel); } catch(e) { return null; }
    },
    findByText: function(tag, text) {
      var els = document.getElementsByTagName(tag || '*');
      for (var i = 0; i < els.length; i++) {
        if ((els[i].textContent || '').trim().toLowerCase() === text.toLowerCase()) return els[i];
      }
      return null;
    },
    click: function(sel) {
      var el = sel.indexOf('=') === -1 ? this.find(sel) : this.findByText(sel.split('=')[0], sel.split('=').slice(1).join('='));
      if (!el) return 'not found: ' + sel;
      el.click(); return 'clicked: ' + sel;
    },
    type: function(sel, text) {
      var el = this.find(sel);
      if (!el) return 'not found: ' + sel;
      el.focus();
      el.value = text;
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
      return 'typed into: ' + sel;
    },
    scroll: function(dy) { window.scrollBy(0, dy); return 'scrolled ' + dy; },
    inject: function(html) {
      document.open(); document.write(html); document.close();
      return 'injected ' + html.length + ' chars';
    },
    pageHtml: function() {
      var h = (document.documentElement && document.documentElement.outerHTML) || '';
      return h.length > 200000 ? h.slice(0, 200000) + '...[truncated]' : h;
    },
    readable: function() {
      var bits = [];
      if (document.title) bits.push('# ' + document.title);
      bits.push(location.href);
      var nodes = document.querySelectorAll('h1,h2,h3,p,li,th,td');
      for (var i = 0; i < nodes.length && bits.length < 220; i++) {
        var x = (nodes[i].innerText || '').replace(/\s+/g, ' ').trim();
        if (x) bits.push(x);
      }
      return bits.join('\n').slice(0, 20000);
    },
    scrape: function() {
      var links = [];
      var as = document.getElementsByTagName('a');
      for (var i = 0; i < as.length && links.length < 40; i++) {
        var t = (as[i].textContent || '').replace(/\s+/g, ' ').trim();
        if (t) links.push(t.slice(0, 80) + ' -> ' + as[i].href);
      }
      return JSON.stringify({
        title: document.title,
        url: location.href,
        text: this.readable(),
        links: links
      });
    },
    title: function() { return document.title + ' | ' + location.href; },
    links: function() {
      var out = [];
      var as = document.getElementsByTagName('a');
      for (var i = 0; i < as.length && out.length < 30; i++) {
        var t = (as[i].textContent || '').trim();
        if (t) out.push(t + ' -> ' + as[i].href);
      }
      return out.join('\n');
    }
  };
})();
"""

    const val WORKSHOP_HTML = """<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Agent browser</title>
<style>
  body{margin:0;background:#0d1117;color:#e6edf3;font-family:system-ui,sans-serif;padding:28px}
  h1{font-size:22px;margin:0 0 10px}
  p{color:#8b949e;line-height:1.45;max-width:34em}
  code{color:#58a6ff}
</style>
</head>
<body>
<h1>Agent browser</h1>
<p>Say <code>make a page about …</code> and I will build it here.</p>
<p>Open a URL, then say <code>scrape this page</code> to pull the text and links.</p>
</body>
</html>"""
}
