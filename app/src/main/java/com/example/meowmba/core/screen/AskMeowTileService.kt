package com.example.meowmba.core.screen

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

class AskMeowTileService : TileService() {
    // The PendingIntent overload exists only on API 34+. The deprecated overload remains
    // necessary on Android 12/13; it is never called on the versions that reject it.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        unlockAndRun {
            val intent = Intent(this, ScreenCapturePromptActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 9910, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            else { @Suppress("DEPRECATION") startActivityAndCollapse(intent) }
        }
    }
}
