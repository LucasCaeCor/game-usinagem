package br.com.usinagemmaster.feature.expansion

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * FIX V6
 * - Nunca trata usuário anônimo como conta Google.
 * - O botão "Continuar com Google" usa o fluxo EXPLÍCITO Sign in with Google.
 * - Se já existir uma identidade anônima, tenta vincular Google à mesma UID.
 * - Se a conta Google já pertencer a outra UID Firebase, entra nessa conta sem apagar o save local.
 */
object GoogleAuthBridge {

    fun isGoogleUser(user: FirebaseUser?): Boolean =
        user != null && user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }

    /** Retorna somente usuário realmente autenticado pelo provedor google.com. */
    fun currentGoogleUser(): FirebaseUser? = runCatching {
        FirebaseAuth.getInstance().currentUser?.takeIf(::isGoogleUser)
    }.getOrNull()

    /** Compatibilidade com fixes antigas: daqui para frente "currentUser" significa Google real. */
    fun currentUser(): FirebaseUser? = currentGoogleUser()

    /** Útil apenas para diagnóstico/migração. Pode ser uma identidade anônima. */
    fun rawFirebaseUser(): FirebaseUser? = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()

    suspend fun signInUser(context: Context): FirebaseUser {
        require(FirebaseApp.getApps(context).isNotEmpty()) {
            "Firebase não inicializado. Confira app/google-services.json."
        }

        val resourceId = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName
        )
        require(resourceId != 0) {
            "default_web_client_id não encontrado. Baixe novamente o google-services.json depois de ativar Google no Firebase."
        }
        val serverClientId = context.getString(resourceId).trim()
        require(serverClientId.isNotBlank()) { "Web Client ID do Google está vazio." }

        val activity = context.findActivity()
            ?: error("Login Google precisa ser aberto a partir de uma Activity visível.")

        val manager = CredentialManager.create(activity)

        // Fluxo oficial para um botão explícito "Continuar com Google".
        // Não usa auto-select e não considera uma sessão anônima como login Google.
        val googleOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleOption)
            .build()

        val credential = try {
            manager.getCredential(activity, request).credential
        } catch (_: GetCredentialCancellationException) {
            error("Login Google cancelado.")
        } catch (_: NoCredentialException) {
            error("Nenhuma conta Google disponível neste aparelho. Adicione uma conta Google em Configurações > Contas e tente novamente.")
        } catch (e: GetCredentialException) {
            error("Falha ao abrir o seletor Google: ${e.type} • ${e.message ?: e::class.java.simpleName}")
        }

        require(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) { "O Google retornou um tipo de credencial inesperado." }

        val google = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(google.idToken, null)
        val auth = FirebaseAuth.getInstance()
        val before = auth.currentUser

        val user = try {
            when {
                // Se já é Google real, reautentica/seleciona explicitamente a conta escolhida.
                isGoogleUser(before) -> auth.signInWithCredential(firebaseCredential).await().user

                // Preserve a UID anônima quando possível.
                before != null -> try {
                    before.linkWithCredential(firebaseCredential).await().user
                } catch (_: FirebaseAuthUserCollisionException) {
                    // A conta Google já existe no projeto Firebase: entra nela e depois
                    // o AccountLinkStore vincula o save LOCAL atual a essa UID.
                    auth.signInWithCredential(firebaseCredential).await().user
                }

                else -> auth.signInWithCredential(firebaseCredential).await().user
            }
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            error("Firebase recusou o token Google. Confira SHA-1/SHA-256, package br.com.usinagemmaster e o google-services.json atualizado. Detalhe: ${e.message}")
        }

        require(isGoogleUser(user)) {
            "Firebase autenticou uma UID, mas ela não possui o provedor google.com. O login foi interrompido para não vincular o save à conta errada."
        }
        require(!user?.email.isNullOrBlank()) {
            "Google autenticou, mas o Firebase não retornou o e-mail da conta."
        }
        return user!!
    }

    // Compatibilidade com telas V2/V3.
    suspend fun signIn(context: Context): String {
        val user = signInUser(context)
        return user.displayName ?: user.email ?: "Conta Google"
    }

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
    }

    suspend fun signOutAndClear(context: Context) {
        FirebaseAuth.getInstance().signOut()
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
