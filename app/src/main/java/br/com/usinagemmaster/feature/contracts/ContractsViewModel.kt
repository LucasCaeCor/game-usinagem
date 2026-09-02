package br.com.usinagemmaster.feature.contracts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.usinagemmaster.core.util.Formatters
import br.com.usinagemmaster.data.local.entity.ContractEntity
import br.com.usinagemmaster.domain.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContractsViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {
    val contracts = repo.contracts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ContractEntity>())
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init { viewModelScope.launch { repo.generateContractsIfNeeded() } }

    fun accept(c: ContractEntity) = viewModelScope.launch {
        _message.value = repo.acceptContract(c).fold({ "Contrato aceito" }, { it.message ?: "Falha ao aceitar" })
    }
    fun complete(c: ContractEntity) = viewModelScope.launch {
        _message.value = repo.completeContract(c).fold({ "Contrato concluído e prêmio liquidado" }, { it.message ?: "Falha ao concluir" })
    }
    fun cancel(c: ContractEntity) = viewModelScope.launch {
        _message.value = repo.cancelContract(c.id).fold(
            { penalty -> "Contrato cancelado • multa paga: ${Formatters.money(penalty)} • reputação reduzida" },
            { it.message ?: "Não foi possível cancelar o contrato" }
        )
    }
    fun dismissFailed(c: ContractEntity) = viewModelScope.launch {
        _message.value = repo.dismissFailedContract(c.id).fold(
            { "Contrato com falha excluído do histórico" },
            { it.message ?: "Não foi possível excluir" }
        )
    }
    fun recoverReward(c: ContractEntity) = viewModelScope.launch {
        _message.value = repo.recoverContractReward(c.id).fold(
            { amount -> "Prêmio recuperado e creditado: ${Formatters.money(amount)}" },
            { it.message ?: "Não foi possível recuperar o prêmio" }
        )
    }
    fun clearMessage() { _message.value = null }
}
