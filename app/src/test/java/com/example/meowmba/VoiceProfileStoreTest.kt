package com.example.meowmba

import android.content.Context
import android.content.SharedPreferences
import com.example.meowmba.core.audio.VoiceProfileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/**
 * Voice prints are the one thing here that must survive a restart — sherpa's
 * SpeakerEmbeddingManager is memory-only, so if this round-trip is wrong the user silently
 * has to re-enrol every launch.
 */
class VoiceProfileStoreTest {

    /** In-memory SharedPreferences good enough for a String round-trip. */
    private class FakePrefs : SharedPreferences {
        private val map = mutableMapOf<String, String?>()

        override fun getString(key: String?, defValue: String?) = map[key] ?: defValue
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, String?>()
            override fun putString(key: String?, value: String?) = apply { pending[key!!] = value }
            override fun apply() { map.putAll(pending) }
            override fun commit(): Boolean { apply(); return true }
            override fun clear() = apply { map.clear() }
            override fun remove(key: String?) = apply { pending[key!!] = null }
            override fun putStringSet(k: String?, v: MutableSet<String>?) = this
            override fun putInt(k: String?, v: Int) = this
            override fun putLong(k: String?, v: Long) = this
            override fun putFloat(k: String?, v: Float) = this
            override fun putBoolean(k: String?, v: Boolean) = this
        }
        override fun getAll() = mutableMapOf<String, Any>()
        override fun getStringSet(k: String?, d: MutableSet<String>?) = d
        override fun getInt(k: String?, d: Int) = d
        override fun getLong(k: String?, d: Long) = d
        override fun getFloat(k: String?, d: Float) = d
        override fun getBoolean(k: String?, d: Boolean) = d
        override fun contains(k: String?) = map.containsKey(k)
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private lateinit var store: VoiceProfileStore

    @Before
    fun setUp() {
        val prefs = FakePrefs()
        val ctx = mock(Context::class.java)
        `when`(ctx.applicationContext).thenReturn(ctx)
        `when`(ctx.getSharedPreferences("voice_profiles", Context.MODE_PRIVATE)).thenReturn(prefs)
        store = VoiceProfileStore(ctx)
    }

    @Test
    fun `starts empty`() {
        assertTrue(store.isEmpty())
        assertEquals(0, store.all().size)
    }

    @Test
    fun `round-trips an embedding exactly`() {
        // Float precision matters: a mangled print silently stops matching its owner.
        val e = floatArrayOf(0.1f, -0.25f, 0.5f, 0.0f, -1.0f)
        store.addSample(VoiceProfileStore.OWNER, e)

        val loaded = store.all().single()
        assertEquals(VoiceProfileStore.OWNER, loaded.name)
        assertEquals(1, loaded.sampleCount)
        loaded.embeddings.single().forEachIndexed { i, v ->
            assertEquals(e[i].toDouble(), v.toDouble(), 1e-6)
        }
    }

    @Test
    fun `multiple samples accumulate under one name`() {
        repeat(3) { i -> store.addSample("owner", floatArrayOf(i.toFloat(), 1f)) }
        val p = store.all().single()
        assertEquals(3, p.sampleCount)
    }

    @Test
    fun `separate names stay separate`() {
        store.addSample("owner", floatArrayOf(1f))
        store.addSample("guest", floatArrayOf(2f))
        assertEquals(listOf("guest", "owner"), store.all().map { it.name })
    }

    @Test
    fun `names are normalised`() {
        store.addSample("  Owner  ", floatArrayOf(1f))
        store.addSample("OWNER", floatArrayOf(2f))
        assertEquals(1, store.all().size)
        assertEquals(2, store.all().single().sampleCount)
    }

    @Test
    fun `delete removes only the named profile`() {
        store.addSample("owner", floatArrayOf(1f))
        store.addSample("guest", floatArrayOf(2f))
        store.delete("guest")
        assertEquals(listOf("owner"), store.all().map { it.name })
    }

    @Test
    fun `clear removes everything`() {
        store.addSample("owner", floatArrayOf(1f))
        store.clear()
        assertTrue(store.isEmpty())
    }

    @Test
    fun `blank names and empty embeddings are rejected`() {
        store.addSample("", floatArrayOf(1f))
        store.addSample("   ", floatArrayOf(1f))
        store.addSample("owner", floatArrayOf())
        assertTrue(store.isEmpty())
    }

    @Test
    fun `corrupt stored json degrades to empty rather than crashing`() {
        store.addSample("owner", floatArrayOf(1f))
        // Simulate corruption by writing junk through a fresh store on the same prefs.
        val prefs = FakePrefs()
        prefs.edit().putString("profiles", "{not json").apply()
        val ctx = mock(Context::class.java)
        `when`(ctx.applicationContext).thenReturn(ctx)
        `when`(ctx.getSharedPreferences("voice_profiles", Context.MODE_PRIVATE)).thenReturn(prefs)
        assertTrue(VoiceProfileStore(ctx).isEmpty())
    }
}
