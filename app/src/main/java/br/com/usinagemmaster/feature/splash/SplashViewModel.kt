package br.com.usinagemmaster.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.usinagemmaster.domain.repository.GameRepository
import br.com.usinagemmaster.domain.repository.OfflineReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SplashState(val loading: Boolean = true, val offlineReport: OfflineReport? = null)

@HiltViewModel
class SplashViewModel @Inject constructor(private val repo: GameRepository) : ViewModel() {
    private val _state = MutableStateFlow(SplashState())
    val state: StateFlow<SplashState> = _state
    init { viewModelScope.launch { _state.value = SplashState(false, repo.initialize()) } }
}
