package br.com.usinagemmaster.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
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

private val Context.workLifeDataStore by preferencesDataStore(name = "work_life_v11")

@Singleton
class WorkLifeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val PLAYER_ID = "__main_player__"
        private const val BREAK_MILLIS = 2L * 60L * 60L * 1000L
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
     * Em 12h, apenas 07:00–19:00 entra como tempo produtivo.
     * O restante é pausa real: sem produção e sem consumir prazo de contrato.
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
                if (hour < 7) {
                    set(Calendar.HOUR_OF_DAY, 7)
                } else if (hour < 19) {
                    set(Calendar.HOUR_OF_DAY, 19)
                } else {
                    add(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 7)
                }
            }.timeInMillis.coerceAtLeast(cursor + 1L)

            val segmentEnd = minOf(endMillis, boundary)
            if (hour in 7..18) work += segmentEnd - cursor
            cursor = segmentEnd
        }
        return WorkSlice(workMillis = work, pausedMillis = (total - work).coerceAtLeast(0L))
    }

    fun productivityMultiplier(
        state: WorkLifeState,
        activeIds: Collection<String>,
    ): Double {
        if (activeIds.isEmpty()) return 1.0
        return activeIds.map(state::efficiency).average().coerceIn(0.35, 1.0)
    }

    /**
     * Atualiza a exaustão sem mexer no Room. Em 12h, o período fora do
     * expediente recupera energia. Em 24h a exaustão cresce mais rápido.
     * Trabalhadores enviados à Copa recuperam energia agressivamente.
     */
    suspend fun advance(
        employees: List<EmployeeEntity>,
        slice: WorkSlice,
        eventTime: Long,
    ) {
        val before = snapshot()
        val workHours = slice.workHours
        val homeHours = slice.pausedHours

        context.workLifeDataStore.edit { prefs ->
            val fatigue = parseIntMap(prefs[Keys.fatigue]).toMutableMap()
            val resting = parseLongMap(prefs[Keys.resting]).toMutableMap()
            resting.entries.removeAll { it.value <= eventTime }

            val allIds = employees.map { it.id } + PLAYER_ID
            allIds.forEach { id ->
                val current = (fatigue[id] ?: 0).toDouble()
                val restingNow = (before.restingUntil[id] ?: 0L) > eventTime
                val employee = employees.firstOrNull { it.id == id }
                val activelyAssigned = id == PLAYER_ID || employee?.assignedMachineId != null

                val workDelta = when {
                    restingNow -> -34.0 * workHours
                    before.mode == FactoryScheduleMode.CONTINUOUS_24H && activelyAssigned -> 6.5 * workHours
                    before.mode == FactoryScheduleMode.CONTINUOUS_24H -> 3.0 * workHours
                    activelyAssigned -> 4.0 * workHours
                    else -> 1.5 * workHours
                }
                val homeDelta = -8.5 * homeHours
                val next = (current + workDelta + homeDelta).roundToInt().coerceIn(0, 100)
                fatigue[id] = next

                if (before.mode == FactoryScheduleMode.CONTINUOUS_24H &&
                    before.autoRest &&
                    next >= 88 &&
                    (resting[id] ?: 0L) <= eventTime
                ) {
                    resting[id] = eventTime + BREAK_MILLIS
                }
            }

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
            val p = token.lastIndexOf('=')
            if (p <= 0) null else token.substring(0, p) to (token.substring(p + 1).toIntOrNull() ?: 0)
        }.toMap()

    private fun parseLongMap(raw: String?): Map<String, Long> =
        raw.orEmpty().split("|").mapNotNull { token ->
            val p = token.lastIndexOf('=')
            if (p <= 0) null else token.substring(0, p) to (token.substring(p + 1).toLongOrNull() ?: 0L)
        }.toMap()

    private fun encodeIntMap(values: Map<String, Int>): String =
        values.entries.joinToString("|") { "${it.key}=${it.value.coerceIn(0, 100)}" }

    private fun encodeLongMap(values: Map<String, Long>): String =
        values.entries.joinToString("|") { "${it.key}=${it.value.coerceAtLeast(0L)}" }
}
