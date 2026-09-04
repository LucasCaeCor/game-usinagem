package br.com.usinagemmaster.feature.gameplay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.usinagemmaster.data.local.entity.EmployeeEntity
import br.com.usinagemmaster.data.local.entity.MachineEntity
import br.com.usinagemmaster.domain.gameplay.*
import br.com.usinagemmaster.domain.model.DashboardStatus

@Composable
fun IndustrialSkillTree(
    career: CareerState,
    companyLevel: Int,
    busy: Boolean,
    onUnlock: (String) -> Unit,
    onPolicy: (ProductionPolicy) -> Unit,
) {
    var branch by remember { mutableStateOf(IndustrialSkillBranch.OPERATION) }
    val nodes = remember(branch) {
        IndustrialSkillCatalog.all.filter { it.branch == branch }.sortedBy { it.tier }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🌳 Árvore Industrial", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("Skills mudam o gameplay. Pontos vêm de feitos na fábrica, nunca de compra.")
                    Text(
                        "${career.availableSkillPoints} ponto(s) • ${career.unlockedSkills.size} aprendidas • ${career.achievements.size} conquistas",
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IndustrialSkillBranch.entries.forEach { item ->
                    FilterChip(
                        selected = branch == item,
                        onClick = { branch = item },
                        label = { Text("${item.icon} ${item.label}") },
                    )
                }
            }
        }

        items(nodes, key = { it.id }) { skill ->
            val owned = career.has(skill.id)
            val missing = skill.prerequisites.filterNot(career::has)
            val canUnlock = !owned && companyLevel >= skill.minCompanyLevel &&
                missing.isEmpty() && career.availableSkillPoints >= skill.cost && !busy

            Card(
                border = if (owned) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("T${skill.tier} • ${skill.name}", fontWeight = FontWeight.Black)
                    Text(skill.description, style = MaterialTheme.typography.bodySmall)
                    Text("Custo ${skill.cost} • fábrica Nv.${skill.minCompanyLevel}", style = MaterialTheme.typography.labelSmall)
                    if (missing.isNotEmpty()) {
                        Text(
                            "Requer: ${missing.mapNotNull(IndustrialSkillCatalog::byId).joinToString { it.name }}",
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    Button(
                        onClick = { onUnlock(skill.id) },
                        enabled = canUnlock,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                owned -> "APRENDIDA"
                                companyLevel < skill.minCompanyLevel -> "LIBERA NO NV.${skill.minCompanyLevel}"
                                missing.isNotEmpty() -> "CONCLUA PRÉ-REQUISITOS"
                                career.availableSkillPoints < skill.cost -> "FALTAM PONTOS"
                                else -> "APRENDER"
                            }
                        )
                    }
                }
            }
        }

        if (career.has("diretor_industrial")) {
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("🏭 Política industrial", fontWeight = FontWeight.Black)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ProductionPolicy.entries.forEach { policy ->
                                FilterChip(
                                    selected = career.productionPolicy == policy,
                                    onClick = { onPolicy(policy) },
                                    label = { Text(policy.label) },
                                    enabled = !busy,
                                )
                            }
                        }
                        Text(career.productionPolicy.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (career.achievements.isNotEmpty()) {
            item {
                Text(
                    "🏆 ${career.achievements.sorted().joinToString(" • ")}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun CareerJourneyCard(
    dashboard: DashboardStatus,
    machines: List<MachineEntity>,
    employees: List<EmployeeEntity>,
    career: CareerState,
) {
    val installed = machines.count { it.installed }
    val stage = when {
        dashboard.companyLevel >= 25 -> 7
        dashboard.companyLevel >= 15 -> 6
        dashboard.companyLevel >= 10 -> 5
        installed >= 6 -> 4
        installed >= 3 -> 3
        employees.isNotEmpty() -> 2
        else -> 1
    }
    val chapters = listOf(
        "A Garagem" to "Você é o primeiro operador: produza, inspecione e entregue.",
        "Sozinho não dá" to "Contrate o primeiro funcionário e delegue o trabalho que você já viveu.",
        "Pequena Usinagem" to "Monte fluxo e elimine gargalos.",
        "Era CNC" to "Domine parâmetros, setups e tolerâncias.",
        "Linha de Produção" to "Balanceie produção, qualidade e prazo.",
        "Indústria" to "Expanda setores e automatize.",
        "Mercado Global" to "Especialize a empresa e dispute contratos de alto prestígio.",
    )
    val current = chapters[stage - 1]
    val next = when {
        career.totalManualOperations == 0 -> "Opere sua primeira máquina manualmente."
        career.shippedBatches == 0 -> "Leve um lote por Q → P → E."
        employees.isEmpty() -> "Use o lucro para contratar seu primeiro funcionário."
        else -> "Complete feitos para ganhar pontos e especializar a fábrica."
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16232A)),
        border = BorderStroke(1.dp, Color(0xFF38545E)),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("JORNADA • CAPÍTULO $stage/7", color = Color(0xFF8EDBE6), fontWeight = FontWeight.Black)
            Text(current.first, color = Color.White, fontWeight = FontWeight.Black)
            Text(current.second, color = Color(0xFFC1D0D5), style = MaterialTheme.typography.bodySmall)
            Text(
                "Próxima meta: $next",
                color = Color(0xFFFFD27D),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
