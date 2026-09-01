package br.com.usinagemmaster.feature.expansion

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.usinagemmaster.data.social.CharacterOffer
import br.com.usinagemmaster.domain.expansion.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private enum class ExpansionTab(val label: String) {
    GACHA("Roleta"), COMPANY("Empresa"), SKILLS("Skills"), TOOLS("Ferramentas"),
    CHARACTER("Personagem"), CONTRACTS("Concluídos"), ACCOUNT("Conta")
}

private data class WheelSector(val type: String, val label: String, val symbol: String)

// 12 setores aproximam visualmente as chances por CATEGORIA.
// A raridade continua sendo decidida pelo algoritmo real/pity no ExpansionRepository.
private val wheelSectors = listOf(
    WheelSector("tool", "Ferramenta", "🔧"),
    WheelSector("skin", "Skin", "👑"),
    WheelSector("tool", "Ferramenta", "🛠"),
    WheelSector("ticket", "Ficha", "🎟"),
    WheelSector("character", "Personagem", "👷"),
    WheelSector("tool", "Ferramenta", "⚙"),
    WheelSector("skin", "Skin", "✨"),
    WheelSector("ticket", "Ficha", "🎫"),
    WheelSector("machine", "Máquina TOP", "🏭"),
    WheelSector("tool", "Ferramenta", "🔩"),
    WheelSector("character", "Personagem", "🧑‍🏭"),
    WheelSector("ticket", "Ficha", "🎟"),
)

@Composable
fun ExpansionHubDialog(
    onDismiss: () -> Unit,
    viewModel: ExpansionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ExpansionHubContent(state, viewModel, onDismiss)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpansionHubContent(state: ExpansionUiState, vm: ExpansionViewModel, onDismiss: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(ExpansionTab.GACHA) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Centro de Evolução", fontWeight = FontWeight.Bold)
                        Text("Nível ${state.companyLevel}", style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = { TextButton(onClick = onDismiss) { Text("Voltar") } },
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // V3: login nunca mais fica escondido dentro de uma aba.
            GoogleLoginBanner(state, vm)

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExpansionTab.entries.forEach { item ->
                    val label = if (item == ExpansionTab.CONTRACTS && state.completedContracts.isNotEmpty()) {
                        "Concluídos (${state.completedContracts.size})"
                    } else item.label
                    FilterChip(selected = tab == item, onClick = { tab = item }, label = { Text(label) })
                }
            }

            state.message?.let {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Text(it, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(6.dp))
            }

            when (tab) {
                ExpansionTab.GACHA -> GachaTab(state, vm)
                ExpansionTab.COMPANY -> CompanyTab(state, vm)
                ExpansionTab.SKILLS -> SkillsTab(state, vm)
                ExpansionTab.TOOLS -> ToolsTab(state, vm)
                ExpansionTab.CHARACTER -> CharacterTab(state, vm)
                ExpansionTab.CONTRACTS -> CompletedContractsTab(state, vm)
                ExpansionTab.ACCOUNT -> AccountTab(state, vm)
            }
        }
    }
}

@Composable
private fun GoogleLoginBanner(state: ExpansionUiState, vm: ExpansionViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (state.accountEmail == null) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(if (state.accountEmail == null) "G" else "✓", fontWeight = FontWeight.Black) }
            }
            Column(Modifier.weight(1f)) {
                Text(if (state.accountEmail == null) "Conta Google não conectada" else (state.accountName ?: "Conta Google"), fontWeight = FontWeight.Bold)
                Text(state.accountEmail ?: "Entre para usar mercado conectado e recursos sociais.", style = MaterialTheme.typography.bodySmall)
            }
            if (state.accountEmail == null) {
                Button(
                    enabled = !state.busy,
                    onClick = {
                        scope.launch {
                            runCatching { GoogleAuthBridge.signIn(context) }
                                .onSuccess { vm.refreshAccount("Login Google realizado: $it") }
                                .onFailure { vm.refreshAccount(it.message ?: "Falha no login Google") }
                        }
                    }
                ) { Text("Entrar") }
            } else {
                TextButton(onClick = { GoogleAuthBridge.signOut(); vm.refreshAccount("Conta desconectada") }) { Text("Sair") }
            }
        }
    }
}

