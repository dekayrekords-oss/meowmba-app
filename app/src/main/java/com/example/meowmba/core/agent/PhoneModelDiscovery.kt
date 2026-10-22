package com.example.meowmba.core.agent

import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class PhoneModelDevice(
    val ramBytes: Long,
    val freeBytes: Long,
    val arm64: Boolean,
    val lowRam: Boolean,
    val availableRamBytes: Long? = null,
)

enum class PhoneModelFormat(val label: String) {
    LITERT_LM("LiteRT-LM"), MEDIAPIPE_TASK("MediaPipe LLM task"), MEDIAPIPE_BIN("MediaPipe legacy binary"),
}

/** Relative footprint estimate only; these labels are never tokens/second measurements. */
enum class PhoneModelSpeed(val label: String) {
    FAST("Lighter · estimated"), BALANCED("Medium · estimated"), HEAVY("Heavier · estimated"), UNKNOWN("Speed unknown"),
}

data class PhoneModelAssessment(
    val eligible: Boolean,
    val reason: String,
    val estimatedRamBytes: Long?,
    val requiredFreeBytes: Long?,
    val speed: PhoneModelSpeed,
    val verifiedOnDevice: Boolean = false,
)

data class PhoneModelCandidate(
    val id: String,
    val repository: String,
    val revision: String,
    val remoteFile: String,
    val fileName: String,
    val downloadUrl: String,
    val sizeBytes: Long?,
    val parameters: Long?,
    val quantization: String?,
    val createdAtMs: Long?,
    val modifiedAtMs: Long?,
    val downloads: Long?,
    val likes: Long?,
    val license: String?,
    val gated: Boolean,
    val format: PhoneModelFormat,
    val assessment: PhoneModelAssessment,
    val parametersSource: String? = null,
    val activeParameters: Long? = null,
    val quantizationSource: String? = null,
    val releasedAtMs: Long? = null,
    val sha256: String? = null,
    val description: String = "",
    val downloadedAtMs: Long? = null,
    val installedPath: String? = null,
    val localModifiedAtMs: Long? = null,
) {
    val displayName: String get() = repository.substringAfterLast('/')
    val repositoryUrl: String get() = "https://huggingface.co/$repository"

    fun toLocalModelOption(): LocalModelOption = LocalModelOption(
        id = id,
        displayName = displayName,
        description = "$remoteFile · ${assessment.reason}",
        approxSize = sizeBytes?.let(PhoneModelDiscovery::formatBytes) ?: "Size unknown",
        downloadUrl = downloadUrl,
        fileName = fileName,
        minRamGb = assessment.estimatedRamBytes?.let { ceil(it / PhoneModelDiscovery.GIB.toDouble()).toInt() } ?: 0,
        requiresHfToken = gated,
    )
}

enum class PhoneModelSort(val label: String) {
    SPEED("Estimated speed"), SIZE("Smallest file"), PARAMETERS("Fewest parameters"),
    RELEASED("Release date"), CREATED("Newest on Hub"), UPDATED("Recently updated"),
    LIKES("Most likes"), DOWNLOADS("Most Hub downloads"), DOWNLOADED("Date downloaded"), NAME("Name"),
}

data class PhoneModelFilters(
    val query: String = "",
    val eligibleOnly: Boolean = true,
    val installedOnly: Boolean = false,
    val ungatedOnly: Boolean = false,
    val maxSizeBytes: Long? = null,
    val maxParameters: Long? = null,
    val quantization: String? = null,
    val speed: PhoneModelSpeed? = null,
    val format: PhoneModelFormat? = null,
    val createdAfterMs: Long? = null,
    val releasedAfterMs: Long? = null,
    val downloadedAfterMs: Long? = null,
    val minLikes: Long? = null,
    val minDownloads: Long? = null,
)

/** Pure metadata policy shared by browsing, recommendation and download confirmation. */
object PhoneModelDiscovery {
    const val GIB = 1_073_741_824L
    const val MAX_MODEL_BYTES = 11L * GIB
    private const val CACHE_RESERVE = 512L * 1024 * 1024

    /**
     * Size and free space are checked separately. Runtime memory includes a 30%
     * working allowance and 512 MiB context/cache estimate; 2 GiB or 25% of total
     * RAM stays available to Android. None of this proves delegate compatibility.
     */
    fun assess(
        device: PhoneModelDevice,
        sizeBytes: Long?,
        parameters: Long?,
        runtimeIssue: String? = null,
    ): PhoneModelAssessment {
        val size = sizeBytes?.takeIf { it > 0 }
        val memory = size?.let { ceil(it.toDouble() * 1.3 + CACHE_RESERVE).toLong() }
        val storage = size?.let { (it.toDouble() + max(CACHE_RESERVE.toDouble(), it * 0.1)).toLong() }
        val budget = min(device.ramBytes * 0.75, (device.ramBytes - 2 * GIB).toDouble()).coerceAtLeast(0.0)
        val speed = when {
            size == null -> PhoneModelSpeed.UNKNOWN
            size <= GIB && (parameters == null || parameters <= 1_500_000_000L) -> PhoneModelSpeed.FAST
            size <= 3 * GIB && (parameters == null || parameters <= 4_000_000_000L) -> PhoneModelSpeed.BALANCED
            else -> PhoneModelSpeed.HEAVY
        }
        val reason = when {
            runtimeIssue != null -> runtimeIssue
            !device.arm64 -> "This local runtime needs an ARM64 Android device."
            device.lowRam -> "Android reports this as a low-RAM device."
            size == null -> "File size is unknown; device fit cannot be checked."
            size > MAX_MODEL_BYTES -> "Exceeds the 11 GiB model-file limit."
            memory!! > budget -> "Estimated working memory exceeds this phone's RAM budget."
            device.availableRamBytes != null && memory > device.availableRamBytes.coerceAtLeast(0) * 0.85 ->
                "Not enough available RAM right now; close other models or apps and refresh."
            storage!! > device.freeBytes -> "Not enough free storage for the file and cache allowance."
            else -> null
        }
        return PhoneModelAssessment(reason == null,
            reason ?: "Compatible file format; estimated to fit. Download and test on this phone to verify.",
            memory, storage, speed)
    }

