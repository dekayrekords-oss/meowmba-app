package com.example.meowmba.core.vision

/** Actual camera pipeline state, separate from the user's saved camera choice. */
enum class FaceTrackingStatus(val label: String) {
    OFF("Camera off"),
    PERMISSION_REQUIRED("Camera permission needed"),
    STARTING("Starting face tracking"),
    WAITING_FOR_FRAMES("Waiting for camera frames"),
    NO_FACE("Looking for your face"),
    TRACKING("Following your face"),
    PAUSED("Face tracking paused"),
    COOLING("Camera paused while cooling"),
    ERROR("Camera tracking unavailable. Turn the camera off and on to retry."),
}
