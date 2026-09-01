package br.com.usinagemmaster.domain.simulation

import br.com.usinagemmaster.domain.catalog.LegendaryMissionCatalog
import br.com.usinagemmaster.domain.model.EmployeeRuntime
import br.com.usinagemmaster.domain.model.EmployeeSpecialty
import br.com.usinagemmaster.domain.model.MachineProduction
import br.com.usinagemmaster.domain.model.MachineRuntime
import br.com.usinagemmaster.domain.model.MachineType
import br.com.usinagemmaster.domain.model.ProductionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class LegendaryMissionProgressEngineTest {
    private val operatingLathe = MachineRuntime("m1", MachineType.MECHANICAL_LATHE.name, 1, 900)
    private val snapshot = ProductionSnapshot(
        totalUnitsPerHour = 10.0,
        operatingMachines = 1,
        averageQuality = 80,
        machineProduction = listOf(
            MachineProduction("m1", "e1", 10.0, 80, 5.0, true)
        )
    )

    @Test
    fun tatuProgressesOnlyOnCompatibleOperatingMachine() {
        val mission = LegendaryMissionCatalog.byLegendaryCode("tatu_banhado")!!
        val tatu = EmployeeRuntime(
            "e1", EmployeeSpecialty.TURNER.name, 7, 84,
            "Casca grossa", "m1", "tatu_banhado"
        )
        assertEquals(
            5L,
            LegendaryMissionProgressEngine.progressDelta(mission, tatu, listOf(operatingLathe), snapshot, 5)
        )
    }

    @Test
    fun nikaoProgressesWhenQualityThresholdIsMet() {
        val mission = LegendaryMissionCatalog.byLegendaryCode("nikao_narizudo")!!
        val nikao = EmployeeRuntime(
            "q1", EmployeeSpecialty.QUALITY_INSPECTOR.name, 9, 76,
            "Controle total", null, "nikao_narizudo"
        )
        assertEquals(
            3L,
            LegendaryMissionProgressEngine.progressDelta(mission, nikao, listOf(operatingLathe), snapshot, 3)
        )
    }
}