    fun filterAndSort(
        models: List<PhoneModelCandidate>,
        filters: PhoneModelFilters = PhoneModelFilters(),
        sort: PhoneModelSort = PhoneModelSort.DOWNLOADS,
    ): List<PhoneModelCandidate> {
        val query = filters.query.trim()
        val selected = models.asSequence().distinctBy { it.id }.filter { model ->
            (!filters.eligibleOnly || model.assessment.eligible) &&
                (!filters.installedOnly || model.installedPath != null) &&
                (!filters.ungatedOnly || !model.gated) &&
                (query.isBlank() || "${model.repository} ${model.remoteFile} ${model.quantization.orEmpty()}".contains(query, true)) &&
                (filters.maxSizeBytes == null || model.sizeBytes?.let { it <= filters.maxSizeBytes } == true) &&
                (filters.maxParameters == null || model.parameters?.let { it <= filters.maxParameters } == true) &&
                (filters.quantization == null || model.quantization.equals(filters.quantization, true)) &&
                (filters.speed == null || model.assessment.speed == filters.speed) &&
                (filters.format == null || model.format == filters.format) &&
                (filters.createdAfterMs == null || model.createdAtMs?.let { it >= filters.createdAfterMs } == true) &&
                (filters.releasedAfterMs == null || model.releasedAtMs?.let { it >= filters.releasedAfterMs } == true) &&
                (filters.downloadedAfterMs == null || model.downloadedAtMs?.let { it >= filters.downloadedAfterMs } == true) &&
                (filters.minLikes == null || model.likes?.let { it >= filters.minLikes } == true) &&
                (filters.minDownloads == null || model.downloads?.let { it >= filters.minDownloads } == true)
        }.toList()
        val comparator: Comparator<PhoneModelCandidate> = when (sort) {
            PhoneModelSort.SPEED -> compareBy<PhoneModelCandidate> { it.assessment.speed.ordinal }.thenBy { it.sizeBytes ?: Long.MAX_VALUE }
            PhoneModelSort.SIZE -> compareBy { it.sizeBytes ?: Long.MAX_VALUE }
            PhoneModelSort.PARAMETERS -> compareBy { it.parameters ?: Long.MAX_VALUE }
            PhoneModelSort.RELEASED -> compareByDescending { it.releasedAtMs ?: Long.MIN_VALUE }
            PhoneModelSort.CREATED -> compareByDescending { it.createdAtMs ?: Long.MIN_VALUE }
            PhoneModelSort.UPDATED -> compareByDescending { it.modifiedAtMs ?: Long.MIN_VALUE }
            PhoneModelSort.LIKES -> compareByDescending { it.likes ?: -1L }
            PhoneModelSort.DOWNLOADS -> compareByDescending { it.downloads ?: -1L }
            PhoneModelSort.DOWNLOADED -> compareByDescending { it.downloadedAtMs ?: Long.MIN_VALUE }
            PhoneModelSort.NAME -> compareBy { it.displayName.lowercase(Locale.ROOT) }
        }
        return selected.sortedWith(comparator.thenBy { it.id })
    }

    /**
     * A filesystem modification date is not a download date. Only an explicitly
     * recorded completion timestamp is copied into downloadedAtMs. Matching the
     * exact generated/curated name and byte length avoids claiming a partial or
     * unrelated same-basename model is installed.
     */
    fun mergeInstalled(
        models: List<PhoneModelCandidate>,
        installed: List<File>,
        downloadedAt: Map<String, Long> = emptyMap(),
    ): List<PhoneModelCandidate> = models.map { model ->
        val file = installed.firstOrNull { it.name == model.fileName && it.isFile && it.length() > 0 &&
            (model.sizeBytes == null || it.length() == model.sizeBytes) }
        model.copy(installedPath = file?.absolutePath,
            localModifiedAtMs = file?.lastModified()?.takeIf { it > 0 },
            downloadedAtMs = file?.let { (downloadedAt[it.absolutePath] ?: downloadedAt[it.name])?.takeIf { date -> date > 0 } })
    }

    /** Old curated names remain discoverable; arbitrary Hub repos cannot collide. */
    fun localFileName(repository: String, revision: String, remoteFile: String): String {
        ModelCatalog.options.firstOrNull { option ->
            runCatching {
                val path = URI(option.downloadUrl).path
                path.startsWith("/$repository/resolve/") && path.substringAfter("/resolve/").substringAfter('/') == remoteFile
            }.getOrDefault(false)
        }?.let { return it.fileName }
        val extension = remoteFile.substringAfterLast('.').lowercase(Locale.ROOT)
        val base = remoteFile.substringAfterLast('/').substringBeforeLast('.')
            .replace(Regex("[^a-zA-Z0-9._-]"), "_").take(90)
        val hash = MessageDigest.getInstance("SHA-256").digest("$repository@$revision/$remoteFile".toByteArray())
            .take(6).joinToString("") { "%02x".format(it) }
        return "$base-$hash.$extension"
    }

    fun formatBytes(bytes: Long): String = if (bytes >= GIB) String.format(Locale.US, "%.2f GiB", bytes / GIB.toDouble())
        else String.format(Locale.US, "%.1f MiB", bytes / (1024.0 * 1024.0))
}
