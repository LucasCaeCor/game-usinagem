package br.com.usinagemmaster.domain.simulation

import br.com.usinagemmaster.domain.catalog.LegendaryEmployeeCatalog
import br.com.usinagemmaster.domain.catalog.LegendaryMissionCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegendaryMissionCatalogTest {
    @Test
    fun everyLegendaryHasOneMission() {
        val legendaryCodes = LegendaryEmployeeCatalog.all.map { it.code }.toSet()
        val missionCodes = LegendaryMissionCatalog.all.map { it.legendaryCode }.toSet()
        assertEquals(11, LegendaryMissionCatalog.all.size)
        assertEquals(legendaryCodes, missionCodes)
    }

    @Test
    fun everyMissionHasPositiveTargetAndReward() {
        assertTrue(LegendaryMissionCatalog.all.all { it.target > 0 && it.rewardCents > 0 })
    }

    @Test
    fun everyLegendaryHasDialogue() {
        LegendaryEmployeeCatalog.all.forEachIndexed { index, employee ->
            assertTrue(LegendaryEmployeeCatalog.quote(employee.code, true, index).orEmpty().isNotBlank())
            assertTrue(LegendaryEmployeeCatalog.quote(employee.code, false, index).orEmpty().isNotBlank())
        }
    }
}
