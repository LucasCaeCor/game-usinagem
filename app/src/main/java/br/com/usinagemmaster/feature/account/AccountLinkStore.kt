package br.com.usinagemmaster.feature.account

import android.content.Context
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class AccountLinkState(
    val localSaveId: String,
    val linkedUid: String?,
    val linkedEmail: String?,
    val linkedName: String?,
    val linkedAt: Long,
) {
    fun isLinkedTo(user: FirebaseUser?): Boolean = user != null && linkedUid == user.uid
    val isLinked: Boolean get() = !linkedUid.isNullOrBlank()
}

data class AccountLinkResult(
    val state: AccountLinkState,
    val cloudRegistryUpdated: Boolean,
)

/**
 * Vinculo de identidade do jogo.
 * IMPORTANTE: nao toca no banco Room. O save existente continua exatamente no aparelho.
 * A conta Google passa a ser a identidade dona deste save local.
 */
object AccountLinkStore {
    private const val PREFS = "usinagem_account_link_v1"
    private const val KEY_LOCAL_SAVE_ID = "local_save_id"
    private const val KEY_UID = "linked_google_uid"
    private const val KEY_EMAIL = "linked_google_email"
    private const val KEY_NAME = "linked_google_name"
    private const val KEY_LINKED_AT = "linked_at"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun state(context: Context): AccountLinkState {
        val p = prefs(context)
        var local = p.getString(KEY_LOCAL_SAVE_ID, null)
        if (local.isNullOrBlank()) {
            local = UUID.randomUUID().toString()
            p.edit().putString(KEY_LOCAL_SAVE_ID, local).apply()
        }
        return AccountLinkState(
            localSaveId = local,
            linkedUid = p.getString(KEY_UID, null),
            linkedEmail = p.getString(KEY_EMAIL, null),
            linkedName = p.getString(KEY_NAME, null),
            linkedAt = p.getLong(KEY_LINKED_AT, 0L),
        )
    }

    suspend fun linkCurrentProgress(context: Context, user: FirebaseUser): AccountLinkResult {
        val before = state(context)
        if (before.linkedUid != null && before.linkedUid != user.uid) {
            error("Este progresso já está vinculado a outra conta Google. Entre com a conta originalmente vinculada.")
        }

        val now = System.currentTimeMillis()
        prefs(context).edit()
            .putString(KEY_UID, user.uid)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_NAME, user.displayName)
            .putLong(KEY_LINKED_AT, now)
            .apply()

        val after = state(context)
        val cloudOk = runCatching {
            FirebaseFirestore.getInstance()
                .collection("player_accounts")
                .document(user.uid)
                .set(
                    mapOf(
                        "uid" to user.uid,
                        "email" to user.email,
                        "displayName" to user.displayName,
                        "localSaveId" to after.localSaveId,
                        "provider" to "google",
                        "lastLinkedAt" to FieldValue.serverTimestamp(),
                        "clientLinkedAtMs" to now,
                    ),
                    SetOptions.merge(),
                )
                .await()
        }.isSuccess

        return AccountLinkResult(after, cloudOk)
    }

    suspend fun retryCloudRegistry(context: Context, user: FirebaseUser): Boolean {
        val s = state(context)
        if (!s.isLinkedTo(user)) return false
        return runCatching {
            FirebaseFirestore.getInstance().collection("player_accounts").document(user.uid)
                .set(
                    mapOf(
                        "uid" to user.uid,
                        "email" to user.email,
                        "displayName" to user.displayName,
                        "localSaveId" to s.localSaveId,
                        "provider" to "google",
                        "lastLinkedAt" to FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge(),
                ).await()
        }.isSuccess
    }
}
