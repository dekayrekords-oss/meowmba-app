package com.example.meowmba.ui.companion

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * An explicit drawer search belongs in the Google app. A generic web intent
 * opened Chrome on the phone, even though the drawer button said Google.
 * Both supported routes stay package-scoped; an unavailable Google app is an
 * actionable failure, never an unannounced browser or WebView substitution.
 */
object GoogleAppSearch {
    const val PACKAGE_NAME = "com.google.android.googlequicksearchbox"

    enum class Result(val message: String) {
        OPENED("Search opened in the Google app."),
        EMPTY("Type something to search in the Google app."),
        NO_CONTEXT("Google search is unavailable in this session. Reopen Memba and try again."),
        NOT_INSTALLED("The Google app is not installed. Install Google from the Play Store, then try again."),
        DISABLED("The Google app is disabled. Enable Google in Android Settings → Apps, then try again."),
        BLOCKED("Android blocked opening the Google app. Check its app settings or work-profile restrictions, then try again."),
        UNSUPPORTED("The Google app could not open this search. Update or reopen Google, then try again."),
    }

    /** Call only after submit or an explicit Google button tap, never on text changes. */
    @Suppress("DEPRECATION") // Int overloads also support the app's API 31 minimum.
    fun launch(context: Context?, rawQuery: String): Result {
        val query = rawQuery.trim()
        if (query.isEmpty()) return Result.EMPTY
        if (context == null) return Result.NO_CONTEXT
        val packageManager = context.packageManager
        try {
            if (!packageManager.getApplicationInfo(PACKAGE_NAME, 0).enabled) {
                return Result.DISABLED
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return Result.NOT_INSTALLED
        } catch (_: SecurityException) {
            return Result.BLOCKED
        }

        val search = Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(SearchManager.QUERY, query)
        // Some Google app variants expose their search URL but not WEB_SEARCH.
        // Uri.Builder preserves Unicode and treats '&', '#', '+' as query text.
        val searchUrl = Intent(Intent.ACTION_VIEW, Uri.Builder()
            .scheme("https").authority("www.google.com").path("/search")
            .appendQueryParameter("q", query).build())
        for (intent in listOf(search, searchUrl)) {
            intent.setPackage(PACKAGE_NAME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                val activity = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo ?: continue
                if (activity.packageName != PACKAGE_NAME || !activity.exported ||
                    !activity.enabled || activity.applicationInfo?.enabled != true) continue
                context.startActivity(intent)
                return Result.OPENED
            } catch (_: ActivityNotFoundException) {
                // The app can update between resolution and launch. Retry only
                // its compatible URL entry point, never an arbitrary browser.
            } catch (_: SecurityException) {
                return Result.BLOCKED
            }
        }
        return Result.UNSUPPORTED
    }
}
