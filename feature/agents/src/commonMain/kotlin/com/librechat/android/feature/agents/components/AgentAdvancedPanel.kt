package com.librechat.android.feature.agents.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import librechat_android.feature.agents.generated.resources.Res
import librechat_android.feature.agents.generated.resources.*
import kotlin.math.roundToInt

data class AgentAdvancedSettings(
    val temperature: Float? = null,
    val topP: Float? = null,
    val maxTokens: Int? = null,
)

@Composable
fun AgentAdvancedPanel(
    settings: AgentAdvancedSettings,
    onSettingsChanged: (AgentAdvancedSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.advanced_settings),
                style = MaterialTheme.typography.titleSmall,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) stringResource(Res.string.cd_collapse) else stringResource(Res.string.cd_expand),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Temperature slider (0-2)
                AdvancedSlider(
                    label = stringResource(Res.string.label_temperature),
                    value = settings.temperature ?: 1.0f,
                    onValueChange = {
                        onSettingsChanged(settings.copy(temperature = roundToStep(it, 0.1f)))
                    },
                    valueRange = 0f..2f,
                    steps = 19,
                    description = stringResource(Res.string.temperature_description),
                )

                // Top P slider (0-1)
                AdvancedSlider(
                    label = stringResource(Res.string.label_top_p),
                    value = settings.topP ?: 1.0f,
                    onValueChange = {
                        onSettingsChanged(settings.copy(topP = roundToStep(it, 0.05f)))
                    },
                    valueRange = 0f..1f,
                    steps = 19,
                    description = stringResource(Res.string.top_p_description),
                )

                // Max tokens input
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(Res.string.label_max_tokens),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(Res.string.max_tokens_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = settings.maxTokens?.toString() ?: "",
                        onValueChange = { newValue ->
                            val filtered = newValue.filter { it.isDigit() }
                            onSettingsChanged(
                                settings.copy(maxTokens = filtered.toIntOrNull()),
                            )
                        },
                        placeholder = { Text(stringResource(Res.string.default_value)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    description: String,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = formatFloat(sliderValue),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                onValueChange(it)
            },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun roundToStep(value: Float, step: Float): Float {
    return (value / step).roundToInt() * step
}

private fun formatFloat(value: Float): String {
    val rounded = (value * 100).roundToInt() / 100.0
    val str = rounded.toString()
    val dotIndex = str.indexOf('.')
    return if (dotIndex < 0) {
        "$str.00"
    } else {
        val decimals = str.length - dotIndex - 1
        when {
            decimals >= 2 -> str.substring(0, dotIndex + 3)
            decimals == 1 -> "${str}0"
            else -> "$str.00"
        }
    }
}
