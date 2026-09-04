package br.com.usinagemmaster.domain.gameplay

import br.com.usinagemmaster.data.local.entity.ContractEntity
import kotlin.math.roundToInt

enum class ProductionStage(val label: String) {
    RAW("Matéria-prima"), WAITING_MACHINE("Aguardando máquina"), MACHINING("Usinando"),
    MACHINED("Lote usinado"), WAITING_QC("Levar à Qualidade"), QC("Inspeção dimensional"),
    APPROVED("Lote aprovado"), REWORK("Retrabalho"), PACKING("Embalagem"),
    READY_TO_SHIP("Pronto para expedição"), SHIPPED("Expedido"), SCRAP("Refugo")
}

enum class OwnerStation(val label: String) {
    MATERIAL("Matéria-prima"), TOOLS("Ferramentaria"), QUALITY("Qualidade"),
    PACKING("Embalagem"), SHIPPING("Expedição"), BREAK_ROOM("Copa")
}

enum class MinigameKind(val title: String) {
    LATHE("Torneamento dimensional"), MILLING("Estratégia de fresagem"),
    DRILLING("Furação e profundidade"), GRINDING("Retífica de precisão"),
    CNC("Programa CNC e parâmetros"), WELDING("Soldagem e energia"),
    EDM("Eletroerosão"), LASER("Corte a laser"), PLASMA("Corte a plasma"), QUALITY("Metrologia")
}

data class MinigameResult(
    val score: Float, val precision: Float, val speed: Float, val quality: Float, val mistakes: Int = 0
) {
    val normalizedScore get() = score.coerceIn(0f, 1f)
    val perfect get() = normalizedScore >= .94f && mistakes == 0
}

data class OwnerWorkBatch(
    val id: String, val machineId: String, val machineType: String, val contractId: String,
    val stage: ProductionStage, val producedQuantity: Int, val quality: Int, val precision: Int,
    val speed: Int, val mistakes: Int, val perfect: Boolean, val manual: Boolean,
    val reworkCount: Int = 0, val createdAt: Long, val updatedAt: Long,
) {
    val carrying get() = stage in setOf(ProductionStage.MACHINED, ProductionStage.WAITING_QC,
        ProductionStage.APPROVED, ProductionStage.PACKING, ProductionStage.READY_TO_SHIP)
}

data class MachineMastery(val machineType: String, val xp: Int = 0) {
    val level get() = (1 + xp.coerceAtLeast(0) / 180).coerceIn(1, 20)
    val quantityBonusPct get() = ((level - 1) * 2).coerceAtMost(24)
    val qualityBonus get() = ((level - 1) / 4).coerceAtMost(5)
}

enum class IndustrialSkillBranch(val label: String, val icon: String) {
    OPERATION("Operador", "🧑‍🏭"), QUALITY("Qualidade", "📏"), PRODUCTION("Produção", "🏭"),
    MANAGEMENT("Gestão", "👥"), COMMERCIAL("Comercial", "🤝"), HYBRID("Especializações", "⚙️")
}

data class IndustrialSkillDefinition(
    val id: String, val name: String, val branch: IndustrialSkillBranch, val tier: Int,
    val minCompanyLevel: Int, val description: String, val prerequisites: Set<String> = emptySet(),
    val cost: Int = 1,
)

enum class ProductionPolicy(val label: String, val description: String) {
    BALANCED("Balanceada", "Equilibra prazo, qualidade e margem."),
    DEADLINE("Priorizar prazo", "+produção automática, pequena perda de qualidade."),
    QUALITY("Priorizar qualidade", "+qualidade, produção um pouco mais lenta."),
    PROFIT("Priorizar margem", "Menor consumo, ritmo mais conservador.")
}

