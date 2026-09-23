package com.example.meowmba.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupUpdatePolicyTest {

    private val preview = BackupUpdatePolicy.PREVIEW
    private val play = BackupUpdatePolicy.PLAY
    private val signer = setOf("release-key")

    @Test fun `matching newer package and signer is allowed`() {
        assertNull(BackupUpdatePolicy.updateProblem(preview, 10, signer, preview, 11, signer))
    }

    @Test fun `lower or equal candidate version is blocked`() {
        assertEquals(
            "Choose a newer version. Downgrades are blocked to protect your data.",
            BackupUpdatePolicy.updateProblem(preview, 10, signer, preview, 9, signer)
        )
        assertEquals(
            "Choose a newer version. Downgrades are blocked to protect your data.",
            BackupUpdatePolicy.updateProblem(preview, 10, signer, preview, 10, signer)
        )
    }

    @Test fun `wrong package is blocked`() {
        assertEquals(
            "This APK is a different edition. Keep the old edition and use backup and restore to move your data.",
            BackupUpdatePolicy.updateProblem(preview, 10, signer, play, 11, signer)
        )
    }

    @Test fun `wrong or empty signer is blocked`() {
        val expected = "The signing key does not match. Do not uninstall Meow to bypass this check."
        assertEquals(expected, BackupUpdatePolicy.updateProblem(preview, 10, signer, preview, 11, setOf("other-key")))
        assertEquals(expected, BackupUpdatePolicy.updateProblem(preview, 10, emptySet(), preview, 11, signer))
    }

    @Test fun `counterpart is exact and unknown editions have no counterpart`() {
        assertEquals(play, BackupUpdatePolicy.counterpart(preview))
        assertEquals(preview, BackupUpdatePolicy.counterpart(play))
        assertNull(BackupUpdatePolicy.counterpart("com.example.other"))
    }

    private fun cleanup(
        currentPackage: String = preview,
        currentVersion: Long = 10,
        oldPackage: String? = play,
        oldVersion: Long? = 10,
        sourcePackage: String? = play,
        sourceVersion: Long = 10,
        complete: Boolean = true,
        backupReadable: Boolean = true,
        confirmed: Boolean = true
    ): String? = BackupUpdatePolicy.cleanupProblem(
        currentPackage, currentVersion, oldPackage, oldVersion,
        sourcePackage, sourceVersion, complete, backupReadable, confirmed
    )

    @Test fun `cleanup blocks removing the current edition`() {
        assertEquals("Only the other Meow edition can be removed here.", cleanup(oldPackage = preview))
    }

    @Test fun `cleanup blocks removing an unrelated app`() {
        assertEquals("Only the other Meow edition can be removed here.", cleanup(oldPackage = "com.example.other"))
    }

    @Test fun `cleanup blocks missing import`() {
        assertEquals("Restore a current backup made by the other edition first.", cleanup(sourcePackage = null))
    }

    @Test fun `cleanup blocks unresolved conflicts`() {
        assertEquals("Resolve restore conflicts before removing the other edition.", cleanup(complete = false))
    }

    @Test fun `cleanup blocks a missing backup`() {
        assertEquals("Keep the verified backup accessible before removing the other edition.", cleanup(backupReadable = false))
    }

    @Test fun `cleanup blocks until the user confirms`() {
        assertEquals("Confirm that your keys and data work in this edition first.", cleanup(confirmed = false))
    }

    @Test fun `cleanup blocks when source data is newer`() {
        assertEquals("Update this edition before moving newer data into it.", cleanup(currentVersion = 9, sourceVersion = 10))
    }

    @Test fun `cleanup blocks a source and old version mismatch`() {
        assertEquals("Restore a current backup made by the other edition first.", cleanup(oldVersion = 9))
    }

    @Test fun `complete confirmed counterpart with readable matching backup is allowed`() {
        assertNull(cleanup())
    }
}
