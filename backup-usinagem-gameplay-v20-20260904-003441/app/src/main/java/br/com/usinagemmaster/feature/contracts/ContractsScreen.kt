package br.com.usinagemmaster.feature.contracts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import br.com.usinagemmaster.core.designsystem.component.ScreenHeader
import br.com.usinagemmaster.core.util.Formatters
import br.com.usinagemmaster.data.local.entity.ContractEntity
import br.com.usinagemmaster.domain.expansion.ContractProgression
import br.com.usinagemmaster.domain.expansion.ExpansionProgression
import br.com.usinagemmaster.domain.expansion.ExpansionState
import br.com.usinagemmaster.domain.model.ContractStatus

private enum class ContractFilter(val label: String) {
    ALL("Todos"), AVAILABLE("Disponíveis"), ACTIVE("Ativos"), COMPLETED("Concluídos"), FAILED("Falharam")
}

@Composable
fun ContractsScreen(vm: ContractsViewModel = hiltViewModel()) {
    val contracts by vm.contracts.collectAsState()
    val companyLevel by vm.companyLevel.collectAsState()
    val reputation by vm.reputation.collectAsState()
    val expansion by vm.expansion.collectAsState()
    val message by vm.message.collectAsState()
    val snack = remember { SnackbarHostState() }
    var filter by remember { mutableStateOf(ContractFilter.AVAILABLE) }
    var cancelTarget by remember { mutableStateOf<ContractEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<ContractEntity?>(null) }

    LaunchedEffect(Unit) { vm.refreshContracts() }
    LaunchedEffect(message) { message?.let { snack.showSnackbar(it); vm.clearMessage() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            ScreenHeader("Contratos", "Progressão dinâmica • só aparecem oportunidades do seu nível")

            FactoryProgressCard(
                companyLevel = companyLevel,
                reputation = reputation,
            )

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ContractFilter.entries.forEach { item ->
                    val count = contracts.count { c -> item == ContractFilter.ALL || c.status == item.name }
                    FilterChip(
                        selected = filter == item,
                        onClick = { filter = item },
                        label = { Text("${item.label} $count") },
                    )
                }
            }

            val visible = contracts.filter { filter == ContractFilter.ALL || it.status == filter.name }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (visible.isEmpty()) {
                    item {
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Nenhum contrato nesta categoria.", fontWeight = FontWeight.Bold)
                                if (filter == ContractFilter.AVAILABLE) {
                                    Text(
                                        "O jogo está gerando novas oportunidades compatíveis com o nível $companyLevel.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    TextButton(onClick = vm::refreshContracts) { Text("Atualizar contratos") }
                                }
                            }
                        }
                    }
                }
                items(visible, key = { it.id }) { c ->
                    ContractCardV8(
                        c = c,
                        companyLevel = companyLevel,
                        expansion = expansion,
                        vm = vm,
                        onCancel = { cancelTarget = c },
                        onDelete = { deleteTarget = c },
                    )
                }
            }
        }
    }

    cancelTarget?.let { c ->
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            title = { Text("Cancelar contrato?", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "${c.clientName} aplicará multa de ${Formatters.money(c.penaltyCents)} e você perderá ${c.reputationPenalty} ponto(s) de reputação. O XP ainda não conquistado deste contrato também será perdido.",
                )
            },
            confirmButton = {
                Button(onClick = { cancelTarget = null; vm.cancel(c) }) { Text("Pagar multa e cancelar") }
            },
            dismissButton = { TextButton(onClick = { cancelTarget = null }) { Text("Manter contrato") } },
        )
    }

    deleteTarget?.let { c ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Excluir contrato com falha?") },
            text = { Text("Ele será removido da lista. Multa e reputação já aplicadas não serão devolvidas.") },
            confirmButton = { Button(onClick = { deleteTarget = null; vm.dismissFailed(c) }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Voltar") } },
        )
    }
}

