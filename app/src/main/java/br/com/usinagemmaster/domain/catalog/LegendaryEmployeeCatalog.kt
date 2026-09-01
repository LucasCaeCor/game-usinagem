package br.com.usinagemmaster.domain.catalog

import br.com.usinagemmaster.domain.model.EmployeeSpecialty

data class LegendaryEmployeeDefinition(
    val code: String,
    val name: String,
    val specialty: EmployeeSpecialty,
    val skillLevel: Int,
    val morale: Int,
    val salaryCents: Long,
    val trait: String,
    val unlockLevel: Int,
    val visualScale: Float = 1f,
    val walkSpeedMultiplier: Float = 1f,
    val description: String
)

object LegendaryEmployeeCatalog {
    val all = listOf(
        LegendaryEmployeeDefinition(
            code = "tatu_banhado",
            name = "Tatu do Banhado",
            specialty = EmployeeSpecialty.TURNER,
            skillLevel = 7,
            morale = 84,
            salaryCents = 465_000L,
            trait = "Casca grossa",
            unlockLevel = 1,
            visualScale = 1.03f,
            walkSpeedMultiplier = .94f,
            description = "Torneiro veterano. Mantém ritmo forte mesmo em máquina desgastada."
        ),
        LegendaryEmployeeDefinition(
            code = "kendao",
            name = "Kendão",
            specialty = EmployeeSpecialty.MILLER,
            skillLevel = 8,
            morale = 78,
            salaryCents = 525_000L,
            trait = "Mão pesada",
            unlockLevel = 1,
            visualScale = 1.08f,
            description = "Fresador rápido e agressivo, ótimo para lotes médios."
        ),
        LegendaryEmployeeDefinition(
            code = "chupa_engole",
            name = "Chupa Engole",
            specialty = EmployeeSpecialty.WELDER,
            skillLevel = 7,
            morale = 92,
            salaryCents = 490_000L,
            trait = "Rei da solda",
            unlockLevel = 3,
            walkSpeedMultiplier = 1.06f,
            description = "Soldador de ritmo alto. Brilha quando o setor de caldeiraria aperta."
        ),
        LegendaryEmployeeDefinition(
            code = "moskitao",
            name = "Moskitão",
            specialty = EmployeeSpecialty.DRILL_OPERATOR,
            skillLevel = 8,
            morale = 86,
            salaryCents = 505_000L,
            trait = "Elétrico",
            unlockLevel = 2,
            visualScale = .96f,
            walkSpeedMultiplier = 1.18f,
            description = "Operador veloz, vive atravessando o galpão e acelera a furação."
        ),
        LegendaryEmployeeDefinition(
            code = "nikao_narizudo",
            name = "Nikao Narizudo",
            specialty = EmployeeSpecialty.QUALITY_INSPECTOR,
            skillLevel = 9,
            morale = 76,
            salaryCents = 610_000L,
            trait = "Controle total",
            unlockLevel = 4,
            description = "Inspetor rigoroso. Enquanto contratado, aumenta a qualidade geral da fábrica."
        ),
        LegendaryEmployeeDefinition(
            code = "gumersvaldo",
            name = "Gumersvaldo",
            specialty = EmployeeSpecialty.CNC_PROGRAMMER,
            skillLevel = 10,
            morale = 73,
            salaryCents = 790_000L,
            trait = "Mestre CNC",
            unlockLevel = 4,
            walkSpeedMultiplier = .92f,
            description = "Programador CNC de elite. Extrai muito mais das máquinas CNC."
        ),
        LegendaryEmployeeDefinition(
            code = "magrao",
            name = "Magrão",
            specialty = EmployeeSpecialty.STOCK_ASSISTANT,
            skillLevel = 7,
            morale = 88,
            salaryCents = 410_000L,
            trait = "Logística rápida",
            unlockLevel = 1,
            visualScale = .90f,
            walkSpeedMultiplier = 1.25f,
            description = "Corre no estoque e mantém material chegando às máquinas."
        ),
        LegendaryEmployeeDefinition(
            code = "pedrao",
            name = "Pedrão",
            specialty = EmployeeSpecialty.WELDER,
            skillLevel = 8,
            morale = 81,
            salaryCents = 565_000L,
            trait = "Braço de aço",
            unlockLevel = 1,
            visualScale = 1.14f,
            walkSpeedMultiplier = .93f,
            description = "Caldeireiro forte e confiável, excelente em solda e estruturas."
        ),
        LegendaryEmployeeDefinition(
            code = "nelsinho_treme_treme",
            name = "Nelsinho Treme Treme",
            specialty = EmployeeSpecialty.DRILL_OPERATOR,
            skillLevel = 6,
            morale = 69,
            salaryCents = 385_000L,
            trait = "Treme mas entrega",
            unlockLevel = 3,
            visualScale = .97f,
            walkSpeedMultiplier = 1.08f,
            description = "Tem seu jeito peculiar, mas surpreende na produtividade."
        ),
        LegendaryEmployeeDefinition(
            code = "merciao",
            name = "Mercião",
            specialty = EmployeeSpecialty.GRINDER_OPERATOR,
            skillLevel = 9,
            morale = 79,
            salaryCents = 620_000L,
            trait = "Acabamento espelho",
            unlockLevel = 2,
            description = "Especialista em retífica. Entrega acabamento e qualidade excepcionais."
        ),
        LegendaryEmployeeDefinition(
            code = "bodybuilder",
            name = "Bodybuilder",
            specialty = EmployeeSpecialty.STOCK_ASSISTANT,
            skillLevel = 8,
            morale = 90,
            salaryCents = 520_000L,
            trait = "Força bruta",
            unlockLevel = 2,
            visualScale = 1.22f,
            walkSpeedMultiplier = .88f,
            description = "Move material pesado sem reclamar e melhora o fluxo interno."
        )
    )

