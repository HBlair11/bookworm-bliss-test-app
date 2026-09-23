package com.bookwormbliss.app.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookwormbliss.app.R
import com.bookwormbliss.app.data.model.AlignOption
import com.bookwormbliss.app.data.model.FontId
import com.bookwormbliss.app.ui.theme.ReaderFontId
import com.bookwormbliss.app.ui.theme.ReaderThemeId
import com.bookwormbliss.app.ui.theme.dimens

@Composable
fun ReaderScreen(
    bookId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ReaderViewModel = viewModel(factory = ReaderViewModel.factory(bookId))
    val state by viewModel.uiState.collectAsState()
    val dimens = MaterialTheme.dimens

    if (state.isLoading || state.book == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val readerTheme = ReaderThemeId.entries.firstOrNull { it.name == state.prefs.theme.name } ?: ReaderThemeId.IVORY
    val fontFamily = ReaderFontId.entries.firstOrNull { it.name == state.prefs.font.name }?.family
        ?: androidx.compose.ui.text.font.FontFamily.Serif
    val textAlign = when (state.prefs.align) {
        AlignOption.LEFT, AlignOption.ORIGINAL -> TextAlign.Start
        AlignOption.JUSTIFY -> TextAlign.Justify
        AlignOption.CENTER -> TextAlign.Center
        AlignOption.RIGHT -> TextAlign.End
    }

    Column(modifier.fillMaxSize().background(readerTheme.bg)) {
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.reader_chapter_of, state.currentIndex + 1, state.chapters.size),
                    style = MaterialTheme.typography.titleSmall,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            },
            actions = {
                IconButton(onClick = { viewModel.toggleTocSheet(true) }) {
                    Icon(Icons.Filled.List, contentDescription = stringResource(R.string.reader_toc))
                }
                IconButton(onClick = viewModel::addBookmark) {
                    Icon(Icons.Filled.BookmarkAdd, contentDescription = stringResource(R.string.reader_add_bookmark))
                }
                IconButton(onClick = { if (state.isTtsSpeaking) viewModel.pauseTts() else viewModel.playTts() }) {
                    Icon(
                        if (state.isTtsSpeaking) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.reader_tts_play),
                    )
                }
                IconButton(onClick = { viewModel.toggleSettingsSheet(true) }) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.reader_settings))
                }
            },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = readerTheme.surface,
                titleContentColor = readerTheme.ink,
                navigationIconContentColor = readerTheme.ink,
                actionIconContentColor = readerTheme.ink,
            ),
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = state.prefs.margin.dp, vertical = dimens.lg),
            verticalArrangement = Arrangement.spacedBy(dimens.md),
        ) {
            items(state.paragraphs) { paragraph ->
                Text(
                    text = paragraph,
                    color = readerTheme.ink,
                    fontFamily = fontFamily,
                    fontSize = state.prefs.fontSize.sp,
                    lineHeight = (state.prefs.fontSize * state.prefs.lineHeight).sp,
                    textAlign = textAlign,
                )
            }
        }

        LinearProgressIndicator(
            progress = { if (state.chapters.size > 1) state.currentIndex.toFloat() / (state.chapters.size - 1) else 1f },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().background(readerTheme.surface).padding(dimens.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = viewModel::previousChapter, enabled = state.currentIndex > 0) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = readerTheme.ink)
            }
            Text(
                "${((if (state.chapters.size > 1) state.currentIndex.toFloat() / (state.chapters.size - 1) else 1f) * 100).toInt()}%",
                color = readerTheme.ink,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
            IconButton(onClick = viewModel::nextChapter, enabled = state.currentIndex < state.chapters.lastIndex) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = readerTheme.ink)
            }
        }
    }

    if (state.showTocSheet) {
        ModalBottomSheet(onDismissRequest = { viewModel.toggleTocSheet(false) }) {
            LazyColumn(Modifier.padding(dimens.md)) {
                items(state.chapters) { chapter ->
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.goToChapter(chapter.spineIndex) }
                            .padding(vertical = dimens.sm),
                    )
                }
            }
        }
    }

    if (state.showSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { viewModel.toggleSettingsSheet(false) }) {
            ReaderSettingsSheetContent(
                prefs = state.prefs,
                onFontSizeChange = { size -> viewModel.updatePrefs { it.copy(fontSize = size) } },
                onLineHeightChange = { lh -> viewModel.updatePrefs { it.copy(lineHeight = lh) } },
                onThemeChange = { theme -> viewModel.updatePrefs { it.copy(theme = theme) } },
                onFontChange = { font -> viewModel.updatePrefs { it.copy(font = font) } },
            )
        }
    }
}

@Composable
private fun ReaderSettingsSheetContent(
    prefs: com.bookwormbliss.app.data.prefs.ReaderPreferences,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onThemeChange: (com.bookwormbliss.app.data.model.ThemeId) -> Unit,
    onFontChange: (FontId) -> Unit,
) {
    val dimens = MaterialTheme.dimens
    Column(Modifier.padding(dimens.lg)) {
        Text(stringResource(R.string.reader_font_size), style = MaterialTheme.typography.titleSmall)
        Slider(
            value = prefs.fontSize.toFloat(),
            onValueChange = { onFontSizeChange(it.toInt()) },
            valueRange = 20f..56f,
        )
        Text(stringResource(R.string.reader_line_height), style = MaterialTheme.typography.titleSmall)
        Slider(
            value = prefs.lineHeight,
            onValueChange = onLineHeightChange,
            valueRange = 1.0f..2.6f,
        )
        Text(stringResource(R.string.reader_theme), style = MaterialTheme.typography.titleSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.sm),
            modifier = Modifier.padding(vertical = dimens.xs),
        ) {
            com.bookwormbliss.app.ui.theme.ReaderThemeId.entries.forEach { theme ->
                val isSelected = theme.name == prefs.theme.name
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 36.dp else 32.dp)
                        .background(theme.bg, androidx.compose.foundation.shape.CircleShape)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = theme.ink,
                            shape = androidx.compose.foundation.shape.CircleShape,
                        )
                        .clickable {
                            onThemeChange(com.bookwormbliss.app.data.model.ThemeId.valueOf(theme.name))
                        },
                )
            }
        }
        Text(stringResource(R.string.reader_font), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.sm)) {
            com.bookwormbliss.app.ui.theme.ReaderFontId.entries.forEach { font ->
                val isSelected = runCatching { FontId.valueOf(font.name) }.getOrNull() == prefs.font
                Text(
                    text = font.label,
                    fontFamily = font.family,
                    style = if (isSelected) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clickable { runCatching { FontId.valueOf(font.name) }.getOrNull()?.let(onFontChange) }
                        .padding(dimens.xs),
                )
            }
        }
    }
}
