package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ParameterDefinition
import com.garfiec.librechat.core.model.ParameterType

@Stable
data class ModelParameters(
    val temperature: Float = 1.0f,
    val maxOutputTokens: Int? = null,
    val topP: Float = 1.0f,
    val frequencyPenalty: Float = 0.0f,
    val presencePenalty: Float = 0.0f,
    val customName: String = "",
    val customInstructions: String = "",
    val maxContextTokens: Int? = null,
    val topK: Int? = null,
    val resendFiles: Boolean = false,
    val thinking: Boolean = false,
    val thinkingBudget: String = "Auto",
    val webSearch: Boolean = false,
    val fileTokenLimit: Int? = null,
    val dynamicValues: Map<String, String> = emptyMap(),
) {
    companion object {
        val DEFAULT = ModelParameters()
    }

    /**
     * Reads a parameter value by its registry key, bridging between the typed fields
     * in ModelParameters and the dynamic string-based system.
     */
    fun getValueForKey(key: String): String = when (key) {
        "chatGptLabel", "modelLabel" -> customName
        "promptPrefix", "system" -> customInstructions
        "maxContextTokens" -> maxContextTokens?.toString() ?: ""
        "max_tokens", "maxOutputTokens", "maxTokens" -> maxOutputTokens?.toString() ?: ""
        "temperature" -> temperature.toString()
        "top_p", "topP" -> topP.toString()
        "frequency_penalty" -> frequencyPenalty.toString()
        "presence_penalty" -> presencePenalty.toString()
        "topK" -> (topK ?: 0).toString()
        "resendFiles" -> resendFiles.toString()
        "thinking" -> thinking.toString()
        "thinkingBudget" -> thinkingBudget
        "web_search" -> webSearch.toString()
        "fileTokenLimit" -> fileTokenLimit?.toString() ?: ""
        "stop" -> dynamicValues["stop"] ?: ""
        "reasoning_effort", "effort" -> dynamicValues[key] ?: ""
        "promptCache" -> dynamicValues["promptCache"] ?: "false"
        else -> dynamicValues[key] ?: ""
    }

    /**
     * Returns a copy of ModelParameters with the given key updated to the new value.
     * Maps dynamic registry keys back to the typed fields.
     */
    fun withUpdatedKey(key: String, value: String): ModelParameters = when (key) {
        "chatGptLabel", "modelLabel" -> copy(customName = value)
        "promptPrefix", "system" -> copy(customInstructions = value)
        "maxContextTokens" -> copy(maxContextTokens = value.toIntOrNull())
        "max_tokens", "maxOutputTokens", "maxTokens" -> copy(maxOutputTokens = value.toIntOrNull())
        "temperature" -> copy(temperature = value.toFloatOrNull() ?: temperature)
        "top_p", "topP" -> copy(topP = value.toFloatOrNull() ?: topP)
        "frequency_penalty" -> copy(frequencyPenalty = value.toFloatOrNull() ?: frequencyPenalty)
        "presence_penalty" -> copy(presencePenalty = value.toFloatOrNull() ?: presencePenalty)
        "topK" -> {
            val intVal = value.toIntOrNull()
            copy(topK = if (intVal == 0) null else intVal)
        }
        "resendFiles" -> copy(resendFiles = value.toBooleanStrictOrNull() ?: resendFiles)
        "thinking" -> copy(thinking = value.toBooleanStrictOrNull() ?: thinking)
        "thinkingBudget" -> copy(thinkingBudget = value)
        "web_search" -> copy(webSearch = value.toBooleanStrictOrNull() ?: webSearch)
        "fileTokenLimit" -> copy(fileTokenLimit = value.toIntOrNull())
        else -> copy(dynamicValues = dynamicValues.toMutableMap().apply { this[key] = value })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelParameterSheet(
    parameters: ModelParameters,
    onParametersChange: (ModelParameters) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    selectedEndpoint: String = "",
    dynamicParameterDefinitions: List<ParameterDefinition>? = null,
    onSaveAsPreset: () -> Unit = {},
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        ModelParameterContent(
            parameters = parameters,
            onParametersChange = onParametersChange,
            selectedEndpoint = selectedEndpoint,
            dynamicParameterDefinitions = dynamicParameterDefinitions,
            onSaveAsPreset = onSaveAsPreset,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
        )
    }
}

@Composable
fun ModelParameterContent(
    parameters: ModelParameters,
    onParametersChange: (ModelParameters) -> Unit,
    modifier: Modifier = Modifier,
    selectedEndpoint: String = "",
    dynamicParameterDefinitions: List<ParameterDefinition>? = null,
    onSaveAsPreset: () -> Unit = {},
) {
    val definitions = remember(selectedEndpoint, dynamicParameterDefinitions) {
        if (!dynamicParameterDefinitions.isNullOrEmpty()) {
            dynamicParameterDefinitions
        } else {
            EndpointParameterRegistry.getDefinitions(selectedEndpoint)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Model Parameters",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )

        Spacer(modifier = Modifier.height(4.dp))

        definitions.forEach { definition ->
            val currentValue = parameters.getValueForKey(definition.key)

            when (definition.type) {
                ParameterType.TEXT -> {
                    DynamicInput(
                        label = definition.label,
                        value = currentValue,
                        onValueChange = { newValue ->
                            onParametersChange(parameters.withUpdatedKey(definition.key, newValue))
                        },
                        placeholder = definition.default?.ifEmpty { "Default" } ?: "Default",
                        description = definition.description,
                    )
                }

                ParameterType.TEXTAREA -> {
                    DynamicTextarea(
                        label = definition.label,
                        value = currentValue,
                        onValueChange = { newValue ->
                            onParametersChange(parameters.withUpdatedKey(definition.key, newValue))
                        },
                        placeholder = definition.default?.ifEmpty { null },
                        description = definition.description,
                    )
                }

                ParameterType.SLIDER -> {
                    val min = definition.min?.toFloat() ?: 0f
                    val max = definition.max?.toFloat() ?: 1f
                    val step = definition.step?.toFloat() ?: 0.01f
                    val floatValue = currentValue.toFloatOrNull() ?: (definition.default?.toFloatOrNull() ?: min)

                    DynamicSlider(
                        label = definition.label,
                        value = floatValue.coerceIn(min, max),
                        onValueChange = { newValue ->
                            onParametersChange(parameters.withUpdatedKey(definition.key, newValue.toString()))
                        },
                        min = min,
                        max = max,
                        step = step,
                        description = definition.description,
                    )
                }

                ParameterType.SWITCH, ParameterType.CHECKBOX -> {
                    val checked = currentValue.toBooleanStrictOrNull()
                        ?: (definition.default?.toBooleanStrictOrNull() ?: false)

                    DynamicCheckbox(
                        label = definition.label,
                        checked = checked,
                        onCheckedChange = { newChecked ->
                            onParametersChange(parameters.withUpdatedKey(definition.key, newChecked.toString()))
                        },
                        description = definition.description,
                    )
                }

                ParameterType.DROPDOWN -> {
                    val displayValue = if (currentValue.isEmpty()) {
                        definition.options?.firstOrNull() ?: ""
                    } else {
                        currentValue
                    }

                    DynamicDropdown(
                        label = definition.label,
                        selectedValue = displayValue,
                        options = definition.options ?: emptyList(),
                        onValueChange = { newValue ->
                            onParametersChange(parameters.withUpdatedKey(definition.key, newValue))
                        },
                        description = definition.description,
                    )
                }

                ParameterType.TAGS -> {
                    val tags = if (currentValue.isBlank()) {
                        emptyList()
                    } else {
                        currentValue.split("\n").filter { it.isNotBlank() }
                    }

                    DynamicTagsInput(
                        label = definition.label,
                        tags = tags,
                        onTagsChange = { newTags ->
                            onParametersChange(
                                parameters.withUpdatedKey(definition.key, newTags.joinToString("\n")),
                            )
                        },
                        description = definition.description,
                        maxTags = definition.max?.toInt() ?: 4,
                    )
                }
            }
        }

        // Reset to defaults
        OutlinedButton(
            onClick = {
                onParametersChange(ModelParameters.DEFAULT)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Reset to Defaults")
        }

        // Save As Preset
        FilledTonalButton(
            onClick = onSaveAsPreset,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save As Preset")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
