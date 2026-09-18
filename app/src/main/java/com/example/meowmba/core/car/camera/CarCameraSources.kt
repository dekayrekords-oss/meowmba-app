package com.example.meowmba.core.car.camera

/**
 * Registry of driver / car-connected camera sources for drive mode.
 *
 * - [FRONT_DRIVER]: phone front camera (Viewfinder / VisionManager) when CAMERA granted.
 * - [REAR_PHONE]: phone rear camera when available.
 * - [USB_UVC]: USB webcam / dashcam when a UVC device is attached (listed; capture
 *   depends on OEM Camera2 / USB host support).
 * - [IP_RTSP]: user-configured RTSP / HTTP URL for an IP dashcam.
 */
enum class CarCameraKind { FRONT_DRIVER, REAR_PHONE, USB_UVC, IP_RTSP }

data class CarCameraSource(
    val id: String,
    val label: String,
    val kind: CarCameraKind,
    val uri: String? = null,
    val available: Boolean = true,
)

object CarCameraSources {
    const val PREF_RTSP_URL = "car_dashcam_rtsp_url"
    const val PREF_ACTIVE_ID = "car_camera_active_id"

    fun defaultSources(rtspUrl: String? = null, usbAvailable: Boolean = false): List<CarCameraSource> {
        val list = mutableListOf(
            CarCameraSource("front_driver", "Driver (front camera)", CarCameraKind.FRONT_DRIVER),
            CarCameraSource("rear_phone", "Phone rear camera", CarCameraKind.REAR_PHONE),
        )
        if (usbAvailable) {
            list += CarCameraSource("usb_uvc", "USB / UVC dashcam", CarCameraKind.USB_UVC)
        }
        val url = rtspUrl?.trim().orEmpty()
        if (url.isNotBlank()) {
            list += CarCameraSource(
                id = "ip_rtsp",
                label = "IP dashcam (RTSP)",
                kind = CarCameraKind.IP_RTSP,
                uri = url,
            )
        }
        return list
    }

    fun describe(sources: List<CarCameraSource>, activeId: String?): String {
        if (sources.isEmpty()) return "No car cameras configured."
        return sources.joinToString("\n") { src ->
            val mark = if (src.id == activeId) "●" else "○"
            "$mark ${src.label}" + (src.uri?.let { " · $it" } ?: "")
        }
    }
}
