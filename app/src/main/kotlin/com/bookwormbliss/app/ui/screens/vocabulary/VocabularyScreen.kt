package com.bookwormbliss.app.ui.screens.vocabulary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookwormbliss.app.R
import com.bookwormbliss.app.ui.components.EmptyState
import com.bookwormbliss.app.ui.theme.bookwormColors
import com.bookwormbliss.app.ui.theme.dimens
import com.bookwormbliss.app.ui.theme.radii

@Composable
fun VocabularyScreen(modifier: Modifier = Modifier) {
    val viewModel: VocabularyViewModel = viewModel(factory = VocabularyViewModel.Factory)
    val words by viewModel.words.collectAsState()
    val dimens = MaterialTheme.dimens
    val colors = MaterialTheme.bookwormColors

    if (words.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.coming_soon_title),
            body = stringResource(R.string.coming_soon_vocabulary),
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(dimens.screenHorizontalPhone, dimens.md, dimens.screenHorizontalPhone, dimens.huge),
        verticalArrangement = Arrangement.spacedBy(dimens.sm),
    ) {
        items(words, key = { it.id }) { entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, RoundedCornerShape(MaterialTheme.radii.medium))
                    .padding(dimens.md),
            ) {
                Text(entry.word, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text(entry.partOfSpeech, style = MaterialTheme.typography.labelSmall, color = colors.secondary)
                Text(entry.definition, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.padding(top = dimens.xxs))
                entry.example?.let {
                    Text("\u201c$it\u201d", style = MaterialTheme.typography.bodySmall, color = colors.textMuted, modifier = Modifier.padding(top = dimens.xxs))
                }
                entry.bookTitle?.let {
                    Text("From $it", style = MaterialTheme.typography.labelSmall, color = colors.textMuted, modifier = Modifier.padding(top = dimens.xs))
                }
            }
        }
    }
}
