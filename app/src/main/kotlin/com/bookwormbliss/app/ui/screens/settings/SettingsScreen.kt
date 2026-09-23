package com.bookwormbliss.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bookwormbliss.app.R
import com.bookwormbliss.app.ui.components.SectionHeading
import com.bookwormbliss.app.ui.theme.bookwormColors
import com.bookwormbliss.app.ui.theme.dimens

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val prefs by viewModel.prefs.collectAsState()
    val dimens = MaterialTheme.dimens
    val colors = MaterialTheme.bookwormColors

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(dimens.screenHorizontalPhone),
        verticalArrangement = Arrangement.spacedBy(dimens.md),
    ) {
        SectionHeading(stringResource(R.string.settings_section_reading))

        SettingRow(
            label = stringResource(R.string.settings_keep_screen_on),
            checked = prefs.keepScreenOn,
            onCheckedChange = { checked -> viewModel.update { it.copy(keepScreenOn = checked) } },
        )
        SettingRow(
            label = stringResource(R.string.settings_page_turn_animation),
            checked = prefs.pageTurnAnimation,
            onCheckedChange = { checked -> viewModel.update { it.copy(pageTurnAnimation = checked) } },
        )
        SettingRow(
            label = stringResource(R.string.settings_hyphenation),
            checked = prefs.hyphenation,
            onCheckedChange = { checked -> viewModel.update { it.copy(hyphenation = checked) } },
        )

        Divider(color = colors.divider)
        SectionHeading(stringResource(R.string.settings_section_library))
        SettingRow(
            label = "Grid view by default",
            checked = prefs.viewModeGrid,
            onCheckedChange = { checked -> viewModel.update { it.copy(viewModeGrid = checked) } },
        )

        Divider(color = colors.divider)
        SectionHeading(stringResource(R.string.settings_section_about))
        Text(
            text = stringResource(R.string.settings_privacy_note),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.bookwormColors.textPrimary)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
