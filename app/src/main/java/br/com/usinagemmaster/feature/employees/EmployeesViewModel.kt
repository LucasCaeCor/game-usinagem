package br.com.usinagemmaster.feature.employees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.usinagemmaster.data.local.entity.EmployeeEntity
import br.com.usinagemmaster.data.local.entity.LegendaryMissionEntity
import br.com.usinagemmaster.domain.model.DashboardStatus
import br.com.usinagemmaster.domain.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EmployeesViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {
    val employees = repo.employees().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList<EmployeeEntity>()
    )
    val dashboard = repo.dashboard().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        DashboardStatus()
    )
    val legendaryMissions = repo.legendaryMissions().stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList<LegendaryMissionEntity>()
    )

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun hire() = viewModelScope.launch {
        _message.value = repo.hireRandomEmployee().fold(
            { "Funcionário contratado" },
            { it.message ?: "Falha na contratação" }
        )
    }

    fun hireLegendary() = viewModelScope.launch {
        _message.value = repo.hireLegendaryEmployee().fold(
            { name -> "★ LENDÁRIO CONTRATADO: $name" },
            { it.message ?: "Nenhum lendário disponível" }
        )
    }

    fun claimLegendaryMission(mission: LegendaryMissionEntity) = viewModelScope.launch {
        _message.value = repo.claimLegendaryMission(mission).fold(
            { "★ Missão lendária concluída: ${mission.title}" },
            { it.message ?: "Não foi possível coletar a recompensa" }
        )
    }

    fun fire(e: EmployeeEntity) = viewModelScope.launch {
        repo.fireEmployee(e)
        _message.value = if (e.isLegendary) {
            "${e.name} saiu da empresa e pode aparecer novamente no recrutamento"
        } else {
            "Funcionário demitido"
        }
    }

    fun clearMessage() { _message.value = null }
}