@Composable
private fun GachaTab(state: ExpansionUiState, vm: ExpansionViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ElevatedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Roleta Industrial", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Fichas: ${state.expansion.gachaTickets} • Pity épico ${state.expansion.pityEpic}/30 • lendário ${state.expansion.pityLegendary}/80")
                    Text("A seta agora para exatamente no MEIO do setor do prêmio recebido.")
                    GachaWheelV3(state, vm)
                    OutlinedButton(onClick = vm::claimDailyTicket, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text("Coletar ficha diária")
                    }
                }
            }
        }
        state.lastReward?.let { reward -> item { RewardCard(reward) } }
        item {
            Text("Probabilidades base", fontWeight = FontWeight.Bold)
            Text("Lendário ~0,8% • épico+ ~4,5% • máquina premium ~6% • personagens ~12% • skins ~18% • ferramentas ~40% • restante fichas. Pity garante épico no 30º e lendário no 80º giro.")
        }
        item { Text("Personagens possíveis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(ExpansionCatalog.gachaCharacters) { character ->
            ListItem(
                leadingContent = { Text("👷", style = MaterialTheme.typography.headlineSmall) },
                headlineContent = { Text("${character.rarity.label} • ${character.name}") },
                supportingContent = { Text(character.description) }
            )
        }
        item { Text("Máquinas possíveis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(ExpansionCatalog.premiumMachines) { machine -> PremiumMachineCard(machine, state, vm, showBuy = false) }
    }
}

@Composable
private fun GachaWheelV3(state: ExpansionUiState, vm: ExpansionViewModel) {
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var spinning by remember { mutableStateOf(false) }
    var highlightedSector by remember { mutableIntStateOf(-1) }
    val wheelAccent = MaterialTheme.colorScheme.primary

    val colors = listOf(
        Color(0xFF37474F), Color(0xFF7B1FA2), Color(0xFF00695C), Color(0xFF455A64),
        Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFF8E24AA), Color(0xFF546E7A),
        Color(0xFFF9A825), Color(0xFF00838F), Color(0xFF283593), Color(0xFF607D8B),
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Ponteiro FIXO. A ponta encosta no aro; o setor vencedor termina centralizado aqui.
        Canvas(Modifier.width(54.dp).height(32.dp)) {
            val p = Path().apply {
                moveTo(size.width / 2f, size.height)
                lineTo(4f, 2f)
                lineTo(size.width - 4f, 2f)
                close()
            }
            drawPath(p, wheelAccent)
        }

        Box(modifier = Modifier.size(286.dp), contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier.fillMaxSize().shadow(8.dp, CircleShape).graphicsLayer(rotationZ = rotation.value)
            ) {
                val sweep = 360f / wheelSectors.size
                val radius = size.minDimension / 2f
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    textSize = size.minDimension * 0.115f
                }
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER
                    textSize = size.minDimension * 0.032f
                    color = android.graphics.Color.WHITE
                    isFakeBoldText = true
                }

                wheelSectors.forEachIndexed { index, sector ->
                    val color = if (index == highlightedSector) Color(0xFFFFD54F) else colors[index % colors.size]
                    val start = -90f + index * sweep
                    drawArc(color = color, startAngle = start, sweepAngle = sweep - 0.8f, useCenter = true)

                    val angleDeg = start + sweep / 2f
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val iconRadius = radius * 0.63f
                    val x = center.x + cos(angleRad).toFloat() * iconRadius
                    val y = center.y + sin(angleRad).toFloat() * iconRadius
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(sector.symbol, x, y - (textPaint.ascent() + textPaint.descent()) / 2f, textPaint)
                    }
                    val labelRadius = radius * 0.86f
                    val lx = center.x + cos(angleRad).toFloat() * labelRadius
                    val ly = center.y + sin(angleRad).toFloat() * labelRadius
                    drawIntoCanvas { canvas -> canvas.nativeCanvas.drawText(sector.label.take(9), lx, ly, labelPaint) }
                }

                drawCircle(Color.White, radius = radius * 0.18f)
                drawCircle(wheelAccent, radius = radius * 0.12f)
                drawCircle(Color.White, radius = radius * 0.035f)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Text("👷 Personagem", style = MaterialTheme.typography.labelSmall)
            Text("👑 Skin", style = MaterialTheme.typography.labelSmall)
            Text("🔧 Ferramenta", style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Text("🏭 Máquina TOP", style = MaterialTheme.typography.labelSmall)
            Text("🎟 Fichas", style = MaterialTheme.typography.labelSmall)
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !spinning && !state.busy && state.expansion.gachaTickets > 0,
            onClick = {
                if (spinning) return@Button
                spinning = true
                highlightedSector = -1
                scope.launch {
                    val result = runCatching { vm.drawWheelReward() }
                    val reward = result.getOrElse {
                        vm.showMessage(it.message ?: "Falha ao girar a roleta")
                        spinning = false
                        return@launch
                    }

                    val candidates = wheelSectors.indices.filter { wheelSectors[it].type == reward.type }
                    val winner = if (candidates.isEmpty()) 0 else candidates[Random.nextInt(candidates.size)]
                    val sweep = 360f / wheelSectors.size

                    // Centro do setor i = i*sweep + sweep/2 a partir do topo.
                    // Para levar esse centro ao ponteiro (topo), rotacionamos exatamente o oposto.
                    val desiredModulo = ((360f - (winner * sweep + sweep / 2f)) % 360f + 360f) % 360f
                    val currentModulo = ((rotation.value % 360f) + 360f) % 360f
                    val delta = ((desiredModulo - currentModulo) + 360f) % 360f
                    val target = rotation.value + 5f * 360f + delta

                    rotation.animateTo(target, animationSpec = tween(durationMillis = 3100))
                    highlightedSector = winner
                    vm.revealWheelReward(reward)
                    spinning = false
                }
            }
        ) {
            Text(if (spinning) "GIRANDO..." else "🎰 GIRAR ROLETA • 1 FICHA")
        }
    }
}

