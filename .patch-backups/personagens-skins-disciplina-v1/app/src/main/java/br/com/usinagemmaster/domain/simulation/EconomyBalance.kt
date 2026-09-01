package br.com.usinagemmaster.domain.simulation

/**
 * Regras de ritmo da economia mobile.
 *
 * O valor base calculado pelo ProductionEngine continua sendo neutro para
 * facilitar balanceamento e testes. O multiplicador é aplicado no fechamento
 * de caixa e na UI, mantendo produção física, qualidade e energia intactas.
 */
object EconomyBalance {
    const val PROFIT_MULTIPLIER: Long = 3L
    const val BOOST_CYCLE_MILLIS: Long = 10L * 60L * 1000L
    const val DAILY_BOOST_TOKENS: Int = 2
    const val STARTING_BOOST_TOKENS: Int = 2
    const val MINIGAME_COOLDOWN_MILLIS: Long = 15L * 60L * 1000L

    fun boostedProfit(baseCents: Long): Long =
        (baseCents.coerceAtLeast(0L) * PROFIT_MULTIPLIER)
}
