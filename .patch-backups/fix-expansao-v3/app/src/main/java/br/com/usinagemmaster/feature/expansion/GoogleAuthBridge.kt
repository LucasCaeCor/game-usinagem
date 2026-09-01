package br.com.usinagemmaster.feature.expansion

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

object GoogleAuthBridge {
    suspend fun signIn(context: Context): String {
        val resourceId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        require(resourceId != 0) {
            "default_web_client_id não encontrado. Atualize app/google-services.json e habilite Google no Firebase Authentication."
        }
        val serverClientId = context.getString(resourceId)
        val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = CredentialManager.create(context).getCredential(context, request)
        val credential = response.credential
        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Credencial Google inválida"
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(google.idToken, null)
        val authResult = FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
        return authResult.user?.displayName ?: authResult.user?.email ?: "Jogador"
    }

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
    }
}
