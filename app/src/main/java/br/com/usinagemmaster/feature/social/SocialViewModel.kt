package br.com.usinagemmaster.feature.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.usinagemmaster.data.preferences.GamePreferences
import br.com.usinagemmaster.data.preferences.PlayerProfilePreferences
import br.com.usinagemmaster.domain.model.DashboardStatus
import br.com.usinagemmaster.domain.model.ProductionSnapshot
import br.com.usinagemmaster.domain.repository.GameRepository
import br.com.usinagemmaster.domain.social.LocalPlayerProfile
import br.com.usinagemmaster.domain.social.OnlinePlayer
import br.com.usinagemmaster.domain.social.SocialConnectionState
import br.com.usinagemmaster.domain.social.SocialHelpGift
import br.com.usinagemmaster.domain.social.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SocialViewModel @Inject constructor(
    private val social: SocialRepository,
    private val playerPrefs: PlayerProfilePreferences,
    private val gamePrefs: GamePreferences,
    game: GameRepository
) : ViewModel() {
    val profile = playerPrefs.profile.stateIn(viewModelScope, SharingStarted.Eagerly, LocalPlayerProfile())
    private val dashboard = game.dashboard().stateIn(viewModelScope, SharingStarted.Eagerly, DashboardStatus())
    private val production = game.production().stateIn(viewModelScope, SharingStarted.Eagerly, ProductionSnapshot())

    private val _connection = MutableStateFlow(SocialConnectionState(configured = social.isFirebaseConfigured()))
    val connection = _connection.asStateFlow()
    private val _players = MutableStateFlow<List<OnlinePlayer>>(emptyList())
    val players = _players.asStateFlow()
    private val _gifts = MutableStateFlow<List<SocialHelpGift>>(emptyList())
    val gifts = _gifts.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private var playersJob: Job? = null
    private var giftsJob: Job? = null

    fun connect() = viewModelScope.launch {
        val configured = social.isFirebaseConfigured()
        if (!configured) {
            _connection.value = SocialConnectionState(configured = false, connected = false, message = "Firebase não configurado")
            return@launch
        }
        social.connect().fold(
            onSuccess = { uid ->
                _connection.value = SocialConnectionState(configured = true, connected = true, uid = uid)
                if (profile.value.onboardingComplete) publishPresence(showMessage = false)
                observeSocialData()
            },
            onFailure = { error ->
                _connection.value = SocialConnectionState(configured = true, connected = false, message = error.message)
            }
        )
    }

    fun refreshPresence() = viewModelScope.launch {
        if (_connection.value.connected && profile.value.onboardingComplete) publishPresence(showMessage = false)
    }

    fun publishPresence(showMessage: Boolean = true) = viewModelScope.launch {
        social.publishProfile(profile.value, dashboard.value, production.value).fold(
            onSuccess = { if (showMessage) _message.value = "Perfil online atualizado" },
            onFailure = { if (showMessage) _message.value = it.message ?: "Falha ao publicar perfil" }
        )
    }

    fun sendHelp(player: OnlinePlayer) = viewModelScope.launch {
        social.sendHelp(player.uid, profile.value.displayName).fold(
            onSuccess = { _message.value = "Apoio enviado para ${player.displayName}: +10 min de produção" },
            onFailure = { _message.value = it.message ?: "Não foi possível enviar apoio" }
        )
    }

    fun claimHelp(gift: SocialHelpGift) = viewModelScope.launch {
        social.claimHelp(gift.id).fold(
            onSuccess = { boosts ->
                gamePrefs.addBoostTokens(boosts)
                _message.value = "Apoio de ${gift.fromName}: +$boosts impulso${if (boosts > 1) "s" else ""}"
            },
            onFailure = { _message.value = it.message ?: "Não foi possível resgatar o apoio" }
        )
    }

    private fun observeSocialData() {
        playersJob?.cancel()
        giftsJob?.cancel()
        playersJob = viewModelScope.launch { social.observePlayers().collect { _players.value = it } }
        giftsJob = viewModelScope.launch { social.observeIncomingHelp().collect { _gifts.value = it } }
    }

    fun clearMessage() { _message.value = null }
}
