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

@Composable
fun EmployeePortrait(
    legendaryCode: String?,
    specialty: String,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp
) {
    val accent = portraitAccent(legendaryCode, specialty)
    Box(
        modifier = modifier
            .size(size)
            .background(
                Brush.radialGradient(listOf(accent.copy(alpha = .28f), Color(0xFF12191E))),
                RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val s = this.size.minDimension / 60f
            val bodyScale = when (legendaryCode) {
                "bodybuilder" -> 1.23f
                "pedrao" -> 1.12f
                "magrao" -> .86f
                else -> 1f
            }

            drawCircle(Color.Black.copy(alpha = .22f), 18f * s, c + Offset(0f, 13f * s))
            drawRoundRect(
                accent,
                topLeft = c + Offset(-10f * bodyScale * s, 4f * s),
                size = Size(20f * bodyScale * s, 23f * s),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(7f * s, 7f * s)
            )
            drawLine(Color(0xFFFFD54F), c + Offset(-6f * s, 8f * s), c + Offset(5f * s, 23f * s), 1.7f * s)
            drawLine(Color(0xFFFFD54F), c + Offset(6f * s, 8f * s), c + Offset(-5f * s, 23f * s), 1.7f * s)
            drawCircle(Color(0xFFFFC79A), 8.2f * s, c + Offset(0f, -8f * s))
            drawCircle(Color(0xFF263238), .9f * s, c + Offset(3f * s, -9f * s))

            if (legendaryCode == "nikao_narizudo") {
                drawLine(Color(0xFFFFC79A), c + Offset(5f * s, -7f * s), c + Offset(11f * s, -5f * s), 2.5f * s)
            }
            if (legendaryCode == "gumersvaldo") {
                drawLine(Color(0xFF9EDBFF), c + Offset(-5f * s, -9f * s), c + Offset(5f * s, -9f * s), 1.6f * s)
            }

            val helmet = if (legendaryCode != null) Color(0xFFFFB300) else Color(0xFFFFD54F)
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
            if (legendaryCode == "bodybuilder") {
                drawLine(accent, c + Offset(-14f * s, 6f * s), c + Offset(14f * s, 6f * s), 7f * s)
            }
        }
    }
}

private fun portraitAccent(code: String?, specialty: String): Color = when {
    code == "gumersvaldo" -> Color(0xFF1976D2)
    code == "nikao_narizudo" -> Color(0xFF8E24AA)
    code == "bodybuilder" -> Color(0xFF2E9D55)
    code == "tatu_banhado" -> Color(0xFF8D6E63)
    code == "kendao" -> Color(0xFFD5533C)
    code == "moskitao" -> Color(0xFF168A9E)
    code == "merciao" -> Color(0xFF607D8B)
    specialty.contains("CNC") -> Color(0xFF308BD4)
    specialty.contains("WELD") -> Color(0xFFE76742)
    specialty.contains("QUALITY") -> Color(0xFF9A50B4)
    specialty.contains("STOCK") -> Color(0xFF4FA963)
    else -> Color(0xFFDB9F1B)
}
