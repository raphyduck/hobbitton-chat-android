package com.garfiec.librechat.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.source_serif_4_italic
import com.garfiec.librechat.core.ui.resources.source_serif_4_regular
import com.garfiec.librechat.core.ui.resources.source_serif_4_semibold
import org.jetbrains.compose.resources.Font

/**
 * Source Serif 4 (Adobe, SIL Open Font License, `core/ui/LICENSE-SourceSerif4.md`): the voice of
 * the app for the moments it speaks, a greeting, a screen title, a heading in a reply. Body text
 * stays on the system sans.
 */
@Composable
fun butlerSerif(): FontFamily = FontFamily(
    Font(Res.font.source_serif_4_regular, FontWeight.Normal),
    Font(Res.font.source_serif_4_italic, FontWeight.Normal, FontStyle.Italic),
    Font(Res.font.source_serif_4_semibold, FontWeight.SemiBold),
)

/**
 * The type scale: display, headline and `titleLarge` in the serif at regular weight; everything
 * from `titleMedium` down in the sans, with a body line of 26 sp that lets a reply breathe.
 */
@Composable
fun butlerTypography(): Typography {
    val serif = butlerSerif()
    return remember(serif) {
        val sans = FontFamily.SansSerif
        Typography(
            displayLarge = serif.style(52.sp, 60.sp, letterSpacing = (-0.5).sp),
            displayMedium = serif.style(42.sp, 50.sp, letterSpacing = (-0.25).sp),
            displaySmall = serif.style(34.sp, 42.sp),
            headlineLarge = serif.style(30.sp, 38.sp),
            headlineMedium = serif.style(26.sp, 34.sp),
            headlineSmall = serif.style(22.sp, 30.sp),
            titleLarge = serif.style(22.sp, 28.sp),
            titleMedium = sans.style(16.sp, 24.sp, FontWeight.Medium, 0.1.sp),
            titleSmall = sans.style(14.sp, 20.sp, FontWeight.Medium, 0.1.sp),
            bodyLarge = sans.style(16.sp, 26.sp),
            bodyMedium = sans.style(14.sp, 21.sp),
            bodySmall = sans.style(12.sp, 17.sp),
            labelLarge = sans.style(14.sp, 20.sp, FontWeight.Medium, 0.1.sp),
            labelMedium = sans.style(12.sp, 16.sp, FontWeight.Medium, 0.2.sp),
            labelSmall = sans.style(11.sp, 16.sp, FontWeight.Medium, 0.2.sp),
        )
    }
}

private fun FontFamily.style(
    size: TextUnit,
    lineHeight: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: TextUnit = 0.sp,
) = TextStyle(
    fontFamily = this,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
)
