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
                onResult(value?.trim('"')?.take(4096) ?: "null")
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
      return h.length > 6000 ? h.slice(0, 6000) + '...[truncated]' : h;
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
}
