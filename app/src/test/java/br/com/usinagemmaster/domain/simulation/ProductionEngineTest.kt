package br.com.usinagemmaster.domain.simulation

import br.com.usinagemmaster.domain.model.EmployeeRuntime
import br.com.usinagemmaster.domain.model.EmployeeSpecialty
import br.com.usinagemmaster.domain.model.MachineRuntime
import br.com.usinagemmaster.domain.model.MachineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionEngineTest {
    private val lathe = MachineRuntime("m1", MachineType.MECHANICAL_LATHE.name, 1, 900)

    @Test
    fun machineWithoutOperatorDoesNotProduce() {
        val result = ProductionEngine.calculate(listOf(lathe), emptyList())
        assertEquals(0.0, result.totalUnitsPerHour, 0.001)
        assertEquals(1, result.idleMachines)
    }

    @Test
    fun compatibleOperatorActivatesProduction() {
        val turner = EmployeeRuntime("e1", EmployeeSpecialty.TURNER.name, 5, 90, "Rápido", "m1")
        val result = ProductionEngine.calculate(listOf(lathe), listOf(turner))
        assertTrue(result.totalUnitsPerHour > 0.0)
        assertTrue(result.netPerHourCents > 0)
        assertEquals(1, result.operatingMachines)
    }

    @Test
    fun specialistProducesMoreThanWrongSpecialty() {
        val turner = EmployeeRuntime("e1", EmployeeSpecialty.TURNER.name, 5, 90, "Cuidadoso", "m1")
        val welder = EmployeeRuntime("e2", EmployeeSpecialty.WELDER.name, 5, 90, "Cuidadoso", "m1")
        val correct = ProductionEngine.calculate(listOf(lathe), listOf(turner))
        val wrong = ProductionEngine.calculate(listOf(lathe), listOf(welder))
        assertTrue(correct.totalUnitsPerHour > wrong.totalUnitsPerHour)
    }
}