@Composable
private fun RewardCard(reward: GachaReward) {
    val symbol = when (reward.type) {
        "character" -> "👷"
        "skin" -> "👑"
        "tool" -> "🔧"
        "machine" -> "🏭"
        "ticket" -> "🎟"
        else -> "🎁"
    }
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(symbol, style = MaterialTheme.typography.displayMedium)
            Column(Modifier.weight(1f)) {
                Text(reward.rarity.label.uppercase(), style = MaterialTheme.typography.labelLarge)
                Text(reward.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text("Prêmio confirmado pela roleta", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CompletedContractsTab(state: ExpansionUiState, vm: ExpansionViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Histórico de contratos concluídos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Contratos concluídos não ocupam mais a lista normal. O pagamento continua registrado no financeiro.")
        }
        if (state.completedContracts.isEmpty()) {
            item { ElevatedCard { Text("Nenhum contrato concluído arquivado.", Modifier.padding(18.dp)) } }
        }
        items(state.completedContracts, key = { it.id }) { contract ->
            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("✓ ${contract.clientName}", fontWeight = FontWeight.Bold)
                            Text("Dificuldade ${contract.difficulty} • qualidade ${contract.requiredQuality}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(money(contract.rewardCents), fontWeight = FontWeight.Black)
                    }
                    Text("Produzido: ${contract.completedQuantity}/${contract.quantity} • reputação +${contract.reputationReward}", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { vm.dismissCompletedContract(contract.id) }, enabled = !state.busy) {
                        Text("Dispensar do histórico")
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanyTab(state: ExpansionUiState, vm: ExpansionViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Especialidade da empresa", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Defina o foco técnico. Especialidades mais avançadas liberam com o nível.")
        }
        items(CompanySpecialty.entries) { spec ->
            ElevatedCard { Column(Modifier.padding(14.dp)) {
                Text(spec.label, fontWeight = FontWeight.Bold)
                Text(spec.description, style = MaterialTheme.typography.bodySmall)
                val selected = state.expansion.specialty == spec.code
                Button(onClick = { vm.chooseSpecialty(spec.code) }, enabled = !selected && state.companyLevel >= spec.minLevel && !state.busy) {
                    Text(if (selected) "Selecionada" else if (state.companyLevel < spec.minLevel) "Libera nível ${spec.minLevel}" else "Definir especialidade")
                }
            } }
        }
        item { HorizontalDivider(); Text("Máquinas premium", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(ExpansionCatalog.premiumMachines) { machine -> PremiumMachineCard(machine, state, vm, showBuy = true) }
    }
}

@Composable
private fun PremiumMachineCard(machine: PremiumMachineDefinition, state: ExpansionUiState, vm: ExpansionViewModel, showBuy: Boolean) {
    val owned = machine.id in state.expansion.premiumMachines
    ElevatedCard { Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("🏭", style = MaterialTheme.typography.headlineLarge)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${machine.rarity.label} • ${machine.name}", fontWeight = FontWeight.Bold)
            Text(machine.description, style = MaterialTheme.typography.bodySmall)
            Text("Nível ${machine.minLevel} • ${money(machine.priceCents)}", style = MaterialTheme.typography.labelMedium)
            if (owned) Text("INSTALADA • bônus permanente", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            if (showBuy && !owned) {
                Button(onClick = { vm.buyPremiumMachine(machine.id) }, enabled = !state.busy && state.companyLevel >= machine.minLevel && state.cashCents >= machine.priceCents) {
                    Text(if (state.companyLevel < machine.minLevel) "Bloqueada" else "Comprar")
                }
            }
        }
    } }
}

@Composable
private fun SkillsTab(state: ExpansionUiState, vm: ExpansionViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Árvore da empresa • ${state.expansion.companySkillPoints(state.companyLevel)} ponto(s)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(ExpansionCatalog.companySkills) { skill -> SkillCard(skill, skill.id in state.expansion.companySkills, state.companyLevel, state.expansion.companySkills) { vm.unlockCompanySkill(skill.id) } }
        item { HorizontalDivider(); Text("Árvore do personagem • ${state.expansion.playerSkillPoints(state.companyLevel)} ponto(s)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(ExpansionCatalog.playerSkills) { skill -> SkillCard(skill, skill.id in state.expansion.playerSkills, state.companyLevel, state.expansion.playerSkills) { vm.unlockPlayerSkill(skill.id) } }
    }
}

@Composable
private fun SkillCard(skill: SkillDefinition, owned: Boolean, level: Int, ownedSet: Set<String>, unlock: () -> Unit) {
    val prerequisiteOk = skill.prerequisite == null || skill.prerequisite in ownedSet
    ElevatedCard { Column(Modifier.padding(14.dp)) {
        Text(skill.name, fontWeight = FontWeight.Bold)
        Text(skill.description, style = MaterialTheme.typography.bodySmall)
        Text("Nível ${skill.minLevel}${skill.prerequisite?.let { " • requer $it" } ?: ""}", style = MaterialTheme.typography.labelSmall)
        Button(onClick = unlock, enabled = !owned && level >= skill.minLevel && prerequisiteOk) { Text(if (owned) "Aprendida" else "Aprender") }
    } }
}

@Composable
private fun ToolsTab(state: ExpansionUiState, vm: ExpansionViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Ferramentas por contrato", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Uma ferramenta fica reservada para um contrato e é consumida quando o contrato termina. Ela altera velocidade e/ou qualidade.")
        }
        if (state.activeContracts.isEmpty()) item { Text("Nenhum contrato ativo para equipar ferramenta.") }
        items(state.activeContracts, key = { it.id }) { contract ->
            val bound = state.expansion.contractTools[contract.id]
            ElevatedCard { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(contract.clientName, fontWeight = FontWeight.Bold)
                Text("Dificuldade ${contract.difficulty} • qualidade ${contract.requiredQuality}", style = MaterialTheme.typography.bodySmall)
                Text("Equipada: ${ExpansionCatalog.tools.firstOrNull { it.id == bound }?.name ?: "nenhuma"}")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { vm.bindTool(contract.id, null) }, label = { Text("Sem ferramenta") })
                    ExpansionCatalog.tools.forEach { tool ->
                        val count = state.expansion.tools[tool.id] ?: 0
                        if (count > 0) AssistChip(onClick = { vm.bindTool(contract.id, tool.id) }, label = { Text("${tool.name} ×$count") })
                    }
                }
            } }
        }
        item { HorizontalDivider(); Text("Inventário", fontWeight = FontWeight.Bold) }
        items(ExpansionCatalog.tools) { tool ->
            val count = state.expansion.tools[tool.id] ?: 0
            ListItem(
                leadingContent = { Text("🔧") },
                headlineContent = { Text("${tool.name} ×$count") },
                supportingContent = { Text("${tool.rarity.label} • ${tool.description}") },
            )
        }
    }
}

