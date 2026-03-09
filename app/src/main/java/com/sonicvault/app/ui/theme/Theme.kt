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
 * Dieter Rams / Braun T3–inspired: warm neutrals, layered depth, honest materials.
 * Dark:  #0A0907 chassis → #100E0B panels → #161310 UI → #D4CCBC text (aged paper).
 * Light: #F5F3F0 chassis → #EFECE8 panels → #E8E5E0 UI → #0A0907 text (warm charcoal).
 * No cold blue-white; Rams used warm off-whites and cream. Same family, inverted.
 */

/* Light: Rams inverse. Warm off-white chassis, charcoal text. No cold blue-white — honest materials. */
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0A0907),           /* Warm charcoal: Rams used black for emphasis on light Braun */
    onPrimary = Color(0xFFF5F3F0),         /* Warm cream when primary fills (e.g. buttons) */
    primaryContainer = Color(0xFFE8E5E0), /* Light walnut for primary container */
    onPrimaryContainer = Color(0xFF0A0907),
    secondary = Color(0xFF525249),         /* Muted warm gray for secondary */
    onSecondary = Color(0xFFF5F3F0),
    surface = Color(0xFFF5F3F0),           /* Chassis: warm off-white, aged paper (Rams light) */
    surfaceContainerLow = Color(0xFFEFECE8), /* Panels above chassis — subtle depth */
    surfaceContainer = Color(0xFFE8E5E0), /* UI elements: light walnut */
    onSurface = Color(0xFF0A0907),         /* Text: warm charcoal, same as dark chassis */
    onSurfaceVariant = Color(0xFF525249), /* Secondary text: muted warm gray */
    outline = Color(0xFF9C9892),
    error = Color(0xFF991B1B),
    onError = Color(0xFFF5F3F0),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

/* Dark: Braun T3–inspired. Chassis → panels → UI elements. Aged paper text (Rams on dark Braun). */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD4CCBC),           /* Warm accent: aged paper, used sparingly for emphasis */
    onPrimary = Color(0xFF0A0907),         /* Chassis dark when primary fills (e.g. buttons) */
    primaryContainer = Color(0xFF161310),  /* Dark walnut for primary container */
    onPrimaryContainer = Color(0xFFD4CCBC),
    secondary = Color(0xFFB8AFA0),         /* Slightly muted for secondary elements */
    onSecondary = Color(0xFF0A0907),
    surface = Color(0xFF0A0907),           /* Chassis: warm charcoal, Braun T3 back */
    surfaceContainerLow = Color(0xFF100E0B), /* Panels above chassis — depth */
    surfaceContainer = Color(0xFF161310),  /* UI elements: dark walnut */
    onSurface = Color(0xFFD4CCBC),         /* Text: aged paper, Rams on dark Braun */
    onSurfaceVariant = Color(0xFFB8AFA0), /* Secondary text: slightly muted */
    outline = Color(0xFF3D3832),
    error = Color(0xFFE8A598),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF5C2018),
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

/** Work Sans for brand title (KYMA). Variable font; W700 for confident hierarchy. Rams: minimal, geometric. */
val WorkSansFamily: FontFamily = FontFamily(
    Font(R.font.work_sans_wght, FontWeight.W600),
    Font(R.font.work_sans_wght, FontWeight.W700)
)

/** Title style for KYMA: Work Sans Bold. Strong but not overpowering. */
val HeadlineLargeStyle = TextStyle(
    fontFamily = WorkSansFamily,
    fontWeight = FontWeight.W700,
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
