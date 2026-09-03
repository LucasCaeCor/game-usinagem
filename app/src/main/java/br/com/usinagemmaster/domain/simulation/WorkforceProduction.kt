package br.com.usinagemmaster.domain.simulation

import br.com.usinagemmaster.domain.model.ProductionSnapshot
import br.com.usinagemmaster.domain.worklife.WorkLifeState
import kotlin.math.roundToInt

/** Applies individual fatigue once, keeping machine flags, energy and totals consistent. */
object WorkforceProduction {
    fun adjust(snapshot: ProductionSnapshot, state: WorkLifeState, now: Long, checkSchedule: Boolean = true): ProductionSnapshot {
        val open = !checkSchedule || state.factoryOpen(now)
        val machines = snapshot.machineProduction.map { machine ->
            val employee = machine.employeeId
            val available = open && machine.isOperating && employee != null && !state.isResting(employee, now)
            machine.copy(
                unitsPerHour = if (available) machine.unitsPerHour * state.efficiency(employee!!) else 0.0,
                powerKw = if (available) machine.powerKw else 0.0,
                quality = if (available) machine.quality else 0,
                isOperating = available,
            )
        }
        val active = machines.filter { it.isOperating }
        val units = active.sumOf { it.unitsPerHour }
        val throughputRatio = if (snapshot.totalUnitsPerHour > 0.0) units / snapshot.totalUnitsPerHour else 0.0
        val power = snapshot.machineProduction.sumOf { it.powerKw }
        val powerRatio = if (power > 0.0) active.sumOf { it.powerKw } / power else 0.0
        val gross = (snapshot.grossPerHourCents * throughputRatio).toLong()
        val energy = (snapshot.energyPerHourCents * powerRatio).toLong()
        return snapshot.copy(
            totalUnitsPerHour = units,
            grossPerHourCents = gross,
            energyPerHourCents = energy,
            netPerHourCents = (gross - energy).coerceAtLeast(0L),
            operatingMachines = active.size,
            idleMachines = machines.size - active.size,
            averageQuality = if (active.isEmpty()) 0 else active.map { it.quality }.average().roundToInt(),
            machineProduction = machines,
        )
    }
}
