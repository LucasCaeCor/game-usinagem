package br.com.usinagemmaster.feature.machines

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.usinagemmaster.data.local.entity.EmployeeEntity
import br.com.usinagemmaster.data.preferences.WorkLifeRepository
import br.com.usinagemmaster.domain.worklife.FactoryScheduleMode
import br.com.usinagemmaster.domain.worklife.WorkLifeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class WorkLifeViewModel @Inject constructor(
    private val repository: WorkLifeRepository,
) : ViewModel() {
    val state: StateFlow<WorkLifeState> =
        repository.state.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            WorkLifeState(),
        )

    fun setMode(mode: FactoryScheduleMode) =
        viewModelScope.launch { repository.setMode(mode) }

    fun setAutoRest(enabled: Boolean) =
        viewModelScope.launch { repository.setAutoRest(enabled) }

    fun rest(id: String) =
        viewModelScope.launch { repository.sendToBreak(id) }

    fun returnToWork(id: String) =
        viewModelScope.launch { repository.returnFromBreak(id) }
}

@Composable
fun WorkLifeHomeCard(
    vm: WorkLifeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFF172128),
            contentColor = Color.White,
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .12f)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "🕒 Turno da empresa",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        state.statusText(now),
                        color = Color(0xFFD6E0E5),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.mode == FactoryScheduleMode.SHIFT_12H) {
                    val next = state.nextScheduleChangeMillis(now)
                    if (next != null) {
                        Text(
                            formatRemaining(next - now),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.mode == FactoryScheduleMode.SHIFT_12H,
                    onClick = { vm.setMode(FactoryScheduleMode.SHIFT_12H) },
                    label = { Text("12h • 07–19") },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = state.mode == FactoryScheduleMode.CONTINUOUS_24H,
                    onClick = { vm.setMode(FactoryScheduleMode.CONTINUOUS_24H) },
                    label = { Text("24h • exaustão") },
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                if (state.mode == FactoryScheduleMode.SHIFT_12H)
                    "No modo 12h a produção e o prazo dos contratos só avançam entre 07:00 e 19:00. À noite a equipe vai para casa."
                else
                    "No modo 24h a fábrica não fecha. A equipe acumula exaustão e perde produtividade se você não usar a Copa.",
                color = Color(0xFFD6E0E5),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun WorkLifeFactoryCard(
    employees: List<EmployeeEntity>,
    onOpenCopa: () -> Unit,
    vm: WorkLifeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            now = System.currentTimeMillis()
        }
    }

    val ids = employees.map { it.id } + WorkLifeRepository.PLAYER_ID
    val average = state.averageExhaustion(ids)
    val restingCount = employees.count { state.isResting(it.id, now) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFF172128),
            contentColor = Color.White,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .28f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("🕒 VIDA DA EMPRESA", color = Color.White, fontWeight = FontWeight.Black)
                    Text(
                        state.statusText(now),
                        color = Color(0xFFD6E0E5),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    if (state.mode == FactoryScheduleMode.CONTINUOUS_24H) "$average%" else "07–19",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.mode == FactoryScheduleMode.SHIFT_12H,
                    onClick = { vm.setMode(FactoryScheduleMode.SHIFT_12H) },
                    label = { Text("12h • casa") },
                )
                FilterChip(
                    selected = state.mode == FactoryScheduleMode.CONTINUOUS_24H,
                    onClick = { vm.setMode(FactoryScheduleMode.CONTINUOUS_24H) },
                    label = { Text("24h • exaustão") },
                )
            }

            if (state.mode == FactoryScheduleMode.CONTINUOUS_24H) {
                LinearProgressIndicator(
                    progress = { average / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Exaustão média $average% • $restingCount na Copa agora",
                    color = Color(0xFFD6E0E5),
                    style = MaterialTheme.typography.labelMedium,
                )
            } else if (!state.factoryOpen(now)) {
                Text(
                    "🌙 Os funcionários estão em casa. Eles deixam o chão de fábrica e recuperam exaustão. Contratos estão pausados.",
                    color = Color(0xFFD6E0E5),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = onOpenCopa,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("☕ Abrir Copa e exaustão")
            }
        }
    }
}

@Composable
fun WorkLifeEmployeeSection(
    employees: List<EmployeeEntity>,
    vm: WorkLifeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            now = System.currentTimeMillis()
        }
    }

    val ids = employees.map { it.id } + WorkLifeRepository.PLAYER_ID
    val average = state.averageExhaustion(ids)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFF172128),
            contentColor = Color.White,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("⚡ Exaustão da equipe", color = Color.White, fontWeight = FontWeight.Black)
                    Text(
                        "${state.statusText(now)} • média $average%",
                        color = Color(0xFFD6E0E5),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Ocultar" else "Ver equipe")
                }
            }

            LinearProgressIndicator(
                progress = { average / 100f },
                modifier = Modifier.fillMaxWidth(),
            )

            if (expanded) {
                employees.forEach { employee ->
                    CompactFatigueRow(
                        name = employee.name,
                        id = employee.id,
                        state = state,
                        now = now,
                        onRest = vm::rest,
                        onReturn = vm::returnToWork,
                    )
                }
            }
        }
    }
}

