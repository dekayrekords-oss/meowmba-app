package com.example.meowmba.ui.companion

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotFragment
import org.godotengine.godot.GodotHost

/**
 * Hosts the Godot avatar prototype (idle + paw-lick, see
 * docs/godot-avatar-prototype-design.md) as a standalone screen, reachable
 * from [CompanionScreen]'s debug menu, so it can be compared side by side
 * with the real [AvatarRenderer] on the same device.
 *
 * A separate [FragmentActivity] rather than reusing [com.example.meowmba.MainActivity]:
 * hosting [GodotFragment] needs `supportFragmentManager`, and MainActivity is a
 * plain ComponentActivity. This keeps MainActivity/CompanionScreen untouched.
 *
 * Deliberately does not forward system back-press into the engine
 * (`GodotFragment.onBackPressed()`) — there is no in-engine UI to dismiss
 * here, so the default Activity back behavior (finish and return to the
 * real avatar) is what a comparison screen should do.
 */
class GodotAvatarPreviewActivity : FragmentActivity(), GodotHost {

    private var godotFragment: GodotFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = FragmentContainerView(this).apply { id = CONTAINER_ID }
        setContentView(
            container,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val current = supportFragmentManager.findFragmentById(CONTAINER_ID)
        godotFragment = if (current is GodotFragment) {
            current
        } else {
            GodotFragment().also {
                supportFragmentManager.beginTransaction()
                    .replace(CONTAINER_ID, it)
                    .commitNowAllowingStateLoss()
            }
        }
    }

    override fun getActivity(): Activity = this

    override fun getGodot(): Godot? = godotFragment?.godot

    private companion object {
        // Generated once per process (not per onCreate) so it stays stable
        // across Activity recreation, matching what the fragment manager
        // expects when restoring a retained GodotFragment.
        val CONTAINER_ID: Int = View.generateViewId()
    }
}
