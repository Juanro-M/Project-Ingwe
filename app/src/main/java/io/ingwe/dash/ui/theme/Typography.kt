package io.ingwe.dash.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Ingwe typography.
 *
 * Numbers use tabular figures ("tnum") so readouts don't jitter as digits change,
 * and a light weight at large sizes — legible at a glance without shouting.
 * Labels are small, sentence case, secondary colour.
 */
private const val TABULAR = "tnum"

/** Large live readout inside a dial or panel. */
val ReadoutLarge = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Light,
    fontSize = 48.sp,
    letterSpacing = (-0.5).sp,
    fontFeatureSettings = TABULAR,
)

/** Secondary numeric value in a panel row. */
val ReadoutMedium = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 24.sp,
    fontFeatureSettings = TABULAR,
)

/** Small numeric value (bars, list rows). */
val ReadoutSmall = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 17.sp,
    fontFeatureSettings = TABULAR,
)

/** Quiet label above / beside a value. */
val LabelStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    letterSpacing = 0.2.sp,
)

val IngweTypography = Typography(
    displayLarge = ReadoutLarge,
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelMedium = LabelStyle,
    labelSmall = LabelStyle.copy(fontSize = 12.sp),
)
