package ai.guardian.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---- Brand tokens ---------------------------------------------------------------------------

private val Blue10 = Color(0xFF001848)
private val Blue30 = Color(0xFF1B3F99)
private val Blue40 = Color(0xFF2155CD)
private val Blue80 = Color(0xFFB3C5FF)
private val Blue90 = Color(0xFFDBE1FF)
private val Teal40 = Color(0xFF00696B)
private val Teal80 = Color(0xFF4CDADC)
private val Teal90 = Color(0xFFB8F0F0)
private val Neutral10 = Color(0xFF111318)
private val Neutral17 = Color(0xFF1B1D23)
private val Neutral22 = Color(0xFF25282F)
private val Neutral90 = Color(0xFFE2E2E9)
private val Neutral96 = Color(0xFFF2F3FA)
private val Neutral98 = Color(0xFFF9F9FF)
private val NeutralVariant30 = Color(0xFF44464F)
private val NeutralVariant50 = Color(0xFF757780)
private val NeutralVariant80 = Color(0xFFC5C6D0)
private val NeutralVariant90 = Color(0xFFE1E2EC)
private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)

private val LightColors = lightColorScheme(
    primary = Blue40, onPrimary = Color.White, primaryContainer = Blue90, onPrimaryContainer = Blue10,
    secondary = Teal40, onSecondary = Color.White, secondaryContainer = Teal90, onSecondaryContainer = Color(0xFF002020),
    background = Neutral98, onBackground = Neutral10, surface = Neutral98, onSurface = Neutral10,
    surfaceVariant = NeutralVariant90, onSurfaceVariant = NeutralVariant30, surfaceContainer = Neutral96,
    surfaceContainerHigh = Color(0xFFE8E9F0), surfaceContainerLow = Color(0xFFF3F3FA),
    outline = NeutralVariant50, outlineVariant = NeutralVariant80, error = Red40,
)

private val DarkColors = darkColorScheme(
    primary = Blue80, onPrimary = Color(0xFF002A78), primaryContainer = Blue30, onPrimaryContainer = Blue90,
    secondary = Teal80, onSecondary = Color(0xFF003737), secondaryContainer = Color(0xFF004F50), onSecondaryContainer = Teal90,
    background = Neutral10, onBackground = Neutral90, surface = Neutral10, onSurface = Neutral90,
    surfaceVariant = NeutralVariant30, onSurfaceVariant = NeutralVariant80, surfaceContainer = Neutral17,
    surfaceContainerHigh = Neutral22, surfaceContainerLow = Color(0xFF16181D),
    outline = Color(0xFF8F909A), outlineVariant = NeutralVariant30, error = Red80,
)

// ---- Risk tokens ----------------------------------------------------------------------------

/** Colours for the five risk bands. Each band has a strong colour and a soft container. */
@Immutable
data class RiskColors(
    val low: Color, val lowContainer: Color,
    val guarded: Color, val guardedContainer: Color,
    val moderate: Color, val moderateContainer: Color,
    val high: Color, val highContainer: Color,
    val critical: Color, val criticalContainer: Color,
    val neutral: Color, val neutralContainer: Color,
)

private val LightRisk = RiskColors(
    low = Color(0xFF2E7D32), lowContainer = Color(0xFFDDF3DE),
    guarded = Color(0xFF00707A), guardedContainer = Color(0xFFD3F1F3),
    moderate = Color(0xFF9A6700), moderateContainer = Color(0xFFFFEDC2),
    high = Color(0xFFC25100), highContainer = Color(0xFFFFE0CC),
    critical = Color(0xFFB3261E), criticalContainer = Color(0xFFFFDAD6),
    neutral = NeutralVariant50, neutralContainer = NeutralVariant90,
)

private val DarkRisk = RiskColors(
    low = Color(0xFF8CD790), lowContainer = Color(0xFF173D1A),
    guarded = Color(0xFF6FD6DE), guardedContainer = Color(0xFF0B3A3E),
    moderate = Color(0xFFF5C451), moderateContainer = Color(0xFF45340A),
    high = Color(0xFFFFB07A), highContainer = Color(0xFF4F2708),
    critical = Color(0xFFFFB4AB), criticalContainer = Color(0xFF5C1712),
    neutral = NeutralVariant80, neutralContainer = NeutralVariant30,
)

val LocalRiskColors = staticCompositionLocalOf { LightRisk }

private val GuardianTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val GuardianShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

/** Monospace style for showing raw evidence (links, matched phrases). */
val EvidenceTextStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 13.sp)

@Composable
fun GuardianTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRiskColors provides if (darkTheme) DarkRisk else LightRisk) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = GuardianTypography,
            shapes = GuardianShapes,
            content = content,
        )
    }
}

object GuardianTheme {
    val riskColors: RiskColors
        @Composable get() = LocalRiskColors.current
}
