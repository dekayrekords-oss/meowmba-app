package com.example.meowmba.core.robot.cozmo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Bind only the robot socket to Wi-Fi. Meow's internet traffic can keep using cellular. */
class CozmoController {
    private val mutableState = MutableStateFlow(CozmoState())
    val state = mutableState.asStateFlow()
    private val mutableImage = MutableStateFlow<Bitmap?>(null)
    val image = mutableImage.asStateFlow()
    private var session: CozmoSession? = null
    private var epoch = 0L
    private var imageEpoch = 0L
    // Never let JPEG decoding delay telemetry, ACKs, or the motor stop deadline.
    private val decoder = ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
        ArrayBlockingQueue<Runnable>(1),
        { task -> Thread(task, "Cozmo-camera").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy()).apply { allowCoreThreadTimeOut(true) }

    private fun acceptImage(bytes: ByteArray?, generation: Long) {
        val token = synchronized(this) {
            if (generation != epoch) return
            if (bytes == null) { imageEpoch++; decoder.queue.clear(); mutableImage.value = null; return }
            imageEpoch
        }
        decoder.execute {
            val bitmap = decodeCozmoFrame(bytes!!)
            synchronized(this) {
                if (generation == epoch && token == imageEpoch && mutableState.value.cameraEnabled && bitmap != null)
                    mutableImage.value = bitmap
            }
        }
    }

    fun connect(context: Context) {
        val (generation, previous) = synchronized(this) {
            val old = session
            session = null
            ++epoch to old
        }
        previous?.close()
        previous?.awaitClosed()
        var socket: DatagramSocket? = null
        try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            @Suppress("DEPRECATION")
            val wifi = cm.allNetworks.firstOrNull { network ->
                cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                    cm.getLinkProperties(network)?.linkAddresses?.any {
                        it.address.hostAddress?.startsWith("172.31.1.") == true
                    } == true
            } ?: error("Join Cozmo's Wi-Fi in Android settings, then tap Connect. Keep the official Cozmo app closed.")
            socket = DatagramSocket(null)
            wifi.bindSocket(socket)
            socket.bind(InetSocketAddress(0))
            socket.connect(InetSocketAddress(CozmoProtocol.HOST, CozmoProtocol.PORT))
            val next = CozmoSession(socket, { value -> synchronized(this) {
                if (generation == epoch) mutableState.value = value
            } }, { bytes -> acceptImage(bytes, generation) })
            synchronized(this) {
                if (generation != epoch) { socket.close(); return }
                mutableImage.value = null
                mutableState.value = CozmoState(connecting = true, message = "Contacting Cozmo…")
                session = next
                next.start()
            }
        } catch (e: Exception) {
            socket?.close()
            synchronized(this) { if (generation == epoch) mutableState.value = CozmoState(message = e.message ?: "Cozmo connection failed") }
        }
    }
    @Synchronized fun disconnect() {
        epoch++
        // Retain the closing session so a subsequent Connect can join it before sending a new reset.
        session?.close()
        mutableImage.value = null
        mutableState.value = CozmoState(message = "Disconnected")
    }
    @Synchronized fun stop() { session?.stop() }
    @Synchronized fun action(name: String): String = session?.action(name) ?: "Cozmo is not connected"
    @Synchronized fun setWheels(on: Boolean) { if (!on) session?.stop() else session?.setWheels(true) }
    @Synchronized fun setCamera(on: Boolean) {
        session?.setCamera(on)
        if (!on) { imageEpoch++; decoder.queue.clear(); mutableImage.value = null }
    }
    @Synchronized fun setCompanion(on: Boolean) { session?.setCompanion(on) }
    @Synchronized fun connectCube(id: Int): String = session?.connectCube(id) ?: "Cozmo is not connected"
    @Synchronized fun expression(name: String) { if (state.value.connected && state.value.companionEyes) session?.expression(name) }
}

/** Inspect dimensions before allocating pixels, even for full JPEG packets. */
internal fun decodeCozmoFrame(bytes: ByteArray): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth != 320 || bounds.outHeight != 240) null
    else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()
