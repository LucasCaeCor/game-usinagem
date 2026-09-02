package br.com.usinagemmaster.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.usinagemmaster.domain.model.DashboardStatus
import br.com.usinagemmaster.domain.model.ProductionSnapshot
import br.com.usinagemmaster.domain.repository.GameRepository
import br.com.usinagemmaster.data.preferences.PlayerProfilePreferences
import br.com.usinagemmaster.domain.social.LocalPlayerProfile
import br.com.usinagemmaster.domain.social.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: GameRepository,
    playerProfilePreferences: PlayerProfilePreferences,
    private val social: SocialRepository
) : ViewModel() {
    val state = repo.dashboard().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        DashboardStatus()
    )

    val playerProfile = playerProfilePreferences.profile.stateIn(viewModelScope, SharingStarted.Eagerly, LocalPlayerProfile())
    private var lastPresenceAt = 0L

    val production = repo.production().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ProductionSnapshot()
    )

    fun tickProduction() = viewModelScope.launch {
        repo.tickProduction()
        val now = System.currentTimeMillis()
        if (now - lastPresenceAt >= 60_000L && social.isFirebaseConfigured() && playerProfile.value.onboardingComplete) {
            social.publishProfile(playerProfile.value, state.value, production.value)
            lastPresenceAt = now
        }
    }


    // V10_RENAME_COMPANY_VM
    fun renameCompany(newName: String) = viewModelScope.launch {
        repo.renameCompany(newName)
    }
}
