package com.automatelinux.bursa.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// "The board": the deep blue-black of a quote board after hours, numbers in warm paper
// white, and exactly two colours that mean something — green for up, red for down.
// Gold is the brand accent and is never used for a number that moved.

private val Night = Color(0xFF0E1626)
private val Gold = Color(0xFFF2B84B)

private val DarkScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF2B1D00),
    primaryContainer = Color(0xFF3A2C0E),
    onPrimaryContainer = Color(0xFFFFE2A8),
    secondary = Color(0xFFAEB9CC),
    onSecondary = Color(0xFF18202E),
    secondaryContainer = Color(0xFF222E44),
    onSecondaryContainer = Color(0xFFDCE4F2),
    tertiary = Color(0xFF8FB8FF),
    background = Color(0xFF0A101B),
    onBackground = Color(0xFFEDEFF3),
    surface = Color(0xFF0A101B),
    onSurface = Color(0xFFEDEFF3),
    surfaceVariant = Color(0xFF1B2638),
    onSurfaceVariant = Color(0xFF98A4B8),
    surfaceContainerLowest = Color(0xFF070B13),
    surfaceContainerLow = Color(0xFF101827),
    surfaceContainer = Color(0xFF131D2E),
    surfaceContainerHigh = Color(0xFF1B2638),
    surfaceContainerHighest = Color(0xFF243146),
    outline = Color(0xFF3A475C),
    outlineVariant = Color(0xFF212C3F),
    error = Color(0xFFFF8A7A),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF16233D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE4F1),
    onPrimaryContainer = Color(0xFF16233D),
    secondary = Color(0xFF566176),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7E4DC),
    onSecondaryContainer = Color(0xFF242B38),
    tertiary = Color(0xFF8A5E08),
    background = Color(0xFFF4F2EC),
    onBackground = Color(0xFF131A26),
    surface = Color(0xFFF4F2EC),
    onSurface = Color(0xFF131A26),
    surfaceVariant = Color(0xFFE9E6DE),
    onSurfaceVariant = Color(0xFF5A6474),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFAF7),
    surfaceContainer = Color(0xFFEFECE5),
    surfaceContainerHigh = Color(0xFFE9E6DE),
    surfaceContainerHighest = Color(0xFFE2DED4),
    outline = Color(0xFFC6C1B5),
    outlineVariant = Color(0xFFDFDBD1),
    error = Color(0xFFB3261E),
)

@Immutable
data class MarketColors(
    val up: Color,
    val upSoft: Color,
    val down: Color,
    val downSoft: Color,
    /** No change, or no number yet. */
    val flat: Color,
    val flatSoft: Color,
    /** Raised card surface. */
    val card: Color,
    /** Brand accent: the star, the active tab. */
    val accent: Color,
    /** The header band behind the portfolio total and the lead index: top of its gradient… */
    val hero: Color,
    /** …and the bottom. */
    val heroDeep: Color,
    val onHero: Color,
    val onHeroMuted: Color,
)

private val DarkMarket = MarketColors(
    up = Color(0xFF3DD68C), upSoft = Color(0xFF123524),
    down = Color(0xFFFF6B6B), downSoft = Color(0xFF3D1A1D),
    flat = Color(0xFF98A4B8), flatSoft = Color(0xFF1F2A3C),
    card = Color(0xFF131D2E),
    accent = Gold,
    hero = Color(0xFF1D2E52), heroDeep = Color(0xFF111A2E), onHero = Color(0xFFF7F1E3), onHeroMuted = Color(0xFFA9B6CC),
)

private val LightMarket = MarketColors(
    up = Color(0xFF0B8A4B), upSoft = Color(0xFFDDF3E6),
    down = Color(0xFFCC2F2F), downSoft = Color(0xFFFBE3E1),
    flat = Color(0xFF5A6474), flatSoft = Color(0xFFE9E6DE),
    card = Color(0xFFFFFFFF),
    accent = Color(0xFFB07A0C),
    hero = Color(0xFF22365E), heroDeep = Night, onHero = Color(0xFFF7F1E3), onHeroMuted = Color(0xFFA9B6CC),
)

val LocalMarketColors = staticCompositionLocalOf { DarkMarket }

object Bursa {
    val colors: MarketColors @Composable get() = LocalMarketColors.current
}

/** Green above zero, red below, grey at zero or unknown. */
fun MarketColors.of(v: Double?): Color = when {
    v == null || v == 0.0 -> flat
    v > 0 -> up
    else -> down
}

fun MarketColors.softOf(v: Double?): Color = when {
    v == null || v == 0.0 -> flatSoft
    v > 0 -> upSoft
    else -> downSoft
}

// Numbers are tabular (so a column lines up and a live price does not jitter) and always
// left-to-right: without that a Hebrew paragraph draws "-1.22%" as "1.22%-".
private fun number(size: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontSize = size.sp,
    fontWeight = weight,
    fontFeatureSettings = "tnum",
    letterSpacing = tracking.sp,
    textDirection = TextDirection.Ltr,
)

val NumHero = number(42, FontWeight.SemiBold, -1f)
val NumLarge = number(30, FontWeight.SemiBold, -0.5f)
val NumMedium = number(17, FontWeight.SemiBold)
val NumBody = number(15, FontWeight.Medium)
val NumSmall = number(13, FontWeight.Medium)
val NumTiny = number(11, FontWeight.Medium)

private val base = Typography()

/** Every Material style, in the app's own typeface. */
private fun typography(font: FontFamily): Typography {
    fun TextStyle.own() = copy(fontFamily = font, letterSpacing = 0.sp)
    return Typography(
        displayLarge = base.displayLarge.own(),
        displayMedium = base.displayMedium.own(),
        displaySmall = base.displaySmall.own(),
        headlineLarge = base.headlineLarge.own().copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.own().copy(fontWeight = FontWeight.Bold, fontSize = 28.sp),
        headlineSmall = base.headlineSmall.own().copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.own().copy(fontWeight = FontWeight.Bold, fontSize = 21.sp),
        titleMedium = base.titleMedium.own().copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        titleSmall = base.titleSmall.own().copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        bodyLarge = base.bodyLarge.own().copy(fontSize = 16.sp, lineHeight = 23.sp),
        bodyMedium = base.bodyMedium.own().copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = base.bodySmall.own().copy(fontSize = 12.5.sp, lineHeight = 17.sp),
        labelLarge = base.labelLarge.own().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.own().copy(fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.own().copy(fontWeight = FontWeight.Medium),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** The app's typeface, for the few text styles Material does not own (the Num* styles). */
val LocalAppFont = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/**
 * The app is Hebrew whatever the phone's language is, so the layout is always right-to-left.
 * [font] comes from the platform shell, which is where the font files live.
 */
@Composable
fun AppTheme(font: FontFamily, dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalMarketColors provides if (dark) DarkMarket else LightMarket,
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalAppFont provides font,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = typography(font),
            shapes = AppShapes,
            content = content,
        )
    }
}
