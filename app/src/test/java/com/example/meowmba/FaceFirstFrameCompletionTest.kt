package com.example.meowmba

import android.app.Application
import android.os.Looper
import com.example.meowmba.core.vision.completeFaceFirst
import com.google.android.gms.tasks.TaskCompletionSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FaceFirstFrameCompletionTest {
    private val direct = Executor { it.run() }

    @Test fun fastFacePublishesBeforePoseWarmupButSharedImageStaysOpen() {
        val face = TaskCompletionSource<List<String>>()
        val pose = TaskCompletionSource<String>()
        val results = mutableListOf<List<String>>()
        var releases = 0
        completeFaceFirst(face.task, pose.task, direct,
            publish = { faces, _, error -> assertNull(error); results += faces },
            release = { releases++ })

        face.setResult(listOf("observed face"))
        assertEquals(listOf(listOf("observed face")), results)
        assertEquals(0, releases)
        pose.setResult("late skeleton")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, results.size)
        assertEquals(1, releases)
    }

    @Test fun emptyFaceWaitsForPoseSoHandFollowingStillReceivesSkeleton() {
        val face = TaskCompletionSource<List<String>>()
        val pose = TaskCompletionSource<String>()
        var resultPose: String? = null
        var publications = 0
        var releases = 0
        completeFaceFirst(face.task, pose.task, direct,
            publish = { faces, skeleton, error ->
                assertTrue(faces.isEmpty()); assertNull(error)
                resultPose = skeleton; publications++
            }, release = { releases++ })

        face.setResult(emptyList())
        assertEquals(0, publications)
        pose.setResult("raised wrist")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("raised wrist", resultPose)
        assertEquals(1, publications)
        assertEquals(1, releases)
    }

    @Test fun poseFailureDoesNotDiscardDetectedFaceOrReleaseSharedImageEarly() {
        val face = TaskCompletionSource<List<String>>()
        val pose = TaskCompletionSource<String>()
        var publications = 0
        var releases = 0
        completeFaceFirst(face.task, pose.task, direct,
            publish = { faces, _, error ->
                assertEquals(listOf("face"), faces); assertNull(error); publications++
            }, release = { releases++ })
        face.setResult(listOf("face"))
        assertEquals(1, publications)
        assertEquals(0, releases)
        pose.setException(IllegalStateException("optional skeleton unavailable"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, publications)
        assertEquals(1, releases)
    }

    @Test fun faceErrorIsReportedPromptlyAndSharedImageWaitsForOtherReader() {
        val face = TaskCompletionSource<List<String>>()
        val pose = TaskCompletionSource<String>()
        val failure = IllegalStateException("face model unavailable")
        var observedError: Exception? = null
        var publications = 0
        var releases = 0
        completeFaceFirst(face.task, pose.task, direct,
            publish = { _, _, error -> observedError = error; publications++ },
            release = { releases++ })
        face.setException(failure)
        assertSame(failure, observedError)
        assertEquals(0, releases)
        pose.setResult("pose")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, publications)
        assertEquals(1, releases)
    }

    @Test fun faceOnlyModePublishesAndReleasesExactlyOnce() {
        val face = TaskCompletionSource<List<String>>()
        var publications = 0
        var releases = 0
        completeFaceFirst<String, String>(face.task, null, direct,
            publish = { _, skeleton, error -> assertNull(skeleton); assertNull(error); publications++ },
            release = { releases++ })
        face.setResult(listOf("face"))
        assertEquals(1, publications)
        assertEquals(1, releases)
    }
}
