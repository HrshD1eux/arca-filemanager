package com.Hrshd1eux.filemanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Hrshd1eux.filemanager.core.designsystem.ArcaTheme
import com.Hrshd1eux.filemanager.core.lock.manager.AppLockManager
import com.Hrshd1eux.filemanager.core.navigation.ArcaNavHost
import com.Hrshd1eux.filemanager.core.settings.model.ThemeMode
import com.Hrshd1eux.filemanager.core.settings.preferences.AppSettingsRepository
import com.Hrshd1eux.filemanager.core.vault.lock.VaultLockManager
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import com.Hrshd1eux.filemanager.core.update.AppUpdateManager
import com.Hrshd1eux.filemanager.core.update.model.ReleaseInfo
import com.Hrshd1eux.filemanager.core.update.ui.AppUpdateDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class MainActivity : ComponentActivity() {

    private companion object {
        private const val UPDATE_CHECK_COOLDOWN_MS = 15 * 60 * 1000L
    }

    private val pendingIntentFlow = MutableStateFlow<Intent?>(null)
    private val updateCheckTrigger = MutableStateFlow(1)
    private var lastSuccessfulUpdateCheckElapsedMs: Long = 0L

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                VaultLockManager.getInstance(applicationContext).onScreenOff()
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = com.Hrshd1eux.filemanager.core.settings.locale.AppLocaleManager.getCurrentLanguageTag(newBase)
        val wrapped = com.Hrshd1eux.filemanager.core.settings.locale.AppLocaleManager.applyLocaleContext(newBase, lang)
        super.attachBaseContext(wrapped)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, filter)

        if (intent?.action == Intent.ACTION_VIEW) {
            pendingIntentFlow.value = intent
        }

        setContent {
            val appSettings = remember { AppSettingsRepository.getInstance(applicationContext) }
            val appLockManager = remember { AppLockManager.getInstance(applicationContext) }
            val themeMode by appSettings.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by appSettings.dynamicColor.collectAsStateWithLifecycle()
            val pendingIntent by pendingIntentFlow.collectAsStateWithLifecycle()
            val isAppLocked by appLockManager.isAppLocked.collectAsStateWithLifecycle()
            val checkTrigger by updateCheckTrigger.collectAsStateWithLifecycle()

            var launchUpdateRelease by remember { mutableStateOf<ReleaseInfo?>(null) }
            val currentVersionName = remember {
                runCatching {
                    val pInfo = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
                    pInfo.versionName ?: "1.6.25"
                }.getOrDefault("1.6.25")
            }

            LaunchedEffect(checkTrigger, isAppLocked) {
                if (isAppLocked || launchUpdateRelease != null) return@LaunchedEffect
                val now = SystemClock.elapsedRealtime()
                val elapsedSinceLast = now - lastSuccessfulUpdateCheckElapsedMs
                if (lastSuccessfulUpdateCheckElapsedMs != 0L && elapsedSinceLast < UPDATE_CHECK_COOLDOWN_MS) {
                    return@LaunchedEffect
                }

                val result = withContext(Dispatchers.IO) {
                    AppUpdateManager.checkForUpdates(currentVersionName)
                }
                result.onSuccess { release ->
                    lastSuccessfulUpdateCheckElapsedMs = SystemClock.elapsedRealtime()
                    val lastPrompted = appSettings.getLastPromptedUpdateVersion()
                    if (AppUpdateManager.shouldPromptForUpdate(currentVersionName, release, lastPrompted)) {
                        launchUpdateRelease = release
                    }
                }
            }

            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            ArcaTheme(
                darkTheme = isDark,
                dynamicColor = dynamicColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    ArcaNavHost(
                        incomingIntent = pendingIntent,
                        onIntentHandled = { pendingIntentFlow.value = null }
                    )

                    launchUpdateRelease?.let { release ->
                        AppUpdateDialog(
                            currentVersion = currentVersionName,
                            initialRelease = release,
                            onUserInteracted = { version ->
                                appSettings.setLastPromptedUpdateVersion(version)
                            },
                            onDismiss = {
                                appSettings.setLastPromptedUpdateVersion(release.versionName)
                                launchUpdateRelease = null
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            pendingIntentFlow.value = intent
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        VaultLockManager.getInstance(applicationContext).onUserInteraction()
    }

    override fun onStart() {
        super.onStart()
        VaultLockManager.getInstance(applicationContext).onAppForegrounded()
        val now = SystemClock.elapsedRealtime()
        if (lastSuccessfulUpdateCheckElapsedMs == 0L ||
            (now - lastSuccessfulUpdateCheckElapsedMs) >= UPDATE_CHECK_COOLDOWN_MS
        ) {
            updateCheckTrigger.value += 1
        }
    }

    override fun onStop() {
        super.onStop()
        VaultLockManager.getInstance(applicationContext).onAppBackgrounded()
        com.Hrshd1eux.filemanager.feature.viewer.audio.player.AudioPlaybackController.getInstance(applicationContext).onAppBackgrounded()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(screenOffReceiver) }
        com.Hrshd1eux.filemanager.feature.viewer.audio.player.AudioPlaybackController.getInstance(applicationContext).release()
    }
}
