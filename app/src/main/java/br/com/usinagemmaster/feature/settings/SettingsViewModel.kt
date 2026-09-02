package br.com.usinagemmaster.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.usinagemmaster.data.preferences.GamePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val prefs: GamePreferences): ViewModel() {
    val settings = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, br.com.usinagemmaster.data.preferences.GameSettings())
    fun sound(value:Boolean)=viewModelScope.launch{prefs.setSound(value)}
    fun vibration(value:Boolean)=viewModelScope.launch{prefs.setVibration(value)}
    fun npcSpeech(value:Boolean)=viewModelScope.launch{prefs.setNpcSpeech(value)}
    fun speechDuration(seconds:Int)=viewModelScope.launch{prefs.setSpeechDuration(seconds)}
}