    private val workQuotes = mapOf(
        "tatu_banhado" to listOf("Esse torno aguenta!", "Manda mais peça.", "No braço e no relógio."),
        "kendao" to listOf("Passa mais avanço!", "Essa fresa vai cantar.", "Lote grande é comigo."),
        "chupa_engole" to listOf("Hoje sai faísca!", "Solda bonita é outra coisa.", "Segura essa estrutura."),
        "moskitao" to listOf("Furo pronto, próximo!", "Não para a furadeira!", "Tô voando hoje."),
        "nikao_narizudo" to listOf("Mede de novo.", "Qualidade primeiro.", "Essa tolerância tá apertada."),
        "gumersvaldo" to listOf("Programa redondo.", "CNC bem ajustado rende.", "Zero erro de offset."),
        "magrao" to listOf("Material chegando!", "Não deixa máquina esperando.", "Estoque tá girando."),
        "pedrao" to listOf("Pode trazer pesado.", "Essa solda segura.", "Estrutura firme."),
        "nelsinho_treme_treme" to listOf("Treme, mas não erra!", "Vai dar certo... vai!", "Furo na medida!"),
        "merciao" to listOf("Quero acabamento espelho.", "Mais uma passada fina.", "Agora ficou bonito."),
        "bodybuilder" to listOf("Carga leve hoje!", "Traz o pallet inteiro.", "Material não fica parado.")
    )

    private val idleQuotes = mapOf(
        "tatu_banhado" to listOf("Cadê serviço?", "Torno parado dá tristeza."),
        "kendao" to listOf("Tem fresa pra mim?", "Quero ver cavaco voar."),
        "chupa_engole" to listOf("Cadê a próxima solda?", "Hoje o café tá forte."),
        "moskitao" to listOf("Parado eu não fico.", "Vou dar uma volta no galpão."),
        "nikao_narizudo" to listOf("Vou conferir aquele lote.", "Alguém viu o paquímetro?"),
        "gumersvaldo" to listOf("Vou revisar o programa.", "Esse setup pode melhorar."),
        "magrao" to listOf("Vou buscar material.", "Tem pallet no estoque."),
        "pedrao" to listOf("Chama quando pesar.", "Vou conferir a bancada."),
        "nelsinho_treme_treme" to listOf("Calma... tá tranquilo.", "Cadê minha furadeira?"),
        "merciao" to listOf("Retífica limpa rende mais.", "Vou conferir o rebolo."),
        "bodybuilder" to listOf("Quem deixou isso no caminho?", "Vou organizar a carga.")
    )

    fun byCode(code: String?): LegendaryEmployeeDefinition? = all.firstOrNull { it.code == code }

    fun quote(code: String?, working: Boolean, index: Int): String? {
        if (code == null) return null
        val source = if (working) workQuotes[code] else idleQuotes[code]
        if (source.isNullOrEmpty()) return null
        return source[kotlin.math.abs(index) % source.size]
    }
}
