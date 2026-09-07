package com.example.meowmba.core.workflows

/** Hysteresis avoids restarting a multi-GB model each time the phone drops
 * from 39.0 to 38.9 C. After a thermal pause it must cool below 37.5 C. */
class WorkflowThermalGate(private var cooling: Boolean = false) {
    @Synchronized fun allowsRun(batteryTenthsC: Int, systemModerateOrHotter: Boolean, powerSaving: Boolean): Boolean {
        if (systemModerateOrHotter || batteryTenthsC >= 390) cooling = true
        if (cooling && !systemModerateOrHotter && batteryTenthsC < 375) cooling = false
        return !cooling && !powerSaving
    }
}
