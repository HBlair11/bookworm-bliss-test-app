package com.bookwormbliss.app.ui.screens.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookwormbliss.app.R
import com.bookwormbliss.app.ui.components.BookCard
import com.bookwormbliss.app.ui.components.EmptyState
import com.bookwormbliss.app.ui.theme.dimens

@Composable
fun LibraryScreen(
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val dimens = MaterialTheme.dimens

    val pickEpubs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.importEpubs(uris) { uri ->
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        }
    }

    LaunchedEffect(state.importError) {
        state.importError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pickEpubs.launch(arrayOf("application/epub+zip", "application/octet-stream")) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.library_import)) },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isImporting && state.books.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.books.isEmpty() -> {
                    EmptyState(
                        title = stringResource(R.string.library_empty_title),
                        body = stringResource(R.string.library_empty_body),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(dimens.screenHorizontalPhone, dimens.md, dimens.screenHorizontalPhone, dimens.huge),
                        horizontalArrangement = Arrangement.spacedBy(dimens.md),
                        verticalArrangement = Arrangement.spacedBy(dimens.lg),
                    ) {
                        items(state.books, key = { it.id }) { book ->
                            BookCard(book = book, onClick = { onOpenBook(book.id) })
                        }
                    }
                }
            }
        }
    }
}
