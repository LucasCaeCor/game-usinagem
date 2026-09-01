package br.com.usinagemmaster.data.social

import android.content.Context
import br.com.usinagemmaster.domain.model.DashboardStatus
import br.com.usinagemmaster.domain.model.ProductionSnapshot
import br.com.usinagemmaster.domain.social.LocalPlayerProfile
import br.com.usinagemmaster.domain.social.OnlinePlayer
import br.com.usinagemmaster.domain.social.PlayerAvatar
import br.com.usinagemmaster.domain.social.SocialHelpGift
import br.com.usinagemmaster.domain.social.SocialRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSocialRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : SocialRepository {

    private fun appOrNull(): FirebaseApp? {
        FirebaseApp.getApps(context).firstOrNull()?.let { return it }
        return runCatching { FirebaseApp.initializeApp(context) }.getOrNull()
    }

    private fun authOrNull(): FirebaseAuth? = appOrNull()?.let { FirebaseAuth.getInstance(it) }
    private fun dbOrNull(): FirebaseFirestore? = appOrNull()?.let { FirebaseFirestore.getInstance(it) }

    override fun isFirebaseConfigured(): Boolean = appOrNull() != null

    override suspend fun connect(): Result<String> = runCatching {
        val auth = authOrNull() ?: error("Firebase ainda não configurado. Adicione app/google-services.json.")
        val existing = auth.currentUser
        if (existing != null) return@runCatching existing.uid
        auth.signInAnonymously().await().user?.uid ?: error("Não foi possível criar a sessão online")
    }

    override suspend fun publishProfile(
        profile: LocalPlayerProfile,
        dashboard: DashboardStatus,
        production: ProductionSnapshot
    ): Result<Unit> = runCatching {
        require(profile.displayName.trim().length >= 3) { "Crie seu personagem e escolha um nome antes de publicar" }
        val uid = connect().getOrThrow()
        val db = dbOrNull() ?: error("Firebase indisponível")
        val avatar = profile.avatar
        val payload = mapOf(
            "uid" to uid,
            "displayName" to profile.displayName.trim().take(24),
            "companyName" to dashboard.companyName,
            "companyLevel" to dashboard.companyLevel,
            "reputation" to dashboard.reputation,
            "machineCount" to dashboard.machines,
            "employeeCount" to dashboard.employees,
            "productionPer10Minutes" to production.totalUnitsPer10Minutes,
            "gender" to avatar.gender,
            "skinStyle" to avatar.skinStyle,
            "bodyType" to avatar.bodyType,
            "skinTone" to avatar.skinTone,
            "hairStyle" to avatar.hairStyle,
            "hairColor" to avatar.hairColor,
            "uniformColor" to avatar.uniformColor,
            "helmetColor" to avatar.helmetColor,
            "accessory" to avatar.accessory,
            "lastSeenAt" to System.currentTimeMillis(),
            "updatedAt" to System.currentTimeMillis()
        )
        db.collection("players").document(uid).set(payload, SetOptions.merge()).await()
    }

    override fun observePlayers(): Flow<List<OnlinePlayer>> = callbackFlow {
        val db = dbOrNull()
        val myUid = authOrNull()?.currentUser?.uid
        if (db == null || myUid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = db.collection("players")
            .orderBy("reputation", Query.Direction.DESCENDING)
            .limit(60)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val values = snapshot?.documents.orEmpty()
                    .mapNotNull(::mapPlayer)
                    .filter { it.uid != myUid }
                trySend(values)
            }
        awaitClose { registration.remove() }
    }

    override fun observeIncomingHelp(): Flow<List<SocialHelpGift>> = callbackFlow {
        val db = dbOrNull()
        val myUid = authOrNull()?.currentUser?.uid
        if (db == null || myUid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = db.collection("helps")
            .whereEqualTo("toUid", myUid)
            .whereEqualTo("claimed", false)
            .limit(30)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val gifts = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val fromUid = doc.getString("fromUid") ?: return@mapNotNull null
                    val toUid = doc.getString("toUid") ?: return@mapNotNull null
                    SocialHelpGift(
                        id = doc.id,
                        fromUid = fromUid,
                        fromName = doc.getString("fromName") ?: "Outro jogador",
                        toUid = toUid,
                        createdAt = doc.numberLong("createdAt"),
                        claimed = doc.getBoolean("claimed") ?: false,
                        rewardBoosts = (doc.numberLong("rewardBoosts").toInt()).coerceIn(1, 3)
                    )
                }.sortedByDescending { it.createdAt }
                trySend(gifts)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun sendHelp(toUid: String, fromName: String): Result<Unit> = runCatching {
        val uid = connect().getOrThrow()
        require(toUid.isNotBlank() && toUid != uid) { "Jogador inválido" }
        val db = dbOrNull() ?: error("Firebase indisponível")
        val dayKey = currentDayKey()
        val id = "${dayKey}_${uid}_${toUid}"
        val ref = db.collection("helps").document(id)

        // Não fazemos tx.get(ref) antes da criação. Em um documento diário que ainda
        // não existe, esse GET era avaliado pelas regras como leitura e retornava
        // PERMISSION_DENIED antes que o CREATE pudesse ser autorizado.
        val payload = mapOf(
            "fromUid" to uid,
            "fromName" to fromName.trim().take(24).ifBlank { "Outro dono" },
            "toUid" to toUid,
            "createdAt" to System.currentTimeMillis(),
            "claimed" to false,
            "rewardBoosts" to 1
        )

        try {
            // Documento determinístico por dia. Se ele já existir, o set vira UPDATE;
            // nossas regras não permitem ao remetente sobrescrever um apoio existente.
            ref.set(payload).await()
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                error("Não foi possível enviar o apoio. Se você já ajudou este jogador hoje, aguarde amanhã. Se for a primeira tentativa, publique as regras do arquivo firebase/firestore.rules no Firebase Console.")
            }
            throw e
        }
        Unit
    }

    override suspend fun claimHelp(giftId: String): Result<Int> = runCatching {
        val uid = connect().getOrThrow()
        val db = dbOrNull() ?: error("Firebase indisponível")
        val ref = db.collection("helps").document(giftId)
        db.runTransaction { tx ->
            val doc = tx.get(ref)
            require(doc.exists()) { "Apoio não encontrado" }
            require(doc.getString("toUid") == uid) { "Esse apoio pertence a outro jogador" }
            require(doc.getBoolean("claimed") != true) { "Apoio já resgatado" }
            val reward = doc.numberLong("rewardBoosts").toInt().coerceIn(1, 3)
            tx.update(ref, mapOf("claimed" to true, "claimedAt" to System.currentTimeMillis()))
            reward
        }.await()
    }

    private fun mapPlayer(doc: DocumentSnapshot): OnlinePlayer? {
        val uid = doc.getString("uid") ?: doc.id.takeIf { it.isNotBlank() } ?: return null
        return OnlinePlayer(
            uid = uid,
            displayName = doc.getString("displayName") ?: "Dono de oficina",
            companyName = doc.getString("companyName") ?: "Usinagem",
            companyLevel = doc.numberLong("companyLevel").toInt().coerceAtLeast(1),
            reputation = doc.numberLong("reputation").toInt().coerceAtLeast(0),
            machineCount = doc.numberLong("machineCount").toInt().coerceAtLeast(0),
            employeeCount = doc.numberLong("employeeCount").toInt().coerceAtLeast(0),
            productionPer10Minutes = doc.getDouble("productionPer10Minutes") ?: doc.numberLong("productionPer10Minutes").toDouble(),
            avatar = PlayerAvatar(
                gender = doc.getString("gender") ?: "MALE",
                skinStyle = doc.getString("skinStyle") ?: "WORKSHOP",
                bodyType = doc.getString("bodyType") ?: "STANDARD",
                skinTone = doc.getString("skinTone") ?: "MEDIUM",
                hairStyle = doc.getString("hairStyle") ?: "SHORT",
                hairColor = doc.getString("hairColor") ?: "DARK",
                uniformColor = doc.getString("uniformColor") ?: "NAVY",
                helmetColor = doc.getString("helmetColor") ?: "YELLOW",
                accessory = doc.getString("accessory") ?: "NONE"
            ),
            lastSeenAt = doc.numberLong("lastSeenAt")
        )
    }

    private fun DocumentSnapshot.numberLong(field: String): Long = (get(field) as? Number)?.toLong() ?: 0L

    private fun currentDayKey(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }
}
