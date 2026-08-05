package com.example.meowmba.core.routines

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import com.example.meowmba.core.database.MeowmbaDatabase

/** Exact alarms do not survive reboot — re-arm every enabled routine. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        GlobalScope.launch {
            try {
                val dao = MeowmbaDatabase.getDatabase(context).routineDao()
                RoutineScheduler.scheduleAll(context, dao.getEnabled())
            } finally {
                pending.finish()
            }
        }
    }
}