data class CareerState(
    val activeBatch: OwnerWorkBatch? = null,
    val masteryXp: Map<String, Int> = emptyMap(),
    val unlockedSkills: Set<String> = emptySet(),
    val milestones: Set<String> = emptySet(),
    val achievements: Set<String> = emptySet(),
    val totalManualOperations: Int = 0, val assistedOperations: Int = 0, val perfectOperations: Int = 0,
    val approvedBatches: Int = 0, val shippedBatches: Int = 0, val reworkedBatches: Int = 0,
    val scrappedBatches: Int = 0, val bestScore: Int = 0, val operationStreak: Int = 0,
    val earnedSkillPoints: Int = 1, val productionPolicy: ProductionPolicy = ProductionPolicy.BALANCED,
    val lastOperationAt: Long = 0L,
) {
    val spentSkillPoints get() = unlockedSkills.sumOf { IndustrialSkillCatalog.byId(it)?.cost ?: 1 }
    val availableSkillPoints get() = (earnedSkillPoints - spentSkillPoints).coerceAtLeast(0)
    fun mastery(type: String) = MachineMastery(type, masteryXp[type] ?: 0)
    fun has(id: String) = id in unlockedSkills
    fun manualQuantityMultiplier(): Double = 1.0 + (if (has("ritmo_producao")) .12 else 0.0) +
        (if (has("mestre_usinagem")) .10 else 0.0) + (if (has("celula_cnc_avancada")) .08 else 0.0)
    fun manualQualityBonus(): Int = (if (has("mao_firme")) 2 else 0) + (if (has("metrologista")) 4 else 0) +
        (if (has("zero_defeito")) 4 else 0) + (if (has("usinagem_precisao")) 5 else 0)
    fun automationSpeedMultiplier(): Double {
        var v = 1.0
        listOf("planejamento" to .03, "balanceamento" to .05, "producao_celular" to .06,
            "kanban" to .05, "lean_manufacturing" to .07, "producao_autonoma" to .08,
            "gerente_producao" to .05).forEach { if (has(it.first)) v += it.second }
        if (has("diretor_industrial")) v += when (productionPolicy) { ProductionPolicy.DEADLINE -> .10; ProductionPolicy.QUALITY -> -.05; ProductionPolicy.PROFIT -> -.04; else -> 0.0 }
        return v.coerceIn(.85, 1.55)
    }
    fun automationQualityBonus(): Int {
        var v = (if (has("olho_treinado")) 1 else 0) + (if (has("controle_estatistico")) 3 else 0) +
            (if (has("mestre_qualidade")) 4 else 0) + (if (has("industria_4")) 2 else 0)
        if (has("diretor_industrial")) v += when (productionPolicy) { ProductionPolicy.QUALITY -> 6; ProductionPolicy.DEADLINE -> -2; else -> 0 }
        return v
    }
    fun energyMultiplier(): Double = ((if (has("lean_manufacturing")) .94 else 1.0) *
        (if (has("diretor_industrial") && productionPolicy == ProductionPolicy.PROFIT) .90 else 1.0)).coerceIn(.75, 1.0)
    fun commercialCompletionBonusPct(): Int = ((if (has("boa_reputacao")) 2 else 0) +
        (if (has("contratos_recorrentes")) 2 else 0) + (if (has("empresas_nacionais")) 3 else 0) +
        (if (has("exportacao")) 4 else 0) + (if (has("fornecedor_estrategico")) 5 else 0)).coerceAtMost(16)
}

