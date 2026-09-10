package com.example.meowmba

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.meowmba.core.nav.SavedPlaceStore
import com.example.meowmba.core.security.SecureStorageManager
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.*

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [33])
class SavedPlaceMigrationTest {
    @Test fun legacyStoresMigrateAndDeletionCannotResurrectVoiceCopy() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("nav_saved_places",Context.MODE_PRIVATE).edit().putString("places",
            """[{"name":"home","lat":1.0,"lon":2.0,"savedAt":3}]""").commit()
        val values = mutableMapOf("named_places" to """{"home":"9.0,9.0","fishing":"4.0,5.0"}""")
        val storage = mock(SecureStorageManager::class.java)
        `when`(storage.getPreference(anyString())).thenAnswer { values[it.getArgument<String>(0)] }
        doAnswer { values[it.getArgument(0)] = it.getArgument(1); null }.`when`(storage).savePreference(anyString(),anyString())
        val store = SavedPlaceStore(ctx, storage)
        assertEquals(setOf("home","fishing"),store.names())
        assertEquals(1.0,store.get("home")!!.lat,0.0)
        store.delete("fishing")
        assertNull(SavedPlaceStore(ctx,storage).get("fishing"))
        store.save("studio",6.0,7.0)
        assertNotNull(SavedPlaceStore(ctx,storage).get("studio"))
        assertTrue(values.containsKey("saved_places_v2"))
    }
}
