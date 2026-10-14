package com.Hrshd1eux.filemanager.feature.viewer.audio.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager

/**
 * BroadcastReceiver for [AudioManager.ACTION_AUDIO_BECOMING_NOISY].
 * Pauses audio playback immediately when headphones/Bluetooth audio devices are disconnected.
 */
class BecomingNoisyReceiver(
    private val context: Context,
    private val onBecomingNoisy: () -> Unit
) : BroadcastReceiver() {

    private var isRegistered = false

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            onBecomingNoisy()
        }
    }

    fun register() {
        if (!isRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            context.registerReceiver(this, filter)
            isRegistered = true
        }
    }

    fun unregister() {
        if (isRegistered) {
            runCatching { context.unregisterReceiver(this) }
            isRegistered = false
        }
    }
}
