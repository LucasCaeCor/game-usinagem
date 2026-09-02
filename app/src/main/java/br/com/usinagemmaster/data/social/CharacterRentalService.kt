package br.com.usinagemmaster.data.social

import br.com.usinagemmaster.domain.expansion.ExpansionProgression
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/** Mercado conectado do personagem principal. Cada contratação dura 48h. */
data class CharacterOffer(
    val ownerUid: String,
    val playerName: String,
    val boostPct: Int,
    val skills: Set<String>,
    val characterLevel: Int = 1,
    val leasedBy: String? = null,
    val leasedUntil: Long = 0L,
)

data class RemoteHireResult(
    val ownerUid: String,
    val playerName: String,
    val boostPct: Int,
    val endsAt: Long,
)

data class CharacterRentalXpReward(
    val rentalId: String,
    val xp: Long,
    val endsAt: Long,
)

@Singleton
class CharacterRentalService @Inject constructor() {
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    private fun googleUser(): FirebaseUser {
        val user = auth.currentUser ?: error("Entre com Google antes de usar o mercado de personagens.")
        val google = user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        require(google && !user.email.isNullOrBlank()) {
            "Sua sessão Firebase ainda não é uma conta Google real. Abra Perfil > Entrar com Google e escolha sua conta."
        }
        return user
    }

    private fun marketError(error: Throwable): Throwable {
        val firestore = error as? FirebaseFirestoreException
        return if (firestore?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            IllegalStateException(
                "O Firestore bloqueou o Mercado de Personagens. Publique as regras character_offers/character_rentals no Firebase Console.",
                error,
            )
        } else error
    }

    suspend fun publishMyCharacter(playerName: String, skills: Set<String>, boostPct: Int, playerXp: Long = 0L) {
        val user = googleUser()
        val ref = db.collection("character_offers").document(user.uid)
        try {
            db.runTransaction { tx ->
                val previous = tx.get(ref)
                val previousLease = previous.getTimestamp("leasedUntil") ?: Timestamp(Date(0L))
                val previousLeasedBy = previous.getString("leasedBy")
                val data = mapOf(
                    "ownerUid" to user.uid,
                    "playerName" to playerName.ifBlank { user.displayName ?: "Mestre da Usinagem" },
                    "boostPct" to boostPct.coerceIn(4, 25),
                    "skills" to skills.toList(),
                    "characterLevel" to ExpansionProgression.player(playerXp).level,
                    "updatedAt" to Timestamp.now(),
                    "leasedBy" to previousLeasedBy,
                    "leasedUntil" to previousLease,
                )
                tx.set(ref, data, SetOptions.merge())
            }.await()
        } catch (error: Throwable) {
            throw marketError(error)
        }
    }

    suspend fun withdrawMyCharacter() {
        val user = googleUser()
        try { db.collection("character_offers").document(user.uid).delete().await() }
        catch (error: Throwable) { throw marketError(error) }
    }

    suspend fun offers(): List<CharacterOffer> {
        val user = googleUser()
        val now = System.currentTimeMillis()
        try {
            return db.collection("character_offers").limit(60).get().await().documents.mapNotNull { doc ->
                val owner = doc.getString("ownerUid") ?: doc.id
                if (owner == user.uid) return@mapNotNull null
                CharacterOffer(
                    ownerUid = owner,
                    playerName = doc.getString("playerName") ?: "Operador conectado",
                    boostPct = (doc.getLong("boostPct") ?: 4L).toInt().coerceIn(4, 25),
                    skills = (doc.get("skills") as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet(),
                    characterLevel = (doc.getLong("characterLevel") ?: 1L).toInt().coerceAtLeast(1),
                    leasedBy = doc.getString("leasedBy"),
                    leasedUntil = doc.getTimestamp("leasedUntil")?.toDate()?.time ?: 0L,
                )
            }.filter { it.leasedUntil <= now }
        } catch (error: Throwable) {
            throw marketError(error)
        }
    }

    /**
     * Experiência externa: quando SEU personagem termina uma contratação de 48h
     * em outra fábrica, essa contratação passa a render XP ao personagem.
     * O DataStore do jogo impede coletar a mesma contratação duas vezes.
     */
    suspend fun completedRentalXpForOwner(): List<CharacterRentalXpReward> {
        val user = googleUser()
        val now = System.currentTimeMillis()
        try {
            return db.collection("character_rentals")
                .whereEqualTo("ownerUid", user.uid)
                .limit(60)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    val endsAt = doc.getTimestamp("endsAt")?.toDate()?.time ?: return@mapNotNull null
                    if (endsAt > now) return@mapNotNull null
                    val level = (doc.getLong("characterLevel") ?: 1L).toInt().coerceAtLeast(1)
                    val boost = (doc.getLong("boostPct") ?: 4L).toInt().coerceIn(4, 25)
                    CharacterRentalXpReward(
                        rentalId = doc.id,
                        xp = ExpansionProgression.characterXpForRental(level, boost),
                        endsAt = endsAt,
                    )
                }
        } catch (error: Throwable) {
            throw marketError(error)
        }
    }

    suspend fun hire(offer: CharacterOffer): RemoteHireResult {
        val user = googleUser()
        require(user.uid != offer.ownerUid) { "Você não pode contratar seu próprio personagem" }
        val now = System.currentTimeMillis()
        val endsAt = now + 48L * 60L * 60L * 1000L
        val offerRef = db.collection("character_offers").document(offer.ownerUid)

        try {
            db.runTransaction { tx ->
                val current = tx.get(offerRef)
                require(current.exists()) { "Oferta não está mais disponível" }
                val leasedUntil = current.getTimestamp("leasedUntil")?.toDate()?.time ?: 0L
                require(leasedUntil <= now) { "Esse personagem acabou de ser contratado por outra empresa" }
                tx.update(
                    offerRef,
                    mapOf(
                        "leasedBy" to user.uid,
                        "leasedUntil" to Timestamp(Date(endsAt)),
                    ),
                )
            }.await()

            db.collection("character_rentals").add(
                mapOf(
                    "ownerUid" to offer.ownerUid,
                    "renterUid" to user.uid,
                    "playerName" to offer.playerName,
                    "boostPct" to offer.boostPct,
                    "characterLevel" to offer.characterLevel,
                    "startedAt" to Timestamp(Date(now)),
                    "endsAt" to Timestamp(Date(endsAt)),
                ),
            ).await()
        } catch (error: Throwable) {
            throw marketError(error)
        }
        return RemoteHireResult(offer.ownerUid, offer.playerName, offer.boostPct, endsAt)
    }
}
