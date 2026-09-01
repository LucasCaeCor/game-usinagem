package br.com.usinagemmaster.feature.employees

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.usinagemmaster.core.designsystem.component.drawPlayerAvatarFigure
import br.com.usinagemmaster.domain.catalog.EmployeeVisualCatalog
import br.com.usinagemmaster.domain.social.PlayerAvatar

/**
 * Retrato dos funcionários usando o mesmo renderer temático dos avatares.
 * Isso evita que a lista mostre um boneco genérico diferente do personagem visto na fábrica.
 */
@Composable
fun EmployeePortrait(
    legendaryCode: String?,
    specialty: String,
    name: String = "",
    employeeId: String = name,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp
) {
    val avatar = employeePortraitAvatar(employeeId, legendaryCode, name)
    val accent = portraitAccent(legendaryCode, specialty, avatar.skinStyle)
    Box(
        modifier = modifier
            .size(size)
            .background(
                Brush.radialGradient(listOf(accent.copy(alpha = .28f), Color(0xFF10181D))),
                RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size.minDimension / 92f
            drawCircle(Color.Black.copy(alpha = .22f), radius = this.size.minDimension * .36f, center = center + Offset(0f, 5f*s))
            drawPlayerAvatarFigure(
                base = Offset(this.size.width*.50f, this.size.height*.88f),
                avatar = avatar,
                scale = s,
                phase = .18f,
                walking = false,
                carrying = false
            )
            if (legendaryCode != null) {
                drawCircle(Color(0xFFFFD66B), this.size.minDimension*.44f, center, style = Stroke(1.8f*s))
            }
        }
    }
}

private fun employeePortraitAvatar(id: String, code: String?, name: String): PlayerAvatar {
    val visual = EmployeeVisualCatalog.resolve(id, name, code)
    val female = visual.female
    val skinStyle = visual.skinStyle
    val hair = visual.hairStyle
    return PlayerAvatar(
        gender = if (female) "FEMALE" else "MALE",
        skinStyle = skinStyle,
        bodyType = when (code) {
            "bodybuilder", "tatu_banhado", "pedrao" -> "STRONG"
            "magrao" -> "SLIM"
            else -> "STANDARD"
        },
        skinTone = when (kotlin.math.abs(name.hashCode()) % 4) {
            0 -> "LIGHT"
            1 -> "MEDIUM"
            2 -> "TAN"
            else -> "DARK"
        },
        hairStyle = hair,
        hairColor = visual.hairColor,
        uniformColor = when (code) {
            "moskitao" -> "GREEN"
            "gumersvaldo" -> "BLUE"
            "pedrao" -> "ORANGE"
            else -> "NAVY"
        },
        helmetColor = when (code) {
            "gumersvaldo" -> "BLACK"
            "bodybuilder" -> "WHITE"
            else -> "YELLOW"
        },
        accessory = if (code == "gumersvaldo") "GLASSES" else "NONE"
    )
}

private fun isFemaleName(name: String): Boolean = name.substringBefore(' ') in setOf(
    "Luciana", "Patrícia", "Camila", "Fernanda", "Amanda", "Juliana", "Mariana", "Beatriz", "Renata",
    "Larissa", "Daniela", "Aline", "Carolina", "Bianca", "Vanessa", "Jéssica", "Natália", "Priscila", "Letícia", "Isabela"
)

private fun portraitAccent(code: String?, specialty: String, skinStyle: String): Color = when {
    skinStyle == "PRINCESA" -> Color(0xFFCA5B9D)
    skinStyle == "TATUZAO" -> Color(0xFF8D6E63)
    skinStyle == "KENDAO_KIMONO" -> Color(0xFFE5E0D4)
    skinStyle == "PINOQUIO" -> Color(0xFF4C8ABC)
    skinStyle == "MAGRAO" -> Color(0xFF83A94B)
    skinStyle == "TREME_TREME" -> Color(0xFFD7B145)
    skinStyle == "BEBADO" -> Color(0xFF8B4A44)
    code == "gumersvaldo" -> Color(0xFF1976D2)
    code == "bodybuilder" -> Color(0xFF2E9D55)
    code == "moskitao" -> Color(0xFF168A9E)
    code == "merciao" -> Color(0xFF607D8B)
    specialty.contains("CNC") -> Color(0xFF308BD4)
    specialty.contains("WELD") -> Color(0xFFE76742)
    specialty.contains("QUALITY") -> Color(0xFF9A50B4)
    specialty.contains("STOCK") -> Color(0xFF4FA963)
    else -> Color(0xFFDB9F1B)
}
