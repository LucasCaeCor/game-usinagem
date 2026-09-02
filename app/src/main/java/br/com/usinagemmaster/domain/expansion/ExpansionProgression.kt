package br.com.usinagemmaster.domain.expansion

import kotlin.math.max

/**
 * Progressão visual da V7.
 *
 * A fábrica preserva a regra original do jogo: o nível vem da reputação
 * (20 pontos de reputação por nível). Para a UI, cada ponto de reputação
 * é exibido como 100 XP, portanto cada nível da fábrica possui 2.000 XP.
 *
 * O personagem possui XP próprio e independente, salvo no DataStore da expansão.
 */
data class XpProgress(
    val level: Int,
    val current: Long,
    val needed: Long,
    val total: Long,
) {
    val fraction: Float get() = if (needed <= 0L) 1f else (current.toDouble() / needed.toDouble()).toFloat().coerceIn(0f, 1f)
}

object ExpansionProgression {
    const val FACTORY_XP_PER_REPUTATION = 100L
    const val REPUTATION_PER_FACTORY_LEVEL = 20

    fun factory(companyLevel: Int, reputation: Int): XpProgress {
        val safeLevel = companyLevel.coerceAtLeast(1)
        val levelStartRep = (safeLevel - 1) * REPUTATION_PER_FACTORY_LEVEL
        val repInside = (reputation - levelStartRep).coerceIn(0, REPUTATION_PER_FACTORY_LEVEL)
        return XpProgress(
            level = safeLevel,
            current = repInside * FACTORY_XP_PER_REPUTATION,
            needed = REPUTATION_PER_FACTORY_LEVEL * FACTORY_XP_PER_REPUTATION,
            total = reputation.coerceAtLeast(0) * FACTORY_XP_PER_REPUTATION,
        )
    }

    fun playerXpNeededForLevel(level: Int): Long {
        val l = level.coerceAtLeast(1).toLong()
        return 600L + (l - 1L) * 300L + (l - 1L) * (l - 1L) * 45L
    }

    fun totalXpAtStartOfPlayerLevel(level: Int): Long {
        var total = 0L
        var current = 1
        val target = level.coerceIn(1, 100)
        while (current < target) {
            total += playerXpNeededForLevel(current)
            current++
        }
        return total
    }

    fun player(totalXp: Long): XpProgress {
        var remaining = totalXp.coerceAtLeast(0L)
        var level = 1
        while (level < 100) {
            val needed = playerXpNeededForLevel(level)
            if (remaining < needed) return XpProgress(level, remaining, needed, totalXp.coerceAtLeast(0L))
            remaining -= needed
            level++
        }
        return XpProgress(100, 1, 1, totalXp.coerceAtLeast(0L))
    }

    fun characterXpForContract(difficulty: Int, quantity: Int, requiredQuality: Int): Long {
        val diff = difficulty.coerceIn(1, 10)
        val volume = quantity.coerceIn(1, 2_000)
        val quality = requiredQuality.coerceIn(0, 100)
        return 90L + diff * 75L + volume / 4L + quality * 2L
    }

    fun characterXpForResearch(): Long = 120L

    /** XP por completar 48h contratado em outra fábrica. */
    fun characterXpForRental(characterLevel: Int, boostPct: Int): Long =
        450L + characterLevel.coerceAtLeast(1) * 70L + boostPct.coerceIn(0, 25) * 15L
    fun characterXpForPremiumInstall(): Long = 160L
}
