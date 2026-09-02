package br.com.usinagemmaster.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.usinagemmaster.core.designsystem.theme.IndustrialAmber
import br.com.usinagemmaster.core.designsystem.theme.Steel800
import br.com.usinagemmaster.core.designsystem.theme.Steel950
import kotlin.math.sin

@Composable
fun IndustrialBackground(content: @Composable BoxScope.() -> Unit) {
    val infinite = rememberInfiniteTransition(label = "industrial_background")
    val phase by infinite.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart),
        label = "background_phase"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF060A0D), Color(0xFF0B1217), Steel950)
                )
            )
    ) {
        Canvas(Modifier.matchParentSize()) {
            val glowX = size.width * (.15f + .70f * phase)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(IndustrialAmber.copy(alpha = .055f), Color.Transparent),
                    center = Offset(glowX, size.height * .18f),
                    radius = size.width * .65f
                ),
                radius = size.width * .65f,
                center = Offset(glowX, size.height * .18f)
            )
            val grid = 48f
            var x = -grid
            while (x < size.width + grid) {
                val shift = sin(phase * 6.283f) * 4f
                drawLine(Color.White.copy(alpha = .018f), Offset(x + shift, 0f), Offset(x + shift, size.height), 1f)
                x += grid
            }
        }
        content()
    }
}

@Composable
fun GameCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(listOf(MaterialTheme.colorScheme.outline.copy(alpha = .5f), Color.Transparent))
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                val dotColor = MaterialTheme.colorScheme.primary
                Canvas(Modifier.size(10.dp)) {
                    drawCircle(dotColor.copy(alpha = .8f), radius = size.minDimension * .28f)
                    drawCircle(dotColor.copy(alpha = .25f), radius = size.minDimension * .48f, style = Stroke(1f))
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun StatusPill(text: String, positive: Boolean = true) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (positive) MaterialTheme.colorScheme.primary.copy(alpha = .13f)
        else MaterialTheme.colorScheme.error.copy(alpha = .13f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (positive) MaterialTheme.colorScheme.primary.copy(alpha = .35f)
            else MaterialTheme.colorScheme.error.copy(alpha = .35f)
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row {
            Box(
                Modifier
                    .width(4.dp)
                    .height(29.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
