package com.Hrshd1eux.filemanager.core.storage.apk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

/**
 * BroadcastReceiver to handle asynchronous callbacks from [PackageInstaller.Session].
 *
 * When an unprivileged package installation requires user confirmation, Android delivers
 * [PackageInstaller.STATUS_PENDING_USER_ACTION] with [Intent.EXTRA_INTENT]. This receiver
 * launches that system confirmation prompt.
 */
class ApkInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmIntent)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "Package installation succeeded for session $sessionId")
            }
            else -> {
                Log.e(TAG, "Package installation failed for session $sessionId (status=$status): $message")
            }
        }
    }

    companion object {
        const val TAG = "ApkInstallReceiver"
        const val ACTION_INSTALL_COMMIT = "com.Hrshd1eux.filemanager.ACTION_INSTALL_COMMIT"
        const val EXTRA_SESSION_ID = "extra_session_id"
    }
}