@Composable
private fun FactoryProgressCard(companyLevel: Int, reputation: Int) {
    val xp = ExpansionProgression.factory(companyLevel, reputation)
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("FÁBRICA • NÍVEL ${xp.level}", fontWeight = FontWeight.Black)
                    Text("Contratos são a principal fonte de XP da empresa", style = MaterialTheme.typography.bodySmall)
                }
                Text("${xp.current}/${xp.needed} XP", fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(progress = { xp.fraction }, modifier = Modifier.fillMaxWidth().height(9.dp))
            val missing = (xp.needed - xp.current).coerceAtLeast(0L)
            Text("Faltam $missing XP para o próximo nível", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ContractCardV8(
    c: ContractEntity,
    companyLevel: Int,
    expansion: ExpansionState,
    vm: ContractsViewModel,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val productionProgress = if (c.quantity <= 0) 0f else (c.completedQuantity.toFloat() / c.quantity.toFloat()).coerceIn(0f, 1f)
    val special = ContractProgression.isSpecial(c)
    val gate = br.com.usinagemmaster.domain.expansion.ExpansionCatalog.contractGate(c.difficulty)
    val access = ContractProgression.access(c, companyLevel, expansion)
    val factoryXp = ContractProgression.factoryXp(c)
    val characterXp = ContractProgression.characterXp(c)

    val container = if (special) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface
    val content = if (special) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (special) Text("⭐ CONTRATO ESPECIAL", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                    Text(c.clientName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(c.contractType.removePrefix("⭐ Especial • "), style = MaterialTheme.typography.bodySmall)
                }
                AssistChip(onClick = {}, label = { Text(statusLabel(c.status)) })
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("NÍVEL EXIGIDO ${gate.minLevel}", fontWeight = FontWeight.Black)
                    Text("Requisitos: ${ContractProgression.requirementText(c.difficulty)}", style = MaterialTheme.typography.bodySmall)
                    val levelXp = ExpansionProgression.factory(companyLevel, 0).needed.coerceAtLeast(1L)
                    val pct = ((factoryXp * 100L) / levelXp).coerceAtMost(100L)
                    Text("Este contrato rende cerca de $pct% do XP de um nível da fábrica.", style = MaterialTheme.typography.labelSmall)
                    if (!access.allowed && c.status == ContractStatus.AVAILABLE.name) {
                        Text("🔒 ${access.reason}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RewardMiniCard("🏭 XP fábrica", "+$factoryXp", Modifier.weight(1f))
                RewardMiniCard("👷 XP personagem", "+$characterXp", Modifier.weight(1f))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RewardMiniCard("💰 Recompensa", Formatters.money(c.rewardCents), Modifier.weight(1f))
                RewardMiniCard("⭐ Reputação", "+${c.reputationReward}", Modifier.weight(1f))
            }

            Text(
                "Dificuldade ${c.difficulty}/5 • quantidade ${c.quantity} • qualidade mín. ${c.requiredQuality}",
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.82f),
            )
            Text(
                "Prazo: ${remainingLabel(c.deadlineAt)} • multa ${Formatters.money(c.penaltyCents)}",
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.82f),
            )

            if (c.status == ContractStatus.ACTIVE.name) {
                LinearProgressIndicator(progress = { productionProgress }, modifier = Modifier.fillMaxWidth())
                Text("Produção ${c.completedQuantity}/${c.quantity}", style = MaterialTheme.typography.labelMedium)
            }

            when (c.status) {
                ContractStatus.AVAILABLE.name -> Button(
                    onClick = { vm.accept(c) },
                    enabled = access.allowed,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (access.allowed) "Aceitar • +$factoryXp XP ao concluir" else "Requisito não atendido")
                }
                ContractStatus.ACTIVE.name -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (c.completedQuantity >= c.quantity) {
                        Button(onClick = { vm.complete(c) }, modifier = Modifier.weight(1f)) { Text("Concluir e receber") }
                    }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar • multa") }
                }
                ContractStatus.COMPLETED.name -> OutlinedButton(
                    onClick = { vm.recoverReward(c) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Verificar pagamento") }
                ContractStatus.FAILED.name -> Button(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Excluir FAILED") }
            }
        }
    }
}

@Composable
private fun RewardMiniCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
        }
    }
}

private fun remainingLabel(deadlineAt: Long): String {
    val millis = deadlineAt - System.currentTimeMillis()
    if (millis <= 0L) return "expirado"
    val hours = millis / 3_600_000L
    val minutes = (millis % 3_600_000L) / 60_000L
    return if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
}

private fun statusLabel(status: String) = when (status) {
    ContractStatus.AVAILABLE.name -> "DISPONÍVEL"
    ContractStatus.ACTIVE.name -> "ATIVO"
    ContractStatus.COMPLETED.name -> "CONCLUÍDO"
    ContractStatus.FAILED.name -> "FAILED"
    else -> status
}
