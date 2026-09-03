package com.Hrshd1eux.filemanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.Hrshd1eux.filemanager.core.designsystem.ArcaTheme
import com.Hrshd1eux.filemanager.core.navigation.ArcaNavHost
import com.Hrshd1eux.filemanager.core.vault.lock.VaultLockManager

class MainActivity : ComponentActivity() {

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                VaultLockManager.getInstance(applicationContext).onScreenOff()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, filter)

        setContent {
            ArcaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    ArcaNavHost()
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        VaultLockManager.getInstance(applicationContext).onUserInteraction()
    }

    override fun onStart() {
        super.onStart()
        VaultLockManager.getInstance(applicationContext).onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        VaultLockManager.getInstance(applicationContext).onAppBackgrounded()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(screenOffReceiver) }
    }
}
