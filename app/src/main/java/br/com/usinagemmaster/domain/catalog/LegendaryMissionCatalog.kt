package br.com.usinagemmaster.domain.catalog

import br.com.usinagemmaster.domain.model.MachineType

enum class LegendaryMissionMetric {
    OPERATING_MINUTES,
    SUPPORT_MINUTES,
    QUALITY_MINUTES
}

data class LegendaryMissionDefinition(
    val id: String,
    val legendaryCode: String,
    val title: String,
    val description: String,
    val metric: LegendaryMissionMetric,
    val target: Long,
    val rewardCents: Long,
    val machineTypes: Set<MachineType> = emptySet(),
    val minimumOperatingMachines: Int = 0,
    val minimumQuality: Int = 0
)

object LegendaryMissionCatalog {
    val all = listOf(
        LegendaryMissionDefinition(
            id = "mission_tatu_banhado",
            legendaryCode = "tatu_banhado",
            title = "Casca grossa no torno",
            description = "Deixe Tatu do Banhado produzir por 120 minutos em um torno.",
            metric = LegendaryMissionMetric.OPERATING_MINUTES,
            target = 120,
            rewardCents = 700_000L,
            machineTypes = setOf(MachineType.MECHANICAL_LATHE, MachineType.CNC_LATHE)
        ),
        LegendaryMissionDefinition(
            id = "mission_kendao",
            legendaryCode = "kendao",
            title = "Fresa sem dó",
            description = "Kendão precisa acumular 120 minutos trabalhando em fresagem.",
            metric = LegendaryMissionMetric.OPERATING_MINUTES,
            target = 120,
            rewardCents = 800_000L,
            machineTypes = setOf(MachineType.UNIVERSAL_MILL, MachineType.CNC_MACHINING_CENTER_3_AXIS, MachineType.CNC_MACHINING_CENTER_5_AXIS)
        ),
        LegendaryMissionDefinition(
            id = "mission_chupa_engole",
            legendaryCode = "chupa_engole",
            title = "Faísca até o fim",
            description = "Acumule 100 minutos de solda com Chupa Engole.",
            metric = LegendaryMissionMetric.OPERATING_MINUTES,
            target = 100,
            rewardCents = 900_000L,
            machineTypes = setOf(MachineType.WELDING_BENCH, MachineType.ROBOTIC_WELDING)
        ),
        LegendaryMissionDefinition(
            id = "mission_moskitao",
            legendaryCode = "moskitao",
            title = "Furação relâmpago",
            description = "Moskitão deve operar furadeiras por 100 minutos.",
            metric = LegendaryMissionMetric.OPERATING_MINUTES,
            target = 100,
            rewardCents = 780_000L,
            machineTypes = setOf(MachineType.COLUMN_DRILL, MachineType.CNC_DRILL)
        ),
        LegendaryMissionDefinition(
            id = "mission_nikao_narizudo",
            legendaryCode = "nikao_narizudo",
            title = "Nada passa torto",
            description = "Com Nikao contratado, mantenha qualidade média de 75% ou mais por 90 minutos.",
            metric = LegendaryMissionMetric.QUALITY_MINUTES,
            target = 90,
            rewardCents = 1_150_000L,
            minimumQuality = 75
        ),
        LegendaryMissionDefinition(
            id = "mission_gumersvaldo",
            legendaryCode = "gumersvaldo",
            title = "Programa perfeito",
            description = "Gumersvaldo deve comandar máquinas CNC por 150 minutos.",
            metric = LegendaryMissionMetric.OPERATING_MINUTES,
            target = 150,
            rewardCents = 1_600_000L,
            machineTypes = setOf(
                MachineType.CNC_LATHE, MachineType.CNC_MACHINING_CENTER_3_AXIS,
                MachineType.CNC_MACHINING_CENTER_5_AXIS, MachineType.CNC_DRILL,
                MachineType.EDM, MachineType.LASER_CUTTER
            )
        ),
        LegendaryMissionDefinition(
            id = "mission_magrao",
            legendaryCode = "magrao",
            title = "Material não pode parar",
            description = "Com Magrão na equipe, mantenha pelo menos 2 máquinas operando por 90 minutos.",
            metric = LegendaryMissionMetric.SUPPORT_MINUTES,
            target = 90,
            rewardCents = 850_000L,
            minimumOperatingMachines = 2
        ),
        LegendaryMissionDefinition(
            id = "mission_pedrao",
            legendaryCode = "pedrao",
            title = "Braço de aço",
            description = "Pedrão precisa acumular 130 minutos no setor de solda/caldeiraria.",
            metric = LegendaryMissionMetric.OPERATING_MINUTES,
            target = 130,
            rewardCents = 950_000L,
            machineTypes = setOf(MachineType.WELDING_BENCH, MachineType.ROBOTIC_WELDING, MachineType.PLASMA_CUTTER)
        ),
        LegendaryMissionDefinition(
            id = "mission_nelsinho_treme_treme",
            legendaryCode = "nelsinho_treme_treme",
            title = "Treme mas entrega",
            description = "Nelsinho precisa produzir em furação por 80 minutos.",
            metric = LegendaryMissionMetric.OPERATING_MINUTES,
            target = 80,
            rewardCents = 700_000L,
            machineTypes = setOf(MachineType.COLUMN_DRILL, MachineType.CNC_DRILL)
        ),
        LegendaryMissionDefinition(
            id = "mission_merciao",
            legendaryCode = "merciao",
            title = "Espelho no aço",
            description = "Mercião deve trabalhar 110 minutos em retífica.",
            metric = LegendaryMissionMetric.OPERATING_MINUTES,
            target = 110,
            rewardCents = 1_050_000L,
            machineTypes = setOf(MachineType.CYLINDRICAL_GRINDER, MachineType.CNC_GRINDER)
        ),
        LegendaryMissionDefinition(
            id = "mission_bodybuilder",
            legendaryCode = "bodybuilder",
            title = "Logística pesada",
            description = "Com Bodybuilder contratado, mantenha 3 máquinas operando por 120 minutos.",
            metric = LegendaryMissionMetric.SUPPORT_MINUTES,
            target = 120,
            rewardCents = 1_100_000L,
            minimumOperatingMachines = 3
        )
    )

    fun byLegendaryCode(code: String?): LegendaryMissionDefinition? = all.firstOrNull { it.legendaryCode == code }
    fun byId(id: String) = all.firstOrNull { it.id == id }
}
