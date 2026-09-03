package br.com.usinagemmaster.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.usinagemmaster.data.local.entity.EmployeeEntity
import br.com.usinagemmaster.domain.worklife.FactoryScheduleMode
import br.com.usinagemmaster.domain.worklife.WorkLifeState
import br.com.usinagemmaster.domain.worklife.WorkSlice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private val Context.workLifeDataStore by preferencesDataStore(name = "work_life_v15")

@Singleton
class WorkLifeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val PLAYER_ID = "__main_player__"
        const val BREAK_MILLIS = 2L * 60L * 60L * 1000L
        private const val AUTO_REST_AT = 88
    }

    private object Keys {
        val mode = stringPreferencesKey("mode")
        val fatigue = stringPreferencesKey("fatigue")
        val resting = stringPreferencesKey("resting")
        val autoRest = booleanPreferencesKey("auto_rest")
    }

    val state: Flow<WorkLifeState> = context.workLifeDataStore.data.map(::decode)

    suspend fun snapshot(): WorkLifeState = state.first()

    suspend fun setMode(mode: FactoryScheduleMode) {
        context.workLifeDataStore.edit { it[Keys.mode] = mode.code }
    }

    suspend fun setAutoRest(enabled: Boolean) {
        context.workLifeDataStore.edit { it[Keys.autoRest] = enabled }
    }

    suspend fun sendToBreak(id: String, now: Long = System.currentTimeMillis()) {
        context.workLifeDataStore.edit { prefs ->
            val resting = parseLongMap(prefs[Keys.resting]).toMutableMap()
            resting[id] = now + BREAK_MILLIS
            prefs[Keys.resting] = encodeLongMap(resting)
        }
    }

    suspend fun returnFromBreak(id: String) {
        context.workLifeDataStore.edit { prefs ->
            val resting = parseLongMap(prefs[Keys.resting]).toMutableMap()
            resting.remove(id)
            prefs[Keys.resting] = encodeLongMap(resting)
        }
    }

    /**
     * No modo 12h somente 07:00–19:00 é tempo produtivo.
     * O restante é tempo pausado e será devolvido ao prazo do contrato.
     */
    fun slice(startMillis: Long, endMillis: Long, mode: FactoryScheduleMode): WorkSlice {
        if (endMillis <= startMillis) return WorkSlice(0L, 0L)
        val total = endMillis - startMillis
        if (mode == FactoryScheduleMode.CONTINUOUS_24H) return WorkSlice(total, 0L)

        var cursor = startMillis
        var work = 0L

        while (cursor < endMillis) {
            val cal = Calendar.getInstance().apply { timeInMillis = cursor }
            val hour = cal.get(Calendar.HOUR_OF_DAY)

            val boundary = (cal.clone() as Calendar).apply {
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                when {
                    hour < 7 -> set(Calendar.HOUR_OF_DAY, 7)
                    hour < 19 -> set(Calendar.HOUR_OF_DAY, 19)
                    else -> {
                        add(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 7)
                    }
                }
            }.timeInMillis.coerceAtLeast(cursor + 1L)

            val segmentEnd = minOf(endMillis, boundary)
            if (hour in 7..18) work += segmentEnd - cursor
            cursor = segmentEnd
        }

        return WorkSlice(
            workMillis = work,
            pausedMillis = (total - work).coerceAtLeast(0L),
        )
    }

    fun productivityMultiplier(state: WorkLifeState, activeIds: Collection<String>): Double {
        if (activeIds.isEmpty()) return 1.0
        return activeIds.map(state::efficiency).average().coerceIn(0.35, 1.0)
    }

    /**
     * Atualiza cansaço individual.
     *
     * 12h:
     * - trabalhando no turno: cansa;
     * - fora do turno: recupera em casa.
     *
     * 24h:
     * - cansa mais rápido;
     * - funcionários na Copa recuperam rapidamente;
     * - auto descanso opcional ao chegar em 88%.
     */
    suspend fun advance(
        employees: List<EmployeeEntity>,
        slice: WorkSlice,
        eventTime: Long,
    ) {
        val before = snapshot()
        val startTime = (eventTime - slice.totalMillis).coerceAtLeast(0L)

        context.workLifeDataStore.edit { prefs ->
            val fatigue = parseIntMap(prefs[Keys.fatigue]).toMutableMap()
            val resting = parseLongMap(prefs[Keys.resting]).toMutableMap()
            val allIds = employees.map { it.id } + PLAYER_ID

            allIds.forEach { id ->
                val employee = employees.firstOrNull { it.id == id }
                val assigned = id == PLAYER_ID || employee?.assignedMachineId != null
                val oldFatigue = (fatigue[id] ?: 0).toDouble()

                val restUntil = before.restingUntil[id] ?: 0L
                val restOverlapMillis = if (restUntil > startTime) {
                    (minOf(eventTime, restUntil) - startTime).coerceAtLeast(0L)
                        .coerceAtMost(slice.workMillis)
                } else 0L

                val restHours = restOverlapMillis / 3_600_000.0
                val effectiveWorkHours = (slice.workHours - restHours).coerceAtLeast(0.0)

                val workDelta = when {
                    !assigned -> 1.2 * effectiveWorkHours
                    before.mode == FactoryScheduleMode.CONTINUOUS_24H -> 6.5 * effectiveWorkHours
                    else -> 4.0 * effectiveWorkHours
                }

                val homeRecovery = 8.5 * slice.pausedHours
                val copaRecovery = 28.0 * restHours
                val next = (oldFatigue + workDelta - homeRecovery - copaRecovery)
                    .roundToInt()
                    .coerceIn(0, 100)

                fatigue[id] = next

                if (
                    before.mode == FactoryScheduleMode.CONTINUOUS_24H &&
                    before.autoRest &&
                    assigned &&
                    next >= AUTO_REST_AT &&
                    (resting[id] ?: 0L) <= eventTime
                ) {
                    resting[id] = eventTime + BREAK_MILLIS
                }
            }

            resting.entries.removeAll { it.value <= eventTime }

            prefs[Keys.fatigue] = encodeIntMap(fatigue)
            prefs[Keys.resting] = encodeLongMap(resting)
        }
    }

    private fun decode(prefs: Preferences): WorkLifeState = WorkLifeState(
        modeCode = prefs[Keys.mode] ?: FactoryScheduleMode.SHIFT_12H.code,
        fatigue = parseIntMap(prefs[Keys.fatigue]),
        restingUntil = parseLongMap(prefs[Keys.resting]),
        autoRest = prefs[Keys.autoRest] ?: true,
    )

    private fun parseIntMap(raw: String?): Map<String, Int> =
        raw.orEmpty().split("|").mapNotNull { token ->
            val pos = token.lastIndexOf('=')
            if (pos <= 0) null
            else token.substring(0, pos) to (token.substring(pos + 1).toIntOrNull() ?: 0)
        }.toMap()

    private fun parseLongMap(raw: String?): Map<String, Long> =
        raw.orEmpty().split("|").mapNotNull { token ->
            val pos = token.lastIndexOf('=')
            if (pos <= 0) null
            else token.substring(0, pos) to (token.substring(pos + 1).toLongOrNull() ?: 0L)
        }.toMap()

    private fun encodeIntMap(values: Map<String, Int>): String =
        values.entries.joinToString("|") { "${it.key}=${it.value.coerceIn(0, 100)}" }

    private fun encodeLongMap(values: Map<String, Long>): String =
        values.entries.joinToString("|") { "${it.key}=${it.value.coerceAtLeast(0L)}" }
}
