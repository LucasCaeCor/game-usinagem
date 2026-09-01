package br.com.usinagemmaster.feature.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.usinagemmaster.domain.catalog.MachineCatalog
import br.com.usinagemmaster.domain.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StoreViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {
    val catalog = MachineCatalog.all
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    fun buy(type: String) = viewModelScope.launch {
        _message.value = repo.buyMachine(type).fold({ "Máquina adquirida com sucesso" }, { it.message ?: "Não foi possível comprar" })
    }
    fun clearMessage() { _message.value = null }
}
