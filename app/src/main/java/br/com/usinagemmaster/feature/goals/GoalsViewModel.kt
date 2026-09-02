package br.com.usinagemmaster.feature.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.usinagemmaster.data.local.entity.GoalEntity
import br.com.usinagemmaster.domain.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(private val repo: GameRepository): ViewModel() {
    val goals = repo.goals().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<GoalEntity>())
    private val _message=MutableStateFlow<String?>(null); val message=_message.asStateFlow()
    fun claim(goal: GoalEntity)=viewModelScope.launch { _message.value=repo.claimGoal(goal).fold({"Recompensa coletada"},{it.message}) }
    fun clearMessage(){_message.value=null}
}
