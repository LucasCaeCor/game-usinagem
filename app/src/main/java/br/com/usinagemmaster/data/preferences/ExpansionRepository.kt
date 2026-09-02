package br.com.usinagemmaster.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import br.com.usinagemmaster.data.local.dao.CompanyDao
import br.com.usinagemmaster.data.local.dao.FinanceDao
import br.com.usinagemmaster.data.local.entity.FinancialTransactionEntity
import br.com.usinagemmaster.domain.expansion.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

private val Context.expansionDataStore by preferencesDataStore(name = "expansion_progress_v1")

@Singleton
class ExpansionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val companyDao: CompanyDao,
    private val financeDao: FinanceDao,
) {
    private object Keys {
        val specialty = stringPreferencesKey("specialty")
        val companySkills = stringSetPreferencesKey("company_skills")
        val playerSkills = stringSetPreferencesKey("player_skills")
        val tickets = intPreferencesKey("gacha_tickets")
        val pityEpic = intPreferencesKey("pity_epic")
        val pityLegendary = intPreferencesKey("pity_legendary")
        val playerXp = longPreferencesKey("player_xp")
        val claimedRentalXpIds = stringSetPreferencesKey("claimed_rental_xp_ids")
        val ownedSkins = stringSetPreferencesKey("owned_skins")
        val equippedSkin = stringPreferencesKey("equipped_skin")
        val ownedCharacters = stringSetPreferencesKey("owned_characters")
        val equippedCharacter = stringPreferencesKey("equipped_character")
        val tools = stringSetPreferencesKey("tools")
        val contractTools = stringSetPreferencesKey("contract_tools")
        val premiumMachines = stringSetPreferencesKey("premium_machines")
        val lastDailyTicketDay = longPreferencesKey("last_daily_ticket_day")
        val remoteOwnerUid = stringPreferencesKey("remote_owner_uid")
        val remoteName = stringPreferencesKey("remote_name")
        val remoteBoost = intPreferencesKey("remote_boost")
        val remoteEndsAt = longPreferencesKey("remote_ends_at")
    }

    val state: Flow<ExpansionState> = context.expansionDataStore.data.map(::decode)

    suspend fun snapshot(): ExpansionState = state.first()

    suspend fun chooseSpecialty(code: String, companyLevel: Int) {
        val definition = CompanySpecialty.entries.firstOrNull { it.code == code } ?: error("Especialidade inválida")
        require(companyLevel >= definition.minLevel) { "Essa especialidade libera no nível ${definition.minLevel}" }
        context.expansionDataStore.edit { it[Keys.specialty] = code }
    }

    suspend fun unlockCompanySkill(id: String, companyLevel: Int) {
        val state = snapshot()
        val def = ExpansionCatalog.companySkills.firstOrNull { it.id == id } ?: error("Skill inválida")
        require(id !in state.companySkills) { "Skill já aprendida" }
        require(state.companySkillPoints(companyLevel) > 0) { "Sem pontos de skill da empresa" }
        require(ExpansionCatalog.canUnlock(def, companyLevel, state.companySkills)) { "Pré-requisito ou nível insuficiente" }
        context.expansionDataStore.edit { prefs -> prefs[Keys.companySkills] = (prefs[Keys.companySkills] ?: emptySet()) + id }
    }

    suspend fun unlockPlayerSkill(id: String, companyLevel: Int) {
        val state = snapshot()
        val def = ExpansionCatalog.playerSkills.firstOrNull { it.id == id } ?: error("Skill inválida")
        require(id !in state.playerSkills) { "Skill já aprendida" }
        require(state.playerSkillPoints(companyLevel) > 0) { "Sem pontos de skill do personagem" }
        require(ExpansionCatalog.canUnlock(def, state.playerLevel(), state.playerSkills)) { "Pré-requisito ou nível do personagem insuficiente" }
        context.expansionDataStore.edit { prefs -> prefs[Keys.playerSkills] = (prefs[Keys.playerSkills] ?: emptySet()) + id }
        // V7_PLAYER_XP_RESEARCH
        addPlayerXp(ExpansionProgression.characterXpForResearch())
    }

    suspend fun equipSkin(id: String, companyLevel: Int) {
        val state = snapshot()
        val skin = ExpansionCatalog.skins.firstOrNull { it.id == id } ?: error("Skin inválida")
        val levelUnlocked = companyLevel >= skin.minLevel
        require(levelUnlocked) { "Essa skin exige nível ${skin.minLevel}" }
        require(!skin.gachaOnly || id in state.ownedSkins) { "Essa skin precisa ser obtida na roleta" }
        context.expansionDataStore.edit { prefs ->
            prefs[Keys.ownedSkins] = (prefs[Keys.ownedSkins] ?: emptySet()) + id
            prefs[Keys.equippedSkin] = id
        }
    }

    suspend fun equipCharacter(id: String, companyLevel: Int) {
        val state = snapshot()
        val character = ExpansionCatalog.gachaCharacters.firstOrNull { it.id == id } ?: error("Personagem inválido")
        require(id in state.ownedCharacters) { "Personagem ainda não obtido na roleta" }
        require(companyLevel >= character.minLevel) { "Esse personagem exige nível ${character.minLevel}" }
        context.expansionDataStore.edit { prefs -> prefs[Keys.equippedCharacter] = id }
    }

    suspend fun claimDailyTicket(): Int {
        val day = System.currentTimeMillis() / 86_400_000L
        var total = 0
        context.expansionDataStore.edit { prefs ->
            val last = prefs[Keys.lastDailyTicketDay] ?: -1L
            require(last != day) { "A ficha diária de hoje já foi coletada" }
            total = (prefs[Keys.tickets] ?: 5) + 1
            prefs[Keys.tickets] = total
            prefs[Keys.lastDailyTicketDay] = day
        }
        return total
    }

    suspend fun rollGacha(companyLevel: Int): GachaReward {
        var result: GachaReward? = null
        context.expansionDataStore.edit { prefs ->
            val tickets = prefs[Keys.tickets] ?: 5
            require(tickets > 0) { "Você não tem fichas da roleta" }

            val ownedCharacters = prefs[Keys.ownedCharacters] ?: emptySet()
            val ownedSkins = prefs[Keys.ownedSkins] ?: emptySet()
            val ownedMachines = prefs[Keys.premiumMachines] ?: emptySet()

            val epicPity = (prefs[Keys.pityEpic] ?: 0) + 1
            val legendaryPity = (prefs[Keys.pityLegendary] ?: 0) + 1
            val forcedLegendary = legendaryPity >= 80
            val forcedEpic = epicPity >= 30
            val roll = Random.nextInt(10_000)

            val reward = when {
                forcedLegendary -> legendaryReward(companyLevel, ownedSkins, ownedMachines)
                forcedEpic -> epicOrBetterReward(companyLevel, ownedSkins, ownedMachines)
                roll < 80 -> legendaryReward(companyLevel, ownedSkins, ownedMachines)
                roll < 450 -> epicOrBetterReward(companyLevel, ownedSkins, ownedMachines)
                roll < 1_250 -> randomPremiumMachine(companyLevel, ownedMachines)
                roll < 3_050 -> randomCharacter(companyLevel, ownedCharacters)
                    ?: randomTool(Rarity.RARE)
                roll < 5_250 -> randomSkin(companyLevel, ownedSkins)
                roll < 7_650 -> randomTool(Rarity.RARE)
                else -> randomTool(Rarity.COMMON)
            }

            applyReward(prefs, reward)
            prefs[Keys.tickets] = tickets - 1

            if (reward.rarity == Rarity.LEGENDARY) {
                prefs[Keys.pityLegendary] = 0
                prefs[Keys.pityEpic] = 0
            } else if (reward.rarity == Rarity.EPIC) {
                prefs[Keys.pityEpic] = 0
                prefs[Keys.pityLegendary] = legendaryPity
            } else {
                prefs[Keys.pityEpic] = epicPity
                prefs[Keys.pityLegendary] = legendaryPity
            }

            result = reward
        }
        return result ?: error("Falha ao sortear recompensa")
    }

    suspend fun addTickets(amount: Int): Int {
        require(amount > 0) { "Quantidade de fichas inválida" }
        var total = 0
        context.expansionDataStore.edit { prefs ->
            total = (prefs[Keys.tickets] ?: 5) + amount
            prefs[Keys.tickets] = total
        }
        return total
    }

    suspend fun bindTool(contractId: String, toolId: String?) {
        context.expansionDataStore.edit { prefs ->
            val inventory = parseCounts(prefs[Keys.tools] ?: emptySet())
            val bindings = parseBindings(prefs[Keys.contractTools] ?: emptySet()).toMutableMap()
            if (toolId == null) {
                bindings.remove(contractId)
            } else {
                require(ExpansionCatalog.tools.any { it.id == toolId }) { "Ferramenta inválida" }
                val alreadyBound = bindings.count { it.value == toolId && it.key != contractId }
                require((inventory[toolId] ?: 0) > alreadyBound) { "Todas as unidades dessa ferramenta já estão reservadas" }
                bindings[contractId] = toolId
            }
            prefs[Keys.contractTools] = encodeBindings(bindings)
        }
    }

    suspend fun consumeBoundTool(contractId: String) {
        context.expansionDataStore.edit { prefs ->
            val bindings = parseBindings(prefs[Keys.contractTools] ?: emptySet()).toMutableMap()
            val toolId = bindings.remove(contractId) ?: return@edit
            val counts = parseCounts(prefs[Keys.tools] ?: emptySet()).toMutableMap()
            counts[toolId] = ((counts[toolId] ?: 0) - 1).coerceAtLeast(0)
            if (counts[toolId] == 0) counts.remove(toolId)
            prefs[Keys.tools] = encodeCounts(counts)
            prefs[Keys.contractTools] = encodeBindings(bindings)
        }
    }

    suspend fun buyPremiumMachine(id: String, companyLevel: Int) {
        val def = ExpansionCatalog.premiumMachines.firstOrNull { it.id == id } ?: error("Máquina premium inválida")
        require(companyLevel >= def.minLevel) { "Essa máquina libera no nível ${def.minLevel}" }
        val state = snapshot()
        require(id !in state.premiumMachines) { "Você já possui essa máquina premium" }
        val company = companyDao.get() ?: error("Empresa não inicializada")
        require(company.cashCents >= def.priceCents) { "Caixa insuficiente para essa máquina premium" }

        // Primeiro registra a posse; se uma falha extrema ocorrer depois, o jogador nunca perde dinheiro sem receber o item.
        context.expansionDataStore.edit { prefs ->
            prefs[Keys.premiumMachines] = (prefs[Keys.premiumMachines] ?: emptySet()) + id
        }
        try {
            companyDao.upsert(company.copy(cashCents = company.cashCents - def.priceCents))
            financeDao.insert(
                FinancialTransactionEntity(
                    UUID.randomUUID().toString(),
                    "EXPENSE",
                    "MACHINE",
                    def.priceCents,
                    "Máquina premium: ${def.name}",
                    System.currentTimeMillis(),
                )
            )
        } catch (error: Throwable) {
            context.expansionDataStore.edit { prefs ->
                prefs[Keys.premiumMachines] = (prefs[Keys.premiumMachines] ?: emptySet()) - id
            }
            throw error
        }
    }

    suspend fun buyPremiumCharacter(id: String, companyLevel: Int) {
        val def = ExpansionCatalog.gachaCharacters.firstOrNull { it.id == id }
            ?: error("Personagem premium inválido")
        require(ExpansionCatalog.isPremiumCharacter(def)) {
            "Esse personagem pertence à roleta comum"
        }
        require(companyLevel >= def.minLevel) {
            "Esse personagem libera no nível ${def.minLevel}"
        }

        val state = snapshot()
        require(id !in state.ownedCharacters) {
            "Você já possui esse personagem premium"
        }

        val price = ExpansionCatalog.premiumCharacterPriceCents(id)
        require(price > 0L) { "Preço do personagem não configurado" }

        val company = companyDao.get() ?: error("Empresa não inicializada")
        require(company.cashCents >= price) {
            "Caixa insuficiente para contratar esse especialista permanentemente"
        }

        context.expansionDataStore.edit { prefs ->
            prefs[Keys.ownedCharacters] = (prefs[Keys.ownedCharacters] ?: emptySet()) + id
        }

        try {
            companyDao.upsert(company.copy(cashCents = company.cashCents - price))
            financeDao.insert(
                FinancialTransactionEntity(
                    UUID.randomUUID().toString(),
                    "EXPENSE",
                    "SALARY",
                    price,
                    "Compra de personagem premium: ${def.name}",
                    System.currentTimeMillis(),
                )
            )
        } catch (error: Throwable) {
            context.expansionDataStore.edit { prefs ->
                prefs[Keys.ownedCharacters] = (prefs[Keys.ownedCharacters] ?: emptySet()) - id
            }
            throw error
        }
    }


    suspend fun contractAccess(difficulty: Int, companyLevel: Int): ContractAccess {
        val state = snapshot()
        val gate = ExpansionCatalog.contractGate(difficulty)
        if (companyLevel < gate.minLevel) return ContractAccess(false, "Contrato bloqueado: ${gate.text}")
        val commercialBonus = if ("comercial" in state.companySkills) 1 else 0
        if (state.companySkills.size + commercialBonus < gate.minCompanySkills) {
            return ContractAccess(false, "Contrato exige ${gate.minCompanySkills} skills da empresa")
        }
        if (gate.requiresSpecialty && state.specialty == CompanySpecialty.GENERALIST.code) {
            return ContractAccess(false, "Defina uma especialidade da empresa para contratos nível máximo")
        }
        return ContractAccess(true)
    }

            suspend fun ensurePlayerXpBaseline(companyLevel: Int) {
        val current = snapshot()
        if (current.playerXp > 0L || companyLevel <= 1) return
        val targetPlayerLevel = (1 + (companyLevel - 1) / 2).coerceIn(1, 20)
        val baseline = ExpansionProgression.totalXpAtStartOfPlayerLevel(targetPlayerLevel)
        if (baseline > 0L) context.expansionDataStore.edit { prefs ->
            if ((prefs[Keys.playerXp] ?: 0L) <= 0L) prefs[Keys.playerXp] = baseline
        }
    }

    suspend fun claimRentalXpReward(rentalId: String, amount: Long): Boolean {
        if (rentalId.isBlank() || amount <= 0L) return false
        var claimed = false
        context.expansionDataStore.edit { prefs ->
            val ids = prefs[Keys.claimedRentalXpIds] ?: emptySet()
            if (rentalId !in ids) {
                prefs[Keys.claimedRentalXpIds] = ids + rentalId
                prefs[Keys.playerXp] = ((prefs[Keys.playerXp] ?: 0L) + amount).coerceAtLeast(0L)
                claimed = true
            }
        }
        return claimed
    }

suspend fun addPlayerXp(amount: Long): Long {
        if (amount <= 0L) return snapshot().playerXp
        var total = 0L
        context.expansionDataStore.edit { prefs ->
            total = ((prefs[Keys.playerXp] ?: 0L) + amount).coerceAtLeast(0L)
            prefs[Keys.playerXp] = total
        }
        return total
    }

suspend fun activateRemoteHire(ownerUid: String, name: String, boostPct: Int, endsAt: Long) {
        context.expansionDataStore.edit { prefs ->
            prefs[Keys.remoteOwnerUid] = ownerUid
            prefs[Keys.remoteName] = name
            prefs[Keys.remoteBoost] = boostPct.coerceIn(0, 25)
            prefs[Keys.remoteEndsAt] = endsAt
        }
    }

    suspend fun clearExpiredRemoteHire(now: Long = System.currentTimeMillis()) {
        val current = snapshot()
        if (current.remoteHireEndsAt <= 0L || current.remoteHireEndsAt > now) return
        context.expansionDataStore.edit { prefs ->
            prefs.remove(Keys.remoteOwnerUid)
            prefs.remove(Keys.remoteName)
            prefs.remove(Keys.remoteBoost)
            prefs.remove(Keys.remoteEndsAt)
        }
    }

    private fun decode(prefs: Preferences): ExpansionState = ExpansionState(
        specialty = prefs[Keys.specialty] ?: CompanySpecialty.GENERALIST.code,
        companySkills = prefs[Keys.companySkills] ?: emptySet(),
        playerSkills = prefs[Keys.playerSkills] ?: emptySet(),
        gachaTickets = prefs[Keys.tickets] ?: 5,
        pityEpic = prefs[Keys.pityEpic] ?: 0,
        pityLegendary = prefs[Keys.pityLegendary] ?: 0,
        playerXp = prefs[Keys.playerXp] ?: 0L,
        claimedRentalXpIds = prefs[Keys.claimedRentalXpIds] ?: emptySet(),
        ownedSkins = (prefs[Keys.ownedSkins] ?: emptySet()) + "operador_padrao",
        equippedSkin = prefs[Keys.equippedSkin] ?: "operador_padrao",
        ownedCharacters = prefs[Keys.ownedCharacters] ?: emptySet(),
        equippedCharacter = prefs[Keys.equippedCharacter],
        tools = parseCounts(prefs[Keys.tools] ?: setOf("broca_madeira=2", "ferramenta_soldada=2", "fresa_hss=1")),
        contractTools = parseBindings(prefs[Keys.contractTools] ?: emptySet()),
        premiumMachines = prefs[Keys.premiumMachines] ?: emptySet(),
        lastDailyTicketDay = prefs[Keys.lastDailyTicketDay] ?: -1L,
        remoteHireOwnerUid = prefs[Keys.remoteOwnerUid],
        remoteHireName = prefs[Keys.remoteName],
        remoteHireBoostPct = prefs[Keys.remoteBoost] ?: 0,
        remoteHireEndsAt = prefs[Keys.remoteEndsAt] ?: 0L,
    )

    private fun randomSkin(level: Int, owned: Set<String>): GachaReward {
        val all = ExpansionCatalog.skins.filter { it.gachaOnly && it.id !in owned }
        val pool = all.filter { it.minLevel <= level + 5 }
        val def = (pool.ifEmpty { all }).randomOrNull()
            ?: return randomTool(Rarity.RARE)
        return GachaReward("skin", def.id, def.name, def.rarity)
    }

    private fun randomCharacter(level: Int, owned: Set<String>): GachaReward? {
        val standard = ExpansionCatalog.gachaCharacters.filter {
            !ExpansionCatalog.isPremiumCharacter(it) && it.id !in owned
        }
        val pool = standard.filter { it.minLevel <= level + 4 }
        val def = (pool.ifEmpty { standard }).randomOrNull() ?: return null
        return GachaReward("character", def.id, def.name, def.rarity)
    }

    private fun randomPremiumMachine(level: Int, owned: Set<String>): GachaReward {
        val all = ExpansionCatalog.premiumMachines.filter { it.id !in owned }
        val pool = all.filter { it.minLevel <= level + 4 }
        val def = (pool.ifEmpty { all }).randomOrNull()
            ?: return randomTool(Rarity.RARE)
        return GachaReward("machine", def.id, def.name, def.rarity)
    }

    private fun randomTool(minRarity: Rarity): GachaReward {
        val allowed = ExpansionCatalog.tools.filter { it.rarity.ordinal >= minRarity.ordinal }
        val def = allowed.random()
        return GachaReward("tool", def.id, def.name, def.rarity)
    }

    private fun epicOrBetterReward(
        level: Int,
        ownedSkins: Set<String>,
        ownedMachines: Set<String>,
    ): GachaReward {
        if (Random.nextInt(100) < 18) return legendaryReward(level, ownedSkins, ownedMachines)

        val candidates = buildList {
            addAll(
                ExpansionCatalog.skins
                    .filter { it.gachaOnly && it.rarity == Rarity.EPIC && it.id !in ownedSkins }
                    .map { GachaReward("skin", it.id, it.name, it.rarity) }
            )
            addAll(
                ExpansionCatalog.tools
                    .filter { it.rarity == Rarity.EPIC }
                    .map { GachaReward("tool", it.id, it.name, it.rarity) }
            )
            addAll(
                ExpansionCatalog.premiumMachines
                    .filter { it.rarity == Rarity.EPIC && it.minLevel <= level + 5 && it.id !in ownedMachines }
                    .map { GachaReward("machine", it.id, it.name, it.rarity) }
            )
        }

        return candidates.randomOrNull() ?: randomTool(Rarity.RARE)
    }

    private fun legendaryReward(
        level: Int,
        ownedSkins: Set<String>,
        ownedMachines: Set<String>,
    ): GachaReward {
        val candidates = buildList {
            addAll(
                ExpansionCatalog.skins
                    .filter { it.gachaOnly && it.rarity == Rarity.LEGENDARY && it.id !in ownedSkins }
                    .map { GachaReward("skin", it.id, it.name, it.rarity) }
            )
            addAll(
                ExpansionCatalog.tools
                    .filter { it.rarity == Rarity.LEGENDARY }
                    .map { GachaReward("tool", it.id, it.name, it.rarity) }
            )
            addAll(
                ExpansionCatalog.premiumMachines
                    .filter { it.rarity == Rarity.LEGENDARY && it.minLevel <= level + 6 && it.id !in ownedMachines }
                    .map { GachaReward("machine", it.id, it.name, it.rarity) }
            )
        }
        return candidates.randomOrNull() ?: randomTool(Rarity.EPIC)
    }

    private fun applyReward(prefs: MutablePreferences, reward: GachaReward) {
        when (reward.type) {
            "skin" -> {
                val current = prefs[Keys.ownedSkins] ?: emptySet()
                reward.id?.let { prefs[Keys.ownedSkins] = current + it }
            }
            "machine" -> {
                val current = prefs[Keys.premiumMachines] ?: emptySet()
                reward.id?.let { prefs[Keys.premiumMachines] = current + it }
            }
            "character" -> {
                val current = prefs[Keys.ownedCharacters] ?: emptySet()
                reward.id?.let { prefs[Keys.ownedCharacters] = current + it }
            }
            "tool" -> {
                val counts = parseCounts(prefs[Keys.tools] ?: emptySet()).toMutableMap()
                val id = requireNotNull(reward.id)
                counts[id] = (counts[id] ?: 0) + 1
                prefs[Keys.tools] = encodeCounts(counts)
            }
        }
    }

    private fun parseCounts(values: Set<String>): Map<String, Int> = values.mapNotNull { value ->
        val split = value.split('=', limit = 2)
        if (split.size != 2) null else split[1].toIntOrNull()?.let { split[0] to it.coerceAtLeast(0) }
    }.toMap()

    private fun encodeCounts(values: Map<String, Int>): Set<String> = values.filterValues { it > 0 }.map { "${it.key}=${it.value}" }.toSet()

    private fun parseBindings(values: Set<String>): Map<String, String> = values.mapNotNull { value ->
        val split = value.split('|', limit = 2)
        if (split.size == 2) split[0] to split[1] else null
    }.toMap()

    private fun encodeBindings(values: Map<String, String>): Set<String> = values.map { "${it.key}|${it.value}" }.toSet()
}
