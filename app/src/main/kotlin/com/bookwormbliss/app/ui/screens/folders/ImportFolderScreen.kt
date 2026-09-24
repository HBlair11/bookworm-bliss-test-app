package com.bookwormbliss.app.ui.screens.folders

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookwormbliss.app.R
import com.bookwormbliss.app.ui.components.BookwormButton
import com.bookwormbliss.app.ui.components.EmptyState
import com.bookwormbliss.app.ui.theme.bookwormColors
import com.bookwormbliss.app.ui.theme.dimens
import com.bookwormbliss.app.ui.theme.radii
import java.text.DateFormat
import java.util.Date

@Composable
fun ImportFolderScreen(modifier: Modifier = Modifier) {
    val viewModel: ImportFolderViewModel = viewModel(factory = ImportFolderViewModel.Factory)
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val dimens = MaterialTheme.dimens
    val colors = MaterialTheme.bookwormColors
    val snackbarHostState = remember { SnackbarHostState() }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val name = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: "Folder"
            viewModel.addFolder(uri, name)
        }
    }

    LaunchedEffect(state.lastResultMessage) {
        state.lastResultMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.screenHorizontalPhone),
                horizontalArrangement = Arrangement.spacedBy(dimens.sm),
            ) {
                BookwormButton(
                    text = stringResource(R.string.folders_add),
                    onClick = { pickFolder.launch(null) },
                    icon = Icons.Filled.CreateNewFolder,
                    modifier = Modifier.weight(1f),
                )
                if (state.folders.isNotEmpty()) {
                    IconButton(onClick = viewModel::rescanAll) {
                        Icon(Icons.Filled.Autorenew, contentDescription = stringResource(R.string.folders_rescan_all))
                    }
                }
            }

            if (state.folders.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.folders_empty_title),
                    body = stringResource(R.string.folders_empty_body),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = dimens.screenHorizontalPhone,
                        vertical = dimens.sm,
                    ),
                    verticalArrangement = Arrangement.spacedBy(dimens.sm),
                ) {
                    items(state.folders, key = { it.id }) { folder ->
                        val isScanning = state.scanningFolderId == folder.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surface, RoundedCornerShape(MaterialTheme.radii.medium))
                                .padding(dimens.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(dimens.sm),
                        ) {
                            Icon(Icons.Filled.Folder, contentDescription = null, tint = colors.accent)
                            Column(Modifier.weight(1f)) {
                                Text(folder.displayName, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                                Text(
                                    text = folder.lastScanDate?.let {
                                        stringResource(R.string.folders_last_scanned, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)))
                                    } ?: stringResource(R.string.folders_never_scanned),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                )
                            }
                            if (isScanning) {
                                CircularProgressIndicator(modifier = Modifier.padding(dimens.xs), strokeWidth = 2.dp)
                            } else {
                                IconButton(onClick = { viewModel.rescan(folder) }) {
                                    Icon(Icons.Filled.Autorenew, contentDescription = stringResource(R.string.folders_rescan))
                                }
                            }
                            IconButton(onClick = { viewModel.removeFolder(folder) }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
