package com.awebo.ytext.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.awebo.ytext.data.AppLanguage
import com.awebo.ytext.ui.vm.SettingsViewModel
import org.koin.compose.koinInject

@Composable
fun Settings(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinInject(),
    onDismiss: () -> Unit,
) {
    val currentLanguage = viewModel.currentLanguage.collectAsState()

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Language",
            style = MaterialTheme.typography.h6
        )
        
        AppLanguage.values().forEach { language ->
            LanguageRadioButton(
                text = language.toString(),
                selected = currentLanguage.value == language,
                onSelected = { viewModel.setLanguage(language) }
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Close")
        }
    }
}

@Composable
private fun LanguageRadioButton(
    text: String,
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelected,
                role = Role.RadioButton
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Text(
            text = text,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}