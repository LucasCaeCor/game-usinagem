package br.com.usinagemmaster.feature.expansion

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

object GoogleAuthBridge {
    suspend fun signIn(context: Context): String {
        require(FirebaseApp.getApps(context).isNotEmpty()) {
            "Firebase não inicializado. Confirme app/google-services.json e o plugin com.google.gms.google-services."
        }

        val resourceId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        require(resourceId != 0) {
            "default_web_client_id não encontrado. Baixe NOVAMENTE o google-services.json depois de ativar Google no Firebase."
        }
        val serverClientId = context.getString(resourceId).trim()
        require(serverClientId.isNotBlank()) { "Web Client ID do Google está vazio no google-services.json" }

        val manager = CredentialManager.create(context)

        // Primeiro tenta contas já autorizadas. Se ainda não houver nenhuma, abre TODAS as contas Google do aparelho.
        val credential = try {
            manager.getCredential(context, request(serverClientId, true)).credential
        } catch (_: NoCredentialException) {
            manager.getCredential(context, request(serverClientId, false)).credential
        }

        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "O Google retornou um tipo de credencial inesperado"
        }

        val google = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(google.idToken, null)
        val result = FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
        val user = result.user ?: error("Firebase autenticou, mas não retornou usuário")
        return user.displayName ?: user.email ?: "Jogador"
    }

    private fun request(serverClientId: String, authorizedOnly: Boolean): GetCredentialRequest {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(authorizedOnly)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .build()
        return GetCredentialRequest.Builder().addCredentialOption(option).build()
    }

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
    }
}
