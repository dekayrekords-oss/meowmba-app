package com.example.meowmba.core.nav

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Hands a destination off to a maps app for turn-by-turn.
 *
 * We navigate to explicit coordinates rather than re-sending the search text, so the
 * place the user picked from our list is the place they get routed to. The name is
 * passed only as a map label.
 */
object NavigationLauncher {

    /**
     * Starts turn-by-turn navigation to [place].
     * @return false if no app on the device could handle it.
     */
    fun navigateTo(context: Context, place: Place): Boolean {
        // google.navigation: goes straight into turn-by-turn in Google Maps.
        val nav = Uri.parse("google.navigation:q=${place.lat},${place.lon}&mode=d")
        if (start(context, Intent(Intent.ACTION_VIEW, nav).setPackage(GOOGLE_MAPS))) return true

        // Whatever the user's default maps app is.
        if (start(context, Intent(Intent.ACTION_VIEW, nav))) return true

        // Last resort: a geo: pin, which every maps app understands. Not turn-by-turn,
        // but it puts the destination on screen instead of failing silently.
        val label = Uri.encode(place.name)
        val geo = Uri.parse("geo:${place.lat},${place.lon}?q=${place.lat},${place.lon}($label)")
        return start(context, Intent(Intent.ACTION_VIEW, geo))
    }

    /**
     * Opens a maps search for [query] near the user, letting the maps app do the
     * picking. Used only when we have no coordinates of our own to offer.
     */
    fun searchNearby(context: Context, query: String): Boolean {
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        return start(context, Intent(Intent.ACTION_VIEW, uri))
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: Exception) {
        Log.w(TAG, "Navigation intent failed: ${e.message}")
        false
    }

    private const val GOOGLE_MAPS = "com.google.android.apps.maps"
    private const val TAG = "NavLauncher"
}
