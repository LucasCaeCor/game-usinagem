package br.com.usinagemmaster.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.usinagemmaster.data.preferences.PlayerProfilePreferences
import br.com.usinagemmaster.domain.model.DashboardStatus
import br.com.usinagemmaster.domain.model.ProductionSnapshot
import br.com.usinagemmaster.domain.repository.GameRepository
import br.com.usinagemmaster.domain.social.LocalPlayerProfile
import br.com.usinagemmaster.domain.social.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferences: PlayerProfilePreferences,
    private val social: SocialRepository,
    private val game: GameRepository
) : ViewModel() {
    val profile = preferences.profile.stateIn(viewModelScope, SharingStarted.Eagerly, LocalPlayerProfile())
    private val dashboard = game.dashboard().stateIn(viewModelScope, SharingStarted.Eagerly, DashboardStatus())
    private val production = game.production().stateIn(viewModelScope, SharingStarted.Eagerly, ProductionSnapshot())

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun save(profile: LocalPlayerProfile) = viewModelScope.launch {
        val name = profile.displayName.trim()
        if (name.length !in 3..24) {
            _message.value = "Use um nome entre 3 e 24 caracteres"
            return@launch
        }
        val finalProfile = profile.copy(displayName = name, onboardingComplete = true)
        preferences.save(finalProfile)
        if (social.isFirebaseConfigured()) {
            social.publishProfile(finalProfile, dashboard.value, production.value)
        }
        _message.value = "Personagem salvo"
    }

    fun clearMessage() { _message.value = null }
}
