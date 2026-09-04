package br.com.usinagemmaster.feature.account

import androidx.lifecycle.ViewModel
import br.com.usinagemmaster.data.cloud.CloudSaveRepository
import br.com.usinagemmaster.data.cloud.CloudSaveStatus
import br.com.usinagemmaster.data.cloud.CloudSyncResult
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AccountCloudSaveViewModel @Inject constructor(
    private val repository: CloudSaveRepository,
) : ViewModel() {
    fun status(): CloudSaveStatus = repository.localStatus()
    suspend fun synchronize(user: FirebaseUser, saveId: String): CloudSyncResult = repository.synchronize(user, saveId)
    suspend fun forceUpload(user: FirebaseUser, saveId: String): CloudSyncResult = repository.forceUpload(user, saveId)
    suspend fun forceRestore(user: FirebaseUser): CloudSyncResult = repository.forceRestore(user)
}
