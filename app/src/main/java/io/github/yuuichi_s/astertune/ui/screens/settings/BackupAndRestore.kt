/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 O​u​t​er​Tu​ne Project
 * Copyright (C) 2026 AsterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package io.github.yuuichi_s.astertune.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import io.github.yuuichi_s.astertune.R
import io.github.yuuichi_s.astertune.constants.SettingsGroupSpacing
import io.github.yuuichi_s.astertune.ui.dialog.InfoLabel
import io.github.yuuichi_s.astertune.ui.screens.settings.fragments.BackupAndRestoreFrag
import io.github.yuuichi_s.astertune.viewmodels.BackupRestoreViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupAndRestore(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    SettingsScaffold(
        title = stringResource(R.string.backup_restore),
        navController = navController,
        scrollBehavior = scrollBehavior,
    ) {
        BackupAndRestoreFrag(viewModel)
        Spacer(modifier = Modifier.height(SettingsGroupSpacing))
        InfoLabel(stringResource(R.string.import_innertune_tooltip))
        Spacer(modifier = Modifier.height(8.dp))
        InfoLabel(stringResource(R.string.restore_lm_tooltip))
    }
}
