package com.Hrshd1eux.filemanager.core.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.Hrshd1eux.filemanager.feature.about.AboutScreen
import com.Hrshd1eux.filemanager.feature.archive.ArchiveViewerScreen
import com.Hrshd1eux.filemanager.feature.browser.FileBrowserScreen
import com.Hrshd1eux.filemanager.feature.cleaner.duplicates.DuplicateFinderScreen
import com.Hrshd1eux.filemanager.feature.cleaner.largefiles.LargeFilesScreen
import com.Hrshd1eux.filemanager.feature.home.HomeScreen
import com.Hrshd1eux.filemanager.feature.search.FileSearchScreen
import com.Hrshd1eux.filemanager.feature.settings.SettingsScreen
import com.Hrshd1eux.filemanager.feature.vault.VaultScreen
import java.io.File

@Composable
fun ArcaNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home,
        modifier = modifier
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
                onNavigateToCategorySearch = { category ->
                    navController.navigate(Screen.Search(rootPath = null, categoryName = category.name))
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
                onNavigateToSettings = { navController.navigate(Screen.Settings) },
                onNavigateToAbout = { navController.navigate(Screen.About) }
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
                onNavigateBack = { navController.popBackStack() }
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
                onNavigateBack = { navController.popBackStack() }
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
                onNavigateBack = { navController.popBackStack() }
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
                onNavigateToAbout = { navController.navigate(Screen.About) }
            )
        }

        composable<Screen.About>(
            enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
            exitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
        ) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
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
    }
}
