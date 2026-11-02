package io.github.yuuichi_s.astertune.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.navigation.NavController

@Composable
fun LibraryFoldersScreen(
    navController: NavController,
    filterContent: @Composable (() -> Unit)? = null
) {
    FolderScreen(navController, isRoot = true, libraryFilterContent = filterContent)
}