@Composable
private fun CharacterTab(state: ExpansionUiState, vm: ExpansionViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Personagem principal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Skin equipada: ${ExpansionCatalog.skins.firstOrNull { it.id == state.expansion.equippedSkin }?.name ?: state.expansion.equippedSkin}")
        }
        items(ExpansionCatalog.skins) { skin ->
            val unlockedByLevel = state.companyLevel >= skin.minLevel
            val obtained = skin.id in state.expansion.ownedSkins || (!skin.gachaOnly && unlockedByLevel)
            ElevatedCard { Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (skin.id.contains("princesa")) "👸" else "🧑‍🏭", style = MaterialTheme.typography.headlineLarge)
                Column(Modifier.weight(1f)) {
                    Text("${skin.rarity.label} • ${skin.name}", fontWeight = FontWeight.Bold)
                    Text(skin.description, style = MaterialTheme.typography.bodySmall)
                    if (skin.gachaOnly && skin.id !in state.expansion.ownedSkins) Text("Obtida na roleta • depois exige nível ${skin.minLevel}", style = MaterialTheme.typography.labelSmall)
                    Button(onClick = { vm.equipSkin(skin.id) }, enabled = obtained && unlockedByLevel && state.expansion.equippedSkin != skin.id) {
                        Text(if (!obtained) "Falta obter na roleta" else if (!unlockedByLevel) "Libera nível ${skin.minLevel}" else if (state.expansion.equippedSkin == skin.id) "Equipada" else "Equipar")
                    }
                }
            } }
        }
        item {
            HorizontalDivider()
            Text("Personagens da roleta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Equipe um personagem gacha como especialista ativo. Apenas um bônus fica ativo por vez.")
        }
        items(ExpansionCatalog.gachaCharacters) { character ->
            val owned = character.id in state.expansion.ownedCharacters
            ElevatedCard { Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("👷", style = MaterialTheme.typography.headlineLarge)
                Column(Modifier.weight(1f)) {
                    Text("${character.rarity.label} • ${character.name}", fontWeight = FontWeight.Bold)
                    Text(character.description, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { vm.equipCharacter(character.id) }, enabled = owned && state.companyLevel >= character.minLevel && state.expansion.equippedCharacter != character.id) {
                        Text(if (!owned) "Falta obter na roleta" else if (state.companyLevel < character.minLevel) "Libera nível ${character.minLevel}" else if (state.expansion.equippedCharacter == character.id) "Ativo" else "Ativar")
                    }
                }
            } }
        }
        item {
            HorizontalDivider()
            Text("Mercado conectado • contratação por 48h", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val hired = state.expansion.remoteHireName
            if (hired != null && state.expansion.remoteHireEndsAt > System.currentTimeMillis()) {
                Text("Na sua empresa: $hired • +${state.expansion.remoteHireBoostPct}% até ${dateTime(state.expansion.remoteHireEndsAt)}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::publishCharacter, enabled = state.accountEmail != null && !state.busy) { Text("Ofertar meu personagem") }
                OutlinedButton(onClick = vm::loadOffers, enabled = state.accountEmail != null && !state.busy) { Text("Buscar") }
            }
        }
        items(state.offers, key = { it.ownerUid }) { offer -> RentalOfferCard(offer, state, vm) }
    }
}

