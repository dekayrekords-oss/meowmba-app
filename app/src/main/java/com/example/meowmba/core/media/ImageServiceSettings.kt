package com.example.meowmba.core.media

import com.example.meowmba.core.security.SecureStorageManager

enum class ImageService(val title: String, val detail: String) {
    LEGACY("Keep current setup", "Preserve your existing local/server routing."),
    LOCAL("On this phone", "Offline and private. Requires a downloaded image model and enough memory."),
    HORDE("AI Horde", "No account or card needed. Community-powered; queues can be slow."),
    CLOUDFLARE("Cloudflare", "FLUX.1 Schnell. Requires a Cloudflare account and API token; free daily allowance."),
    CUSTOM("Your own server", "Connect an image-generation server you manage."),
}

object ImageServiceSettings {
    const val PROVIDER = "image_service"
    const val HORDE_KEY = "horde_images"
    const val HORDE_CONSENT = "horde_anonymous_sharing"
    const val CF_KEY = "cloudflare_images"
    const val CF_ACCOUNT = "cloudflare_image_account"
    fun selected(storage: SecureStorageManager?): ImageService =
        ImageService.entries.firstOrNull { it.name == storage?.getPreference(PROVIDER) } ?: ImageService.LEGACY

    fun online(storage: SecureStorageManager?, onProgress: (String) -> Unit): ImageGenerator? = when (selected(storage)) {
        ImageService.HORDE -> HordeImageGenerator(storage?.getApiKey(HORDE_KEY).orEmpty(),
            storage?.getBooleanPreference(HORDE_CONSENT, false) == true, onProgress)
        ImageService.CLOUDFLARE -> CloudflareImageGenerator(storage?.getPreference(CF_ACCOUNT).orEmpty(), storage?.getApiKey(CF_KEY).orEmpty())
        else -> null
    }
}
