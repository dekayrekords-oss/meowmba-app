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

/** Bind only the robot socket to Wi-Fi. Meow's internet traffic can keep using cellular. */
class CozmoController {
    private val mutableState = MutableStateFlow(CozmoState())
    val state = mutableState.asStateFlow()
    private val mutableImage = MutableStateFlow<Bitmap?>(null)
    val image = mutableImage.asStateFlow()
    private var session: CozmoSession? = null
    private var epoch = 0L

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
            } }, { bytes ->
                val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                synchronized(this) { if (generation == epoch) mutableImage.value = bitmap }
            })
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
    @Synchronized fun setCamera(on: Boolean) { session?.setCamera(on); if (!on) mutableImage.value = null }
    @Synchronized fun setCompanion(on: Boolean) { session?.setCompanion(on) }
    @Synchronized fun connectCube(id: Int): String = session?.connectCube(id) ?: "Cozmo is not connected"
    @Synchronized fun expression(name: String) { if (state.value.connected && state.value.companionEyes) session?.expression(name) }
}
