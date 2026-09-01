package br.com.usinagemmaster.feature.employees

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun EmployeePortrait(
    legendaryCode: String?,
    specialty: String,
    name: String = "",
    modifier: Modifier = Modifier,
    size: Dp = 58.dp
) {
    val accent = portraitAccent(legendaryCode, specialty, name)
    Box(
        modifier = modifier
            .size(size)
            .background(
                Brush.radialGradient(listOf(accent.copy(alpha = .30f), Color(0xFF12191E))),
                RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val s = this.size.minDimension / 60f
            val female = isFemaleName(name)
            val bodyScale = when (legendaryCode) {
                "bodybuilder" -> 1.23f
                "pedrao", "tatu_banhado" -> 1.15f
                "magrao" -> .76f
                else -> if (female) .91f else 1f
            }
            val bodyHeight = if (legendaryCode == "magrao") 1.12f else 1f
            val skin = if (female) Color(0xFFD49A74) else Color(0xFFFFC79A)
            val helmet = when {
                female -> Color(0xFFFF9FC7)
                legendaryCode != null -> Color(0xFFFFB300)
                else -> Color(0xFFFFD54F)
            }

            drawCircle(Color.Black.copy(alpha = .22f), 18f * s, c + Offset(0f, 13f * s))
            drawRoundRect(
                accent,
                topLeft = c + Offset(-10f * bodyScale * s, 4f * s),
                size = Size(20f * bodyScale * s, 23f * bodyHeight * s),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(if (female) 9f * s else 7f * s)
            )
            drawLine(Color(0xFFFFD54F), c + Offset(-6f * s, 8f * s), c + Offset(5f * s, 23f * s), 1.7f * s)
            drawLine(Color(0xFFFFD54F), c + Offset(6f * s, 8f * s), c + Offset(-5f * s, 23f * s), 1.7f * s)
            drawCircle(skin, 8.2f * s, c + Offset(0f, -8f * s))

            if (female) {
                drawCircle(Color(0xFF4B302A), 5f*s, c + Offset(-7f*s, -4f*s))
                drawArc(Color(0xFF4B302A), 180f, 180f, true, c + Offset(-8f*s, -16f*s), Size(16f*s, 12f*s))
            }
            if (legendaryCode == "nikao_narizudo") {
                drawLine(skin, c + Offset(5f * s, -7f * s), c + Offset(12f * s, -5f * s), 2.5f * s)
            }
            if (legendaryCode == "gumersvaldo") {
                drawLine(Color(0xFF9EDBFF), c + Offset(-5f * s, -9f * s), c + Offset(5f * s, -9f * s), 1.6f * s)
            }
            if (legendaryCode == "nelsinho_treme_treme") {
                val dx = sin(1.8f) * 2f*s
                drawLine(Color(0xFFFFD66B), c + Offset(-13f*s+dx, -1f*s), c + Offset(-17f*s-dx, 3f*s), 1.2f*s)
                drawLine(Color(0xFFFFD66B), c + Offset(13f*s-dx, -1f*s), c + Offset(17f*s+dx, 3f*s), 1.2f*s)
            }
            if (legendaryCode == "kendao") {
                drawLine(Color(0xFF20272A), c + Offset(-7f*s, 7f*s), c + Offset(7f*s, 21f*s), 1.7f*s)
                drawLine(Color(0xFF20272A), c + Offset(7f*s, 7f*s), c + Offset(-7f*s, 21f*s), 1.7f*s)
            }

            drawArc(
                helmet,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = c + Offset(-9f * s, -18f * s),
                size = Size(18f * s, 10f * s)
            )
            drawLine(helmet, c + Offset(-9f * s, -13f * s), c + Offset(10f * s, -13f * s), 2f * s)

            if (legendaryCode != null) {
                drawCircle(Color(0xFFFFD66B), 24f * s, c, style = Stroke(2f * s))
            }
        }
    }
}

private fun isFemaleName(name: String): Boolean = name.substringBefore(' ') in setOf(
    "Luciana", "Patrícia", "Camila", "Fernanda", "Amanda", "Juliana", "Mariana", "Beatriz", "Renata",
    "Larissa", "Daniela", "Aline", "Carolina", "Bianca", "Vanessa", "Jéssica", "Natália", "Priscila", "Letícia", "Isabela"
)

private fun portraitAccent(code: String?, specialty: String, name: String): Color = when {
    isFemaleName(name) -> Color(0xFF6B4C75)
    code == "gumersvaldo" -> Color(0xFF1976D2)
    code == "nikao_narizudo" -> Color(0xFF8E24AA)
    code == "bodybuilder" -> Color(0xFF2E9D55)
    code == "tatu_banhado" -> Color(0xFF8D6E63)
    code == "kendao" -> Color(0xFFE9E3D7)
    code == "moskitao" -> Color(0xFF168A9E)
    code == "merciao" -> Color(0xFF607D8B)
    specialty.contains("CNC") -> Color(0xFF308BD4)
    specialty.contains("WELD") -> Color(0xFFE76742)
    specialty.contains("QUALITY") -> Color(0xFF9A50B4)
    specialty.contains("STOCK") -> Color(0xFF4FA963)
    else -> Color(0xFFDB9F1B)
}
