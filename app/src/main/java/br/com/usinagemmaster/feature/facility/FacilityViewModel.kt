package br.com.usinagemmaster.feature.facility

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.usinagemmaster.domain.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FacilityViewModel @Inject constructor(private val repo: GameRepository): ViewModel() {
    val dashboard = repo.dashboard().stateIn(viewModelScope, SharingStarted.Eagerly, br.com.usinagemmaster.domain.model.DashboardStatus())
    val upgrades = repo.facilities().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _message = MutableStateFlow<String?>(null); val message = _message.asStateFlow()
    fun expand() = viewModelScope.launch { _message.value = repo.upgradeWarehouse().fold({ "Galpão expandido em 50 m²" }, { it.message }) }
    fun clearMessage(){ _message.value = null }
}
