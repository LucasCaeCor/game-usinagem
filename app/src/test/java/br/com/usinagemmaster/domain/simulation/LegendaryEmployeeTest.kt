package br.com.usinagemmaster.domain.simulation

import br.com.usinagemmaster.domain.catalog.LegendaryEmployeeCatalog
import br.com.usinagemmaster.domain.model.EmployeeRuntime
import br.com.usinagemmaster.domain.model.EmployeeSpecialty
import br.com.usinagemmaster.domain.model.MachineRuntime
import br.com.usinagemmaster.domain.model.MachineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegendaryEmployeeTest {
    @Test
    fun legendaryCatalogHasElevenUniqueEmployees() {
        val all = LegendaryEmployeeCatalog.all
        assertEquals(11, all.size)
        assertEquals(11, all.map { it.code }.distinct().size)
        assertEquals(11, all.map { it.name }.distinct().size)
    }

    @Test
    fun tatuProducesMoreThanEquivalentRegularTurner() {
        val lathe = MachineRuntime("m1", MachineType.MECHANICAL_LATHE.name, 1, 900)
        val regular = EmployeeRuntime("e1", EmployeeSpecialty.TURNER.name, 7, 84, "Cuidadoso", "m1")
        val tatu = EmployeeRuntime(
            "e2", EmployeeSpecialty.TURNER.name, 7, 84,
            "Casca grossa", "m1", "tatu_banhado"
        )

        val regularResult = ProductionEngine.calculate(listOf(lathe), listOf(regular))
        val tatuResult = ProductionEngine.calculate(listOf(lathe), listOf(tatu))

        assertTrue(tatuResult.totalUnitsPerHour > regularResult.totalUnitsPerHour)
    }

    @Test
    fun supportLegendariesAffectFactory() {
        val lathe = MachineRuntime("m1", MachineType.MECHANICAL_LATHE.name, 1, 900)
        val turner = EmployeeRuntime("e1", EmployeeSpecialty.TURNER.name, 7, 84, "Cuidadoso", "m1")
        val magrao = EmployeeRuntime(
            "s1", EmployeeSpecialty.STOCK_ASSISTANT.name, 7, 88,
            "Logística rápida", null, "magrao"
        )
        val nikao = EmployeeRuntime(
            "q1", EmployeeSpecialty.QUALITY_INSPECTOR.name, 9, 76,
            "Controle total", null, "nikao_narizudo"
        )

        val base = ProductionEngine.calculate(listOf(lathe), listOf(turner))
        val supported = ProductionEngine.calculate(listOf(lathe), listOf(turner, magrao, nikao))

        assertTrue(supported.totalUnitsPerHour > base.totalUnitsPerHour)
        assertTrue(supported.averageQuality > base.averageQuality)
    }
}
