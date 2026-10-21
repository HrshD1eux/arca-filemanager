package com.Hrshd1eux.filemanager.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import com.Hrshd1eux.filemanager.feature.archive.ArchiveViewerViewModel
import com.Hrshd1eux.filemanager.feature.backup.BackupViewModel
import com.Hrshd1eux.filemanager.feature.browser.FileBrowserViewModel
import com.Hrshd1eux.filemanager.feature.category.CategoryViewModel
import com.Hrshd1eux.filemanager.feature.cleaner.apps.InstalledAppsViewModel
import com.Hrshd1eux.filemanager.feature.cleaner.duplicates.DuplicateFinderViewModel
import com.Hrshd1eux.filemanager.feature.cleaner.largefiles.LargeFilesViewModel
import com.Hrshd1eux.filemanager.feature.cleaner.storage.StorageAnalyzer2ViewModel
import com.Hrshd1eux.filemanager.feature.home.HomeViewModel
import com.Hrshd1eux.filemanager.feature.network.diagnostics.NetworkPrivacyDiagnosticsViewModel
import com.Hrshd1eux.filemanager.feature.search.FileSearchViewModel
import com.Hrshd1eux.filemanager.feature.security.SecurityCenterViewModel
import com.Hrshd1eux.filemanager.feature.settings.SettingsViewModel
import com.Hrshd1eux.filemanager.feature.vault.VaultViewModel
import com.Hrshd1eux.filemanager.feature.viewer.image.ImageViewerViewModel
import com.Hrshd1eux.filemanager.feature.viewer.unified.UnifiedPreviewViewModel
import com.Hrshd1eux.filemanager.feature.viewer.video.VideoViewerViewModel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Constructor

/**
 * Regression Test Suite ensuring all ViewModels can be instantiated by AndroidX ViewModelProvider
 * factories without throwing NoSuchMethodException.
 */
class ViewModelReflectionInstantiationTest {

    @Test
    fun androidViewModels_haveSingleApplicationConstructor() {
        val androidViewModelClasses = listOf(
            HomeViewModel::class.java,
            BackupViewModel::class.java,
            InstalledAppsViewModel::class.java,
            VaultViewModel::class.java
        )

        for (clazz in androidViewModelClasses) {
            assertTrue("Class ${clazz.name} must extend AndroidViewModel", AndroidViewModel::class.java.isAssignableFrom(clazz))
            val ctor: Constructor<out AndroidViewModel>? = runCatching {
                clazz.getConstructor(Application::class.java)
            }.getOrNull()

            assertNotNull(
                "Class ${clazz.name} MUST have a public constructor accepting exactly (Application) for AndroidViewModelFactory",
                ctor
            )
        }
    }

    @Test
    fun standardViewModels_haveNoArgConstructor() {
        val standardViewModelClasses = listOf(
            FileBrowserViewModel::class.java,
            CategoryViewModel::class.java,
            ArchiveViewerViewModel::class.java,
            DuplicateFinderViewModel::class.java,
            LargeFilesViewModel::class.java,
            StorageAnalyzer2ViewModel::class.java,
            FileSearchViewModel::class.java,
            SecurityCenterViewModel::class.java,
            SettingsViewModel::class.java,
            NetworkPrivacyDiagnosticsViewModel::class.java,
            ImageViewerViewModel::class.java,
            UnifiedPreviewViewModel::class.java,
            VideoViewerViewModel::class.java
        )

        for (clazz in standardViewModelClasses) {
            assertTrue("Class ${clazz.name} must extend ViewModel", ViewModel::class.java.isAssignableFrom(clazz))
            val ctor: Constructor<out ViewModel>? = runCatching {
                clazz.getConstructor()
            }.getOrNull()

            assertNotNull(
                "Class ${clazz.name} MUST have a public no-arg constructor for ViewModelProvider",
                ctor
            )
        }
    }
}
