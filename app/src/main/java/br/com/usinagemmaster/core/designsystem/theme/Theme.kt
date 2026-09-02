package br.com.usinagemmaster.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

val Steel980 = Color(0xFF060A0D)
val Steel950 = Color(0xFF090D11)
val Steel900 = Color(0xFF10171C)
val Steel850 = Color(0xFF162027)
val Steel800 = Color(0xFF1D2931)
val Steel700 = Color(0xFF30414B)
val Steel500 = Color(0xFF657984)
val Steel400 = Color(0xFF91A2AB)
val Steel100 = Color(0xFFE9F0F3)
val IndustrialAmber = Color(0xFFFFB21A)
val IndustrialYellow = Color(0xFFFFD54F)
val IndustrialOrange = Color(0xFFFF7A1A)
val SuccessGreen = Color(0xFF55D998)
val DangerRed = Color(0xFFFF6868)
val ElectricBlue = Color(0xFF5CC8FF)
val PlasmaCyan = Color(0xFF76E4FF)

private val IndustrialColors = darkColorScheme(
        // V12_DARK_CONTRAST — foregrounds claros em superfícies escuras
        onTertiaryContainer = Color.White,
        // V10_DARK_CONTRAST: conteúdo legível em superfícies escuras
    primary = IndustrialAmber,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF493400),
    onPrimaryContainer = Color.White,
    secondary = Steel400,
    onSecondary = Color.White,
    secondaryContainer = Steel800,
    onSecondaryContainer = Color.White,
    tertiary = ElectricBlue,
    onTertiary = Color.White,
    background = Steel980,
    surface = Steel900,
    surfaceVariant = Steel800,
    surfaceContainer = Steel850,
    surfaceContainerHigh = Color(0xFF1E2A31),
    surfaceContainerHighest = Color(0xFF26343C),
    outline = Steel700,
    outlineVariant = Color(0xFF26343C),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFD7DEE3),
    error = DangerRed
)

private val GameTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.6f).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.35f).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 23.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 21.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = .45.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp)
)

private val GameShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun UsinagemMasterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IndustrialColors,
        typography = GameTypography,
        shapes = GameShapes,
        content = content
    )
}
