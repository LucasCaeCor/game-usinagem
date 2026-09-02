package br.com.usinagemmaster.domain.worklife

import java.util.Calendar
import kotlin.math.roundToInt

enum class FactoryScheduleMode(val code: String, val label: String) {
    SHIFT_12H("shift_12h", "Turno 12h • 07:00–19:00"),
    CONTINUOUS_24H("continuous_24h", "Operação 24h");

    companion object {
        fun fromCode(code: String?): FactoryScheduleMode =
            entries.firstOrNull { it.code == code } ?: SHIFT_12H
    }
}

data class WorkSlice(
    val workMillis: Long,
    val pausedMillis: Long,
) {
    val workHours: Double get() = workMillis / 3_600_000.0
    val pausedHours: Double get() = pausedMillis / 3_600_000.0
}

data class WorkLifeState(
    val modeCode: String = FactoryScheduleMode.SHIFT_12H.code,
    val fatigue: Map<String, Int> = emptyMap(),
    val restingUntil: Map<String, Long> = emptyMap(),
    val autoRest: Boolean = true,
) {
    val mode: FactoryScheduleMode get() = FactoryScheduleMode.fromCode(modeCode)

    fun exhaustion(id: String): Int = (fatigue[id] ?: 0).coerceIn(0, 100)

    fun isResting(id: String, now: Long = System.currentTimeMillis()): Boolean =
        (restingUntil[id] ?: 0L) > now

    fun restingEmployeeIds(now: Long = System.currentTimeMillis()): Set<String> =
        restingUntil.filterValues { it > now }.keys

    fun factoryOpen(now: Long = System.currentTimeMillis()): Boolean {
        if (mode == FactoryScheduleMode.CONTINUOUS_24H) return true
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        return calendar.get(Calendar.HOUR_OF_DAY) in 7..18
    }

    fun workerOnFloor(id: String, now: Long = System.currentTimeMillis()): Boolean =
        factoryOpen(now) && !isResting(id, now)

    fun efficiency(id: String): Double = when (exhaustion(id)) {
        in 0..34 -> 1.00
        in 35..59 -> 0.94
        in 60..79 -> 0.82
        in 80..94 -> 0.62
        else -> 0.38
    }

    fun exhaustionLabel(id: String): String = when (exhaustion(id)) {
        in 0..34 -> "Descansado"
        in 35..59 -> "Cansaço leve"
        in 60..79 -> "Cansado"
        in 80..94 -> "Exausto"
        else -> "Limite físico"
    }

    fun averageExhaustion(ids: Collection<String>): Int {
        if (ids.isEmpty()) return 0
        return ids.map(::exhaustion).average().roundToInt().coerceIn(0, 100)
    }

    fun statusText(now: Long = System.currentTimeMillis()): String = when {
        mode == FactoryScheduleMode.CONTINUOUS_24H -> "🟢 Operação 24h • exaustão ativa"
        factoryOpen(now) -> "🟢 Turno aberto • equipe trabalhando até 19:00"
        else -> "🏠 Turno encerrado • equipe em casa • contratos pausados"
    }
}
