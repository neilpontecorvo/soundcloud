package com.neilpontecorvo.soundcloudfiretv.core.navigation

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.neilpontecorvo.soundcloudfiretv.core.input.RemoteAction

class FocusCoordinator(private val activity: Activity) {

    fun handle(action: RemoteAction): Boolean {
        val focused = activity.currentFocus ?: return false
        return when (action) {
            RemoteAction.UP,
            RemoteAction.DOWN,
            RemoteAction.LEFT,
            RemoteAction.RIGHT -> moveFocus(focused, action)
            RemoteAction.SELECT -> {
                focused.performClick()
                true
            }
            else -> false
        }
    }

    private fun moveFocus(current: View, action: RemoteAction): Boolean {
        val direction = when (action) {
            RemoteAction.UP -> View.FOCUS_UP
            RemoteAction.DOWN -> View.FOCUS_DOWN
            RemoteAction.LEFT -> View.FOCUS_LEFT
            RemoteAction.RIGHT -> View.FOCUS_RIGHT
            else -> return false
        }

        val next = current.focusSearch(direction)
        if (next != null) {
            next.requestFocus()
            return true
        }

        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        return root.focusSearch(direction)?.requestFocus() == true
    }
}
