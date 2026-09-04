package br.com.usinagemmaster.domain.expansion

import kotlin.math.max

/**
 * Sistemas de progressão adicionados sem alterar o schema Room existente.
 * O save dessa expansão fica em DataStore para manter compatibilidade com saves atuais.
 */
data class ProductionModifiers(
    val globalSpeedMultiplier: Double = 1.0,
    val qualityBonus: Int = 0,
    val energyMultiplier: Double = 1.0,
    val turningMultiplier: Double = 1.0,
    val millingMultiplier: Double = 1.0,
    val drillingMultiplier: Double = 1.0,
    val grindingMultiplier: Double = 1.0,
    val weldingMultiplier: Double = 1.0,
    val cncMultiplier: Double = 1.0,
) {
    fun multiplierForMachine(machineType: String): Double {
        val type = machineType.uppercase()
        var value = 1.0
        if ("LATHE" in type || "TURN" in type) value *= turningMultiplier
        if ("MILL" in type || "MACHINING_CENTER" in type) value *= millingMultiplier
        if ("DRILL" in type) value *= drillingMultiplier
        if ("GRINDER" in type || "GRIND" in type) value *= grindingMultiplier
        if ("WELD" in type) value *= weldingMultiplier
        if ("CNC" in type || "MACHINING_CENTER" in type || "EDM" in type) value *= cncMultiplier
        return value
    }
}

data class ToolEffect(
    val speedMultiplier: Double = 1.0,
    val qualityBonus: Int = 0,
    val label: String = "Sem ferramenta especial",
)

data class ContractAccess(val allowed: Boolean, val reason: String = "")

