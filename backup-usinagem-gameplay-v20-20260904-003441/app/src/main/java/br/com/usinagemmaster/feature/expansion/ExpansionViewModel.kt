package br.com.usinagemmaster.feature.expansion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.usinagemmaster.data.local.dao.ContractDao
import br.com.usinagemmaster.data.local.entity.ContractEntity
import br.com.usinagemmaster.data.preferences.ExpansionRepository
import br.com.usinagemmaster.data.repository.PremiumMachineInstaller
import br.com.usinagemmaster.data.social.CharacterOffer
import br.com.usinagemmaster.data.social.CharacterRentalService
import br.com.usinagemmaster.domain.expansion.*
import br.com.usinagemmaster.domain.model.ContractStatus
import br.com.usinagemmaster.domain.repository.GameRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExpansionUiState(
    val expansion: ExpansionState = ExpansionState(),
    val companyLevel: Int = 1,
    val reputation: Int = 0,
    val cashCents: Long = 0L,
    val activeContracts: List<ContractEntity> = emptyList(),
    val completedContracts: List<ContractEntity> = emptyList(),
    val offers: List<CharacterOffer> = emptyList(),
    val accountName: String? = null,
    val accountEmail: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val lastReward: GachaReward? = null,
)

@HiltViewModel
class ExpansionViewModel @Inject constructor(
    private val expansionRepository: ExpansionRepository,
    private val gameRepository: GameRepository,
    private val rentalService: CharacterRentalService,
    private val contractDao: ContractDao,
    private val premiumMachineInstaller: PremiumMachineInstaller,
) : ViewModel() {
    private val extras = MutableStateFlow(ExpansionUiState())

    val uiState: StateFlow<ExpansionUiState> = combine(
        expansionRepository.state,
        gameRepository.dashboard(),
        gameRepository.contracts(),
        contractDao.observeCompleted(),
        extras,
    ) { expansion, dashboard, contracts, completed, extra ->
        extra.copy(
            expansion = expansion,
            companyLevel = dashboard.companyLevel,
            reputation = dashboard.reputation,
            cashCents = dashboard.cashCents,
            activeContracts = contracts.filter { it.status == ContractStatus.ACTIVE.name },
            completedContracts = completed,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ExpansionUiState())

    init {
        refreshAccount()
        viewModelScope.launch { expansionRepository.clearExpiredRemoteHire() }
        viewModelScope.launch {
            val dashboard = gameRepository.dashboard().first()
            expansionRepository.ensurePlayerXpBaseline(dashboard.companyLevel)
        }
        viewModelScope.launch { claimRentalExperience(showMessage = false) }
    }

    fun refreshAccount(message: String? = null) {
        val user = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
        extras.update { it.copy(accountName = user?.displayName, accountEmail = user?.email, message = message) }
    }

    fun showMessage(message: String) = extras.update { it.copy(message = message) }

    fun chooseSpecialty(code: String) = action { expansionRepository.chooseSpecialty(code, uiState.value.companyLevel) }
    fun unlockCompanySkill(id: String) = action { expansionRepository.unlockCompanySkill(id, uiState.value.companyLevel) }
    fun unlockPlayerSkill(id: String) = action { expansionRepository.unlockPlayerSkill(id, uiState.value.companyLevel) }
    fun equipSkin(id: String) = action { expansionRepository.equipSkin(id, uiState.value.companyLevel) }
    fun equipCharacter(id: String) = action { expansionRepository.equipCharacter(id, uiState.value.companyLevel) }
    fun buyPremiumCharacter(id: String) = action { expansionRepository.buyPremiumCharacter(id, uiState.value.companyLevel) }

    fun buyPremiumMachine(id: String) = action {
        expansionRepository.buyPremiumMachine(id, uiState.value.companyLevel)
        val installed = premiumMachineInstaller.install(id)
        extras.update { it.copy(message = installed.message) }
    }
    fun installPremiumMachine(id: String) = action {
        val installed = premiumMachineInstaller.install(id)
        extras.update { it.copy(message = installed.message) }
    }
    fun bindTool(contractId: String, toolId: String?) = action { expansionRepository.bindTool(contractId, toolId) }

    fun claimDailyTicket() = action {
        val total = expansionRepository.claimDailyTicket()
        extras.update { it.copy(message = "Ficha diária coletada. Total: $total") }
    }

    suspend fun drawWheelReward(): GachaReward {
        check(!extras.value.busy) { "Aguarde a operação atual" }
        extras.update { it.copy(busy = true, message = null, lastReward = null) }
        return try {
            expansionRepository.rollGacha(uiState.value.companyLevel)
        } finally {
            extras.update { it.copy(busy = false) }
        }
    }

    fun revealWheelReward(reward: GachaReward) {
        extras.update { it.copy(lastReward = reward, message = "${reward.rarity.label}: ${reward.title}") }
    }

    fun roll() = action {
        val reward = expansionRepository.rollGacha(uiState.value.companyLevel)
        extras.update { it.copy(lastReward = reward, message = "${reward.rarity.label}: ${reward.title}") }
    }

    fun dismissCompletedContract(id: String) = action {
        contractDao.dismissCompleted(id)
        extras.update { it.copy(message = "Contrato dispensado do histórico. O lançamento financeiro foi mantido.") }
    }

    fun publishCharacter() = action {
        claimRentalExperience(showMessage = false)
        val state = uiState.value.expansion
        val name = FirebaseAuth.getInstance().currentUser?.displayName ?: "Mestre da Usinagem"
        rentalService.publishMyCharacter(name, state.playerSkills, state.playerRentalBoostPct(), state.playerXp)
        extras.update { it.copy(message = "Personagem publicado para contratos de 48h") }
    }

    fun loadOffers() = action {
        val gained = claimRentalExperience(showMessage = false)
        val offers = rentalService.offers()
        extras.update {
            it.copy(
                offers = offers,
                message = when {
                    gained > 0L -> "+$gained XP de experiência externa coletados"
                    offers.isEmpty() -> "Nenhuma oferta livre agora"
                    else -> null
                },
            )
        }
    }

    fun collectRentalXp() = action {
        val gained = claimRentalExperience(showMessage = false)
        extras.update {
            it.copy(message = if (gained > 0L) "+$gained XP do personagem por trabalhos em outras fábricas" else "Nenhum aluguel concluído com XP pendente")
        }
    }

    fun hire(offer: CharacterOffer) = action {
        val result = rentalService.hire(offer)
        expansionRepository.activateRemoteHire(result.ownerUid, result.playerName, result.boostPct, result.endsAt)
        extras.update { it.copy(message = "${result.playerName} contratado por 48h • +${result.boostPct}% produção") }
    }

    fun withdrawCharacter() = action {
        rentalService.withdrawMyCharacter()
        extras.update { it.copy(message = "Oferta removida do mercado") }
    }

    fun clearMessage() = extras.update { it.copy(message = null) }

    private suspend fun claimRentalExperience(showMessage: Boolean): Long {
        val user = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
        if (user?.email.isNullOrBlank()) return 0L
        return runCatching {
            var total = 0L
            rentalService.completedRentalXpForOwner().forEach { reward ->
                if (expansionRepository.claimRentalXpReward(reward.rentalId, reward.xp)) total += reward.xp
            }
            if (showMessage && total > 0L) extras.update { it.copy(message = "+$total XP de experiência externa") }
            total
        }.getOrDefault(0L)
    }

    private fun action(block: suspend () -> Unit) {
        if (extras.value.busy) return
        viewModelScope.launch {
            extras.update { it.copy(busy = true, message = null) }
            runCatching { block() }
                .onFailure { error -> extras.update { it.copy(message = error.message ?: "Falha na operação") } }
            extras.update { it.copy(busy = false) }
        }
    }
}