object IndustrialSkillCatalog {
    val all = listOf(
        IndustrialSkillDefinition("mao_firme","Mão firme",IndustrialSkillBranch.OPERATION,1,1,"Melhora leitura e qualidade do trabalho manual."),
        IndustrialSkillDefinition("ritmo_producao","Ritmo de produção",IndustrialSkillBranch.OPERATION,2,2,"Sequências boas rendem mais peças.",setOf("mao_firme")),
        IndustrialSkillDefinition("preparador","Preparador",IndustrialSkillBranch.OPERATION,3,4,"Libera setups e reduz penalidade de parâmetros.",setOf("ritmo_producao")),
        IndustrialSkillDefinition("operador_cnc","Operador CNC",IndustrialSkillBranch.OPERATION,4,6,"Melhora desafios de programação CNC.",setOf("preparador")),
        IndustrialSkillDefinition("mestre_usinagem","Mestre de usinagem",IndustrialSkillBranch.OPERATION,5,10,"Peças perfeitas geram lotes maiores.",setOf("operador_cnc"),2),
        IndustrialSkillDefinition("olho_treinado","Olho treinado",IndustrialSkillBranch.QUALITY,1,1,"Dica extra na inspeção e bônus automático."),
        IndustrialSkillDefinition("metrologista","Metrologista",IndustrialSkillBranch.QUALITY,2,3,"Medições exigentes e mais qualidade manual.",setOf("olho_treinado")),
        IndustrialSkillDefinition("controle_estatistico","Controle estatístico",IndustrialSkillBranch.QUALITY,3,5,"A equipe detecta tendência de processo.",setOf("metrologista")),
        IndustrialSkillDefinition("zero_defeito","Zero Defeito",IndustrialSkillBranch.QUALITY,4,8,"Operações excelentes toleram pequenas falhas.",setOf("controle_estatistico")),
        IndustrialSkillDefinition("mestre_qualidade","Mestre da Qualidade",IndustrialSkillBranch.QUALITY,5,12,"Grande bônus de qualidade automática.",setOf("zero_defeito"),2),
        IndustrialSkillDefinition("planejamento","Planejamento",IndustrialSkillBranch.PRODUCTION,1,2,"Organiza filas e aumenta produção automática."),
        IndustrialSkillDefinition("balanceamento","Balanceamento",IndustrialSkillBranch.PRODUCTION,2,4,"Reduz gargalos entre postos.",setOf("planejamento")),
        IndustrialSkillDefinition("producao_celular","Produção celular",IndustrialSkillBranch.PRODUCTION,3,6,"Máquinas próximas cooperam melhor.",setOf("balanceamento")),
        IndustrialSkillDefinition("kanban","Kanban",IndustrialSkillBranch.PRODUCTION,4,8,"Reposição e movimentação mais eficientes.",setOf("producao_celular")),
        IndustrialSkillDefinition("lean_manufacturing","Lean Manufacturing",IndustrialSkillBranch.PRODUCTION,5,11,"Menos desperdício e mais velocidade.",setOf("kanban"),2),
        IndustrialSkillDefinition("industria_4","Indústria 4.0",IndustrialSkillBranch.PRODUCTION,6,15,"Leitura avançada de eficiência, gargalos e qualidade.",setOf("lean_manufacturing"),2),
        IndustrialSkillDefinition("primeiro_lider","Primeiro líder",IndustrialSkillBranch.MANAGEMENT,1,2,"Melhora o treinamento da equipe."),
        IndustrialSkillDefinition("lider_equipe","Líder de equipe",IndustrialSkillBranch.MANAGEMENT,2,4,"Operadores trabalham melhor em conjunto.",setOf("primeiro_lider")),
        IndustrialSkillDefinition("plano_carreira","Plano de carreira",IndustrialSkillBranch.MANAGEMENT,3,6,"Funcionários evoluem mais rápido.",setOf("lider_equipe")),
        IndustrialSkillDefinition("supervisor","Supervisor",IndustrialSkillBranch.MANAGEMENT,4,9,"Delegação avançada de postos.",setOf("plano_carreira")),
        IndustrialSkillDefinition("gerente_producao","Gerente de produção",IndustrialSkillBranch.MANAGEMENT,5,12,"Automatiza prioridades e melhora fluxo.",setOf("supervisor")),
        IndustrialSkillDefinition("diretor_industrial","Diretor industrial",IndustrialSkillBranch.MANAGEMENT,6,16,"Libera políticas globais de prazo, qualidade e margem.",setOf("gerente_producao"),2),
        IndustrialSkillDefinition("clientes_locais","Clientes locais",IndustrialSkillBranch.COMMERCIAL,1,1,"Base comercial da empresa."),
        IndustrialSkillDefinition("boa_reputacao","Boa reputação",IndustrialSkillBranch.COMMERCIAL,2,3,"Bônus de fechamento de contratos.",setOf("clientes_locais")),
        IndustrialSkillDefinition("contratos_recorrentes","Contratos recorrentes",IndustrialSkillBranch.COMMERCIAL,3,5,"Clientes valiosos retornam com mais frequência.",setOf("boa_reputacao")),
        IndustrialSkillDefinition("empresas_nacionais","Empresas nacionais",IndustrialSkillBranch.COMMERCIAL,4,8,"Amplia bônus e contratos de maior escala.",setOf("contratos_recorrentes")),
        IndustrialSkillDefinition("exportacao","Exportação",IndustrialSkillBranch.COMMERCIAL,5,12,"Abre mercado global e maior bonificação.",setOf("empresas_nacionais"),2),
        IndustrialSkillDefinition("fornecedor_estrategico","Fornecedor estratégico",IndustrialSkillBranch.COMMERCIAL,6,16,"Contratos de alto prestígio e bônus máximo.",setOf("exportacao"),2),
        IndustrialSkillDefinition("usinagem_precisao","Usinagem de precisão",IndustrialSkillBranch.HYBRID,1,8,"Combina operação e metrologia para lotes premium.",setOf("preparador","metrologista"),2),
        IndustrialSkillDefinition("producao_autonoma","Produção autônoma",IndustrialSkillBranch.HYBRID,2,12,"Equipe resolve gargalos sem o dono.",setOf("kanban","supervisor"),2),
        IndustrialSkillDefinition("celula_cnc_avancada","Célula CNC avançada",IndustrialSkillBranch.HYBRID,3,15,"Bônus manual e automático em células CNC.",setOf("operador_cnc","producao_celular"),2),
    )
    fun byId(id: String) = all.firstOrNull { it.id == id }
    fun canUnlock(skill: IndustrialSkillDefinition, state: CareerState, companyLevel: Int) =
        skill.id !in state.unlockedSkills && companyLevel >= skill.minCompanyLevel &&
            skill.prerequisites.all { it in state.unlockedSkills } && state.availableSkillPoints >= skill.cost
}

data class MachineMinigameBlueprint(
    val kind: MinigameKind, val title: String, val goal: String,
    val parameterA: String, val parameterB: String, val targetA: Float, val targetB: Float,
    val toleranceA: Float, val toleranceB: Float,
)

