package com.example.meowmba

import org.junit.Test
import org.junit.Assert.assertTrue
import java.net.InetAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Read-only probe in the real app process; adb run-as has a different SELinux context. */
class NetworkDiagnosticDeviceTest {
    @Test fun weatherCityWithoutLocationPermission() = kotlinx.coroutines.runBlocking {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val result = com.example.meowmba.core.weather.WeatherRepository(context).fetchPlace("New River Arizona")
        assertTrue("City lookup result: $result", result is com.example.meowmba.core.weather.WeatherRepository.Result.Ok)
    }
    @Test fun huggingFaceDnsAndHttps() {
        assertTrue(InetAddress.getAllByName("huggingface.co").isNotEmpty())
        val connection = URL("https://huggingface.co").openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            val status = connection.responseCode
            assertTrue("Hugging Face HTTP status $status", status in 200..399)
        } finally {
            connection.disconnect()
        }
    }
}
