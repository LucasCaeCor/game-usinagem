package br.com.usinagemmaster.domain.simulation

import br.com.usinagemmaster.domain.catalog.LegendaryMissionDefinition
import br.com.usinagemmaster.domain.catalog.LegendaryMissionMetric
import br.com.usinagemmaster.domain.model.EmployeeRuntime
import br.com.usinagemmaster.domain.model.MachineRuntime
import br.com.usinagemmaster.domain.model.ProductionSnapshot

object LegendaryMissionProgressEngine {
    fun progressDelta(
        definition: LegendaryMissionDefinition,
        employee: EmployeeRuntime?,
        machines: List<MachineRuntime>,
        snapshot: ProductionSnapshot,
        elapsedMinutes: Long
    ): Long {
        if (employee == null || elapsedMinutes <= 0) return 0

        val operatingIds = snapshot.machineProduction
            .filter { it.isOperating }
            .map { it.machineId }
            .toSet()
        val machineById = machines.associateBy { it.id }

        val valid = when (definition.metric) {
            LegendaryMissionMetric.OPERATING_MINUTES -> {
                val machineId = employee.assignedMachineId
                val machine = machineId?.let(machineById::get)
                machineId != null &&
                    machineId in operatingIds &&
                    machine != null &&
                    definition.machineTypes.any { it.name == machine.machineType }
            }

            LegendaryMissionMetric.SUPPORT_MINUTES ->
                snapshot.operatingMachines >= definition.minimumOperatingMachines

            LegendaryMissionMetric.QUALITY_MINUTES ->
                snapshot.operatingMachines > 0 && snapshot.averageQuality >= definition.minimumQuality
        }

        return if (valid) elapsedMinutes else 0
    }
}
