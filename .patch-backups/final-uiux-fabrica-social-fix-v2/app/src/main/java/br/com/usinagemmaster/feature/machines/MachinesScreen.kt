package br.com.usinagemmaster.feature.machines

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import br.com.usinagemmaster.core.designsystem.component.ScreenHeader
import br.com.usinagemmaster.core.util.Formatters
import br.com.usinagemmaster.data.local.entity.EmployeeEntity
import br.com.usinagemmaster.data.local.entity.MachineEntity
import br.com.usinagemmaster.data.preferences.EngagementState
import br.com.usinagemmaster.domain.catalog.MachineCatalog
import br.com.usinagemmaster.domain.model.DashboardStatus
import br.com.usinagemmaster.domain.model.MachineProduction
import br.com.usinagemmaster.domain.model.ProductionSnapshot
import br.com.usinagemmaster.domain.simulation.EconomyBalance
import br.com.usinagemmaster.domain.simulation.SimulationCadence
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class WarehouseMode { LIVE, LAYOUT, LIST }

@Composable
fun MachinesScreen(
    onNavigate: (String) -> Unit = {},
    vm: MachinesViewModel = hiltViewModel()
) {
    val machines by vm.machines.collectAsState()
    val employees by vm.employees.collectAsState()
    val production by vm.production.collectAsState()
    val dashboard by vm.dashboard.collectAsState()
    val settings by vm.settings.collectAsState()
    val engagement by vm.engagement.collectAsState()
    val playerProfile by vm.playerProfile.collectAsState()
    val message by vm.message.collectAsState()
    val snack = remember { SnackbarHostState() }
    var mode by remember { mutableStateOf(WarehouseMode.LIVE) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showMinigame by remember { mutableStateOf(false) }
    var showDailyReward by remember { mutableStateOf(false) }
    var showCopa by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(message) {
        message?.let {
            snack.showSnackbar(it)
            vm.clearMessage()
        }
    }

    // Mantém o fechamento de 10 min vivo enquanto a fábrica está aberta.
    LaunchedEffect(Unit) {
        var seconds = 0
        while (true) {
            now = System.currentTimeMillis()
            if (seconds % 5 == 0) vm.tickProduction()
            seconds++
            delay(1_000L)
        }
    }

    val selectedMachine = machines.firstOrNull { it.id == selectedId }
    val operatingIds = production.machineProduction.filter { it.isOperating }.map { it.machineId }.toSet()
    val idleEmployees = employees.filter { employee ->
        val assigned = employee.assignedMachineId
        assigned == null || assigned !in operatingIds
    }
    val logistics = idleEmployees.count { it.specialty.contains("STOCK", ignoreCase = true) }
    val inspection = idleEmployees.count { it.legendaryCode == "nikao_narizudo" }
    val coffeeEmployees = idleEmployees.filter {
        !it.specialty.contains("STOCK", ignoreCase = true) && it.legendaryCode != "nikao_narizudo"
    }
    val coffee = coffeeEmployees.size

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            FactoryBottomNavigation(
                activeRoute = "machines",
                onNavigate = onNavigate
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FactoryHud(
                dashboard = dashboard,
                boostTokens = engagement.boostTokens
            )

            ScreenHeader(
                "Fábrica viva",
                "Operação ao vivo • toque nas máquinas, aproxime a câmera e acompanhe a equipe"
            )

            WarehouseTabs(mode = mode, onMode = { mode = it })

            // FIX FINAL 1.0: atalhos críticos ficam sempre visíveis no modo Fábrica Viva.
            // Eles ficam fora do scroll da cena: não somem mesmo em telas menores.
            if (mode == WarehouseMode.LIVE) {
                FactoryActionRow(
                    engagement = engagement,
                    now = now,
                    coffeeCount = coffee,
                    onMinigame = { showMinigame = true },
                    onDailyReward = { showDailyReward = true },
                    onAccelerate = vm::accelerateProduction,
                    onCopa = { showCopa = true }
                )
                Spacer(Modifier.height(6.dp))
            }

            if (mode != WarehouseMode.LIVE) {
                FactoryStatusRow(
                    active = production.operatingMachines,
                    waiting = production.idleMachines,
                    coffee = coffee,
                    logistics = logistics,
                    inspection = inspection
                )
            }

            when {
                machines.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Nenhuma máquina instalada.")
                    }
                }

                mode == WarehouseMode.LIVE -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            FactoryLiveSceneStudio(
                                machines = machines,
                                employees = employees,
                                production = production.machineProduction,
                                soundEnabled = settings.sound,
                                speechEnabled = settings.npcSpeech,
                                speechDurationSeconds = settings.speechDurationSeconds,
                                playerProfile = playerProfile,
                                onSelect = { selectedId = it.id }
                            )
                        }
                        item {
                            FactoryEarningsPanel(
                                production = production,
                                lastSimulationAt = dashboard.lastSimulationAt,
                                now = now
                            )
                        }
                    }
                }

                mode == WarehouseMode.LAYOUT -> {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        WarehouseLayout(
                            machines = machines,
                            employees = employees,
                            production = production.machineProduction,
                            onMove = vm::move,
                            onSelect = { selectedId = it.id }
                        )
                    }
                }

                else -> {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        MachineList(
                            machines = machines,
                            employees = employees,
                            production = production.machineProduction,
                            onSelect = { selectedId = it.id }
                        )
                    }
                }
            }
        }
    }

    if (selectedMachine != null) {
        MachineManagementDialog(
            machine = selectedMachine,
            employees = employees,
            production = production.machineProduction.firstOrNull { it.machineId == selectedMachine.id },
            onDismiss = { selectedId = null },
            onAssign = { employeeId -> vm.assign(selectedMachine.id, employeeId) },
            onRepair = { vm.repair(selectedMachine.id) }
        )
    }

    if (showDailyReward) {
        DailyRewardDialog(
            available = engagement.dailyRewardAvailable,
            estimatedCash = maxOf(150_000L, production.netPer10MinutesCents),
            onDismiss = { showDailyReward = false },
            onClaim = {
                showDailyReward = false
                vm.claimDailyReward()
            }
        )
    }

    if (showMinigame) {
        ProductionMinigameDialog(
            available = engagement.minigameAvailable,
            remainingMillis = engagement.minigameRemainingMillis(now),
            onDismiss = { showMinigame = false },
            onFinished = { score ->
                showMinigame = false
                vm.completeMinigame(score)
            }
        )
    }

    if (showCopa) {
        FactoryCopaDialog(
            employees = coffeeEmployees,
            onDismiss = { showCopa = false }
        )
    }
}

