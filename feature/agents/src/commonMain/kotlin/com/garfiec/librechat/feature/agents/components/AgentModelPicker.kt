package com.garfiec.librechat.feature.agents.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import librechat_mobile.feature.agents.generated.resources.Res
import librechat_mobile.feature.agents.generated.resources.*

data class ModelOption(
    val id: String,
    val name: String,
    val endpoint: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentModelPicker(
    selectedModel: String,
    availableModels: List<ModelOption>,
    onModelSelected: (modelId: String, provider: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredModels = remember(searchQuery, availableModels) {
        if (searchQuery.isBlank()) {
            availableModels
        } else {
            availableModels.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.id.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val groupedModels = remember(filteredModels) {
        filteredModels.groupBy { it.endpoint }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = if (expanded) searchQuery else selectedModel.ifEmpty { "" },
            onValueChange = { searchQuery = it },
            label = { Text(stringResource(Res.string.model_required_label)) },
            placeholder = { Text(stringResource(Res.string.select_model_hint)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                searchQuery = ""
            },
            modifier = Modifier.heightIn(max = 300.dp),
        ) {
            if (groupedModels.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(Res.string.no_models_found),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = {},
                    enabled = false,
                )
            } else {
                groupedModels.forEach { (endpoint, models) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = endpoint,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        onClick = {},
                        enabled = false,
                    )
                    models.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.name) },
                            onClick = {
                                onModelSelected(model.id, model.endpoint)
                                expanded = false
                                searchQuery = ""
                            },
                        )
                    }
                }
            }
        }
    }
}
