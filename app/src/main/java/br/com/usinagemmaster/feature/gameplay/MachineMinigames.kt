package br.com.usinagemmaster.feature.gameplay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.usinagemmaster.data.local.entity.ContractEntity
import br.com.usinagemmaster.data.local.entity.MachineEntity
import br.com.usinagemmaster.domain.catalog.MachineCatalog
import br.com.usinagemmaster.domain.gameplay.*
import kotlin.math.abs

@Composable
fun OwnerOperationDialog(
    machine: MachineEntity,
    contracts: List<ContractEntity>,
    career: CareerState,
    busy: Boolean,
    onDismiss: () -> Unit,
    onManual: (ContractEntity) -> Unit,
    onAssisted: (ContractEntity) -> Unit,
) {
    var selectedId by remember(contracts) { mutableStateOf(contracts.firstOrNull()?.id) }
    val selected = contracts.firstOrNull { it.id == selectedId }
    val mastery = career.mastery(machine.machineType)
    val definition = MachineCatalog.byType(machine.machineType)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assumir ${definition?.name ?: "máquina"}", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    "Somente o dono faz minigame. Funcionários continuam automáticos; o ciclo manual acelera a produção sem ser obrigatório.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        Text("Proficiência Nv.${mastery.level}", fontWeight = FontWeight.Bold)
                        Text(
                            "+${mastery.quantityBonusPct}% potencial • +${mastery.qualityBonus} qualidade",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }

                if (contracts.isEmpty()) {
                    Text("Aceite um contrato antes de iniciar um lote.", color = MaterialTheme.colorScheme.error)
                } else {
                    contracts.take(4).forEach { contract ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selectedId = contract.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedId == contract.id,
                                onClick = { selectedId = contract.id },
                            )
                            Column {
                                Text(contract.clientName, fontWeight = FontWeight.Bold)
                                Text(
                                    "${contract.completedQuantity}/${contract.quantity} • qualidade ${contract.requiredQuality}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()
                Text("OPERAR EU MESMO", fontWeight = FontWeight.Black)
                Text(
                    "Desafio específico da máquina: desempenho vira peças, qualidade, XP e proficiência.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("CICLO ASSISTIDO", fontWeight = FontWeight.Black)
                Text(
                    "Sem minigame e sem bônus, mas o lote ainda passa fisicamente por Q → P → E.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { selected?.let(onManual) },
                enabled = selected != null && !busy && career.activeBatch == null,
            ) { Text("OPERAR EU MESMO") }
        },
        dismissButton = {
            Row {
                OutlinedButton(
                    onClick = { selected?.let(onAssisted) },
                    enabled = selected != null && !busy && career.activeBatch == null,
                ) { Text("Ciclo assistido") }
                Spacer(Modifier.width(5.dp))
                TextButton(onClick = onDismiss) { Text("Fechar") }
            }
        },
    )
}

private data class ProcessChoice(
    val prompt: String,
    val labels: List<String>,
    val correct: Int,
)

private fun processChoice(kind: MinigameKind, difficulty: Int): ProcessChoice = when (kind) {
    MinigameKind.LATHE -> ProcessChoice(
        "Escolha a ferramenta para o passe final",
        listOf("HSS", "Pastilha CNMG", "Pastilha acabamento"),
        if (difficulty >= 4) 2 else 1,
    )
    MinigameKind.MILLING -> ProcessChoice(
        "Escolha a estratégia de trajetória",
        listOf("Contorno direto", "Desbaste adaptativo", "Passe aleatório"),
        1,
    )
    MinigameKind.DRILLING -> ProcessChoice(
        "Escolha a ferramenta de furação",
        listOf("Broca HSS", "Broca metal duro", "Escareador"),
        if (difficulty >= 3) 1 else 0,
    )
    MinigameKind.GRINDING -> ProcessChoice(
        "Escolha o passe de acabamento",
        listOf("0,10 mm", "0,03 mm", "0,30 mm"),
        1,
    )
    MinigameKind.WELDING -> ProcessChoice(
        "Escolha o modo de transferência",
        listOf("Curto-circuito", "Spray", "Energia máxima"),
        if (difficulty >= 4) 1 else 0,
    )
    MinigameKind.EDM -> ProcessChoice(
        "Escolha o regime da descarga",
        listOf("Desbaste", "Semiacabamento", "Acabamento"),
        if (difficulty >= 4) 2 else 1,
    )
    MinigameKind.LASER -> ProcessChoice(
        "Escolha o gás de assistência",
        listOf("N₂", "O₂", "Sem gás"),
        if (difficulty >= 4) 0 else 1,
    )
    MinigameKind.PLASMA -> ProcessChoice(
        "Escolha o processo de corte",
        listOf("Ar", "N₂", "Corrente máxima"),
        if (difficulty >= 4) 1 else 0,
    )
    else -> ProcessChoice(
        "Escolha a estratégia do processo",
        listOf("Conservador", "Janela recomendada", "Agressivo"),
        1,
    )
}

@Composable
fun MachineMinigameDialog(
    machine: MachineEntity,
    contract: ContractEntity,
    career: CareerState,
    rework: Boolean = false,
    onDismiss: () -> Unit,
    onFinished: (MinigameResult) -> Unit,
) {
    val blueprint = remember(machine.machineType, contract.difficulty) {
        MachineMinigameCatalog.blueprint(machine.machineType, contract.difficulty)
    }
    val profile = remember(contract.id) { ContractGameplayProfile.from(contract) }
    val mastery = career.mastery(machine.machineType)
    val choiceData = remember(blueprint.kind, contract.difficulty) {
        processChoice(blueprint.kind, contract.difficulty)
    }
    val expectedSequence = remember { listOf("Facear", "Furar", "Contornar") }
    val availableSequence = remember(contract.id) { expectedSequence.shuffled() }

    var parameterA by remember { mutableFloatStateOf(50f) }
    var parameterB by remember { mutableFloatStateOf(50f) }
    var selectedChoice by remember { mutableIntStateOf(-1) }
    val sequence = remember { mutableStateListOf<String>() }

    val parameterScore = scoreFromParameters(
        parameterA,
        blueprint.targetA,
        blueprint.toleranceA,
        parameterB,
        blueprint.targetB,
        blueprint.toleranceB,
    )
    val processErrors = if (blueprint.kind == MinigameKind.CNC) {
        expectedSequence.indices.count { sequence.getOrNull(it) != expectedSequence[it] }
    } else {
        if (selectedChoice == choiceData.correct) 0 else 1
    }
    val skillAssist =
        (if (career.has("preparador")) .04f else 0f) +
            (if (blueprint.kind == MinigameKind.CNC && career.has("operador_cnc")) .05f else 0f) +
            ((mastery.level - 1) * .006f).coerceAtMost(.08f)
    val score = (parameterScore - processErrors * .13f + skillAssist).coerceIn(0f, 1f)
    val precision = (1f - abs(parameterA - blueprint.targetA) / (blueprint.toleranceA * 2f)).coerceIn(0f, 1f)
    val speed = (1f - abs(parameterB - blueprint.targetB) / (blueprint.toleranceB * 2f)).coerceIn(0f, 1f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    if (rework) "Retrabalho • ${blueprint.title}" else blueprint.title,
                    fontWeight = FontWeight.Black,
                )
                Text("${profile.material} • ${profile.toleranceLabel}", style = MaterialTheme.typography.labelSmall)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(blueprint.goal)
                Text(
                    "Contrato: ${contract.clientName} • qualidade ${contract.requiredQuality}",
                    style = MaterialTheme.typography.bodySmall,
                )
                MachineProcessHint(blueprint.kind)

                if (blueprint.kind == MinigameKind.CNC) {
                    Text("1. Monte a OP10: Facear → Furar → Contornar", fontWeight = FontWeight.Bold)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        availableSequence.forEach { operation ->
                            FilterChip(
                                selected = operation in sequence,
                                onClick = { if (operation !in sequence) sequence += operation },
                                label = { Text(operation) },
                                enabled = operation !in sequence,
                            )
                        }
                        TextButton(onClick = { sequence.clear() }) { Text("Limpar") }
                    }
                    Text(
                        "Sequência escolhida: ${if (sequence.isEmpty()) "—" else sequence.joinToString(" → ")}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                } else {
                    Text("1. ${choiceData.prompt}", fontWeight = FontWeight.Bold)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        choiceData.labels.forEachIndexed { index, label ->
                            FilterChip(
                                selected = selectedChoice == index,
                                onClick = { selectedChoice = index },
                                label = { Text(label) },
                            )
                        }
                    }
                }

                Text("2. Ajuste ${blueprint.parameterA}", fontWeight = FontWeight.Bold)
                ProcessSlider(parameterA, { parameterA = it }, blueprint.targetA, blueprint.toleranceA)
                Text("3. Ajuste ${blueprint.parameterB}", fontWeight = FontWeight.Bold)
                ProcessSlider(parameterB, { parameterB = it }, blueprint.targetB, blueprint.toleranceB)

                Surface(
                    color = when {
                        score >= .90f -> Color(0xFF173B2C)
                        score >= .70f -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                    },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        Text("Eficiência estimada ${(score * 100).toInt()}%", fontWeight = FontWeight.Black)
                        Text(
                            "Precisão ${(precision * 100).toInt()}% • ritmo ${(speed * 100).toInt()}% • erros $processErrors",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onFinished(
                        MinigameResult(
                            score = score,
                            precision = precision,
                            speed = speed,
                            quality = (score * .85f + precision * .15f).coerceIn(0f, 1f),
                            mistakes = processErrors,
                        )
                    )
                }
            ) { Text(if (rework) "CONCLUIR RETRABALHO" else "CONCLUIR OPERAÇÃO") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Sair") } },
    )
}

@Composable
private fun MachineProcessHint(kind: MinigameKind) {
    val (icon, title, detail) = when (kind) {
        MinigameKind.LATHE -> Triple("🌀", "Torneamento", "Controle corte, avanço e ferramenta sem ultrapassar a medida.")
        MinigameKind.MILLING -> Triple("🧭", "Fresagem", "Escolha uma estratégia de passe eficiente antes de ajustar o corte.")
        MinigameKind.DRILLING -> Triple("🎯", "Furação", "Ferramenta correta + profundidade correta evitam peça perdida.")
        MinigameKind.GRINDING -> Triple("📐", "Retífica", "Passe pequeno e controle fino: aqui centésimos importam.")
        MinigameKind.CNC -> Triple("⌨️", "Programação CNC", "A ordem das operações importa tanto quanto os parâmetros.")
        MinigameKind.WELDING -> Triple("⚡", "Soldagem", "Equilibre energia e velocidade para evitar falta de fusão e empeno.")
        MinigameKind.EDM -> Triple("✨", "Eletroerosão", "Gap e descarga precisam permanecer estáveis para manter precisão.")
        MinigameKind.LASER -> Triple("🔦", "Laser", "Foco, gás e velocidade definem rebarba e qualidade do corte.")
        MinigameKind.PLASMA -> Triple("🔥", "Plasma", "Corrente e avanço controlam largura do corte e acabamento.")
        MinigameKind.QUALITY -> Triple("🔎", "Metrologia", "Leia a dimensão e decida conforme a tolerância do desenho.")
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(icon)
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Text(detail, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ProcessSlider(
    value: Float,
    onValue: (Float) -> Unit,
    target: Float,
    tolerance: Float,
) {
    Column {
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = 0f..100f,
        )
        Text(
            "Atual ${value.toInt()} • janela recomendada ${(target - tolerance).toInt()}–${(target + tolerance).toInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = if (abs(value - target) <= tolerance) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
fun QualityInspectionDialog(
    batch: OwnerWorkBatch,
    contract: ContractEntity,
    career: CareerState,
    onDismiss: () -> Unit,
    onDecision: (Boolean) -> Unit,
) {
    val profile = remember(contract.id) { ContractGameplayProfile.from(contract) }
    val measured = 25.000 + (batch.quality - contract.requiredQuality).coerceIn(-20, 20) * .0012

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Controle de Qualidade", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Leia a medida e decida. Aprovar lote fora do requisito não burla o sistema: ele volta para retrabalho.")
                Card {
                    Column(Modifier.padding(14.dp)) {
                        Text("Desenho: Ø25,000 ${profile.toleranceLabel}", fontWeight = FontWeight.Black)
                        Text(if (contract.difficulty >= 3) "Instrumento: micrômetro" else "Instrumento: paquímetro")
                        Text(
                            String.format(java.util.Locale.getDefault(), "Medição %.3f mm", measured),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
                Text("Qualidade ${batch.quality}/100 • exige ${contract.requiredQuality}/100")
                if (career.has("olho_treinado")) {
                    Text(
                        if (batch.quality >= contract.requiredQuality) "👁 Tendência conforme" else "👁 Desvio detectado; retrabalho recomendado",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { onDecision(true) }) { Text("APROVAR") } },
        dismissButton = {
            Row {
                OutlinedButton(onClick = { onDecision(false) }) { Text("RETRABALHAR") }
                TextButton(onClick = onDismiss) { Text("Fechar") }
            }
        },
    )
}

@Composable
fun OwnerBatchCard(
    batch: OwnerWorkBatch?,
    contract: ContractEntity?,
    career: CareerState,
    onQuality: () -> Unit,
    onInspect: () -> Unit,
    onPack: () -> Unit,
    onShip: () -> Unit,
    onRework: () -> Unit,
    onScrap: () -> Unit,
    onAbandon: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF172B31)),
        border = BorderStroke(1.dp, Color(0xFF4E8992)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(
                    "🧑‍🏭  TRABALHO DO DONO",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                )
                Text("Pts ${career.availableSkillPoints}", color = Color(0xFFFFCC66), fontWeight = FontWeight.Bold)
            }

            if (batch == null) {
                Text(
                    "Toque numa máquina e escolha OPERAR EU MESMO. Não há energia ou cooldown bloqueando o jogo.",
                    color = Color(0xFFC5D5DA),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    "${contract?.clientName ?: "Contrato"} • ${batch.producedQuantity} pç • Q${batch.quality} • precisão ${batch.precision}%",
                    color = Color.White,
                )
                if (batch.perfect) {
                    Text("⭐ PEÇA DE MESTRE", color = Color(0xFFFFD067), fontWeight = FontWeight.Black)
                }
                Text(nextStep(batch), color = Color(0xFF9FE3C3), fontWeight = FontWeight.Bold)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when (batch.stage) {
                        ProductionStage.MACHINED -> Button(onClick = onQuality) { Text("Levar ao Q") }
                        ProductionStage.WAITING_QC, ProductionStage.QC -> Button(onClick = onInspect) { Text("Inspecionar") }
                        ProductionStage.APPROVED -> Button(onClick = onPack) { Text("Levar ao P") }
                        ProductionStage.REWORK -> Button(onClick = onRework) { Text("Voltar à máquina") }
                        ProductionStage.READY_TO_SHIP -> Button(onClick = onShip) { Text("Levar ao E") }
                        else -> Unit
                    }
                    if (batch.stage == ProductionStage.REWORK) {
                        OutlinedButton(onClick = onScrap) { Text("Refugar") }
                    }
                    OutlinedButton(onClick = onAbandon) { Text("Descartar") }
                }
            }
        }
    }
}

private fun nextStep(batch: OwnerWorkBatch) = when (batch.stage) {
    ProductionStage.MACHINED -> "Próximo: Q • Controle de Qualidade"
    ProductionStage.WAITING_QC, ProductionStage.QC -> "Próximo: medir e decidir"
    ProductionStage.APPROVED -> "Próximo: P • Embalagem"
    ProductionStage.REWORK -> "Próximo: retornar à máquina"
    ProductionStage.READY_TO_SHIP -> "Próximo: E • Expedição"
    else -> batch.stage.label
}