@Composable
private fun FactoryHud(dashboard: DashboardStatus, boostTokens: Int) {
    Surface(
        color = Color(0xFF0A1116),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .32f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = .10f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .65f))
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${dashboard.companyLevel}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(5.dp))
                    Text("NÍVEL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("CAIXA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Formatters.money(dashboard.cashCents), fontWeight = FontWeight.Black, color = Color(0xFF6BE7A0))
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF15222A),
                border = BorderStroke(1.dp, Color(0xFFFFB21A).copy(alpha = .35f))
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚡", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(boostTokens.toString(), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        Text("IMPULSOS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun WarehouseTabs(mode: WarehouseMode, onMode: (WarehouseMode) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = mode == WarehouseMode.LIVE,
            onClick = { onMode(WarehouseMode.LIVE) },
            label = { Text("Fábrica viva") }
        )
        FilterChip(
            selected = mode == WarehouseMode.LAYOUT,
            onClick = { onMode(WarehouseMode.LAYOUT) },
            label = { Text("Editar layout") }
        )
        FilterChip(
            selected = mode == WarehouseMode.LIST,
            onClick = { onMode(WarehouseMode.LIST) },
            label = { Text("Lista técnica") }
        )
    }
}

@Composable
private fun FactoryStatusRow(active: Int, waiting: Int, coffee: Int, logistics: Int, inspection: Int) {
    Row(
        Modifier.padding(horizontal = 18.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PremiumStatusPill("$active produzindo", Color(0xFF5BDD83))
        PremiumStatusPill("$waiting em espera", Color(0xFFFF9D24))
        PremiumStatusPill("☕ $coffee no café", Color(0xFFC7A76D))
        if (logistics > 0) PremiumStatusPill("🚚 $logistics logística", Color(0xFF68BCEB))
        if (inspection > 0) PremiumStatusPill("🔎 $inspection inspeção", Color(0xFFC58BE8))
    }
}

@Composable
private fun PremiumStatusPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = .10f),
        border = BorderStroke(1.dp, color.copy(alpha = .55f))
    ) {
        Row(Modifier.padding(horizontal = 11.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, RoundedCornerShape(99.dp)))
            Spacer(Modifier.width(7.dp))
            Text(text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun FactoryEarningsPanel(
    production: ProductionSnapshot,
    lastSimulationAt: Long,
    now: Long
) {
    val remaining = SimulationCadence.millisUntilNextCycle(lastSimulationAt, now)
    val totalSeconds = (remaining / 1000L).coerceAtLeast(0L)
    val min = totalSeconds / 60L
    val sec = totalSeconds % 60L
    val progress = (1f - remaining.toFloat() / SimulationCadence.CYCLE_MILLIS.toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10181E)),
        border = BorderStroke(1.dp, Color(0xFF5A6870).copy(alpha = .52f))
    ) {
        Column {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f).padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("GANHOS / 10 MIN", style = MaterialTheme.typography.labelMedium, color = Color(0xFFB8C4CA), fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(7.dp), color = Color(0xFF24592F)) {
                            Text("3x", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = Color(0xFF8CF39D), fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        Formatters.money(production.netPer10MinutesCents),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFFFC02C)
                    )
                    Text(
                        "Produção: ${String.format(Locale.getDefault(), "%.1f", production.totalUnitsPer10Minutes)} peças / 10 min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    Modifier.width(112.dp).height(130.dp).background(
                        Brush.radialGradient(
                            listOf(Color(0xFF7A5700), Color(0xFF2B2108), Color(0xFF15140F))
                        )
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("GANHOS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("3x", style = MaterialTheme.typography.headlineLarge, color = Color(0xFFFFC021), fontWeight = FontWeight.Black)
                        Text("ATIVO", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8AF29A), fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = .07f))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("PRÓXIMO CICLO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(String.format(Locale.getDefault(), "%02d:%02d", min, sec), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    }
                }
                Column(Modifier.weight(1.25f)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(7.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color(0xFF3A454C)
                    )
                    Spacer(Modifier.height(5.dp))
                    Text("Fechamento automático do caixa", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FactoryActionRow(
    engagement: EngagementState,
    now: Long,
    coffeeCount: Int,
    onMinigame: () -> Unit,
    onDailyReward: () -> Unit,
    onAccelerate: () -> Unit,
    onCopa: () -> Unit
) {
    val remaining = engagement.minigameRemainingMillis(now)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0C151A),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .08f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FactoryActionCard(
                title = "MINIGAME",
                subtitle = if (remaining == 0L) "Pronto" else "${(remaining / 60_000L) + 1} min",
                icon = "🎮",
                accent = Color(0xFFB45CFF),
                onClick = onMinigame,
                modifier = Modifier.weight(1f)
            )
            FactoryActionCard(
                title = "BÔNUS",
                subtitle = if (engagement.dailyRewardAvailable) "Disponível" else "Amanhã",
                icon = "🎁",
                accent = Color(0xFF35C8FF),
                onClick = onDailyReward,
                modifier = Modifier.weight(1f)
            )
            FactoryActionCard(
                title = "+10 MIN",
                subtitle = "${engagement.boostTokens} impulso${if (engagement.boostTokens == 1) "" else "s"}",
                icon = "⚡",
                accent = Color(0xFFFFB21A),
                onClick = onAccelerate,
                modifier = Modifier.weight(1f)
            )
            FactoryActionCard(
                title = "COPA",
                subtitle = if (coffeeCount == 0) "Vazia" else "$coffeeCount pausa",
                icon = "☕",
                accent = Color(0xFFFFD08A),
                onClick = onCopa,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FactoryActionCard(
    title: String,
    subtitle: String,
    icon: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(70.dp),
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = .08f)),
        border = BorderStroke(1.dp, accent.copy(alpha = .42f))
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(icon, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FactoryCopaDialog(
    employees: List<EmployeeEntity>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text("☕", style = MaterialTheme.typography.headlineLarge) },
        title = { Text("Copa", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Área de descanso da equipe",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (employees.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f)
                    ) {
                        Text(
                            "Ninguém está em pausa agora. A equipe está no chão de fábrica.",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    employees.take(6).forEach { employee ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFFC766).copy(alpha = .07f),
                            border = BorderStroke(1.dp, Color(0xFFFFC766).copy(alpha = .18f))
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (employee.legendaryCode != null) "⭐" else "👷")
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(employee.name, fontWeight = FontWeight.Bold)
                                    Text("Pausa para café", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("☕")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Voltar à fábrica") }
        }
    )
}

@Composable
private fun DailyRewardDialog(
    available: Boolean,
    estimatedCash: Long,
    onDismiss: () -> Unit,
    onClaim: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text("🎁", style = MaterialTheme.typography.headlineLarge) },
        title = { Text("Recompensa diária", fontWeight = FontWeight.Black) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (available) "Volte todos os dias para manter sua fábrica acelerada." else "Você já resgatou a recompensa de hoje.",
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = .10f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .32f))
                ) {
                    Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+${EconomyBalance.DAILY_BOOST_TOKENS} impulsos de produção", fontWeight = FontWeight.Black)
                        Text("+${Formatters.money(estimatedCash)}", color = Color(0xFF67DD98), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onClaim, enabled = available) {
                Text(if (available) "Resgatar" else "Resgatado")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
private fun ProductionMinigameDialog(
    available: Boolean,
    remainingMillis: Long,
    onDismiss: () -> Unit,
    onFinished: (Float) -> Unit
) {
    val transition = rememberInfiniteTransition(label = "minigame_cursor")
    val cursor by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_350, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor"
    )
    val score = (1f - abs(cursor - .5f) * 2f).coerceIn(0f, 1f)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text("🎮", style = MaterialTheme.typography.headlineLarge) },
        title = { Text("Minigame de produção", fontWeight = FontWeight.Black) },
        text = {
            Column {
                if (!available) {
                    val seconds = remainingMillis / 1000L
                    Text("O painel está resfriando. Volte em ${seconds / 60}:${String.format(Locale.getDefault(), "%02d", seconds % 60)}.")
                } else {
                    Text("Pare o marcador o mais perto possível do centro para ganhar caixa e impulsos.")
                    Spacer(Modifier.height(18.dp))
                    Canvas(Modifier.fillMaxWidth().height(54.dp)) {
                        drawRoundRect(Color(0xFF29343B), size = size)
                        drawRoundRect(
                            Color(0xFF4DCA75).copy(alpha = .24f),
                            topLeft = Offset(size.width * .40f, 0f),
                            size = Size(size.width * .20f, size.height)
                        )
                        drawLine(
                            color = Color(0xFFFFC126),
                            start = Offset(size.width * cursor, 3f),
                            end = Offset(size.width * cursor, size.height - 3f),
                            strokeWidth = 8f
                        )
                        drawLine(
                            color = Color.White.copy(alpha = .32f),
                            start = Offset(size.width * .5f, 0f),
                            end = Offset(size.width * .5f, size.height),
                            strokeWidth = 2f
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Zona perfeita = 2 impulsos", color = Color(0xFF72E69A), fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onFinished(score) },
                enabled = available
            ) { Text(if (available) "PARAR AGORA" else "Recarregando") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
private fun FactoryBottomNavigation(activeRoute: String, onNavigate: (String) -> Unit) {
    val items = listOf(
        Triple("dashboard", "Visão geral", Icons.Default.Dashboard),
        Triple("store", "Máquinas", Icons.Default.PrecisionManufacturing),
        Triple("machines", "Galpão", Icons.Default.Factory),
        Triple("employees", "Equipe", Icons.Default.Groups),
        Triple("goals", "Metas", Icons.Default.EmojiEvents)
    )
    NavigationBar(containerColor = Color(0xFF10171C), tonalElevation = 10.dp) {
        items.forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = activeRoute == route,
                onClick = { if (activeRoute != route) onNavigate(route) },
                icon = { Icon(icon, null) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF241800),
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun WarehouseLayout(
    machines: List<MachineEntity>,
    employees: List<EmployeeEntity>,
    production: List<MachineProduction>,
    onMove: (String, Int, Int) -> Unit,
    onSelect: (MachineEntity) -> Unit
) {
    val employeeByMachine = employees.filter { it.assignedMachineId != null }.associateBy { it.assignedMachineId!! }
    val productionByMachine = production.associateBy { it.machineId }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = .30f)
    val density = LocalDensity.current

    Card(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .92f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(560.dp).padding(8.dp).background(MaterialTheme.colorScheme.background.copy(alpha = .45f), RoundedCornerShape(14.dp))
        ) {
            val columns = 5
            val rows = 6
            val cellWidth = maxWidth / columns
            val cellHeight = 90.dp
            val cellWidthPx = with(density) { cellWidth.toPx() }
            val cellHeightPx = with(density) { cellHeight.toPx() }

            Canvas(Modifier.matchParentSize()) {
                for (x in 1 until columns) {
                    drawLine(gridColor, Offset(x * cellWidthPx, 0f), Offset(x * cellWidthPx, size.height), strokeWidth = 1f)
                }
                for (y in 1 until rows) {
                    drawLine(gridColor, Offset(0f, y * cellHeightPx), Offset(size.width, y * cellHeightPx), strokeWidth = 1f)
                }
            }

            machines.forEach { machine ->
                var dragOffset by remember(machine.id, machine.gridX, machine.gridY) { mutableStateOf(Offset.Zero) }
                val def = MachineCatalog.byType(machine.machineType)
                val operator = employeeByMachine[machine.id]
                val work = productionByMachine[machine.id]
                val operating = work?.isOperating == true

                Card(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (machine.gridX * cellWidthPx + dragOffset.x).roundToInt(),
                                (machine.gridY * cellHeightPx + dragOffset.y).roundToInt()
                            )
                        }
                        .width((cellWidth - 6.dp).coerceAtLeast(54.dp))
                        .height(82.dp)
                        .pointerInput(machine.id, machine.gridX, machine.gridY) {
                            detectDragGestures(
                                onDragEnd = {
                                    val x = ((machine.gridX * cellWidthPx + dragOffset.x) / cellWidthPx).roundToInt().coerceIn(0, columns - 1)
                                    val y = ((machine.gridY * cellHeightPx + dragOffset.y) / cellHeightPx).roundToInt().coerceIn(0, rows - 1)
                                    dragOffset = Offset.Zero
                                    onMove(machine.id, x, y)
                                },
                                onDragCancel = { dragOffset = Offset.Zero }
                            ) { change, amount ->
                                change.consume()
                                dragOffset += amount
                            }
                        }
                        .clickable { onSelect(machine) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (operating) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(Icons.Default.PrecisionManufacturing, null, Modifier.size(18.dp), tint = if (operating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                        Text(def?.name ?: machine.machineType, maxLines = 1, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(operator?.name?.substringBefore(' ') ?: "Sem operador", maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        Text(if (operating) "${String.format(Locale.getDefault(), "%.1f", work?.unitsPer10Minutes ?: 0.0)} pç/10 min" else "PARADA", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun MachineList(
    machines: List<MachineEntity>,
    employees: List<EmployeeEntity>,
    production: List<MachineProduction>,
    onSelect: (MachineEntity) -> Unit
) {
    val employeeByMachine = employees.filter { it.assignedMachineId != null }.associateBy { it.assignedMachineId!! }
    val productionByMachine = production.associateBy { it.machineId }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(machines, key = { it.id }) { machine ->
            val def = MachineCatalog.byType(machine.machineType)
            val operator = employeeByMachine[machine.id]
            val work = productionByMachine[machine.id]
            Card(onClick = { onSelect(machine) }) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PrecisionManufacturing, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(def?.name ?: machine.machineType, fontWeight = FontWeight.Bold)
                            Text("Nível ${machine.level} • ${operator?.name ?: "Sem operador"}", style = MaterialTheme.typography.bodySmall)
                        }
                        PremiumStatusPill(if (work?.isOperating == true) "OPERANDO" else "PARADA", if (work?.isOperating == true) Color(0xFF5BDD83) else Color(0xFFFF7B72))
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { machine.condition / 1000f }, modifier = Modifier.fillMaxWidth())
                    Text("Conservação ${machine.condition / 10}% • ${String.format(Locale.getDefault(), "%.1f", work?.unitsPer10Minutes ?: 0.0)} pç/10 min", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun MachineManagementDialog(
    machine: MachineEntity,
    employees: List<EmployeeEntity>,
    production: MachineProduction?,
    onDismiss: () -> Unit,
    onAssign: (String?) -> Unit,
    onRepair: () -> Unit
) {
    val def = MachineCatalog.byType(machine.machineType)
    val current = employees.firstOrNull { it.assignedMachineId == machine.id }
    val candidates = employees.sortedWith(
        compareByDescending<EmployeeEntity> { it.specialty == def?.specialty?.name }.thenByDescending { it.skillLevel }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(def?.name ?: "Máquina", fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Nível ${machine.level} • Conservação ${machine.condition / 10}%")
                Text("Especialidade recomendada: ${def?.specialty?.name ?: "-"}", color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(10.dp))
                Text("Produção atual", fontWeight = FontWeight.Bold)
                Text(if (production?.isOperating == true) "${String.format(Locale.getDefault(), "%.2f", production.unitsPer10Minutes)} peças/10 min • qualidade ${production.quality}%" else "Máquina parada")
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Operador: ${current?.name ?: "nenhum"}", fontWeight = FontWeight.Bold)
                }
                if (current != null) {
                    TextButton(onClick = { onAssign(null) }) { Text("Remover operador") }
                }
                HorizontalDivider()
                Text("Atribuir funcionário", Modifier.padding(vertical = 8.dp), fontWeight = FontWeight.Bold)
                if (candidates.isEmpty()) {
                    Text("Contrate funcionários para colocar esta máquina em produção.")
                } else {
                    candidates.forEach { employee ->
                        val recommended = employee.specialty == def?.specialty?.name
                        TextButton(onClick = { onAssign(employee.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(employee.name, fontWeight = if (recommended) FontWeight.Bold else FontWeight.Normal)
                                Text("${employee.specialty} • Nv. ${employee.skillLevel}${if (recommended) " • recomendado" else ""}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (machine.condition < 1000) {
                Button(onClick = onRepair) {
                    Icon(Icons.Default.Build, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Manutenção")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}
