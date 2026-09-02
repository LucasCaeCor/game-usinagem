package br.com.usinagemmaster.feature.dashboard

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import br.com.usinagemmaster.core.designsystem.component.GameCard
import br.com.usinagemmaster.core.designsystem.component.IndustrialBackground
import br.com.usinagemmaster.core.designsystem.component.StatusPill
import br.com.usinagemmaster.core.util.Formatters
import br.com.usinagemmaster.domain.simulation.SimulationCadence
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.sin
import br.com.usinagemmaster.feature.expansion.ExpansionHomeMenu
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class DashboardAction(val title: String, val subtitle: String, val route: String, val icon: ImageVector)

@Composable
fun DashboardScreen(vm: DashboardViewModel = hiltViewModel(), onNavigate: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val production by vm.production.collectAsStateWithLifecycle()
    val playerProfile by vm.playerProfile.collectAsStateWithLifecycle()

    
    // V10_RENAME_DIALOG_STATE
    var showRenameCompany by remember { mutableStateOf(false) }
LaunchedEffect(Unit) {
        while (true) {
            vm.tickProduction()
            delay(10_000L)
        }
    }

    // V13_REMEMBER_ACTIONS
    val actions = remember { listOf(
        DashboardAction("Fábrica viva", "Veja máquinas, operadores e pausas", "machines", Icons.Default.Factory),
        DashboardAction("Funcionários", "Equipe, lendários e missões", "employees", Icons.Default.Groups),
        DashboardAction("Contratos", "Produção, qualidade e prazos", "contracts", Icons.Default.Assignment),
        DashboardAction("Loja", "Modernize o parque fabril", "store", Icons.Default.ShoppingCart),
        DashboardAction("Reforma", "Amplie a infraestrutura", "facility", Icons.Default.Construction),
        DashboardAction("Finanças", "Receitas e despesas", "finance", Icons.Default.AccountBalance),
        DashboardAction("Metas", "Objetivos e recompensas", "goals", Icons.Default.EmojiEvents),
        DashboardAction("Meu personagem", "Crie o dono da sua fábrica", "profile", Icons.Default.Person),
        DashboardAction("Comunidade", "Ranking e apoio entre oficinas", "social", Icons.Default.Public),
        DashboardAction("Configurações", "Som, falas e preferências", "settings", Icons.Default.Settings)
    ) }

    IndustrialBackground {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 34.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Column {
                    Text("USINAGEM MASTER", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.ExtraBold)
                    Row(
                // V10_COMPANY_NAME_EDITOR_TRIGGER
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    state.companyName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                IconButton(onClick = { showRenameCompany = true }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Renomear empresa",
                        tint = Color.White
                    )
                }
            }
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        AssistChip(
                            onClick = { onNavigate("profile") },
                            label = { Text(playerProfile.displayName.ifBlank { "Criar personagem" }) },
                            leadingIcon = { Icon(Icons.Default.Person, null, Modifier.size(16.dp)) }
                        )
                        AssistChip(
                            onClick = { onNavigate("social") },
                            label = { Text("Comunidade") },
                            leadingIcon = { Icon(Icons.Default.Public, null, Modifier.size(16.dp)) }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LiveCompanyHeader(
                        cash = Formatters.money(state.cashCents),
                        companyLevel = state.companyLevel,
                        reputation = state.reputation,
                        space = "${state.usedWarehouseSpace}/${state.warehouseSpace} m²",
                        operating = production.operatingMachines,
                        idle = production.idleMachines
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            item { GameCard("Produção / 10 min", String.format(Locale.getDefault(), "%.1f pç", production.totalUnitsPer10Minutes), Modifier.fillMaxWidth(), "${production.operatingMachines} máquinas operando") }
            item { GameCard("Lucro / 10 min • 3x", Formatters.money(production.netPer10MinutesCents), Modifier.fillMaxWidth(), "Bônus permanente 3x • energia ${Formatters.money(production.energyPer10MinutesCents)}") }
            item { GameCard("Qualidade média", "${production.averageQuality}%", Modifier.fillMaxWidth(), if (production.averageQuality >= 80) "Padrão industrial alto" else "Manutenção e treinamento ajudam") }
            item { GameCard("Contratos ativos", state.activeContracts.toString(), Modifier.fillMaxWidth(), "${production.idleMachines} máquinas em espera") }

            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                SettlementCountdownCard(state.lastSimulationAt, production.netPer10MinutesCents)
            }

            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                Row(
                    Modifier.padding(top = 10.dp, bottom = 2.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Gestão da fábrica", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    StatusPill(if (production.operatingMachines > 0) "TURNO ATIVO" else "SEM PRODUÇÃO", production.operatingMachines > 0)
                }
            }

            // V12_HOME_ADVANCED_MENU
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            ExpansionHomeMenu()
        }

        items(actions) { action ->
                Card(
                    onClick = { onNavigate(action.route) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f))
                ) {
                    Column(Modifier.padding(16.dp).heightIn(min = 112.dp)) {
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)
                        ) {
                            Icon(action.icon, null, Modifier.padding(8.dp).size(21.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(action.title, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text(action.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    // V10_COMPANY_RENAME_DIALOG
    if (showRenameCompany) {
        var draftCompanyName by remember(state.companyName, showRenameCompany) {
            mutableStateOf(state.companyName)
        }
        val normalizedCompanyName = draftCompanyName.trim().replace(Regex("\\s+"), " ")
        val validCompanyName = normalizedCompanyName.length in 3..32 &&
            normalizedCompanyName.any { it.isLetterOrDigit() }

        AlertDialog(
            onDismissRequest = { showRenameCompany = false },
            title = {
                Text(
                    "Nome da empresa",
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Esse nome identifica sua fábrica no jogo e na comunidade.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = draftCompanyName,
                        onValueChange = { if (it.length <= 32) draftCompanyName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Nome da fábrica") },
                        singleLine = true,
                        supportingText = {
                            Text(
                                "${normalizedCompanyName.length}/32 caracteres",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    if (draftCompanyName.isNotBlank() && !validCompanyName) {
                        Text(
                            "Use entre 3 e 32 caracteres.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = validCompanyName && normalizedCompanyName != state.companyName,
                    onClick = {
                        vm.renameCompany(normalizedCompanyName)
                        showRenameCompany = false
                    }
                ) {
                    Text("Salvar nome")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameCompany = false }) {
                    Text("Cancelar", color = Color.White)
                }
            }
        )
    }

}

@Composable
private fun SettlementCountdownCard(lastSimulationAt: Long, nextProfitCents: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastSimulationAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val remaining = SimulationCadence.millisUntilNextCycle(lastSimulationAt, now)
    val totalSeconds = (remaining / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .10f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .30f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("PRÓXIMO FECHAMENTO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                Text(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = Color.White)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("estimativa", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("+${Formatters.money(nextProfitCents)}", fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
    }
}

@Composable
private fun LiveCompanyHeader(
    cash: String,
    companyLevel: Int,
    reputation: Int,
    space: String,
    operating: Int,
    idle: Int
) {
    // V13_STATIC_HEADER — sem animação infinita na Home
    val phase = 0.52f
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF493400), MaterialTheme.colorScheme.surfaceContainerHigh, Color(0xFF102532))
                ),
                RoundedCornerShape(24.dp)
            )
    ) {
        Canvas(Modifier.matchParentSize()) {
            val scannerX = phase * size.width
            drawLine(Color(0xFFFFD05B).copy(alpha = .10f), Offset(scannerX, 0f), Offset(scannerX, size.height), 18f)
            repeat(7) { i ->
                val x = (i / 6f) * size.width
                val wave = sin((phase + i * .13f) * 6.283f) * 5f
                drawCircle(Color.White.copy(alpha = .035f), 12f, Offset(x, size.height * .78f + wave))
            }
        }
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("CAIXA DISPONÍVEL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(cash, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Color.White)
                }
                StatusPill("NÍVEL $companyLevel")
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("★ Reputação $reputation", style = MaterialTheme.typography.bodySmall, color = Color.White)
                Text("Galpão $space", style = MaterialTheme.typography.bodySmall, color = Color.White)
            }
        // V7_FACTORY_XP_DASHBOARD
        val factoryXp = br.com.usinagemmaster.domain.expansion.ExpansionProgression.factory(companyLevel, reputation)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("XP DA FÁBRICA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            Text("${factoryXp.current}/${factoryXp.needed} XP", style = MaterialTheme.typography.labelSmall)
        }
        LinearProgressIndicator(
            progress = { factoryXp.fraction },
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )

            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { if (operating + idle == 0) 0f else operating.toFloat() / (operating + idle).toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = .45f)
            )
            Spacer(Modifier.height(5.dp))
            Text("$operating em produção • $idle em espera", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
