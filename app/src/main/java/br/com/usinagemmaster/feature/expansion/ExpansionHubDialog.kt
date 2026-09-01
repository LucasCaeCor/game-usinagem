package br.com.usinagemmaster.feature.expansion

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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

private enum class ExpansionTab(val label: String) {
    GACHA("Roleta"), COMPANY("Empresa"), SKILLS("Skills"), TOOLS("Ferramentas"), CHARACTER("Personagem"), ACCOUNT("Conta")
}

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
                title = { Column { Text("Centro de Evolução", fontWeight = FontWeight.Bold); Text("Nível ${state.companyLevel}", style = MaterialTheme.typography.labelMedium) } },
                navigationIcon = { TextButton(onClick = onDismiss) { Text("Voltar") } },
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpansionTab.entries.forEach { item ->
                    FilterChip(selected = tab == item, onClick = { tab = item }, label = { Text(item.label) })
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
                ExpansionTab.ACCOUNT -> AccountTab(state, vm)
            }
        }
    }
}

@Composable
private fun GachaTab(state: ExpansionUiState, vm: ExpansionViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ElevatedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Roleta Industrial", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Fichas: ${state.expansion.gachaTickets} • Pity épico ${state.expansion.pityEpic}/30 • lendário ${state.expansion.pityLegendary}/80")
                Text("Pode vir personagem, skin, ferramenta rara, máquina premium ou ficha bônus. Itens top têm chance bem menor.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::roll, enabled = !state.busy && state.expansion.gachaTickets > 0) { Text("Girar") }
                    OutlinedButton(onClick = vm::claimDailyTicket, enabled = !state.busy) { Text("Ficha diária") }
                }
            } }
        }
        state.lastReward?.let { reward ->
            item { RewardCard(reward) }
        }
        item {
            Text("Probabilidades base", fontWeight = FontWeight.Bold)
            Text("Lendário ~0,8% • épico+ ~4,5% • máquina premium ~6% • personagens ~12% • skins ~18% • ferramentas ~40% • restante fichas. Pity garante épico no 30º e lendário no 80º giro.")
        }
        item { Text("Personagens possíveis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(ExpansionCatalog.gachaCharacters) { character ->
            ListItem(headlineContent = { Text("${character.rarity.label} • ${character.name}") }, supportingContent = { Text(character.description) })
        }
        item { Text("Máquinas possíveis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(ExpansionCatalog.premiumMachines) { machine -> PremiumMachineCard(machine, state, vm, showBuy = false) }
    }
}

@Composable
private fun RewardCard(reward: GachaReward) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(18.dp)) {
            Text(reward.rarity.label.uppercase(), style = MaterialTheme.typography.labelLarge)
            Text(reward.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
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
    ElevatedCard { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${machine.rarity.label} • ${machine.name}", fontWeight = FontWeight.Bold)
        Text(machine.description, style = MaterialTheme.typography.bodySmall)
        Text("Nível ${machine.minLevel} • ${money(machine.priceCents)}", style = MaterialTheme.typography.labelMedium)
        if (owned) Text("INSTALADA • bônus permanente", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        if (showBuy && !owned) {
            Button(onClick = { vm.buyPremiumMachine(machine.id) }, enabled = !state.busy && state.companyLevel >= machine.minLevel && state.cashCents >= machine.priceCents) {
                Text(if (state.companyLevel < machine.minLevel) "Bloqueada" else "Comprar")
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
            ElevatedCard { Column(Modifier.padding(14.dp)) {
                Text("${skin.rarity.label} • ${skin.name}", fontWeight = FontWeight.Bold)
                Text(skin.description, style = MaterialTheme.typography.bodySmall)
                if (skin.gachaOnly && skin.id !in state.expansion.ownedSkins) Text("Obtida na roleta • depois exige nível ${skin.minLevel}", style = MaterialTheme.typography.labelSmall)
                Button(onClick = { vm.equipSkin(skin.id) }, enabled = obtained && unlockedByLevel && state.expansion.equippedSkin != skin.id) {
                    Text(if (!obtained) "Falta obter na roleta" else if (!unlockedByLevel) "Libera nível ${skin.minLevel}" else if (state.expansion.equippedSkin == skin.id) "Equipada" else "Equipar")
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
            ElevatedCard { Column(Modifier.padding(14.dp)) {
                Text("${character.rarity.label} • ${character.name}", fontWeight = FontWeight.Bold)
                Text(character.description, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { vm.equipCharacter(character.id) }, enabled = owned && state.companyLevel >= character.minLevel && state.expansion.equippedCharacter != character.id) {
                    Text(if (!owned) "Falta obter na roleta" else if (state.companyLevel < character.minLevel) "Libera nível ${character.minLevel}" else if (state.expansion.equippedCharacter == character.id) "Ativo" else "Ativar")
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
            Text("Conta e sincronização social", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("O jogo continua offline. O login Google é usado para o mercado conectado do personagem.")
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
                    }) { Text("Entrar com Google") }
                } else {
                    Text(state.accountName ?: "Jogador", fontWeight = FontWeight.Bold)
                    Text(state.accountEmail)
                    OutlinedButton(onClick = { GoogleAuthBridge.signOut(); vm.refreshAccount("Conta desconectada") }) { Text("Sair") }
                }
            } }
        }
        item {
            Text("Configuração necessária", fontWeight = FontWeight.Bold)
            Text("No Firebase: habilite Authentication > Google, cadastre o SHA-1/SHA-256 do app e baixe um google-services.json atualizado para app/google-services.json.")
        }
    }
}

private fun money(cents: Long): String = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(cents / 100.0)
private fun dateTime(millis: Long): String = java.text.SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(java.util.Date(millis))
