package com.Hrshd1eux.filemanager.feature.settings

import androidx.lifecycle.ViewModel
import com.Hrshd1eux.filemanager.core.display.preferences.DisplayPreferencesRepository
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    private val displayPreferencesRepository: DisplayPreferencesRepository = DisplayPreferencesRepository()
) : ViewModel() {

    val showHiddenFiles: StateFlow<Boolean> = displayPreferencesRepository.showHiddenFiles

    fun setShowHiddenFiles(show: Boolean) {
        displayPreferencesRepository.updateShowHiddenFiles(show)
    }
}
