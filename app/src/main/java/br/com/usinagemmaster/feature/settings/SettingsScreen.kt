package br.com.usinagemmaster.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import br.com.usinagemmaster.core.designsystem.component.IndustrialBackground
import br.com.usinagemmaster.core.designsystem.component.ScreenHeader
import androidx.compose.ui.graphics.Color

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsState()

    IndustrialBackground {
        Column(Modifier.fillMaxSize().padding(top = 22.dp)) {
            ScreenHeader("Configurações", "Ajuste a experiência da Fábrica Viva")
            Column(
                Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SettingCard(Icons.Default.GraphicEq, "Som da fábrica", "Ambiente industrial, máquinas e faíscas", settings.sound, vm::sound)
                SettingCard(Icons.Default.Vibration, "Vibração", "Feedback háptico em ações importantes", settings.vibration, vm::vibration)
                SettingCard(Icons.Default.ChatBubbleOutline, "Falas dos NPCs", "Balões contextuais dos funcionários lendários", settings.npcSpeech, vm::npcSpeech)
                SpeechSpeedCard(
                    enabled = settings.npcSpeech,
                    seconds = settings.speechDurationSeconds,
                    onSelect = vm::speechDuration
                )

                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("USINAGEM MASTER", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(4.dp))
                        Text("Império do Aço", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Versão 0.8.0 • Fábrica Premium + ganhos 3x", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeechSpeedCard(
    enabled: Boolean,
    seconds: Int,
    onSelect: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Text("Velocidade das falas", fontWeight = FontWeight.Bold)
            Text(
                "Quanto tempo o balão permanece visível",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5 to "Rápida", 8 to "Normal", 12 to "Lenta").forEach { (value, label) ->
                    FilterChip(
                        selected = seconds == value,
                        onClick = { onSelect(value) },
                        enabled = enabled,
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)
            ) {
                Icon(icon, null, Modifier.padding(9.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
