package br.com.usinagemmaster.domain.simulation

import br.com.usinagemmaster.domain.model.MachineProduction
import br.com.usinagemmaster.domain.model.ProductionSnapshot
import br.com.usinagemmaster.domain.worklife.FatigueAccrual
import br.com.usinagemmaster.domain.worklife.FactoryScheduleMode
import br.com.usinagemmaster.domain.worklife.WorkLifeState
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class WorkforceProductionTest {
    private val now = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 3, 12, 0, 0) }.timeInMillis
    private val snapshot = ProductionSnapshot(
        totalUnitsPerHour = 100.0, grossPerHourCents = 125000, energyPerHourCents = 1100,
        netPerHourCents = 123900, operatingMachines = 1, averageQuality = 90,
        machineProduction = listOf(MachineProduction("m", "e", 100.0, 90, 10.0, true)),
    )
    private val state = WorkLifeState(modeCode = FactoryScheduleMode.CONTINUOUS_24H.code)

    @Test fun individualFatigueIsAppliedOnceToUnitsAndRevenue() {
        val result = WorkforceProduction.adjust(snapshot, state.copy(fatigue = mapOf("e" to 80)), now)
        assertEquals(62.0, result.totalUnitsPerHour, .001)
        assertEquals(77500L, result.grossPerHourCents)
        assertEquals(1100L, result.energyPerHourCents) // Slower operator does not turn off the spindle.
        assertEquals(76400L, result.netPerHourCents)
    }

    @Test fun breakDisablesMachineFlagThroughputAndEnergyTogether() {
        val result = WorkforceProduction.adjust(snapshot, state.copy(restingUntil = mapOf("e" to now + 1000)), now)
        assertEquals(0.0, result.totalUnitsPerHour, .001)
        assertEquals(0L, result.energyPerHourCents)
        assertEquals(0, result.operatingMachines)
        assertEquals(1, result.idleMachines)
        assertFalse(result.machineProduction.single().isOperating)
        assertEquals(0, result.averageQuality)
    }

    @Test fun expiredBreakRestoresProductionWithoutADatabaseWrite() {
        val onBreak = state.copy(restingUntil = mapOf("e" to now + 1000))
        assertEquals(0, WorkforceProduction.adjust(snapshot, onBreak, now).operatingMachines)
        assertEquals(1, WorkforceProduction.adjust(snapshot, onBreak, now + 1000).operatingMachines)
    }

    @Test fun shiftClosureStopsCurrentProductionButNotPreviouslyWorkedHours() {
        val evening = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 20) }.timeInMillis
        val shift = WorkLifeState()
        assertEquals(0, WorkforceProduction.adjust(snapshot, shift, evening).operatingMachines)
        assertEquals(1, WorkforceProduction.adjust(snapshot, shift, evening, checkSchedule = false).operatingMachines)
    }

    @Test fun oneWorkersBreakDoesNotPenalizeAnotherWorkersMachine() {
        val two = snapshot.copy(totalUnitsPerHour = 200.0, grossPerHourCents = 250000, energyPerHourCents = 2200,
            machineProduction = snapshot.machineProduction + MachineProduction("m2", "e2", 100.0, 80, 10.0, true))
        val result = WorkforceProduction.adjust(two, state.copy(restingUntil = mapOf("e" to now + 1000)), now)
        assertEquals(100.0, result.totalUnitsPerHour, .001)
        assertEquals(1100L, result.energyPerHourCents)
        assertEquals(80, result.averageQuality)
    }

    @Test fun fractionalFatigueIsIndependentOfSettlementFrequency() {
        var frequent = 0.0
        repeat(6) { frequent = FatigueAccrual.advance(frequent, true, false, 1.0 / 6.0, 0.0, 0.0) }
        val hourly = FatigueAccrual.advance(0.0, true, false, 1.0, 0.0, 0.0)
        assertEquals(4.0, frequent, .00001)
        assertEquals(hourly, frequent, .00001)
    }

    @Test fun continuousShiftAndRestStayWithinFatigueBounds() {
        assertEquals(6.5, FatigueAccrual.advance(0.0, true, true, 1.0, 0.0, 0.0), .00001)
        assertEquals(52.0, FatigueAccrual.advance(80.0, true, true, 1.0, 0.0, 1.0), .00001)
        assertEquals(100.0, FatigueAccrual.advance(99.0, true, true, 8.0, 0.0, 0.0), .00001)
        assertEquals(0.0, FatigueAccrual.advance(1.0, true, false, 0.0, 8.0, 0.0), .00001)
    }
}
