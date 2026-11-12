package com.Hrshd1eux.filemanager.feature.drop

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Hrshd1eux.filemanager.MainActivity
import com.Hrshd1eux.filemanager.core.designsystem.ArcaTheme
import com.Hrshd1eux.filemanager.core.drop.server.ArcaDropManager
import com.Hrshd1eux.filemanager.core.settings.model.ThemeMode
import com.Hrshd1eux.filemanager.core.settings.preferences.AppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lightweight system Share Sheet target for receiving ACTION_SEND / ACTION_SEND_MULTIPLE
 * of any MIME type or plain text/links straight into ARCA Drop.
 */
class ArcaDropShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val incomingUris = extractIncomingUris(intent)
        val incomingText = intent?.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }

        setContent {
            val appSettings = remember { AppSettingsRepository.getInstance(applicationContext) }
            val themeMode by appSettings.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by appSettings.dynamicColor.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                val manager = ArcaDropManager.getInstance()
                withContext(Dispatchers.IO) {
                    if (incomingUris.isNotEmpty()) {
                        manager.stageExternalUris(applicationContext, incomingUris)
                    }
                    if (incomingText != null) {
                        manager.setSharedText(incomingText)
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
                Surface(modifier = Modifier.fillMaxSize()) {
                    ArcaDropScreen(
                        autoStart = true,
                        onNavigateBack = { finish() },
                        onNavigateToDirectory = {
                            val openMain = Intent(this@ArcaDropShareActivity, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            startActivity(openMain)
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun extractIncomingUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
                listOfNotNull(uri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }
                list ?: emptyList()
            }
            else -> emptyList()
        }
    }
}
