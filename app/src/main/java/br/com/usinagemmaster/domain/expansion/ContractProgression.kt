package br.com.usinagemmaster.domain.expansion

import br.com.usinagemmaster.data.local.entity.ContractEntity

/**
 * Regras de progressão dos contratos da V8.
 *
 * Não altera o schema Room. O nível mínimo continua derivado da dificuldade,
 * mas agora o gerador só cria contratos compatíveis com o nível atual.
 */
object ContractProgression {
    fun minLevel(difficulty: Int): Int = when (difficulty.coerceIn(1, 5)) {
        1 -> 1
        2 -> 2
        3 -> 4
        4 -> 7
        else -> 10
    }

    fun allowedDifficulties(companyLevel: Int): List<Int> =
        (1..5).filter { minLevel(it) <= companyLevel.coerceAtLeast(1) }

    fun targetAvailable(companyLevel: Int): Int = when {
        companyLevel <= 2 -> 7
        companyLevel <= 6 -> 6
        else -> 7
    }

    fun isSpecial(contract: ContractEntity): Boolean = contract.contractType.startsWith("⭐")

    fun specialChancePct(companyLevel: Int): Int = (10 + companyLevel * 2).coerceIn(10, 28)

    /** XP da fábrica é a representação visual da reputação: 1 reputação = 100 XP. */
    fun factoryXp(contract: ContractEntity): Long = contract.reputationReward.coerceAtLeast(0) * 100L

    fun characterXp(contract: ContractEntity): Long {
        val base = ExpansionProgression.characterXpForContract(
            contract.difficulty,
            contract.quantity,
            contract.requiredQuality,
        )
        return if (isSpecial(contract)) base * 3L / 2L else base
    }

    /**
     * Balanceamento para que níveis iniciais avancem em poucos contratos,
     * sem limitar o jogador a uma quantidade fixa por nível.
     */
    fun reputationReward(difficulty: Int, special: Boolean): Int {
        val base = when (difficulty.coerceIn(1, 5)) {
            1 -> 5       // 500 XP fábrica
            2 -> 6       // 600 XP fábrica
            3 -> 8       // 800 XP fábrica
            4 -> 10      // 1.000 XP fábrica
            else -> 13   // 1.300 XP fábrica
        }
        return base + if (special) 3 else 0
    }

    fun requirementText(difficulty: Int): String {
        val gate = ExpansionCatalog.contractGate(difficulty)
        val parts = mutableListOf("Nível ${gate.minLevel}")
        if (gate.minCompanySkills > 0) parts += "${gate.minCompanySkills} skill(s) da empresa"
        if (gate.requiresSpecialty) parts += "especialidade definida"
        return parts.joinToString(" • ")
    }

    fun access(contract: ContractEntity, companyLevel: Int, state: ExpansionState): ContractAccess {
        val gate = ExpansionCatalog.contractGate(contract.difficulty)
        if (companyLevel < gate.minLevel) {
            return ContractAccess(false, "Exige nível ${gate.minLevel}")
        }
        val commercialBonus = if ("comercial" in state.companySkills) 1 else 0
        if (state.companySkills.size + commercialBonus < gate.minCompanySkills) {
            return ContractAccess(false, "Exige ${gate.minCompanySkills} skill(s) da empresa")
        }
        if (gate.requiresSpecialty && state.specialty == CompanySpecialty.GENERALIST.code) {
            return ContractAccess(false, "Defina uma especialidade da empresa")
        }
        return ContractAccess(true)
    }
}
