package com.Hrshd1eux.filemanager.core.settings.locale

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.Hrshd1eux.filemanager.core.settings.preferences.AppSettingsRepository
import java.util.Locale

/**
 * Descriptor for an in-app selectable locale option.
 */
data class SupportedLanguage(
    val tag: String,
    val nativeName: String,
    val englishName: String
)

/**
 * Coordinates app-wide language preferences across Android 13+ (per-app locale framework)
 * and legacy Android versions with full persistence and fallback to system defaults.
 */
object AppLocaleManager {

    val supportedLanguages: List<SupportedLanguage> = listOf(
        SupportedLanguage("", "System Default", "Follow System Language"),
        SupportedLanguage("en", "English", "English"),
        SupportedLanguage("es", "Español", "Spanish"),
        SupportedLanguage("pt", "Português", "Portuguese"),
        SupportedLanguage("pt-BR", "Português (Brasil)", "Brazilian Portuguese"),
        SupportedLanguage("de", "Deutsch", "German"),
        SupportedLanguage("ru", "Русский", "Russian"),
        SupportedLanguage("fr", "Français", "French"),
        SupportedLanguage("hi", "हिन्दी", "Hindi"),
        SupportedLanguage("bn", "বাংলা", "Bengali"),
        SupportedLanguage("mr", "मराठी", "Marathi"),
        SupportedLanguage("te", "తెలుగు", "Telugu"),
        SupportedLanguage("ta", "தமிழ்", "Tamil"),
        SupportedLanguage("gu", "ગુજરાતી", "Gujarati")
    )

    fun getCurrentLanguageTag(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val locales = localeManager?.applicationLocales
            if (locales != null && !locales.isEmpty) {
                val primary = locales.get(0)
                val lang = primary.language
                val country = primary.country
                return if (lang.equals("pt", ignoreCase = true) && country.equals("BR", ignoreCase = true)) {
                    "pt-BR"
                } else {
                    lang
                }
            }
        }
        return AppSettingsRepository.getInstance(context).appLanguage.value
    }

    fun setLanguage(context: Context, languageTag: String) {
        AppSettingsRepository.getInstance(context).updateAppLanguage(languageTag)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            if (languageTag.isEmpty()) {
                localeManager?.applicationLocales = LocaleList.getEmptyLocaleList()
            } else {
                localeManager?.applicationLocales = LocaleList.forLanguageTags(languageTag)
            }
        }
    }

    fun applyLocaleContext(context: Context, languageTag: String): Context {
        if (languageTag.isEmpty()) return context
        val parts = languageTag.split('-')
        val targetLocale = if (parts.size == 2) {
            Locale(parts[0], parts[1])
        } else {
            Locale(parts[0])
        }
        Locale.setDefault(targetLocale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(targetLocale)
        return context.createConfigurationContext(config)
    }

    fun getDisplayNameForTag(tag: String): String {
        if (tag.isEmpty()) return "System Default"
        val match = supportedLanguages.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
        return if (match != null) {
            "${match.nativeName} • ${match.englishName}"
        } else {
            tag
        }
    }
}