@Composable
private fun RentalOfferCard(offer: CharacterOffer, state: ExpansionUiState, vm: ExpansionViewModel) {
    ElevatedCard { Column(Modifier.padding(14.dp)) {
        Text(offer.playerName, fontWeight = FontWeight.Bold)
        Text("Benefício: +${offer.boostPct}% produção por 48h")
        Text("Skills: ${if (offer.skills.isEmpty()) "iniciante" else offer.skills.joinToString()}", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { vm.hire(offer) }, enabled = !state.busy) { Text("Contratar por 2 dias") }
    } }
}

@Composable
private fun AccountTab(state: ExpansionUiState, vm: ExpansionViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Conta Google", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("O botão também aparece permanentemente no topo do Centro de Evolução.")
        }
        item {
            ElevatedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.accountEmail == null) {
                    Text("Nenhuma conta Google conectada")
                    Button(onClick = {
                        scope.launch {
                            runCatching { GoogleAuthBridge.signIn(context) }
                                .onSuccess { vm.refreshAccount("Login realizado: $it") }
                                .onFailure { vm.refreshAccount(it.message ?: "Falha no login Google") }
                        }
                    }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Entrar com Google") }
                } else {
                    Text(state.accountName ?: "Jogador", fontWeight = FontWeight.Bold)
                    Text(state.accountEmail)
                    OutlinedButton(onClick = { GoogleAuthBridge.signOut(); vm.refreshAccount("Conta desconectada") }) { Text("Sair") }
                }
            } }
        }
        item {
            Text("Diagnóstico", fontWeight = FontWeight.Bold)
            Text("Se o seletor de contas não abrir, confirme que app/google-services.json é o NOVO arquivo baixado depois de ativar Google e que ele contém oauth_client do tipo web.")
        }
    }
}

/** Entrada explícita para a expansão, usada na Fábrica Viva. */
@Composable
fun ExpansionLauncherCard(onOpen: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🎰 CENTRO DE EVOLUÇÃO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text("Roleta com figuras • Personagens • Skins • Máquinas TOP • Ferramentas • Skills • Conta Google", style = MaterialTheme.typography.bodySmall)
            FilledTonalButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("ABRIR ROLETA E EVOLUÇÃO") }
        }
    }
}

private fun money(cents: Long): String = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(cents / 100.0)
private fun dateTime(millis: Long): String = java.text.SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(java.util.Date(millis))
