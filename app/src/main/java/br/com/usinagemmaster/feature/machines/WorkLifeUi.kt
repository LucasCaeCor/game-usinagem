package br.com.usinagemmaster.feature.machines

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkLifeViewModel @Inject constructor(
    private val repository: WorkLifeRepository,
) : ViewModel() {
    val state: StateFlow<WorkLifeState> =
        repository.state.stateIn(viewModelScope, SharingStarted.Eagerly, WorkLifeState())

    fun setMode(mode: FactoryScheduleMode) = viewModelScope.launch { repository.setMode(mode) }
    fun setAutoRest(enabled: Boolean) = viewModelScope.launch { repository.setAutoRest(enabled) }
    fun rest(id: String) = viewModelScope.launch { repository.sendToBreak(id) }
    fun returnToWork(id: String) = viewModelScope.launch { repository.returnFromBreak(id) }
}

@Composable
fun WorkLifeCard(
    employees: List<EmployeeEntity>,
    vm: WorkLifeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val now = System.currentTimeMillis()
    val ids = employees.map { it.id } + WorkLifeRepository.PLAYER_ID
    val average = state.averageExhaustion(ids)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF172128)),
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
                        color = Color(0xFFD4DEE3),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.mode == FactoryScheduleMode.CONTINUOUS_24H) {
                    Text("$average%", color = Color.White, fontWeight = FontWeight.Black)
                }
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

            if (state.mode == FactoryScheduleMode.SHIFT_12H) {
                Text(
                    "07:00–19:00: produção e contratos contam tempo. Depois das 19:00 a equipe vai para casa, descansa e o prazo dos contratos fica pausado.",
                    color = Color(0xFFD4DEE3),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                LinearProgressIndicator(
                    progress = average / 100f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "24h: a fábrica nunca fecha. Quanto maior a exaustão, menor a produtividade. Use a Copa ou o descanso automático.",
                    color = Color(0xFFD4DEE3),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun WorkLifeCopaDialog(
    employees: List<EmployeeEntity>,
    state: WorkLifeState,
    onDismiss: () -> Unit,
    onRest: (String) -> Unit,
    onReturn: (String) -> Unit,
    onAutoRest: (Boolean) -> Unit,
) {
    val now = System.currentTimeMillis()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("☕ Copa e descanso", color = Color.White, fontWeight = FontWeight.Black)
                Text(
                    if (state.mode == FactoryScheduleMode.CONTINUOUS_24H)
                        "Recupere a equipe sem fechar a fábrica"
                    else
                        "No turno 12h a equipe também recupera energia em casa",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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

                if (state.mode == FactoryScheduleMode.CONTINUOUS_24H) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Descanso automático", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Manda para a Copa ao chegar em 88% de exaustão.", style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(checked = state.autoRest, onCheckedChange = onAutoRest)
                    }
                }

                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 390.dp),
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
            TextButton(onClick = onDismiss) { Text("Voltar à fábrica", color = Color.White) }
        },
    )
}

@Composable
private fun Furniture(icon: String, label: String, modifier: Modifier = Modifier) {
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
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF1A252B)),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    if (resting) "☕ DESCANSANDO" else "$exhaustion%",
                    color = if (resting) Color(0xFF7EE2A8) else Color.White,
                    fontWeight = FontWeight.Black,
                )
            }
            LinearProgressIndicator(
                progress = exhaustion / 100f,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${state.exhaustionLabel(id)} • eficiência ${(state.efficiency(id) * 100).toInt()}%",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
                if (state.mode == FactoryScheduleMode.CONTINUOUS_24H) {
                    if (resting) {
                        TextButton(onClick = { onReturn(id) }) { Text("Voltar") }
                    } else {
                        FilledTonalButton(onClick = { onRest(id) }) { Text("Descansar 2h") }
                    }
                }
            }
        }
    }
}
