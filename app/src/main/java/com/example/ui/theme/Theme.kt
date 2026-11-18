package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.data.model.ThemeMode
import com.example.ui.i18n.ArabicStrings
import com.example.ui.i18n.EnglishStrings
import com.example.ui.i18n.LocalAppStrings

private val TacticalDarkColorScheme = darkColorScheme(
    primary = SignalAmber,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1A08),
    onPrimaryContainer = Color(0xFFFFB690),
    secondary = TacticalCyan,
    onSecondary = Color(0xFF00242E),
    secondaryContainer = Color(0xFF083240),
    onSecondaryContainer = Color(0xFF9DEFFF),
    tertiary = EmeraldOnline,
    onTertiary = Color(0xFF002B18),
    error = CrimsonBusy,
    onError = Color.White,
    errorContainer = Color(0xFF420A16),
    onErrorContainer = Color(0xFFFFB3BE),
    background = ObsidianBg,
    onBackground = TextLightPrimary,
    surface = SlateSurface,
    onSurface = TextLightPrimary,
    surfaceVariant = SlateElevated,
    onSurfaceVariant = TextLightSecondary,
    outline = SlateCardBorder,
    outlineVariant = Color(0xFF1D2735)
)

private val AnodizedLightColorScheme = lightColorScheme(
    primary = SignalAmberDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDFCE),
    onPrimaryContainer = Color(0xFF4D1A00),
    secondary = TacticalCyanDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0F3FF),
    onSecondaryContainer = Color(0xFF003242),
    tertiary = EmeraldOnlineDark,
    onTertiary = Color.White,
    error = CrimsonBusyDark,
    onError = Color.White,
    errorContainer = Color(0xFFFFD9DF),
    onErrorContainer = Color(0xFF4F0012),
    background = AluminumBg,
    onBackground = TextDarkPrimary,
    surface = AluminumSurface,
    onSurface = TextDarkPrimary,
    surfaceVariant = AluminumElevated,
    onSurfaceVariant = TextDarkSecondary,
    outline = AluminumBorder,
    outlineVariant = Color(0xFFE3E9F0)
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun GlobalWalkieTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    languageCode: String = "en",
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val isArabic = languageCode.startsWith("ar")
    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
    val strings = if (isArabic) ArabicStrings else EnglishStrings

    CompositionLocalProvider(
        LocalLayoutDirection provides layoutDirection,
        LocalAppStrings provides strings
    ) {
        MaterialTheme(
            colorScheme = if (isDark) TacticalDarkColorScheme else AnodizedLightColorScheme,
            typography = buildAppTypography(isArabic),
            shapes = AppShapes,
            content = content
        )
    }
}
