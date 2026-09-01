package br.com.usinagemmaster.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.usinagemmaster.domain.social.LocalPlayerProfile
import br.com.usinagemmaster.domain.social.PlayerAvatar
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playerProfileDataStore by preferencesDataStore("player_profile")

@Singleton
class PlayerProfilePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val GENDER = stringPreferencesKey("gender")
        val SKIN_STYLE = stringPreferencesKey("skin_style")
        val BODY_TYPE = stringPreferencesKey("body_type")
        val SKIN_TONE = stringPreferencesKey("skin_tone")
        val HAIR_STYLE = stringPreferencesKey("hair_style")
        val HAIR_COLOR = stringPreferencesKey("hair_color")
        val UNIFORM_COLOR = stringPreferencesKey("uniform_color")
        val HELMET_COLOR = stringPreferencesKey("helmet_color")
        val ACCESSORY = stringPreferencesKey("accessory")
        val COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    val profile: Flow<LocalPlayerProfile> = context.playerProfileDataStore.data.map { prefs ->
        LocalPlayerProfile(
            displayName = prefs[Keys.DISPLAY_NAME].orEmpty(),
            avatar = PlayerAvatar(
                gender = prefs[Keys.GENDER] ?: "MALE",
                skinStyle = prefs[Keys.SKIN_STYLE] ?: "WORKSHOP",
                bodyType = prefs[Keys.BODY_TYPE] ?: "STANDARD",
                skinTone = prefs[Keys.SKIN_TONE] ?: "MEDIUM",
                hairStyle = prefs[Keys.HAIR_STYLE] ?: "SHORT",
                hairColor = prefs[Keys.HAIR_COLOR] ?: "DARK",
                uniformColor = prefs[Keys.UNIFORM_COLOR] ?: "NAVY",
                helmetColor = prefs[Keys.HELMET_COLOR] ?: "YELLOW",
                accessory = prefs[Keys.ACCESSORY] ?: "NONE"
            ),
            onboardingComplete = prefs[Keys.COMPLETE] ?: false
        )
    }

    suspend fun save(profile: LocalPlayerProfile) {
        context.playerProfileDataStore.edit { prefs ->
            prefs[Keys.DISPLAY_NAME] = profile.displayName.trim().take(24)
            prefs[Keys.GENDER] = profile.avatar.gender
            prefs[Keys.SKIN_STYLE] = profile.avatar.skinStyle
            prefs[Keys.BODY_TYPE] = profile.avatar.bodyType
            prefs[Keys.SKIN_TONE] = profile.avatar.skinTone
            prefs[Keys.HAIR_STYLE] = profile.avatar.hairStyle
            prefs[Keys.HAIR_COLOR] = profile.avatar.hairColor
            prefs[Keys.UNIFORM_COLOR] = profile.avatar.uniformColor
            prefs[Keys.HELMET_COLOR] = profile.avatar.helmetColor
            prefs[Keys.ACCESSORY] = profile.avatar.accessory
            prefs[Keys.COMPLETE] = profile.onboardingComplete
        }
    }
}
