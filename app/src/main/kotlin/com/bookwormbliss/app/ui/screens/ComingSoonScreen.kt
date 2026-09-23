package com.bookwormbliss.app.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bookwormbliss.app.R
import com.bookwormbliss.app.ui.components.EmptyState

/**
 * Shared placeholder for destinations that are wired into navigation but
 * don't have their full native implementation yet (Stats, Vocabulary,
 * Authors/Series, Reading Nook, Search). Styled with the same design system
 * as everything else so the drawer feels complete even before these screens
 * get built out.
 */
@Composable
fun ComingSoonScreen(bodyRes: Int, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.coming_soon_title),
        body = stringResource(bodyRes),
        modifier = modifier.fillMaxSize(),
    )
}
