package br.com.usinagemmaster.feature.menu

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.usinagemmaster.core.designsystem.component.IndustrialBackground
import kotlin.math.sin

@Composable
fun MainMenuScreen(onContinue: () -> Unit, onSettings: () -> Unit, onProfile: () -> Unit, onSocial: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "menu_factory")
    val phase by infinite.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "menu_phase"
    )

    IndustrialBackground {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surface.copy(alpha = .65f))
                        ),
                        RoundedCornerShape(30.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val floorY = size.height * .72f
                    drawLine(Color(0xFF53626A), Offset(18f, floorY), Offset(size.width - 18f, floorY), 2f)
                    repeat(7) { index ->
                        val x = 24f + index * (size.width - 48f) / 6f
                        drawLine(Color.White.copy(alpha = .035f), Offset(x, floorY), Offset(size.width / 2f, size.height * .93f), 1f)
                    }
                    // Conveyor belt animado.
                    drawLine(Color(0xFF86959C), Offset(28f, floorY + 28f), Offset(size.width - 28f, floorY + 28f), 6f, StrokeCap.Round)
                    repeat(8) { i ->
                        val x = 30f + (((i / 8f + phase) % 1f) * (size.width - 60f))
                        drawCircle(Color(0xFFFFB21A), 3.5f, Offset(x, floorY + 28f))
                    }
                    // Faíscas discretas no lado direito.
                    repeat(6) { i ->
                        val p = (phase + i * .16f) % 1f
                        val x = size.width * .78f + p * 28f
                        val y = floorY - 38f + sin((p + i) * 6.283f) * 15f + p * 20f
                        drawLine(Color(0xFFFFC34D).copy(alpha = 1f - p), Offset(size.width * .77f, floorY - 38f), Offset(x, y), 2f)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(126.dp)
                        .background(
                            Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .30f), Color(0xFF11191E))),
                            RoundedCornerShape(34.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Factory, null, Modifier.size(76.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .32f))
                ) {
                    Text("EDIÇÃO FINAL • 1.0", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("USINAGEM MASTER", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color.White)
            Text("IMPÉRIO DO AÇO", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text("Transforme uma oficina antiga em uma indústria CNC viva.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(34.dp))

            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Entrar na fábrica", fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onProfile, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Default.Person, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Personagem")
                }
                OutlinedButton(onClick = onSocial, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Default.Public, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Comunidade")
                }
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, null)
                Spacer(Modifier.width(6.dp))
                Text("Configurações")
            }
        }
    }
}
