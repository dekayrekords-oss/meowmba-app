package com.example.meowmba.core.backup

/** Pure gates: no automatic removal and no fallback from a failed update to uninstall. */
object BackupUpdatePolicy {
    const val PREVIEW = "com.example.meowmba"
    const val PLAY = "com.meow.meowmba"
    fun counterpart(current: String): String? = when (current) { PREVIEW -> PLAY; PLAY -> PREVIEW; else -> null }

    fun updateProblem(currentPackage: String, currentVersion: Long, currentSigners: Set<String>,
                      candidatePackage: String, candidateVersion: Long, candidateSigners: Set<String>): String? = when {
        candidatePackage != currentPackage -> "This APK is a different edition. Keep the old edition and use backup and restore to move your data."
        candidateVersion <= currentVersion -> "Choose a newer version. Downgrades are blocked to protect your data."
        currentSigners.isEmpty() || currentSigners != candidateSigners -> "The signing key does not match. Do not uninstall Meow to bypass this check."
        else -> null
    }

    fun cleanupProblem(currentPackage: String, currentVersion: Long, oldPackage: String?, oldVersion: Long?,
                       sourcePackage: String?, sourceVersion: Long, complete: Boolean, backupReadable: Boolean,
                       confirmed: Boolean): String? = when {
        oldPackage == null || oldVersion == null -> "No other Meow edition is installed. Normal updates already replace the old version."
        counterpart(currentPackage) != oldPackage -> "Only the other Meow edition can be removed here."
        sourcePackage != oldPackage || sourceVersion != oldVersion -> "Restore a current backup made by the other edition first."
        currentVersion < sourceVersion -> "Update this edition before moving newer data into it."
        !complete -> "Resolve restore conflicts before removing the other edition."
        !backupReadable -> "Keep the verified backup accessible before removing the other edition."
        !confirmed -> "Confirm that your keys and data work in this edition first."
        else -> null
    }
}
