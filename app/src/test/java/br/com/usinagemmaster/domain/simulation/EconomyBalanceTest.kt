package br.com.usinagemmaster.domain.simulation

import br.com.usinagemmaster.domain.model.ProductionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class EconomyBalanceTest {
    @Test
    fun tenMinuteProfitIsTripled() {
        val snapshot = ProductionSnapshot(netPerHourCents = 600_000L)
        // Base seria 100.000 centavos em 10 min; v8 paga 3x.
        assertEquals(300_000L, snapshot.netPer10MinutesCents)
    }

    @Test
    fun boostCycleMatchesTenMinuteSettlement() {
        assertEquals(SimulationCadence.CYCLE_MILLIS, EconomyBalance.BOOST_CYCLE_MILLIS)
    }
}
