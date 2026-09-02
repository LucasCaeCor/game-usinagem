package br.com.usinagemmaster.feature.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.usinagemmaster.data.local.entity.FinancialTransactionEntity
import br.com.usinagemmaster.domain.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class FinanceViewModel @Inject constructor(repo: GameRepository): ViewModel() {
    val transactions = repo.finances().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<FinancialTransactionEntity>())
}