data class ExpansionState(
    val specialty: String = CompanySpecialty.GENERALIST.code,
    val companySkills: Set<String> = emptySet(),
    val playerSkills: Set<String> = emptySet(),
    val gachaTickets: Int = 5,
    val pityEpic: Int = 0,
    val pityLegendary: Int = 0,
    val playerXp: Long = 0L,
    val claimedRentalXpIds: Set<String> = emptySet(),
    val ownedSkins: Set<String> = setOf("operador_padrao"),
    val equippedSkin: String = "operador_padrao",
    val ownedCharacters: Set<String> = emptySet(),
    val equippedCharacter: String? = null,
    val tools: Map<String, Int> = mapOf("broca_madeira" to 2, "ferramenta_soldada" to 2, "fresa_hss" to 1),
    val contractTools: Map<String, String> = emptyMap(),
    val premiumMachines: Set<String> = emptySet(),
    val lastDailyTicketDay: Long = -1L,
    val remoteHireOwnerUid: String? = null,
    val remoteHireName: String? = null,
    val remoteHireBoostPct: Int = 0,
    val remoteHireEndsAt: Long = 0L,
) {
    fun toolEffectForContract(contractId: String): ToolEffect {
        val toolId = contractTools[contractId] ?: return ToolEffect()
        val definition = ExpansionCatalog.tools.firstOrNull { it.id == toolId } ?: return ToolEffect()
        return ToolEffect(definition.speedMultiplier, definition.qualityBonus, definition.name)
    }

    fun productionModifiers(now: Long = System.currentTimeMillis()): ProductionModifiers {
        var speed = 1.0
        var quality = 0
        var energy = 1.0
        var turning = 1.0
        var milling = 1.0
        var drilling = 1.0
        var grinding = 1.0
        var welding = 1.0
        var cnc = 1.0

        when (specialty) {
            CompanySpecialty.TORNEARIA.code -> turning *= 1.14
            CompanySpecialty.CNC_TORNO.code -> { turning *= 1.10; cnc *= 1.16 }
            CompanySpecialty.FRESAGEM.code -> milling *= 1.14
            CompanySpecialty.CNC_FRESAGEM.code -> { milling *= 1.10; cnc *= 1.16 }
            CompanySpecialty.SOLDA.code -> welding *= 1.16
            CompanySpecialty.RETIFICA.code -> grinding *= 1.16
        }

        if ("lean_i" in companySkills) speed += 0.05
        if ("lean_ii" in companySkills) speed += 0.07
        if ("qualidade_celula" in companySkills) quality += 5
        if ("ferramentaria" in companySkills) speed += 0.04
        if ("eficiencia_energetica" in companySkills) energy *= 0.88
        if ("automacao_cnc" in companySkills) cnc *= 1.15
        if ("gemeo_digital" in companySkills) { speed += 0.08; quality += 3 }

        if ("setup_rapido" in playerSkills) speed += 0.04
        if ("metrologia" in playerSkills) quality += 4
        if ("programacao_cnc" in playerSkills) cnc *= 1.08
        if ("lideranca" in playerSkills) speed += 0.03
        if ("mestre_processo" in playerSkills) { speed += 0.06; quality += 4 }

        val skin = ExpansionCatalog.skins.firstOrNull { it.id == equippedSkin }
        if (skin != null) {
            speed += skin.globalSpeedPct / 100.0
            quality += skin.qualityBonus
            turning *= 1.0 + skin.turningPct / 100.0
            milling *= 1.0 + skin.millingPct / 100.0
            cnc *= 1.0 + skin.cncPct / 100.0
        }

        val character = ExpansionCatalog.gachaCharacters.firstOrNull { it.id == equippedCharacter }
        if (character != null && character.id in ownedCharacters) {
            speed += character.globalSpeedPct / 100.0
            quality += character.qualityBonus
            turning *= 1.0 + character.turningPct / 100.0
            milling *= 1.0 + character.millingPct / 100.0
            cnc *= 1.0 + character.cncPct / 100.0
        }

        premiumMachines.mapNotNull { id -> ExpansionCatalog.premiumMachines.firstOrNull { it.id == id } }.forEach { machine ->
            speed += machine.globalSpeedPct / 100.0
            turning *= 1.0 + machine.turningPct / 100.0
            milling *= 1.0 + machine.millingPct / 100.0
            drilling *= 1.0 + machine.drillingPct / 100.0
            grinding *= 1.0 + machine.grindingPct / 100.0
            welding *= 1.0 + machine.weldingPct / 100.0
            cnc *= 1.0 + machine.cncPct / 100.0
            quality += machine.qualityBonus
        }

        if (remoteHireEndsAt > now && remoteHireBoostPct > 0) {
            speed += remoteHireBoostPct.coerceAtMost(25) / 100.0
        }

        return ProductionModifiers(
            globalSpeedMultiplier = speed.coerceAtMost(2.25),
            qualityBonus = quality.coerceAtMost(25),
            energyMultiplier = energy.coerceIn(0.65, 1.0),
            turningMultiplier = turning.coerceAtMost(1.75),
            millingMultiplier = milling.coerceAtMost(1.75),
            drillingMultiplier = drilling.coerceAtMost(1.75),
            grindingMultiplier = grinding.coerceAtMost(1.75),
            weldingMultiplier = welding.coerceAtMost(1.75),
            cncMultiplier = cnc.coerceAtMost(1.85),
        )
    }

    fun companySkillPoints(companyLevel: Int): Int = max(0, companyLevel / 2 - companySkills.size)
    fun playerSkillPoints(companyLevel: Int): Int = max(0, 1 + playerLevel() / 2 + companyLevel / 6 - playerSkills.size)
    fun playerRentalBoostPct(): Int = (4 + playerSkills.size * 2 + playerLevel() / 4 + if ("negociacao" in playerSkills) 3 else 0).coerceAtMost(25)

    fun playerProgress(): XpProgress = ExpansionProgression.player(playerXp)
    fun playerLevel(): Int = playerProgress().level
}

