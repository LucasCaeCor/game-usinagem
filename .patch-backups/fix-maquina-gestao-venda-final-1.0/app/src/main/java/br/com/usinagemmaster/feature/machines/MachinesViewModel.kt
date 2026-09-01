package br.com.usinagemmaster.feature.machines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.usinagemmaster.core.util.Formatters
import br.com.usinagemmaster.data.local.entity.EmployeeEntity
import br.com.usinagemmaster.data.local.entity.MachineEntity
import br.com.usinagemmaster.data.preferences.EngagementState
import br.com.usinagemmaster.data.preferences.GamePreferences
import br.com.usinagemmaster.data.preferences.GameSettings
import br.com.usinagemmaster.data.preferences.PlayerProfilePreferences
import br.com.usinagemmaster.domain.social.LocalPlayerProfile
import br.com.usinagemmaster.domain.social.SocialRepository
import br.com.usinagemmaster.domain.model.DashboardStatus
import br.com.usinagemmaster.domain.model.ProductionSnapshot
import br.com.usinagemmaster.domain.repository.GameRepository
import br.com.usinagemmaster.domain.simulation.EconomyBalance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToLong

@HiltViewModel
class MachinesViewModel @Inject constructor(
    private val repo: GameRepository,
    private val prefs: GamePreferences,
    playerProfilePreferences: PlayerProfilePreferences,
    private val social: SocialRepository
) : ViewModel() {
    val machines = repo.machines().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<MachineEntity>())
    val employees = repo.employees().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<EmployeeEntity>())
    val production = repo.production().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductionSnapshot())
    val dashboard = repo.dashboard().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardStatus())
    val settings = prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameSettings())
    val engagement = prefs.engagement.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EngagementState())
    val playerProfile = playerProfilePreferences.profile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalPlayerProfile())

    private var lastPresenceAt = 0L
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun move(machineId: String, x: Int, y: Int) = viewModelScope.launch {
        _message.value = repo.moveMachine(machineId, x, y).exceptionOrNull()?.message
    }

    fun assign(machineId: String, employeeId: String?) = viewModelScope.launch {
        _message.value = repo.assignEmployee(machineId, employeeId).fold(
            { if (employeeId == null) "Operador removido" else "Operador atribuído" },
            { it.message ?: "Não foi possível atribuir o operador" }
        )
    }

    fun repair(machineId: String) = viewModelScope.launch {
        _message.value = repo.repairMachine(machineId).fold(
            { "Manutenção concluída" },
            { it.message ?: "Falha na manutenção" }
        )
    }

    fun tickProduction() = viewModelScope.launch {
        repo.tickProduction()
        val now = System.currentTimeMillis()
        if (now - lastPresenceAt >= 60_000L && social.isFirebaseConfigured() && playerProfile.value.onboardingComplete) {
            social.publishProfile(playerProfile.value, dashboard.value, production.value)
            lastPresenceAt = now
        }
    }

    fun claimDailyReward() = viewModelScope.launch {
        if (!prefs.claimDailyReward()) {
            _message.value = "A recompensa diária de hoje já foi resgatada"
            return@launch
        }
        val cashReward = maxOf(150_000L, production.value.netPer10MinutesCents)
        repo.grantBonusCash(cashReward, "Recompensa diária").fold(
            onSuccess = {
                _message.value = "Recompensa diária: +${Formatters.money(cashReward)} e +${EconomyBalance.DAILY_BOOST_TOKENS} impulsos"
            },
            onFailure = {
                _message.value = it.message ?: "Não foi possível resgatar a recompensa"
            }
        )
    }

    /**
     * score vai de 0 a 1. O minigame sempre premia quem conclui, mas precisão
     * alta rende mais caixa e um impulso extra.
     */
    fun completeMinigame(score: Float) = viewModelScope.launch {
        if (!engagement.value.minigameAvailable) {
            _message.value = "O minigame ainda está recarregando"
            return@launch
        }

        val safeScore = score.coerceIn(0f, 1f)
        val tokens = if (safeScore >= .82f) 2 else 1
        if (!prefs.recordMinigameReward(tokens)) {
            _message.value = "O minigame ainda está recarregando"
            return@launch
        }

        val baseCycle = production.value.netPer10MinutesCents.coerceAtLeast(80_000L)
        val cashReward = (baseCycle * (.30 + safeScore * .70)).roundToLong()
        repo.grantBonusCash(cashReward, "Minigame de produção • precisão ${(safeScore * 100).toInt()}%").fold(
            onSuccess = {
                _message.value = "Minigame: +${Formatters.money(cashReward)} e +$tokens impulso${if (tokens > 1) "s" else ""}"
            },
            onFailure = {
                _message.value = it.message ?: "Falha ao entregar prêmio do minigame"
            }
        )
    }

    fun accelerateProduction() = viewModelScope.launch {
        if (!prefs.consumeBoostToken()) {
            _message.value = "Sem impulsos. Ganhe mais no minigame ou na recompensa diária."
            return@launch
        }

        repo.accelerateProduction10Minutes().fold(
            onSuccess = { earned ->
                _message.value = "+10 min produzidos agora • +${Formatters.money(earned)}"
            },
            onFailure = { error ->
                // Devolve a ficha se não foi possível produzir.
                prefs.addBoostTokens(1)
                _message.value = error.message ?: "Não foi possível acelerar a produção"
            }
        )
    }

    fun clearMessage() { _message.value = null }
}
