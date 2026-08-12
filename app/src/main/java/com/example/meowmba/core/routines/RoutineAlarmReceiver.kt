package com.example.meowmba.core.routines

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.meowmba.MainActivity
import com.example.meowmba.core.database.MeowmbaDatabase

/**
 * Fired by AlarmManager when a routine is due: reschedules the next
 * occurrence, then brings the companion to the front to run it.
 */
class RoutineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(RoutineScheduler.EXTRA_ROUTINE_ID, -1L)
        if (id < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = MeowmbaDatabase.getDatabase(context).routineDao()
                val routine = dao.getById(id)
                if (routine != null) {
                    RoutineScheduler.schedule(context, routine)
                    val launch = Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(RoutineScheduler.EXTRA_ROUTINE_ID, id)
                    context.startActivity(launch)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
