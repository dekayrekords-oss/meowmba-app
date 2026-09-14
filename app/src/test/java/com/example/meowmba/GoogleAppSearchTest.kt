package com.example.meowmba

import android.app.Application
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.example.meowmba.ui.companion.GoogleAppSearch
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
@Suppress("DEPRECATION")
class GoogleAppSearchTest {
    private lateinit var context: Context
    private lateinit var packages: PackageManager
    private lateinit var google: ApplicationInfo
    private val launches = mutableListOf<Intent>()

    @Before fun setUp() {
        context = mock(Context::class.java)
        packages = mock(PackageManager::class.java)
        google = ApplicationInfo().apply {
            packageName = GoogleAppSearch.PACKAGE_NAME
            enabled = true
        }
        launches.clear()
        `when`(context.packageManager).thenReturn(packages)
        `when`(packages.getApplicationInfo(GoogleAppSearch.PACKAGE_NAME, 0)).thenReturn(google)
        doAnswer { launches += Intent(it.getArgument<Intent>(0)); null }
            .`when`(context).startActivity(any(Intent::class.java))
    }

    private fun handler(
        packageName: String = GoogleAppSearch.PACKAGE_NAME,
        exported: Boolean = true,
        enabled: Boolean = true,
    ) = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            name = "SearchActivity"
            this.exported = exported
            this.enabled = enabled
            applicationInfo = google
        }
    }

    private fun resolveWith(block: (Intent) -> ResolveInfo?) {
        `when`(packages.resolveActivity(any(Intent::class.java), eq(PackageManager.MATCH_DEFAULT_ONLY)))
            .thenAnswer { block(it.getArgument(0)) }
    }

    @Test fun webSearchUsesTheActualGooglePackageAndPreservesUnicodeQuery() {
        resolveWith { handler() }
        val query = "café 🛸 & fish+chips #1 / 東京"
        assertEquals(GoogleAppSearch.Result.OPENED, GoogleAppSearch.launch(context, "  $query  "))
        val intent = launches.single()
        assertEquals(GoogleAppSearch.PACKAGE_NAME, intent.`package`)
        assertEquals(Intent.ACTION_WEB_SEARCH, intent.action)
        assertEquals(query, intent.getStringExtra(SearchManager.QUERY))
        assertNull(intent.data)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun compatibleUrlCanOnlyOpenInsideTheSameGoogleApp() {
        val resolved = mutableListOf<Intent>()
        resolveWith {
            resolved += Intent(it)
            if (it.action == Intent.ACTION_VIEW) handler() else null
        }
        val query = "moon café? 🛸 &x=1+#fragment"
        assertEquals(GoogleAppSearch.Result.OPENED, GoogleAppSearch.launch(context, query))
        assertEquals(listOf(Intent.ACTION_WEB_SEARCH, Intent.ACTION_VIEW), resolved.map { it.action })
        assertTrue(resolved.all { it.`package` == GoogleAppSearch.PACKAGE_NAME })
        val intent = launches.single()
        assertEquals(GoogleAppSearch.PACKAGE_NAME, intent.`package`)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https", intent.data?.scheme)
        assertEquals("www.google.com", intent.data?.host)
        assertEquals("/search", intent.data?.path)
        assertEquals(setOf("q"), intent.data?.queryParameterNames)
        assertEquals(query, intent.data?.getQueryParameter("q"))
        assertNull(intent.data?.fragment)
    }

    @Test fun missingGoogleShowsAnInstallExplanationWithoutLaunchingAnything() {
        `when`(packages.getApplicationInfo(GoogleAppSearch.PACKAGE_NAME, 0))
            .thenThrow(PackageManager.NameNotFoundException())
        val result = GoogleAppSearch.launch(context, "stars")
        assertEquals(GoogleAppSearch.Result.NOT_INSTALLED, result)
        assertTrue(result.message.contains("Install Google"))
        assertTrue(launches.isEmpty())
    }

    @Test fun disabledGoogleShowsEnableInstructionsWithoutLaunchingAnything() {
        google.enabled = false
        resolveWith { handler() }
        val result = GoogleAppSearch.launch(context, "stars")
        assertEquals(GoogleAppSearch.Result.DISABLED, result)
        assertTrue(result.message.contains("Enable Google"))
        assertTrue(launches.isEmpty())
    }

    @Test fun absentExportedSearchEntryDoesNotFallBackToABrowser() {
        resolveWith { null }
        assertEquals(GoogleAppSearch.Result.UNSUPPORTED, GoogleAppSearch.launch(context, "stars"))
        assertTrue(launches.isEmpty())
    }

    @Test fun wrongPackageUnexportedAndDisabledActivitiesAreNeverStarted() {
        for (activity in listOf(handler("com.android.chrome"), handler(exported = false), handler(enabled = false))) {
            resolveWith { activity }
            assertEquals(GoogleAppSearch.Result.UNSUPPORTED, GoogleAppSearch.launch(context, "stars"))
        }
        assertTrue(launches.isEmpty())
    }

    @Test fun androidLaunchDenialIsReportedWithoutRetryingAnotherRoute() {
        resolveWith { handler() }
        doThrow(SecurityException("work profile blocked"))
            .`when`(context).startActivity(any(Intent::class.java))
        assertEquals(GoogleAppSearch.Result.BLOCKED, GoogleAppSearch.launch(context, "stars"))
        verify(context, times(1)).startActivity(any(Intent::class.java))
    }

    @Test fun packageLookupAndResolutionDenialsAreReported() {
        resolveWith { throw SecurityException("profile restricted") }
        assertEquals(GoogleAppSearch.Result.BLOCKED, GoogleAppSearch.launch(context, "stars"))
        `when`(packages.getApplicationInfo(GoogleAppSearch.PACKAGE_NAME, 0))
            .thenThrow(SecurityException("package restricted"))
        assertEquals(GoogleAppSearch.Result.BLOCKED, GoogleAppSearch.launch(context, "stars"))
        assertTrue(launches.isEmpty())
    }

    @Test fun appUpdateRaceCanUseOnlyGooglesCompatibleRoute() {
        resolveWith { handler() }
        val attempts = mutableListOf<Intent>()
        doAnswer {
            val intent = it.getArgument<Intent>(0)
            attempts += Intent(intent)
            if (intent.action == Intent.ACTION_WEB_SEARCH) throw ActivityNotFoundException()
            null
        }.`when`(context).startActivity(any(Intent::class.java))
        assertEquals(GoogleAppSearch.Result.OPENED, GoogleAppSearch.launch(context, "stars"))
        assertEquals(2, attempts.size)
        assertTrue(attempts.all { it.`package` == GoogleAppSearch.PACKAGE_NAME })
        assertEquals(Intent.ACTION_VIEW, attempts.last().action)
    }

    @Test fun bothLaunchRoutesDisappearingIsAnHonestFailure() {
        resolveWith { handler() }
        doThrow(ActivityNotFoundException()).`when`(context).startActivity(any(Intent::class.java))
        assertEquals(GoogleAppSearch.Result.UNSUPPORTED, GoogleAppSearch.launch(context, "stars"))
        verify(context, times(2)).startActivity(any(Intent::class.java))
    }

    @Test fun blankQueryAndMissingContextCannotLaunchAnything() {
        assertEquals(GoogleAppSearch.Result.EMPTY, GoogleAppSearch.launch(context, " \n\t "))
        assertEquals(GoogleAppSearch.Result.NO_CONTEXT, GoogleAppSearch.launch(null, "stars"))
        verifyNoInteractions(packages)
        assertTrue(launches.isEmpty())
    }
}
