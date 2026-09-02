package br.com.usinagemmaster.feature.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.usinagemmaster.data.social.CommunityFactory
import br.com.usinagemmaster.data.social.CommunityFactoryService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CommunityFactoryUiState(
    val factories: List<CommunityFactory> = emptyList(),
    val selected: CommunityFactory? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class CommunityFactoryViewModel @Inject constructor(private val service: CommunityFactoryService) : ViewModel() {
    private val _state = MutableStateFlow(CommunityFactoryUiState())
    val state = _state.asStateFlow()

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(busy = true, error = null) }
        runCatching {
            runCatching { service.publishMine() }
            service.list()
        }.onSuccess { list -> _state.update { it.copy(factories = list, busy = false) } }
            .onFailure { e -> _state.update { it.copy(busy = false, error = e.message ?: "Falha ao abrir a comunidade") } }
    }
    fun select(factory: CommunityFactory) { _state.update { it.copy(selected = factory) } }
    fun backToList() { _state.update { it.copy(selected = null) } }
}
