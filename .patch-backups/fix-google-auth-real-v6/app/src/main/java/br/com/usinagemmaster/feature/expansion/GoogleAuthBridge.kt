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
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

object GoogleAuthBridge {
    suspend fun signInUser(context: Context): FirebaseUser {
        require(FirebaseApp.getApps(context).isNotEmpty()) {
            "Firebase não inicializado. Confirme app/google-services.json e o plugin com.google.gms.google-services."
        }
        val resourceId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        require(resourceId != 0) {
            "default_web_client_id não encontrado. Baixe novamente o google-services.json após ativar Google no Firebase."
        }
        val serverClientId = context.getString(resourceId).trim()
        require(serverClientId.isNotBlank()) { "Web Client ID do Google está vazio." }

        val manager = CredentialManager.create(context)
        val credential = try {
            manager.getCredential(context, request(serverClientId, true)).credential
        } catch (_: NoCredentialException) {
            manager.getCredential(context, request(serverClientId, false)).credential
        } catch (first: Exception) {
            // Alguns aparelhos/emuladores retornam erro no filtro de contas autorizadas mesmo com
            // Firebase/OAuth corretos. A segunda tentativa abre todas as contas disponíveis.
            manager.getCredential(context, request(serverClientId, false)).credential
        }

        require(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "O Google retornou uma credencial inesperada."
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(google.idToken, null)
        val auth = FirebaseAuth.getInstance()
        val current = auth.currentUser

        // Se o app já possuía uma identidade Firebase (ex.: anônima/outro provedor),
        // vincula Google à MESMA UID em vez de trocar de conta e perder associação remota.
        if (current != null && current.providerData.none { it.providerId == "google.com" }) {
            val linked = runCatching { current.linkWithCredential(firebaseCredential).await().user }.getOrNull()
            if (linked != null) return linked
        }

        return auth.signInWithCredential(firebaseCredential).await().user
            ?: error("Firebase autenticou, mas não retornou usuário.")
    }

    // Mantem compatibilidade com as telas V2/V3 que esperam String.
    suspend fun signIn(context: Context): String {
        val user = signInUser(context)
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

    fun currentUser(): FirebaseUser? = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
    fun signOut() { FirebaseAuth.getInstance().signOut() }
}
