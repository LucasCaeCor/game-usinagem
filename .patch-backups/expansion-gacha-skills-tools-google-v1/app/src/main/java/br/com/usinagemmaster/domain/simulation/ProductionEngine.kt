package br.com.usinagemmaster.domain.simulation

import br.com.usinagemmaster.domain.catalog.MachineCatalog
import br.com.usinagemmaster.domain.model.EmployeeRuntime
import br.com.usinagemmaster.domain.model.MachineProduction
import br.com.usinagemmaster.domain.model.MachineRuntime
import br.com.usinagemmaster.domain.model.ProductionSnapshot
import kotlin.math.roundToInt

object ProductionEngine {
    private const val SALE_VALUE_PER_UNIT_CENTS = 1_250.0
    private const val ENERGY_PRICE_PER_KWH_CENTS = 110.0

    fun calculate(
        machines: List<MachineRuntime>,
        employees: List<EmployeeRuntime>,
        idleEmployeeIds: Set<String> = emptySet()
    ): ProductionSnapshot {
        val employeesByMachine = employees
            .filter { it.assignedMachineId != null }
            .associateBy { it.assignedMachineId!! }

        // Lendários de suporte melhoram a fábrica inteira mesmo sem operar máquina.
        val supportProductivityMultiplier = 1.0 + employees.sumOf {
            when (it.legendaryCode) {
                "magrao" -> 0.05
                "bodybuilder" -> 0.04
                else -> 0.0
            }
        }.coerceAtMost(0.12)

        val supportQualityBonus = employees.fold(0) { total, employee ->
            total + if (employee.legendaryCode == "nikao_narizudo") 6 else 0
        }.coerceAtMost(10)

        val production = machines.map { machine ->
            val definition = MachineCatalog.byType(machine.machineType)
            val employee = employeesByMachine[machine.id]

            if (definition == null || employee == null || machine.condition <= 80 || employee.id in idleEmployeeIds) {
                return@map MachineProduction(
                    machineId = machine.id,
                    employeeId = employee?.id,
                    unitsPerHour = 0.0,
                    quality = 0,
                    powerKw = 0.0,
                    isOperating = false
                )
            }

            val condition = machine.condition.coerceIn(0, 1000) / 1000.0
            val levelBonus = 1.0 + (machine.level - 1).coerceAtLeast(0) * 0.10
            val skillBonus = 0.70 + employee.skillLevel.coerceIn(1, 10) * 0.06
            val moraleBonus = 0.70 + employee.morale.coerceIn(0, 100) / 333.0
            val specialtyBonus = if (employee.specialty == definition.specialty.name) 1.15 else 0.45
            val traitBonus = when (employee.trait) {
                "Rápido" -> 1.12
                "CNC especialista" -> if (machine.machineType.contains("CNC")) 1.18 else 1.0
                "Distraído" -> 0.90
                "Falta muito" -> 0.88
                "Casca grossa" -> 1.06
                "Mão pesada" -> 1.08
                "Rei da solda" -> if (machine.machineType.contains("WELD")) 1.12 else 1.0
                "Elétrico" -> 1.08
                "Mestre CNC" -> if (machine.machineType.contains("CNC")) 1.15 else 1.0
                "Braço de aço" -> if (machine.machineType.contains("WELD")) 1.10 else 1.03
                "Treme mas entrega" -> 1.05
                else -> 1.0
            }
            val legendaryBonus = legendaryMachineMultiplier(employee.legendaryCode, machine.machineType)

            val units = definition.baseProductionPerHour *
                condition * levelBonus * skillBonus * moraleBonus * specialtyBonus * traitBonus *
                legendaryBonus * supportProductivityMultiplier

            val qualityTrait = when (employee.trait) {
                "Perfeccionista", "Cuidadoso" -> 6
                "Acabamento espelho" -> 10
                "Rei da solda" -> 4
                "Casca grossa" -> 2
                "Distraído" -> -8
                "Treme mas entrega" -> -3
                else -> 0
            }
            val quality = (
                definition.quality * condition +
                    employee.skillLevel * 1.5 +
                    qualityTrait +
                    supportQualityBonus
                ).roundToInt().coerceIn(1, 100)

            MachineProduction(
                machineId = machine.id,
                employeeId = employee.id,
                unitsPerHour = units,
                quality = quality,
                powerKw = definition.powerKw,
                isOperating = true
            )
        }

        val active = production.filter { it.isOperating }
        val unitsPerHour = active.sumOf { it.unitsPerHour }
        val gross = (unitsPerHour * SALE_VALUE_PER_UNIT_CENTS).toLong()
        val energy = (active.sumOf { it.powerKw } * ENERGY_PRICE_PER_KWH_CENTS).toLong()
        val averageQuality = if (active.isEmpty()) 0 else active.map { it.quality }.average().roundToInt()

        return ProductionSnapshot(
            totalUnitsPerHour = unitsPerHour,
            grossPerHourCents = gross,
            energyPerHourCents = energy,
            netPerHourCents = (gross - energy).coerceAtLeast(0),
            operatingMachines = active.size,
            idleMachines = production.size - active.size,
            averageQuality = averageQuality,
            machineProduction = production
        )
    }

    private fun legendaryMachineMultiplier(code: String?, machineType: String): Double = when (code) {
        "tatu_banhado" -> if (machineType.contains("LATHE")) 1.10 else 1.03
        "kendao" -> if (machineType.contains("MILL") || machineType.contains("MACHINING_CENTER")) 1.14 else 1.0
        "chupa_engole" -> if (machineType.contains("WELD")) 1.16 else 1.0
        "moskitao" -> if (machineType.contains("DRILL")) 1.15 else 1.02
        "gumersvaldo" -> if (machineType.contains("CNC")) 1.24 else 1.0
        "pedrao" -> if (machineType.contains("WELD")) 1.14 else 1.03
        "nelsinho_treme_treme" -> if (machineType.contains("DRILL")) 1.11 else 1.0
        "merciao" -> if (machineType.contains("GRINDER")) 1.15 else 1.0
        else -> 1.0
    }
}
