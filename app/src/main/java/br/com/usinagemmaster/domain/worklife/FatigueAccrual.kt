package br.com.usinagemmaster.domain.worklife

/** Keep fractional fatigue between settlements; round only for display and thresholds. */
object FatigueAccrual {
    fun advance(current: Double, assigned: Boolean, continuous: Boolean,
        workHours: Double, pausedHours: Double, restHours: Double): Double {
        val working = workHours.coerceAtLeast(0.0)
        val resting = restHours.coerceIn(0.0, working)
        val rate = when {
            !assigned -> 1.2
            continuous -> 6.5
            else -> 4.0
        }
        return (current + rate * (working - resting) - 8.5 * pausedHours.coerceAtLeast(0.0) - 28.0 * resting)
            .coerceIn(0.0, 100.0)
    }
}