object MachineMinigameCatalog {
    fun blueprint(machineType: String, difficulty: Int): MachineMinigameBlueprint {
        val d = difficulty.coerceIn(1,5)
        val tight = (14f - d * 1.5f).coerceAtLeast(5f)
        val t = machineType.uppercase()
        return when {
            "LATHE" in t || "TORNO" in t -> MachineMinigameBlueprint(MinigameKind.LATHE,"Torno • controle de corte","Aproxime RPM e avanço da janela ideal sem forçar a ferramenta.","RPM","Avanço",62f,52f,tight,tight)
            "MILL" in t || "FRESA" in t -> MachineMinigameBlueprint(MinigameKind.MILLING,"Fresagem • estratégia de passe","Escolha a trajetória e equilibre profundidade e avanço.","Profundidade","Avanço",48f,58f,tight,tight)
            "DRILL" in t || "FURA" in t -> MachineMinigameBlueprint(MinigameKind.DRILLING,"Furação • ferramenta e profundidade","Escolha a broca e controle rotação/profundidade.","Rotação","Profundidade",55f,66f,tight,tight)
            "GRIND" in t || "RETIF" in t -> MachineMinigameBlueprint(MinigameKind.GRINDING,"Retífica • microns finais","Chegue à medida sem ultrapassar a tolerância.","Passe final","Avanço",44f,38f,tight*.7f,tight*.7f)
            "CNC" in t -> MachineMinigameBlueprint(MinigameKind.CNC,"CNC • OP10","Ordene Facear → Furar → Contornar e ajuste parâmetros.","RPM","Avanço",64f,55f,tight,tight)
            "WELD" in t || "SOLDA" in t -> MachineMinigameBlueprint(MinigameKind.WELDING,"Soldagem • aporte térmico","Controle energia e velocidade para evitar falta de fusão ou empeno.","Energia","Velocidade",57f,51f,tight,tight)
            "EDM" in t || "EROS" in t -> MachineMinigameBlueprint(MinigameKind.EDM,"EDM • descarga","Equilibre descarga e gap para ganhar precisão sem instabilidade.","Descarga","Gap",49f,60f,tight,tight)
            "LASER" in t -> MachineMinigameBlueprint(MinigameKind.LASER,"Laser • foco e gás","Acerte foco e velocidade para um corte limpo.","Foco","Velocidade",53f,63f,tight,tight)
            "PLASMA" in t -> MachineMinigameBlueprint(MinigameKind.PLASMA,"Plasma • arco e velocidade","Equilibre corrente e avanço para reduzir rebarba.","Corrente","Velocidade",61f,58f,tight,tight)
            else -> MachineMinigameBlueprint(MinigameKind.MILLING,"Operação de usinagem","Ajuste o processo dentro da janela ideal.","Parâmetro A","Parâmetro B",55f,55f,tight,tight)
        }
    }
}

data class ContractGameplayProfile(val segment: String, val material: String, val toleranceLabel: String, val maxScrapPct: Int, val earlyBonusPct: Int, val processHint: String) {
    companion object {
        fun from(contract: ContractEntity): ContractGameplayProfile {
            val d = contract.difficulty.coerceIn(1,5)
            val segments = listOf("Metalúrgica local","Agrícola","Automotivo","Energia / médico","Aeroespacial")
            val materials = listOf("Alumínio 6351","SAE 1020","SAE 1045","Inox 304","Aço ferramenta")
            val tol = listOf("±0,10 mm","±0,07 mm","±0,05 mm","±0,03 mm","±0,015 mm")
            return ContractGameplayProfile(segments[d-1], materials[(kotlin.math.abs(contract.id.hashCode())+d)%materials.size], tol[d-1], (12-d*2).coerceAtLeast(2), 4+d*2, "Qualidade ${contract.requiredQuality}+ • refugo controlado")
        }
    }
}

data class OwnerBatchSettlement(val appliedQuantity: Int, val contractName: String, val contractRewardCents: Long, val commercialBonusCents: Long)

fun scoreFromParameters(a: Float,targetA: Float,toleranceA: Float,b: Float,targetB: Float,toleranceB: Float): Float {
    fun part(v:Float,t:Float,tol:Float)=(1f-kotlin.math.abs(v-t)/(tol*2.4f)).coerceIn(0f,1f)
    return (part(a,targetA,toleranceA)*.5f + part(b,targetB,toleranceB)*.5f).coerceIn(0f,1f)
}

fun suggestedManualQuantity(machineType: String, score: Float, mastery: MachineMastery, career: CareerState): Int {
    val base = when { "CNC" in machineType.uppercase() -> 8; "LATHE" in machineType.uppercase() -> 7; else -> 6 }
    val performance = .75 + score.coerceIn(0f,1f)*.75
    val masteryFactor = 1.0 + mastery.quantityBonusPct/100.0
    return (base*performance*masteryFactor*career.manualQuantityMultiplier()).roundToInt().coerceAtLeast(1)
}
