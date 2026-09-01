package br.com.usinagemmaster.feature.employees

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import br.com.usinagemmaster.core.designsystem.component.ScreenHeader
import br.com.usinagemmaster.core.designsystem.component.StatusPill
import br.com.usinagemmaster.core.util.Formatters
import br.com.usinagemmaster.domain.catalog.LegendaryEmployeeCatalog
import br.com.usinagemmaster.data.local.entity.LegendaryMissionEntity

@Composable
fun EmployeesScreen(vm: EmployeesViewModel = hiltViewModel()) {
    val employees by vm.employees.collectAsState()
    val dashboard by vm.dashboard.collectAsState()
    val missions by vm.legendaryMissions.collectAsState()
    val message by vm.message.collectAsState()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snack.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val hiredLegendaryCodes = remember(employees) {
        employees.mapNotNull { it.legendaryCode }.toSet()
    }
    val unlockedLegendaryCount = LegendaryEmployeeCatalog.all.count {
        it.unlockLevel <= dashboard.companyLevel && it.code !in hiredLegendaryCodes
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = vm::hire,
                icon = { Icon(Icons.Default.PersonAdd, null) },
                text = { Text("Contratar comum") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ScreenHeader(
                    "Funcionários",
                    "Equipe visual, lendários únicos, moral, missão e alocação no chão de fábrica"
                )
            }

            item {
                LegendaryRecruitmentCard(
                    companyLevel = dashboard.companyLevel,
                    hiredCodes = hiredLegendaryCodes,
                    availableCount = unlockedLegendaryCount,
                    onHireLegendary = vm::hireLegendary
                )
            }

            if (missions.isNotEmpty()) {
                item {
                    LegendaryMissionsCard(
                        missions = missions,
                        hiredCodes = hiredLegendaryCodes,
                        onClaim = vm::claimLegendaryMission
                    )
                }
            }

            if (employees.isEmpty()) {
                item {
                    Text(
                        "Nenhum funcionário contratado.",
                        Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }

            items(employees, key = { it.id }) { employee ->
                val legendary = LegendaryEmployeeCatalog.byCode(employee.legendaryCode)
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    colors = if (employee.isLegendary) {
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .62f)
                        )
                    } else {
                        CardDefaults.cardColors()
                    }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                EmployeePortrait(
                                    legendaryCode = employee.legendaryCode,
                                    specialty = employee.specialty,
                                    name = employee.name,
                                    size = 60.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (employee.isLegendary) {
                                            Icon(
                                                Icons.Default.WorkspacePremium,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(employee.name, fontWeight = FontWeight.ExtraBold)
                                    }
                                    Text(
                                        "${employee.specialty} • Nível ${employee.skillLevel}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            StatusPill(
                                if (employee.assignedMachineId == null) "DISPONÍVEL" else "ALOCADO",
                                employee.assignedMachineId != null
                            )
                        }

                        if (employee.isLegendary) {
                            Spacer(Modifier.height(8.dp))
                            AssistChip(
                                onClick = {},
                                leadingIcon = { Icon(Icons.Default.Star, null, Modifier.size(16.dp)) },
                                label = { Text("LENDÁRIO • ${employee.trait}") }
                            )
                            legendary?.let {
                                Text(
                                    it.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { employee.morale / 100f },
                            Modifier.fillMaxWidth()
                        )
                        Text(
                            "Moral ${employee.morale}% • XP ${employee.experience}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Salário ${Formatters.money(employee.salaryCents)} • ${employee.trait}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { vm.fire(employee) }) { Text("Demitir") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendaryMissionsCard(
    missions: List<LegendaryMissionEntity>,
    hiredCodes: Set<String>,
    onClaim: (LegendaryMissionEntity) -> Unit
) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .62f)
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Missões dos lendários", fontWeight = FontWeight.Bold)
                    Text("Desafios pessoais que avançam com a produção real.", style = MaterialTheme.typography.bodySmall)
                }
            }

            missions.forEach { mission ->
                val profile = LegendaryEmployeeCatalog.byCode(mission.legendaryCode)
                val active = mission.legendaryCode in hiredCodes
                val complete = mission.progress >= mission.target
                val progress = if (mission.target <= 0) 0f else (mission.progress.toFloat() / mission.target).coerceIn(0f, 1f)

                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("★ ${profile?.name ?: mission.legendaryCode}", fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                mission.claimed -> "COLETADA"
                                complete -> "CONCLUÍDA"
                                active -> "ATIVA"
                                else -> "PAUSADA"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (complete && !mission.claimed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(mission.title, style = MaterialTheme.typography.labelLarge)
                    Text(mission.description, style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${mission.progress}/${mission.target} min", style = MaterialTheme.typography.bodySmall)
                        if (complete && !mission.claimed) {
                            Button(onClick = { onClaim(mission) }) {
                                Text("Coletar ${Formatters.money(mission.rewardCents)}")
                            }
                        } else {
                            Text("Prêmio ${Formatters.money(mission.rewardCents)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun LegendaryRecruitmentCard(
    companyLevel: Int,
    hiredCodes: Set<String>,
    availableCount: Int,
    onHireLegendary: () -> Unit
) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f)
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WorkspacePremium, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Funcionários lendários", fontWeight = FontWeight.Bold)
                    Text(
                        "Personagens únicos com bônus reais de produção e suporte.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Button(
                onClick = onHireLegendary,
                enabled = availableCount > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Star, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (availableCount > 0) "Buscar lendário ($availableCount disponíveis)"
                    else "Nenhum lendário liberado agora"
                )
            }

            HorizontalDivider()
            Text("Coleção", style = MaterialTheme.typography.labelLarge)

            LegendaryEmployeeCatalog.all.forEach { legendary ->
                val hired = legendary.code in hiredCodes
                val unlocked = legendary.unlockLevel <= companyLevel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (unlocked || hired) {
                        EmployeePortrait(
                            legendaryCode = legendary.code,
                            specialty = legendary.specialty.name,
                            name = legendary.name,
                            size = 42.dp
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            legendary.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (hired) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            "${legendary.trait} • ${legendary.specialty}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        when {
                            hired -> "CONTRATADO"
                            unlocked -> Formatters.money(legendary.salaryCents)
                            else -> "Nível ${legendary.unlockLevel}"
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