enum class CompanySpecialty(val code: String, val label: String, val minLevel: Int, val description: String) {
    GENERALIST("generalista", "Usinagem geral", 1, "Sem foco exclusivo; flexível em todos os contratos."),
    TORNEARIA("tornearia", "Tornearia", 2, "+14% em tornos mecânicos e CNC."),
    CNC_TORNO("cnc_torno", "CNC • Torno", 5, "+10% em torneamento e +16% em máquinas CNC."),
    FRESAGEM("fresagem", "Fresagem", 3, "+14% em fresadoras e centros de usinagem."),
    CNC_FRESAGEM("cnc_fresagem", "CNC • Centro de usinagem", 6, "+10% em fresagem e +16% em CNC."),
    SOLDA("solda", "Caldeiraria e solda", 4, "+16% em células de soldagem."),
    RETIFICA("retifica", "Retífica e acabamento", 4, "+16% em retificação."),
}

data class SkillDefinition(
    val id: String,
    val name: String,
    val description: String,
    val minLevel: Int,
    val prerequisite: String? = null,
)

data class SkinDefinition(
    val id: String,
    val name: String,
    val minLevel: Int,
    val rarity: Rarity,
    val description: String,
    val globalSpeedPct: Int = 0,
    val qualityBonus: Int = 0,
    val turningPct: Int = 0,
    val millingPct: Int = 0,
    val cncPct: Int = 0,
    val gachaOnly: Boolean = false,
)

data class GachaCharacterDefinition(
    val id: String,
    val name: String,
    val minLevel: Int,
    val rarity: Rarity,
    val description: String,
    val globalSpeedPct: Int = 0,
    val qualityBonus: Int = 0,
    val turningPct: Int = 0,
    val millingPct: Int = 0,
    val cncPct: Int = 0,
)

data class ToolDefinition(
    val id: String,
    val name: String,
    val rarity: Rarity,
    val speedMultiplier: Double,
    val qualityBonus: Int,
    val description: String,
)

data class PremiumMachineDefinition(
    val id: String,
    val name: String,
    val minLevel: Int,
    val priceCents: Long,
    val rarity: Rarity,
    val description: String,
    val globalSpeedPct: Int = 0,
    val qualityBonus: Int = 0,
    val turningPct: Int = 0,
    val millingPct: Int = 0,
    val drillingPct: Int = 0,
    val grindingPct: Int = 0,
    val weldingPct: Int = 0,
    val cncPct: Int = 0,
)

enum class Rarity(val label: String) { COMMON("Comum"), RARE("Raro"), EPIC("Épico"), LEGENDARY("Lendário") }

data class ContractGate(val minLevel: Int, val minCompanySkills: Int, val requiresSpecialty: Boolean, val text: String)

data class GachaReward(val type: String, val id: String?, val title: String, val rarity: Rarity)

object ExpansionCatalog {
    val companySkills = listOf(
        SkillDefinition("lean_i", "Lean I", "+5% de velocidade global.", 2),
        SkillDefinition("lean_ii", "Lean II", "+7% de velocidade adicional.", 5, "lean_i"),
        SkillDefinition("qualidade_celula", "Célula de qualidade", "+5 de qualidade em toda produção.", 3),
        SkillDefinition("ferramentaria", "Ferramentaria interna", "+4% de produtividade e prepara o uso avançado de ferramentas.", 4),
        SkillDefinition("eficiencia_energetica", "Eficiência energética", "Reduz consumo elétrico em 12%.", 5),
        SkillDefinition("automacao_cnc", "Automação CNC", "+15% em máquinas CNC.", 7, "lean_i"),
        SkillDefinition("comercial", "Engenharia comercial", "Habilita contratos de requisitos mais altos mais cedo.", 6),
        SkillDefinition("gemeo_digital", "Gêmeo digital", "+8% velocidade e +3 qualidade.", 10, "automacao_cnc"),
    )

