package com.Hrshd1eux.filemanager.core.display.preferences

import android.content.Context
import android.content.SharedPreferences
import com.Hrshd1eux.filemanager.core.display.model.SortDirection
import com.Hrshd1eux.filemanager.core.display.model.SortField
import com.Hrshd1eux.filemanager.core.display.model.SortOrder
import com.Hrshd1eux.filemanager.core.display.model.ViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.Hrshd1eux.filemanager.ArcaApplication

class DisplayPreferencesRepository(context: Context? = null) {

    private val appContext: Context? = context?.applicationContext
        ?: runCatching { ArcaApplication.instance?.applicationContext }.getOrNull()

    private val prefs: SharedPreferences? = appContext?.getSharedPreferences("arca_display_prefs", Context.MODE_PRIVATE)

    private val _sortOrder = MutableStateFlow(loadSortOrder())
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _viewMode = MutableStateFlow(loadViewMode())
    val viewMode: StateFlow<ViewMode> = _viewMode.asStateFlow()

    private val _showHiddenFiles = MutableStateFlow(loadShowHiddenFiles())
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles.asStateFlow()

    private val _isDualPaneMode = MutableStateFlow(loadDualPaneMode())
    val isDualPaneMode: StateFlow<Boolean> = _isDualPaneMode.asStateFlow()

    private val _isTabsMode = MutableStateFlow(loadTabsMode())
    val isTabsMode: StateFlow<Boolean> = _isTabsMode.asStateFlow()

    fun updateSortOrder(newOrder: SortOrder) {
        _sortOrder.value = newOrder
        prefs?.edit()?.apply {
            putString(KEY_SORT_FIELD, newOrder.field.name)
            putString(KEY_SORT_DIRECTION, newOrder.direction.name)
            putBoolean(KEY_FOLDERS_FIRST, newOrder.foldersFirst)
            apply()
        }
    }

    fun updateViewMode(newMode: ViewMode) {
        _viewMode.value = newMode
        prefs?.edit()?.apply {
            putString(KEY_VIEW_MODE, newMode.name)
            apply()
        }
    }

    fun updateShowHiddenFiles(show: Boolean) {
        _showHiddenFiles.value = show
        prefs?.edit()?.apply {
            putBoolean(KEY_SHOW_HIDDEN_FILES, show)
            apply()
        }
    }

    fun updateDualPaneMode(enabled: Boolean) {
        _isDualPaneMode.value = enabled
        prefs?.edit()?.apply {
            putBoolean(KEY_DUAL_PANE_MODE, enabled)
            apply()
        }
    }

    fun updateTabsMode(enabled: Boolean) {
        _isTabsMode.value = enabled
        prefs?.edit()?.apply {
            putBoolean(KEY_TABS_MODE, enabled)
            apply()
        }
    }

    private fun loadSortOrder(): SortOrder {
        if (prefs == null) return SortOrder()

        val fieldName = prefs.getString(KEY_SORT_FIELD, SortField.NAME.name) ?: SortField.NAME.name
        val directionName = prefs.getString(KEY_SORT_DIRECTION, SortDirection.ASCENDING.name) ?: SortDirection.ASCENDING.name
        val foldersFirst = prefs.getBoolean(KEY_FOLDERS_FIRST, true)

        val field = runCatching { SortField.valueOf(fieldName) }.getOrDefault(SortField.NAME)
        val direction = runCatching { SortDirection.valueOf(directionName) }.getOrDefault(SortDirection.ASCENDING)

        return SortOrder(field = field, direction = direction, foldersFirst = foldersFirst)
    }

    private fun loadViewMode(): ViewMode {
        if (prefs == null) return ViewMode.LIST

        val modeName = prefs.getString(KEY_VIEW_MODE, ViewMode.LIST.name) ?: ViewMode.LIST.name
        return runCatching { ViewMode.valueOf(modeName) }.getOrDefault(ViewMode.LIST)
    }

    private fun loadShowHiddenFiles(): Boolean {
        if (prefs == null) return false
        return prefs.getBoolean(KEY_SHOW_HIDDEN_FILES, false)
    }

    private fun loadDualPaneMode(): Boolean {
        if (prefs == null) return false
        return prefs.getBoolean(KEY_DUAL_PANE_MODE, false)
    }

    private fun loadTabsMode(): Boolean {
        if (prefs == null) return false
        return prefs.getBoolean(KEY_TABS_MODE, false)
    }

    companion object {
        private const val KEY_SORT_FIELD = "sort_field"
        private const val KEY_SORT_DIRECTION = "sort_direction"
        private const val KEY_FOLDERS_FIRST = "folders_first"
        private const val KEY_VIEW_MODE = "view_mode"
        private const val KEY_SHOW_HIDDEN_FILES = "show_hidden_files"
        private const val KEY_DUAL_PANE_MODE = "dual_pane_mode"
        private const val KEY_TABS_MODE = "tabs_mode"
    }
}
