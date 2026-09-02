package br.com.usinagemmaster.feature.community

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.usinagemmaster.data.social.CommunityFactory
import kotlin.math.roundToInt

@Composable
fun CommunityFactoryButton(vm: CommunityFactoryViewModel = hiltViewModel()) {
    var open by remember { mutableStateOf(false) }
    FilledTonalButton(
        onClick = { open = true; vm.refresh() },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text("🏭 Explorar fábricas da comunidade") }
    if (open) CommunityFactoriesDialog(onDismiss = { open = false }, vm = vm)
}

@Composable
private fun CommunityFactoriesDialog(onDismiss: () -> Unit, vm: CommunityFactoryViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (state.selected != null) vm.backToList() else onDismiss() }) { Text("← Voltar") }
                    Text(if (state.selected == null) "Comunidade industrial" else state.selected!!.companyName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    if (state.selected == null) TextButton(onClick = vm::refresh) { Text("Atualizar") }
                }
                HorizontalDivider()
                when {
                    state.busy && state.factories.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.selected != null -> RemoteFactoryDetail(state.selected!!)
                    else -> FactoryProfileList(state.factories, state.error, vm::select)
                }
            }
        }
    }
}

@Composable
private fun FactoryProfileList(factories: List<CommunityFactory>, error: String?, onClick: (CommunityFactory) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item { Text("Toque no perfil de uma empresa para entrar no galpão dela.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(factories, key = { it.uid }) { factory ->
            ElevatedCard(Modifier.fillMaxWidth().clickable { onClick(factory) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(50.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(contentAlignment = Alignment.Center) { Text("👤", style = MaterialTheme.typography.headlineMedium) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(factory.playerName, fontWeight = FontWeight.Black)
                        Text(factory.companyName, style = MaterialTheme.typography.bodyMedium)
                        Text("Nível ${factory.companyLevel} • rep ${factory.reputation} • ${factory.machines.size} máquinas", style = MaterialTheme.typography.labelSmall)
                    }
                    Text("ENTRAR ›", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RemoteFactoryDetail(factory: CommunityFactory) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("🏭 ${factory.companyName}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("Proprietário: ${factory.playerName}")
                    Text("Nível ${factory.companyLevel} • reputação ${factory.reputation} • equipe ${factory.employeeCount}")
                    Text("Especialidade: ${factory.specialty}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { Text("Galpão público", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black) }
        item { RemoteFactoryFloor(factory) }
        item {
            Text("Visita somente leitura: você pode conhecer o layout e as máquinas, mas não altera a fábrica do outro jogador.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RemoteFactoryFloor(factory: CommunityFactory) {
    val line = MaterialTheme.colorScheme.outline.copy(alpha = .25f)
    Card(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(440.dp).padding(8.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .25f), RoundedCornerShape(14.dp))) {
            val cols = 5
            val rows = 6
            val cw = maxWidth / cols
            val ch = maxHeight / rows
            Canvas(Modifier.matchParentSize()) {
                for (x in 1 until cols) drawLine(line, Offset(size.width * x / cols, 0f), Offset(size.width * x / cols, size.height))
                for (y in 1 until rows) drawLine(line, Offset(0f, size.height * y / rows), Offset(size.width, size.height * y / rows))
            }
            factory.machines.forEach { m ->
                Card(
                    modifier = Modifier.offset(x = cw * m.x.toFloat(), y = ch * m.y.toFloat()).width(cw - 5.dp).height(ch - 5.dp),
                    colors = CardDefaults.cardColors(containerColor = if (m.premium) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(6.dp)) {
                        Text(if (m.premium) "⭐🏭" else "🏭")
                        Text(m.name, maxLines = 2, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("Nv.${m.level}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
