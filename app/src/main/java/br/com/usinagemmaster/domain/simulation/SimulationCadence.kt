package br.com.usinagemmaster.domain.simulation

/** Regras centrais do ritmo idle. A economia fecha em ciclos curtos de 10 minutos. */
object SimulationCadence {
    const val CYCLE_MINUTES = 10L
    const val CYCLE_MILLIS = CYCLE_MINUTES * 60_000L
    const val MAX_OFFLINE_MILLIS = 8L * 60L * 60L * 1000L

    fun settledMillis(elapsedMillis: Long): Long =
        (elapsedMillis.coerceAtLeast(0L) / CYCLE_MILLIS) * CYCLE_MILLIS

    fun unitsPerCycle(unitsPerHour: Double): Double = unitsPerHour / 6.0
    fun centsPerCycle(centsPerHour: Long): Long = centsPerHour / 6L

    fun millisUntilNextCycle(lastSimulationAt: Long, now: Long): Long {
        if (lastSimulationAt <= 0L) return CYCLE_MILLIS
        val elapsed = (now - lastSimulationAt).coerceAtLeast(0L)
        val remainder = elapsed % CYCLE_MILLIS
        return if (remainder == 0L && elapsed > 0L) 0L else CYCLE_MILLIS - remainder
    }
}