@Composable
fun WorkLifeCopaDialog(
    employees: List<EmployeeEntity>,
    state: WorkLifeState,
    onMode: (FactoryScheduleMode) -> Unit,
    onDismiss: () -> Unit,
    onRest: (String) -> Unit,
    onReturn: (String) -> Unit,
    onAutoRest: (Boolean) -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            now = System.currentTimeMillis()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("☕ Copa e descanso", color = Color.White, fontWeight = FontWeight.Black)
                Text(
                    if (state.mode == FactoryScheduleMode.CONTINUOUS_24H)
                        "Recupere funcionários sem fechar a empresa"
                    else
                        "No turno 12h a equipe também recupera energia em casa",
                    color = Color(0xFFD6E0E5),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Furniture("🛋️", "Sofás", Modifier.weight(1f))
                    Furniture("🍽️", "Mesas", Modifier.weight(1f))
                    Furniture("🪑", "Bancos", Modifier.weight(1f))
                }

                // V15_3_COPA_MODE_SELECTOR
                Text(
                    "Modo de operação",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.mode == FactoryScheduleMode.SHIFT_12H,
                        onClick = { onMode(FactoryScheduleMode.SHIFT_12H) },
                        label = { Text("12h • 07–19") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = state.mode == FactoryScheduleMode.CONTINUOUS_24H,
                        onClick = { onMode(FactoryScheduleMode.CONTINUOUS_24H) },
                        label = { Text("24h") },
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Descanso automático", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "No modo 24h envia automaticamente para a Copa a partir de 88%.",
                            color = Color(0xFFD6E0E5),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Switch(
                        checked = state.autoRest,
                        enabled = state.mode == FactoryScheduleMode.CONTINUOUS_24H,
                        onCheckedChange = onAutoRest,
                    )
                }

                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FatigueRow(
                            name = "Você • personagem principal",
                            id = WorkLifeRepository.PLAYER_ID,
                            state = state,
                            now = now,
                            onRest = onRest,
                            onReturn = onReturn,
                        )
                    }

                    items(employees, key = { it.id }) { employee ->
                        FatigueRow(
                            name = employee.name,
                            id = employee.id,
                            state = state,
                            now = now,
                            onRest = onRest,
                            onReturn = onReturn,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Voltar à fábrica", color = Color.White)
            }
        },
    )
}

@Composable
private fun Furniture(
    icon: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF253139),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .12f)),
    ) {
        Column(
            Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(icon, style = MaterialTheme.typography.headlineSmall)
            Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CompactFatigueRow(
    name: String,
    id: String,
    state: WorkLifeState,
    now: Long,
    onRest: (String) -> Unit,
    onReturn: (String) -> Unit,
) {
    val exhaustion = state.exhaustion(id)
    val resting = state.isResting(id, now)

    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                if (resting) "☕ Copa" else "$exhaustion%",
                color = if (resting) Color(0xFF7EE2A8) else Color.White,
                fontWeight = FontWeight.Black,
            )
        }
        LinearProgressIndicator(
            progress = { exhaustion / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${state.exhaustionLabel(id)} • eficiência ${(state.efficiency(id) * 100).toInt()}%",
            color = Color(0xFFD6E0E5),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun FatigueRow(
    name: String,
    id: String,
    state: WorkLifeState,
    now: Long,
    onRest: (String) -> Unit,
    onReturn: (String) -> Unit,
) {
    val exhaustion = state.exhaustion(id)
    val resting = state.isResting(id, now)
    val factoryOpen = state.factoryOpen(now)

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color(0xFF1A252B),
            contentColor = Color.White,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        !factoryOpen && state.mode == FactoryScheduleMode.SHIFT_12H -> "🏠 EM CASA"
                        resting -> "☕ DESCANSANDO"
                        else -> "$exhaustion%"
                    },
                    color = if (resting) Color(0xFF7EE2A8) else Color.White,
                    fontWeight = FontWeight.Black,
                )
            }

            LinearProgressIndicator(
                progress = { exhaustion / 100f },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${state.exhaustionLabel(id)} • eficiência ${(state.efficiency(id) * 100).toInt()}%",
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFD6E0E5),
                    style = MaterialTheme.typography.labelSmall,
                )

                when {
                    !factoryOpen && state.mode == FactoryScheduleMode.SHIFT_12H -> {
                        Text("Descansando em casa", color = Color(0xFF7EE2A8), style = MaterialTheme.typography.labelSmall)
                    }
                    resting -> {
                        TextButton(onClick = { onReturn(id) }) {
                            Text("Voltar")
                        }
                    }
                    else -> {
                        FilledTonalButton(onClick = { onRest(id) }) {
                            Text("Descansar 2h")
                        }
                    }
                }
            }
        }
    }
}

private fun formatRemaining(millis: Long): String {
    val safe = millis.coerceAtLeast(0L)
    val hours = TimeUnit.MILLISECONDS.toHours(safe)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(safe) % 60
    return "%02d:%02d".format(hours, minutes)
}
