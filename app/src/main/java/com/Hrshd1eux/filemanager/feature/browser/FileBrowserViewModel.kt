package com.Hrshd1eux.filemanager.feature.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.Hrshd1eux.filemanager.core.storage.model.StorageNode
import com.Hrshd1eux.filemanager.core.storage.provider.LocalFileSystemProvider
import com.Hrshd1eux.filemanager.core.storage.provider.StorageProvider
import com.Hrshd1eux.filemanager.feature.browser.model.BreadcrumbItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel managing directory traversal, breadcrumb hierarchy, and browser state.
 */
class FileBrowserViewModel(
    private val storageProvider: StorageProvider = LocalFileSystemProvider()
) : ViewModel() {

    private val _uiState = MutableStateFlow<BrowserUiState>(BrowserUiState.Loading)
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val rootNode: StorageNode = storageProvider.getRootNode()

    fun initialize(initialPath: String?, hasPermission: Boolean) {
        val targetPath = initialPath ?: rootNode.path
        loadDirectory(targetPath, hasPermission)
    }

    fun loadDirectory(path: String, hasPermission: Boolean) {
        if (!hasPermission) {
            _uiState.value = BrowserUiState.PermissionRequired()
            return
        }

        viewModelScope.launch {
            _uiState.value = BrowserUiState.Loading

            val result = storageProvider.listChildren(path)
            result.onSuccess { children ->
                val sorted = children.sortedWith(
                    compareByDescending<StorageNode> { it.isDirectory }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                )

                val breadcrumbs = buildBreadcrumbs(
                    currentPath = path,
                    rootPath = rootNode.path,
                    rootDisplayName = storageProvider.displayName
                )

                val stats = storageProvider.getStorageStats().getOrNull()
                val currentTitle = if (path == rootNode.path) {
                    storageProvider.displayName
                } else {
                    File(path).name.ifEmpty { path }
                }

                _uiState.value = BrowserUiState.Success(
                    currentPath = path,
                    currentTitle = currentTitle,
                    breadcrumbs = breadcrumbs,
                    items = sorted,
                    stats = stats
                )
            }.onFailure { error ->
                _uiState.value = BrowserUiState.Error(
                    message = error.localizedMessage ?: "Failed to read directory."
                )
            }
        }
    }

    fun navigateInto(node: StorageNode, hasPermission: Boolean) {
        if (node.isDirectory) {
            loadDirectory(node.path, hasPermission)
        }
    }

    fun navigateUp(hasPermission: Boolean): Boolean {
        val currentState = _uiState.value
        if (currentState is BrowserUiState.Success) {
            if (currentState.currentPath == rootNode.path) {
                return false // At root, allow back navigation to exit browser
            }
            val parentFile = File(currentState.currentPath).parentFile
            if (parentFile != null && parentFile.exists()) {
                loadDirectory(parentFile.absolutePath, hasPermission)
                return true
            }
        }
        return false
    }

    fun navigateToBreadcrumb(item: BreadcrumbItem, hasPermission: Boolean) {
        loadDirectory(item.path, hasPermission)
    }

    fun refresh(hasPermission: Boolean) {
        val currentState = _uiState.value
        val path = if (currentState is BrowserUiState.Success) {
            currentState.currentPath
        } else {
            rootNode.path
        }
        loadDirectory(path, hasPermission)
    }

    companion object {
        fun buildBreadcrumbs(
            currentPath: String,
            rootPath: String,
            rootDisplayName: String
        ): List<BreadcrumbItem> {
            val normalizedCurrent = File(currentPath).absolutePath
            val normalizedRoot = File(rootPath).absolutePath

            val items = mutableListOf<BreadcrumbItem>()

            // Always add Root breadcrumb
            items.add(
                BreadcrumbItem(
                    name = rootDisplayName,
                    path = normalizedRoot,
                    isCurrent = normalizedCurrent == normalizedRoot
                )
            )

            if (normalizedCurrent != normalizedRoot && normalizedCurrent.startsWith(normalizedRoot)) {
                val relative = normalizedCurrent
                    .removePrefix(normalizedRoot)
                    .trimStart(File.separatorChar, '/', '\\')

                if (relative.isNotEmpty()) {
                    val segments = relative.split(File.separatorChar, '/', '\\').filter { it.isNotEmpty() }
                    var accumulatedPath = normalizedRoot

                    segments.forEachIndexed { index, segment ->
                        accumulatedPath = if (accumulatedPath.endsWith(File.separator) || accumulatedPath.endsWith("/")) {
                            accumulatedPath + segment
                        } else {
                            accumulatedPath + File.separator + segment
                        }

                        val isCurrent = (index == segments.lastIndex)
                        items.add(
                            BreadcrumbItem(
                                name = segment,
                                path = accumulatedPath,
                                isCurrent = isCurrent
                            )
                        )
                    }
                }
            }

            return items
        }
    }
}
