package com.neilpontecorvo.soundcloudfiretv.core.input

import android.view.KeyEvent

object RemoteInputHandler {

    fun mapKeyCode(keyCode: Int): RemoteAction = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> RemoteAction.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> RemoteAction.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> RemoteAction.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> RemoteAction.RIGHT
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER -> RemoteAction.SELECT
        KeyEvent.KEYCODE_BACK -> RemoteAction.BACK
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> RemoteAction.PLAY_PAUSE
        KeyEvent.KEYCODE_MEDIA_PLAY -> RemoteAction.PLAY
        KeyEvent.KEYCODE_MEDIA_PAUSE -> RemoteAction.PAUSE
        KeyEvent.KEYCODE_MEDIA_NEXT -> RemoteAction.NEXT
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> RemoteAction.PREVIOUS
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> RemoteAction.FAST_FORWARD
        KeyEvent.KEYCODE_MEDIA_REWIND -> RemoteAction.REWIND
        KeyEvent.KEYCODE_MENU -> RemoteAction.MENU
        else -> RemoteAction.UNKNOWN
    }
}
