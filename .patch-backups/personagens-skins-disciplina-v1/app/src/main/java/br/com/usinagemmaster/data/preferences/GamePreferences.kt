package br.com.usinagemmaster.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import br.com.usinagemmaster.domain.simulation.EconomyBalance
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("game_settings")

data class GameSettings(
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val npcSpeech: Boolean = true,
    val speechDurationSeconds: Int = 8
)

data class EngagementState(
    val boostTokens: Int = EconomyBalance.STARTING_BOOST_TOKENS,
    val lastDailyRewardDay: Int = 0,
    val lastMinigameAt: Long = 0L
) {
    val dailyRewardAvailable: Boolean get() = lastDailyRewardDay != currentLocalDayKey()

    fun minigameRemainingMillis(now: Long = System.currentTimeMillis()): Long =
        (EconomyBalance.MINIGAME_COOLDOWN_MILLIS - (now - lastMinigameAt)).coerceAtLeast(0L)

    val minigameAvailable: Boolean get() = minigameRemainingMillis() == 0L
}

private fun currentLocalDayKey(now: Long = System.currentTimeMillis()): Int {
    val calendar = Calendar.getInstance().apply { timeInMillis = now }
    return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
}

@Singleton
class GamePreferences @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val SOUND = booleanPreferencesKey("sound")
        val VIBRATION = booleanPreferencesKey("vibration")
        val NPC_SPEECH = booleanPreferencesKey("npc_speech")
        val SPEECH_DURATION = intPreferencesKey("speech_duration_seconds")
        val BOOST_TOKENS = intPreferencesKey("boost_tokens")
        val LAST_DAILY_REWARD_DAY = intPreferencesKey("last_daily_reward_day")
        val LAST_MINIGAME_AT = longPreferencesKey("last_minigame_at")
    }

    val settings: Flow<GameSettings> = context.dataStore.data.map { prefs ->
        val storedSpeech = prefs[Keys.SPEECH_DURATION] ?: 8
        val migratedSpeech = when (storedSpeech) {
            4 -> 5
            7 -> 8
            10 -> 12
            else -> storedSpeech
        }
        GameSettings(
            sound = prefs[Keys.SOUND] ?: true,
            vibration = prefs[Keys.VIBRATION] ?: true,
            npcSpeech = prefs[Keys.NPC_SPEECH] ?: true,
            speechDurationSeconds = migratedSpeech.coerceIn(5, 12)
        )
    }

    val engagement: Flow<EngagementState> = context.dataStore.data.map { prefs ->
        EngagementState(
            boostTokens = (prefs[Keys.BOOST_TOKENS] ?: EconomyBalance.STARTING_BOOST_TOKENS).coerceAtLeast(0),
            lastDailyRewardDay = prefs[Keys.LAST_DAILY_REWARD_DAY] ?: 0,
            lastMinigameAt = prefs[Keys.LAST_MINIGAME_AT] ?: 0L
        )
    }

    suspend fun setSound(value: Boolean) = context.dataStore.edit { it[Keys.SOUND] = value }
    suspend fun setVibration(value: Boolean) = context.dataStore.edit { it[Keys.VIBRATION] = value }
    suspend fun setNpcSpeech(value: Boolean) = context.dataStore.edit { it[Keys.NPC_SPEECH] = value }
    suspend fun setSpeechDuration(seconds: Int) = context.dataStore.edit { it[Keys.SPEECH_DURATION] = seconds.coerceIn(5, 12) }

    suspend fun claimDailyReward(): Boolean {
        var claimed = false
        val today = currentLocalDayKey()
        context.dataStore.edit { prefs ->
            val lastDay = prefs[Keys.LAST_DAILY_REWARD_DAY] ?: 0
            if (lastDay != today) {
                val currentTokens = prefs[Keys.BOOST_TOKENS] ?: EconomyBalance.STARTING_BOOST_TOKENS
                prefs[Keys.BOOST_TOKENS] = currentTokens + EconomyBalance.DAILY_BOOST_TOKENS
                prefs[Keys.LAST_DAILY_REWARD_DAY] = today
                claimed = true
            }
        }
        return claimed
    }

    suspend fun consumeBoostToken(): Boolean {
        var consumed = false
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.BOOST_TOKENS] ?: EconomyBalance.STARTING_BOOST_TOKENS
            if (current > 0) {
                prefs[Keys.BOOST_TOKENS] = current - 1
                consumed = true
            }
        }
        return consumed
    }

    suspend fun addBoostTokens(amount: Int) {
        if (amount <= 0) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.BOOST_TOKENS] ?: EconomyBalance.STARTING_BOOST_TOKENS
            prefs[Keys.BOOST_TOKENS] = current + amount
        }
    }

    suspend fun recordMinigameReward(tokens: Int): Boolean {
        var recorded = false
        val now = System.currentTimeMillis()
        context.dataStore.edit { prefs ->
            val last = prefs[Keys.LAST_MINIGAME_AT] ?: 0L
            if (now - last >= EconomyBalance.MINIGAME_COOLDOWN_MILLIS) {
                val current = prefs[Keys.BOOST_TOKENS] ?: EconomyBalance.STARTING_BOOST_TOKENS
                prefs[Keys.BOOST_TOKENS] = current + tokens.coerceAtLeast(0)
                prefs[Keys.LAST_MINIGAME_AT] = now
                recorded = true
            }
        }
        return recorded
    }
}
