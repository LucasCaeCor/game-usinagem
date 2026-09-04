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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.usinagemmaster.data.social.CommunityFactory

@Composable
fun CommunityFactoryButton(vm: CommunityFactoryViewModel = hiltViewModel()) {
ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF18262D)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🌐 OUTRAS EMPRESAS", color = Color.White, fontWeight = FontWeight.Black)
            Text(
                "Entre com Google para publicar sua fábrica e visitar o galpão de outros jogadores.",
                color = Color(0xFFD4DEE3),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { vm.openBrowser(); vm.refresh() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("VISITAR OUTRAS FÁBRICAS")
            }
        }
    }
}


@Composable
fun CommunityFactoryStableHost(
    vm: CommunityFactoryViewModel = hiltViewModel(),
) {
    // V13_COMMUNITY_STABLE_HOST
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.browserOpen) {
        CommunityFactoriesDialog(
            onDismiss = vm::closeBrowser,
            vm = vm,
        )
    }
}

@Composable
private fun CommunityFactoriesDialog(
    onDismiss: () -> Unit,
    vm: CommunityFactoryViewModel,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = {
                        if (state.selected != null) vm.backToList() else onDismiss()
                    }) { Text("← Voltar") }

                    Text(
                        if (state.selected == null) "Empresas da comunidade" else state.selected!!.companyName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f),
                    )

                    if (state.selected == null) {
                        TextButton(onClick = vm::refresh) { Text("Atualizar") }
                    }
                }

                HorizontalDivider()

                when {
                    state.busy && state.factories.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }

                    state.selected != null -> RemoteFactoryDetail(state.selected!!)

                    else -> FactoryProfileList(
                        factories = state.factories,
                        error = state.error,
                        onClick = vm::select,
                    )
                }
            }
        }
    }
}

@Composable
private fun FactoryProfileList(
    factories: List<CommunityFactory>,
    error: String?,
    onClick: (CommunityFactory) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Para aparecer aqui, cada empresa precisa ter uma conta Google vinculada e publicar seu snapshot no Firebase. Jogar offline continua permitido.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        error?.let {
            item {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(it, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        if (factories.isEmpty() && error == null) {
            item {
                ElevatedCard {
                    Text(
                        "Nenhuma outra empresa publicou a fábrica ainda. Peça para outro jogador entrar com Google e abrir esta tela uma vez.",
                        Modifier.padding(16.dp),
                    )
                }
            }
        }

        items(factories, key = { it.uid }) { factory ->
            ElevatedCard(
                Modifier.fillMaxWidth().clickable { onClick(factory) },
                colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF18262D)),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        Modifier.size(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🏭", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(factory.companyName, color = Color.White, fontWeight = FontWeight.Black)
                        Text("por ${factory.playerName}", color = Color(0xFFD4DEE3))
                        Text(
                            "Nível ${factory.companyLevel} • rep ${factory.reputation} • ${factory.machines.size} máquinas",
                            color = Color(0xFFB7C5CC),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text("VISITAR ›", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RemoteFactoryDetail(factory: CommunityFactory) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF18262D))) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("🏭 ${factory.companyName}", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("Proprietário: ${factory.playerName}", color = Color(0xFFD4DEE3))
                    Text("Nível ${factory.companyLevel} • reputação ${factory.reputation} • equipe ${factory.employeeCount}", color = Color(0xFFD4DEE3))
                    Text("Especialidade: ${factory.specialty}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Text("Galpão público", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        }

        item { RemoteFactoryFloor(factory) }

        item {
            Text(
                "Modo visitante: você vê máquinas e layout, mas não pode mover, vender ou operar nada da fábrica do outro jogador.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RemoteFactoryFloor(factory: CommunityFactory) {
    val line = MaterialTheme.colorScheme.outline.copy(alpha = .25f)
    Card(Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier.fillMaxWidth()
                .height(440.dp)
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .25f), RoundedCornerShape(14.dp)),
        ) {
            val cols = 5
            val rows = 6
            val cw = maxWidth / cols
            val ch = maxHeight / rows

            Canvas(Modifier.matchParentSize()) {
                for (x in 1 until cols) {
                    drawLine(line, Offset(size.width * x / cols, 0f), Offset(size.width * x / cols, size.height))
                }
                for (y in 1 until rows) {
                    drawLine(line, Offset(0f, size.height * y / rows), Offset(size.width, size.height * y / rows))
                }
            }

            factory.machines.forEach { m ->
                Card(
                    modifier = Modifier
                        .offset(x = cw * m.x.toFloat(), y = ch * m.y.toFloat())
                        .width(cw - 5.dp)
                        .height(ch - 5.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (m.premium) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(Modifier.padding(6.dp)) {
                        Text(if (m.premium) "⭐🏭" else "🏭")
                        Text(m.name, maxLines = 2, color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("Nv.${m.level}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
