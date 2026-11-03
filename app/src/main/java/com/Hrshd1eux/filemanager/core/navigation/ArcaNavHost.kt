package com.Hrshd1eux.filemanager.core.navigation

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.Hrshd1eux.filemanager.core.lock.manager.AppLockManager
import com.Hrshd1eux.filemanager.feature.lock.ui.AppLockOverlay
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.Hrshd1eux.filemanager.core.intent.ViewIntentResolver
import com.Hrshd1eux.filemanager.feature.about.AboutScreen
import com.Hrshd1eux.filemanager.feature.archive.ArchiveViewerScreen
import com.Hrshd1eux.filemanager.feature.backup.BackupScreen
import com.Hrshd1eux.filemanager.feature.browser.FileBrowserScreen
import com.Hrshd1eux.filemanager.feature.category.CategoryScreen
import com.Hrshd1eux.filemanager.feature.cleaner.apps.InstalledAppsScreen
import com.Hrshd1eux.filemanager.feature.cleaner.duplicates.DuplicateFinderScreen
import com.Hrshd1eux.filemanager.feature.cleaner.largefiles.LargeFilesScreen
import com.Hrshd1eux.filemanager.feature.cleaner.storage.StorageAnalyzerScreen
import com.Hrshd1eux.filemanager.feature.home.HomeScreen
import com.Hrshd1eux.filemanager.feature.search.FileSearchScreen
import com.Hrshd1eux.filemanager.feature.network.browser.NetworkFileBrowserScreen
import com.Hrshd1eux.filemanager.feature.network.diagnostics.NetworkPrivacyDiagnosticsScreen
import com.Hrshd1eux.filemanager.feature.security.SecurityCenterScreen
import com.Hrshd1eux.filemanager.feature.settings.SettingsScreen
import com.Hrshd1eux.filemanager.feature.trash.TrashScreen
import com.Hrshd1eux.filemanager.feature.vault.VaultScreen
import com.Hrshd1eux.filemanager.feature.viewer.audio.player.AudioPlaybackController
import com.Hrshd1eux.filemanager.feature.viewer.audio.ui.AudioMiniPlayer
import com.Hrshd1eux.filemanager.feature.viewer.audio.ui.AudioPlayerModalSheet
import com.Hrshd1eux.filemanager.feature.viewer.image.ImageViewerScreen
import com.Hrshd1eux.filemanager.feature.viewer.unified.UnifiedPreviewScreen
import com.Hrshd1eux.filemanager.feature.viewer.video.VideoViewerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ArcaNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    incomingIntent: Intent? = null,
    onIntentHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioController = remember { AudioPlaybackController.getInstance(context) }
    val isExpanded by audioController.isExpanded.collectAsStateWithLifecycle()

    val appLockManager = remember { AppLockManager.getInstance(context) }
    val isAppLocked by appLockManager.isAppLocked.collectAsStateWithLifecycle()
    val appLockSettings by appLockManager.settings.collectAsStateWithLifecycle()

    // Handle external ACTION_VIEW Intents
    LaunchedEffect(incomingIntent) {
        val intent = incomingIntent ?: return@LaunchedEffect
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val uri = intent.data!!
            val filePath = withContext(Dispatchers.IO) {
                ViewIntentResolver.resolveFilePath(context, uri)
            }
            if (filePath != null) {
                val destination = ViewIntentResolver.resolveDestination(filePath)
                navController.navigate(destination)
            }
            onIntentHandled()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> appLockManager.onAppForegrounded()
                Lifecycle.Event.ON_PAUSE -> appLockManager.onAppBackgrounded()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home,
            modifier = Modifier.fillMaxSize()
        ) {
        composable<Screen.Home>(
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            HomeScreen(
                onNavigateToBrowser = { initialPath, title ->
                    navController.navigate(Screen.Browser(initialPath = initialPath, title = title))
                },
                onNavigateToSearch = { rootPath ->
                    navController.navigate(Screen.Search(rootPath = rootPath))
                },
                onNavigateToCategory = { category ->
                    navController.navigate(Screen.Category(categoryName = category.name))
                },
                onNavigateToStorageAnalyzer = {
                    navController.navigate(Screen.StorageAnalyzer)
                },
                onNavigateToInstalledApps = {
                    navController.navigate(Screen.InstalledApps)
                },
                onNavigateToLargeFiles = { rootPath ->
                    navController.navigate(Screen.LargeFiles(rootPath = rootPath))
                },
                onNavigateToDuplicateFinder = { rootPath ->
                    navController.navigate(Screen.DuplicateFinder(rootPath = rootPath))
                },
                onNavigateToVault = {
                    navController.navigate(Screen.Vault)
                },
                onNavigateToBackup = {
                    navController.navigate(Screen.Backup)
                },
                onNavigateToNetworkBrowser = { connectionId, title ->
                    navController.navigate(Screen.NetworkBrowser(connectionId = connectionId, title = title))
                },
                onNavigateToSettings = { navController.navigate(Screen.Settings) },
                onNavigateToAbout = { navController.navigate(Screen.About) },
                onNavigateToPrivacy = { navController.navigate(Screen.PrivacyStatement) },
                onNavigateToTrash = { navController.navigate(Screen.Trash) },
                onNavigateToFtpServer = { navController.navigate(Screen.FtpServer) },
                onNavigateToPreview = { filePath, categoryName, directoryPath ->
                    navController.navigate(Screen.Preview(initialPath = filePath, categoryName = categoryName, directoryPath = directoryPath))
                },
                onNavigateToArchiveViewer = { archivePath, title ->
                    navController.navigate(Screen.ArchiveViewer(archivePath = archivePath, title = title))
                }
            )
        }

        composable<Screen.Browser>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.Browser>()
            FileBrowserScreen(
                initialPath = route.initialPath,
                title = route.title,
                onNavigateToSearch = { rootPath ->
                    navController.navigate(Screen.Search(rootPath = rootPath))
                },
                onNavigateToArchiveViewer = { archivePath, title ->
                    navController.navigate(Screen.ArchiveViewer(archivePath = archivePath, title = title))
                },
                onNavigateToImageViewer = { imagePath, categoryName, directoryPath ->
                    navController.navigate(Screen.ImageViewer(initialPath = imagePath, categoryName = categoryName, directoryPath = directoryPath))
                },
                onNavigateToVideoViewer = { videoPath, categoryName, directoryPath ->
                    navController.navigate(Screen.VideoViewer(initialPath = videoPath, categoryName = categoryName, directoryPath = directoryPath))
                },
                onNavigateToPreview = { filePath, categoryName, directoryPath ->
                    navController.navigate(Screen.Preview(initialPath = filePath, categoryName = categoryName, directoryPath = directoryPath))
                },
                onNavigateToPrivacy = { navController.navigate(Screen.PrivacyStatement) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.Category>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.Category>()
            CategoryScreen(
                categoryName = route.categoryName,
                rootPath = route.rootPath,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToArchiveViewer = { archivePath, title ->
                    navController.navigate(Screen.ArchiveViewer(archivePath = archivePath, title = title))
                },
                onNavigateToImageViewer = { imagePath, categoryName, rootPath ->
                    navController.navigate(Screen.ImageViewer(initialPath = imagePath, categoryName = categoryName, directoryPath = rootPath))
                },
                onNavigateToVideoViewer = { videoPath, categoryName, rootPath ->
                    navController.navigate(Screen.VideoViewer(initialPath = videoPath, categoryName = categoryName, directoryPath = rootPath))
                },
                onNavigateToPreview = { filePath, categoryName, rootPath ->
                    navController.navigate(Screen.Preview(initialPath = filePath, categoryName = categoryName, directoryPath = rootPath))
                },
                onNavigateToDirectory = { path ->
                    navController.navigate(Screen.Browser(initialPath = path, title = File(path).name))
                },
                onNavigateToInstalledApps = { navController.navigate(Screen.InstalledApps) }
            )
        }

        composable<Screen.Search>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.Search>()
            FileSearchScreen(
                rootPath = route.rootPath,
                initialCategory = route.categoryName,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDirectory = { path ->
                    navController.navigate(Screen.Browser(initialPath = path, title = File(path).name))
                },
                onNavigateToVault = {
                    navController.navigate(Screen.Vault)
                }
            )
        }

        composable<Screen.LargeFiles>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.LargeFiles>()
            LargeFilesScreen(
                rootPath = route.rootPath,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.DuplicateFinder>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.DuplicateFinder>()
            DuplicateFinderScreen(
                rootPath = route.rootPath,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPreview = { filePath ->
                    navController.navigate(Screen.Preview(initialPath = filePath))
                }
            )
        }

        composable<Screen.StorageAnalyzer>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) {
            StorageAnalyzerScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToInstalledApps = { navController.navigate(Screen.InstalledApps) },
                onNavigateToCategory = { category ->
                    navController.navigate(Screen.Category(categoryName = category.name))
                },
                onNavigateToLargeFiles = { navController.navigate(Screen.LargeFiles(rootPath = null)) },
                onNavigateToDuplicateFinder = { navController.navigate(Screen.DuplicateFinder(rootPath = null)) },
                onNavigateToPreview = { filePath ->
                    navController.navigate(Screen.Preview(initialPath = filePath))
                }
            )
        }

        composable<Screen.InstalledApps>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) {
            InstalledAppsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToApkCategory = { navController.navigate(Screen.Category(categoryName = "APKS")) }
            )
        }

        composable<Screen.ArchiveViewer>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.ArchiveViewer>()
            ArchiveViewerScreen(
                archivePath = route.archivePath,
                title = route.title,
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        (context as? Activity)?.finish()
                    }
                },
                onNavigateToDirectory = { path ->
                    navController.navigate(Screen.Browser(initialPath = path, title = File(path).name.ifEmpty { "Internal Storage" }))
                }
            )
        }

        composable<Screen.Preview>(
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.Preview>()
            UnifiedPreviewScreen(
                initialPath = route.initialPath,
                categoryName = route.categoryName,
                directoryPath = route.directoryPath,
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        (context as? Activity)?.finish()
                    }
                }
            )
        }

        composable<Screen.ImageViewer>(
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.ImageViewer>()
            UnifiedPreviewScreen(
                initialPath = route.initialPath,
                categoryName = route.categoryName,
                directoryPath = route.directoryPath,
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        (context as? Activity)?.finish()
                    }
                }
            )
        }

        composable<Screen.VideoViewer>(
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() }
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.VideoViewer>()
            UnifiedPreviewScreen(
                initialPath = route.initialPath,
                categoryName = route.categoryName,
                directoryPath = route.directoryPath,
                onNavigateBack = {
                    if (!navController.popBackStack()) {
                        (context as? Activity)?.finish()
                    }
                }
            )
        }

        composable<Screen.Settings>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPrivacy = { navController.navigate(Screen.PrivacyStatement) },
                onNavigateToAbout = { navController.navigate(Screen.About) },
                onNavigateToSecurityCenter = { navController.navigate(Screen.SecurityCenter) },
                onNavigateToBackup = { navController.navigate(Screen.Backup) }
            )
        }

        composable<Screen.Backup>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) {
            BackupScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.SecurityCenter>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) {
            SecurityCenterScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPrivacy = { navController.navigate(Screen.PrivacyStatement) }
            )
        }

        composable<Screen.NetworkDiagnostics>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) {
            NetworkPrivacyDiagnosticsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.PrivacyStatement>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) {
            com.Hrshd1eux.filemanager.feature.privacy.PrivacyStatementScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.About>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPrivacy = { navController.navigate(Screen.PrivacyStatement) }
            )
        }

        composable<Screen.Vault>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) {
            VaultScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.NetworkBrowser>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<Screen.NetworkBrowser>()
            NetworkFileBrowserScreen(
                connectionId = route.connectionId,
                initialPath = route.initialPath,
                title = route.title,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.Trash>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) {
            TrashScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Screen.FtpServer>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) {
            com.Hrshd1eux.filemanager.feature.ftp.FtpServerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }

        // Persistent Mini Player docked at the bottom of the screen
        AudioMiniPlayer(
            controller = audioController,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )

        // Full Audio Player Modal Sheet
        if (isExpanded) {
            AudioPlayerModalSheet(
                controller = audioController,
                onDismiss = { audioController.collapsePlayer() }
            )
        }

        // App Lock Fullscreen Overlay (if configured and locked)
        if (isAppLocked && appLockSettings.isConfigured) {
            AppLockOverlay(
                appLockManager = appLockManager,
                settings = appLockSettings
            )
        }
    }
}
