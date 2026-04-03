package com.librechat.android.core.ui.theme

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Ensures a minimum 48dp touch target for accessibility compliance.
 */
fun Modifier.minTouchTarget(): Modifier = sizeIn(minWidth = 48.dp, minHeight = 48.dp)

/**
 * Marks a composable as a semantic heading for screen readers.
 */
fun Modifier.semanticHeading(): Modifier = semantics { heading() }

/**
 * Content description for an AI provider endpoint.
 */
fun endpointContentDescription(endpoint: String): String = "AI provider: $endpoint"

/**
 * Content description for a model icon.
 */
fun modelIconContentDescription(modelName: String): String = "Model: $modelName"
