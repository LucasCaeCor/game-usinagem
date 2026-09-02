package br.com.usinagemmaster.feature.contracts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.usinagemmaster.core.util.Formatters
import br.com.usinagemmaster.data.local.entity.ContractEntity
import br.com.usinagemmaster.data.preferences.ExpansionRepository
import br.com.usinagemmaster.domain.expansion.ContractAccess
import br.com.usinagemmaster.domain.expansion.ContractProgression
import br.com.usinagemmaster.domain.expansion.ExpansionState
import br.com.usinagemmaster.domain.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContractsViewModel @Inject constructor(
    private val repo: GameRepository,
    private val expansionRepository: ExpansionRepository,
) : ViewModel() {
    val contracts = repo.contracts().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList<ContractEntity>(),
    )

    val companyLevel = repo.dashboard()
        .map { it.companyLevel }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1)

    val reputation = repo.dashboard()
        .map { it.reputation }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val expansion = expansionRepository.state.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ExpansionState(),
    )

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        viewModelScope.launch { repo.generateContractsIfNeeded() }
    }

    fun access(c: ContractEntity): ContractAccess =
        ContractProgression.access(c, companyLevel.value, expansion.value)

    fun refreshContracts() = viewModelScope.launch { repo.generateContractsIfNeeded() }

    fun accept(c: ContractEntity) = viewModelScope.launch {
        val access = access(c)
        if (!access.allowed) {
            _message.value = access.reason
            return@launch
        }
        _message.value = repo.acceptContract(c).fold(
            { "Contrato aceito • produção iniciada" },
            { it.message ?: "Falha ao aceitar" },
        )
        repo.generateContractsIfNeeded()
    }

    fun complete(c: ContractEntity) = viewModelScope.launch {
        _message.value = repo.completeContract(c).fold(
            {
                val fxp = ContractProgression.factoryXp(c)
                val pxp = ContractProgression.characterXp(c)
                "Contrato concluído • +$fxp XP fábrica • +$pxp XP personagem"
            },
            { it.message ?: "Falha ao concluir" },
        )
        repo.generateContractsIfNeeded()
    }

    fun cancel(c: ContractEntity) = viewModelScope.launch {
        _message.value = repo.cancelContract(c.id).fold(
            { penalty -> "Contrato cancelado • multa: ${Formatters.money(penalty)} • reputação reduzida" },
            { it.message ?: "Não foi possível cancelar" },
        )
        repo.generateContractsIfNeeded()
    }

    fun dismissFailed(c: ContractEntity) = viewModelScope.launch {
        _message.value = repo.dismissFailedContract(c.id).fold(
            { "Contrato com falha removido" },
            { it.message ?: "Não foi possível excluir" },
        )
        repo.generateContractsIfNeeded()
    }

    fun recoverReward(c: ContractEntity) = viewModelScope.launch {
        _message.value = repo.recoverContractReward(c.id).fold(
            { amount -> "Prêmio conferido: ${Formatters.money(amount)}" },
            { it.message ?: "Não foi possível conferir o pagamento" },
        )
    }

    fun clearMessage() { _message.value = null }
}
