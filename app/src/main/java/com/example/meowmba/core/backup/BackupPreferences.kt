package com.example.meowmba.core.backup

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Serializes preference edits with backup/restore checks and merges. */
object BackupPreferences {
    private val lock = ReentrantLock()

    fun <T> locked(block: () -> T): T = lock.withLock(block)

    fun open(context: Context, name: String, mode: Int = Context.MODE_PRIVATE): SharedPreferences =
        protect(context.getSharedPreferences(name, mode))

    fun protect(delegate: SharedPreferences): SharedPreferences = LockedPreferences(delegate)

    private class LockedPreferences(private val delegate: SharedPreferences) : SharedPreferences {
        override fun getAll(): Map<String, *> = delegate.all
        override fun getString(key: String?, defValue: String?): String? = delegate.getString(key, defValue)
        override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = delegate.getStringSet(key, defValues)
        override fun getInt(key: String?, defValue: Int): Int = delegate.getInt(key, defValue)
        override fun getLong(key: String?, defValue: Long): Long = delegate.getLong(key, defValue)
        override fun getFloat(key: String?, defValue: Float): Float = delegate.getFloat(key, defValue)
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = delegate.getBoolean(key, defValue)
        override fun contains(key: String?): Boolean = delegate.contains(key)
        override fun edit(): SharedPreferences.Editor = LockedEditor(delegate.edit())
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = delegate.registerOnSharedPreferenceChangeListener(listener)
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = delegate.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private class LockedEditor(private val delegate: SharedPreferences.Editor) : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor { delegate.putString(key, value); return this }
        override fun putStringSet(key: String?, values: Set<String>?): SharedPreferences.Editor { delegate.putStringSet(key, values); return this }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor { delegate.putInt(key, value); return this }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor { delegate.putLong(key, value); return this }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { delegate.putFloat(key, value); return this }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { delegate.putBoolean(key, value); return this }
        override fun remove(key: String?): SharedPreferences.Editor { delegate.remove(key); return this }
        override fun clear(): SharedPreferences.Editor { delegate.clear(); return this }
        override fun apply() = lock.withLock { delegate.apply() }
        override fun commit(): Boolean = lock.withLock { delegate.commit() }
    }
}