    val playerSkills = listOf(
        SkillDefinition("setup_rapido", "Setup rápido", "+4% de produção quando o personagem está na empresa.", 1),
        SkillDefinition("metrologia", "Metrologia", "+4 de qualidade.", 2),
        SkillDefinition("programacao_cnc", "Programação CNC", "+8% em CNC.", 4, "setup_rapido"),
        SkillDefinition("lideranca", "Liderança", "+3% global.", 5),
        SkillDefinition("negociacao", "Negociação", "Seu personagem vale mais no mercado conectado.", 6),
        SkillDefinition("mestre_processo", "Mestre de processo", "+6% global e +4 qualidade.", 9, "metrologia"),
    )

    val skins = listOf(
        SkinDefinition("operador_padrao", "Operador padrão", 1, Rarity.COMMON, "Visual inicial sem bônus."),
        SkinDefinition("princesa", "Princesa da Usinagem", 3, Rarity.RARE, "Coroa, vestido industrial e cabelo longo. +4 qualidade.", qualityBonus = 4),
        SkinDefinition("pinoquio", "Pinóquio Narigudo", 5, Rarity.RARE, "Olho clínico para medida. +2% velocidade e +3 qualidade.", globalSpeedPct = 2, qualityBonus = 3),
        SkinDefinition("tatuzao", "Tatuzão", 7, Rarity.EPIC, "Especialista de torno. +10% em torneamento.", turningPct = 10, gachaOnly = true),
        SkinDefinition("magrao", "Magrão Alto", 9, Rarity.EPIC, "Alcance e logística melhores. +6% global.", globalSpeedPct = 6, gachaOnly = true),
        SkinDefinition("kendao", "Kendão", 12, Rarity.LEGENDARY, "Mestre dos centros de usinagem. +12% fresagem e +8% CNC.", millingPct = 12, cncPct = 8, gachaOnly = true),
        SkinDefinition("princesa_dourada", "Princesa Dourada", 15, Rarity.LEGENDARY, "Skin lendária: +8 qualidade e +5% global.", globalSpeedPct = 5, qualityBonus = 8, gachaOnly = true),
    )

    val gachaCharacters = listOf(
        // Personagens comuns/raros: exclusivos da roleta e SEM repetição.
        GachaCharacterDefinition("operador_multitarefa", "Operador Multitarefa", 1, Rarity.COMMON, "+2% de produção global.", globalSpeedPct = 2),
        GachaCharacterDefinition("torneiro_junior", "Torneiro Júnior", 2, Rarity.COMMON, "+5% em torneamento.", turningPct = 5),
        GachaCharacterDefinition("cuca_aprendiz", "Cuca • Aprendiz de Setup", 2, Rarity.RARE, "+3% de produção global.", globalSpeedPct = 3),
        GachaCharacterDefinition("fresadora_agil", "Fresadora Ágil", 3, Rarity.RARE, "+7% em fresagem.", millingPct = 7),
        GachaCharacterDefinition("controle_qualidade", "Controle de Qualidade", 4, Rarity.RARE, "+4 de qualidade.", qualityBonus = 4),

        // PREMIUM: não saem mais na roleta. São comprados permanentemente na Loja de Personagens.
        GachaCharacterDefinition("mestre_torneiro", "Mestre Torneiro", 5, Rarity.EPIC, "+12% em torneamento.", turningPct = 12),
        GachaCharacterDefinition("programadora_cnc", "Programadora CNC", 6, Rarity.EPIC, "+10% CNC e +3 qualidade.", qualityBonus = 3, cncPct = 10),
        GachaCharacterDefinition("inspetor_zero", "Inspetor Zero", 7, Rarity.EPIC, "+8 de qualidade.", qualityBonus = 8),
        GachaCharacterDefinition("mestre_5_eixos", "Mestre dos 5 Eixos", 10, Rarity.LEGENDARY, "+8% global, +10% fresagem e +10% CNC.", globalSpeedPct = 8, millingPct = 10, cncPct = 10),
        GachaCharacterDefinition("lenda_chao_fabrica", "Lenda do Chão de Fábrica", 14, Rarity.LEGENDARY, "+12% global e +5 qualidade.", globalSpeedPct = 12, qualityBonus = 5),
    )


