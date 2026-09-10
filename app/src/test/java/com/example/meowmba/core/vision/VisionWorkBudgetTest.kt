package com.example.meowmba.core.vision

import org.junit.Assert.*
import org.junit.Test

class VisionWorkBudgetTest {
    @Test fun cameraFrameRateCannotMultiplyInferenceRate() {
        val budget = VisionWorkBudget()
        assertEquals(3, (0L until 1000L step 16).count { budget.admit(it, false, 0) })
        val tracking = VisionWorkBudget()
        assertEquals(8, (0L until 1000L step 16).count { tracking.admit(it, true, 0) })
    }
    @Test fun warmIdleAndSevereHaveLowerOrNoInference() {
        val budget = VisionWorkBudget()
        assertEquals(1, (0L until 1000L step 16).count { budget.admit(it, false, 2) })
        assertFalse(budget.admit(2000, true, 3))
        assertTrue(budget.admit(2000, true, 0))
    }
    @Test fun poseNeverDuplicatesTrackedFaceOrRunsWhileHot() {
        val budget = VisionWorkBudget()
        assertFalse(budget.admitPose(0, true, 0, false))
        assertFalse(budget.admitPose(0, false, 1, false))
        assertFalse(budget.admitPose(0, false, 0, true))
        assertTrue(budget.admitPose(0, false, 0, false))
        assertFalse(budget.admitPose(999, false, 0, false))
        assertTrue(budget.admitPose(1000, false, 0, false))
    }
}
