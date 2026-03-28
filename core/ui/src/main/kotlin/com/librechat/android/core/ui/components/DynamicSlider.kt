package com.librechat.android.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.librechat.android.core.ui.R
import kotlin.math.roundToInt

/** Labeled slider that snaps to step increments, with stepCount derived from (max - min) / step. */
@Composable
fun DynamicSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    min: Float = 0f,
    max: Float = 1f,
    step: Float = 0.1f,
    description: String? = null,
    displayDecimals: Int = 2,
) {
    val stepCount = ((max - min) / step).toInt().coerceAtLeast(1) - 1
    val displayValue = "%.${displayDecimals}f".format(value)

    val sliderCd = stringResource(R.string.cd_slider_value, label, displayValue)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = sliderCd },
    ) {
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
                text = displayValue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = { newValue ->
                onValueChange((newValue / step).roundToInt() * step)
            },
            valueRange = min..max,
            steps = stepCount,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DynamicSliderPreview() {
    var value by remember { mutableFloatStateOf(0.7f) }
    DynamicSlider(
        label = "Temperature",
        value = value,
        onValueChange = { value = it },
        min = 0f,
        max = 2f,
        step = 0.1f,
        description = "Controls randomness of the output.",
    )
}
