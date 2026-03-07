package com.sonicvault.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.sonicvault.app.R
import androidx.compose.ui.unit.sp

/**
 * Dieter Rams / Japanese minimalist: restrained palette, one accent, neutrals.
 * "Good design is as little design as possible." — Unobtrusive, honest, long-lasting.
 * Accessibility: onSurface/onSurfaceVariant on surface meet WCAG 4.5:1 contrast minimum.
 */

/* Light: off-white surface, charcoal/black text, Rams: honest, unobtrusive */
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5E7EB),
    onPrimaryContainer = Color(0xFF1F2937),
    secondary = Color(0xFF6B7280),
    onSecondary = Color(0xFFFFFFFF),
    surface = Color(0xFFFAFAFA),
    surfaceContainerLow = Color(0xFFF5F5F5),
    onSurface = Color(0xFF171717),
    onSurfaceVariant = Color(0xFF525252),
    outline = Color(0xFFA3A3A3),
    error = Color(0xFF991B1B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

/* Dark: dark gray surface, light text, same accent used sparingly */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE5E7EB),
    onPrimary = Color(0xFF111827),
    primaryContainer = Color(0xFF374151),
    onPrimaryContainer = Color(0xFFF9FAFB),
    secondary = Color(0xFF9CA3AF),
    onSecondary = Color(0xFF111827),
    surface = Color(0xFF0A0A0A),
    surfaceContainerLow = Color(0xFF1A1A1A),
    onSurface = Color(0xFFFAFAFA),
    onSurfaceVariant = Color(0xFFA3A3A3),
    outline = Color(0xFF525252),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF7F1D1D),
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFEE2E2)
)

/** Clear hierarchy, readable; no decorative type. */
private val SonicVaultTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.W600,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.W600,
        fontSize = 22.sp,
        letterSpacing = 0.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.W500,
        fontSize = 20.sp,
        letterSpacing = 0.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.W500,
        fontSize = 16.sp,
        letterSpacing = 0.15.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.25.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.25.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp,
        lineHeight = 20.sp
    )
)

/** JetBrains Mono for seed phrases. Fallback to system monospace if load fails. */
val JetBrainsMonoFamily: FontFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal)
)

/** Work Sans SemiBold for brand title (KYMA). Variable font, weight 600. Rams: minimal, geometric. */
val WorkSansFamily: FontFamily = FontFamily(
    Font(R.font.work_sans_wght, FontWeight.W600)
)

/** Title style for KYMA: Work Sans SemiBold. */
val HeadlineLargeStyle = TextStyle(
    fontFamily = WorkSansFamily,
    fontWeight = FontWeight.W600,
    fontSize = 28.sp,
    letterSpacing = (-0.5).sp,
    lineHeight = 36.sp
)

/** Uppercase labels for section headers; tracking-widest equivalent. Rams: understandable. */
val LabelUppercaseStyle = TextStyle(
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    letterSpacing = 2.sp,
    lineHeight = 16.sp
)

/** Material minimum touch target (44dp). Rams #8: thorough; accessibility. */
val TouchTargetMin = 44.dp

/** 8dp grid: use 8, 16, 24, 32, 48 for consistency (Rams: thorough to the last detail). */
object Spacing {
    const val xs = 8
    const val sm = 16
    const val md = 24
    const val lg = 32
    const val xl = 48

    /** Scale factor when compact mode is enabled. */
    private const val COMPACT_SCALE = 0.8f

    /** Returns scaled value in dp; use when compact mode is enabled. */
    fun scaled(compact: Boolean, base: Int) = (base * if (compact) COMPACT_SCALE else 1f).toInt()
}

/** Composition local for compact layout mode. Default false. */
val LocalCompactMode = compositionLocalOf { false }

/** Rams: sharp corners, no decorative rounding. */
private val SonicVaultShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

@Composable
fun SonicVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SonicVaultTypography,
        shapes = SonicVaultShapes,
        content = content
    )
}
