package com.example.meowmba.core.vision

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** A detected face must not wait for optional skeleton warmup. Keep the shared
 * image alive until BOTH readers finish; if no face exists, preserve the pose
 * result for hand attention. One frame publishes at most once.
 */
internal fun <F, P> completeFaceFirst(
    faceTask: Task<List<F>>,
    poseTask: Task<P>?,
    executor: Executor,
    publish: (List<F>, P?, Exception?) -> Unit,
    release: () -> Unit,
) {
    val published = AtomicBoolean(false)
    fun deliver(pose: P?) {
        if (!published.compareAndSet(false, true)) return
        val faces = if (faceTask.isSuccessful) faceTask.result.orEmpty() else emptyList()
        publish(faces, pose, if (faceTask.isSuccessful) null else faceTask.exception ?: IllegalStateException("Face detection cancelled"))
    }
    if (poseTask != null) {
        faceTask.addOnCompleteListener(executor) {
            if (!faceTask.isSuccessful || !faceTask.result.isNullOrEmpty()) {
                deliver(if (poseTask.isSuccessful) poseTask.result else null)
            }
        }
    }
    val readers = if (poseTask == null) faceTask else Tasks.whenAllComplete(faceTask, poseTask)
    readers.addOnCompleteListener(executor) {
        try {
            deliver(if (poseTask?.isSuccessful == true) poseTask.result else null)
        } finally {
            release()
        }
    }
}
