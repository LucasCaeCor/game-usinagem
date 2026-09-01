package br.com.usinagemmaster.domain.simulation

/** Regras centrais de ritmo/economia mobile. */
object EconomyBalance {
    const val PROFIT_MULTIPLIER: Long = 3L
    const val BOOST_CYCLE_MILLIS: Long = 10L * 60L * 1000L
    const val DAILY_BOOST_TOKENS: Int = 2
    const val STARTING_BOOST_TOKENS: Int = 2
    const val MINIGAME_COOLDOWN_MILLIS: Long = 15L * 60L * 1000L

    // Gestão da equipe.
    const val TEAM_SNACK_COST_CENTS: Long = 25_000L // R$ 250,00 por cento de salgados.
    const val SNACK_IMMUNITY_MILLIS: Long = 8L * 60L * 60L * 1000L
    const val EMPLOYEE_IDLE_MAX_MILLIS: Long = 7L * 60L * 1000L
    const val IDLE_CHECK_MIN_MILLIS: Long = 2L * 60L * 1000L
    const val IDLE_CHECK_MAX_MILLIS: Long = 5L * 60L * 1000L
    const val IDLE_EVENT_CHANCE: Float = 0.30f
    const val REPRIMAND_GRACE_MILLIS: Long = 60L * 60L * 1000L

    fun boostedProfit(baseCents: Long): Long =
        (baseCents.coerceAtLeast(0L) * PROFIT_MULTIPLIER)
}