    fun isPremiumCharacter(character: GachaCharacterDefinition): Boolean =
        character.rarity == Rarity.EPIC || character.rarity == Rarity.LEGENDARY

    fun premiumCharacterPriceCents(id: String): Long = when (id) {
        "mestre_torneiro" -> 12_000_000L
        "programadora_cnc" -> 16_000_000L
        "inspetor_zero" -> 18_000_000L
        "mestre_5_eixos" -> 35_000_000L
        "lenda_chao_fabrica" -> 48_000_000L
        else -> 0L
    }

    val tools = listOf(
        ToolDefinition("broca_madeira", "Broca de madeira", Rarity.COMMON, 1.02, -2, "Ferramenta improvisada e básica: +2% velocidade, -2 qualidade."),
        ToolDefinition("ferramenta_soldada", "Ferramenta soldada", Rarity.COMMON, 1.04, 0, "Básica e barata: +4% velocidade."),
        ToolDefinition("fresa_hss", "Fresa de aço rápido (HSS)", Rarity.COMMON, 1.06, 2, "+6% velocidade e +2 qualidade."),
        ToolDefinition("broca_carbeto", "Broca de metal duro", Rarity.RARE, 1.12, 3, "+12% velocidade e +3 qualidade."),
        ToolDefinition("fresa_alto_avanco", "Fresa de alto avanço", Rarity.EPIC, 1.25, 2, "+25% velocidade no contrato e +2 qualidade."),
        ToolDefinition("pastilha_cbn", "Pastilha CBN", Rarity.LEGENDARY, 1.15, 10, "+15% velocidade e +10 qualidade."),
        ToolDefinition("fresa_pcd", "Fresa PCD", Rarity.LEGENDARY, 1.20, 12, "+20% velocidade e +12 qualidade."),
    )

    // Preços propositalmente na faixa de ~10x das máquinas de entrada do jogo.
    val premiumMachines = listOf(
        PremiumMachineDefinition("torno_hyper", "Torno CNC Hyper X", 6, 25_000_000L, Rarity.EPIC, "Célula premium de torneamento: +18% torno e +8% CNC.", turningPct = 18, cncPct = 8),
        PremiumMachineDefinition("centro_5x_titan", "Centro 5 Eixos TITAN", 9, 55_000_000L, Rarity.LEGENDARY, "Centro topado: +22% fresagem, +12% CNC e +3 qualidade.", millingPct = 22, cncPct = 12, qualityBonus = 3),
        PremiumMachineDefinition("celula_robotica", "Célula Robótica de Produção", 12, 90_000_000L, Rarity.LEGENDARY, "+12% global e +5% CNC.", globalSpeedPct = 12, cncPct = 5),
        PremiumMachineDefinition("retifica_ultra", "Retífica Ultra Precision", 10, 65_000_000L, Rarity.LEGENDARY, "+25% retífica e +7 qualidade.", grindingPct = 25, qualityBonus = 7),
        PremiumMachineDefinition("solda_omega", "Robô de Solda Ômega", 8, 45_000_000L, Rarity.EPIC, "+24% solda.", weldingPct = 24),
    )

    fun contractGate(difficulty: Int): ContractGate = when (difficulty.coerceIn(1, 5)) {
        1 -> ContractGate(1, 0, false, "Nível 1")
        2 -> ContractGate(2, 0, false, "Nível 2")
        3 -> ContractGate(4, 1, false, "Nível 4 + 1 skill da empresa")
        4 -> ContractGate(7, 2, false, "Nível 7 + 2 skills da empresa")
        else -> ContractGate(10, 3, true, "Nível 10 + 3 skills + especialidade definida")
    }

    fun canUnlock(skill: SkillDefinition, level: Int, owned: Set<String>): Boolean =
        level >= skill.minLevel && (skill.prerequisite == null || skill.prerequisite in owned)
}
