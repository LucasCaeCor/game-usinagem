package br.com.usinagemmaster.data.social

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/** Mercado conectado de personagem principal. Cada contratação dura 48h. */
data class CharacterOffer(
    val ownerUid: String,
    val playerName: String,
    val boostPct: Int,
    val skills: Set<String>,
    val leasedBy: String? = null,
    val leasedUntil: Long = 0L,
)

data class RemoteHireResult(
    val ownerUid: String,
    val playerName: String,
    val boostPct: Int,
    val endsAt: Long,
)

@Singleton
class CharacterRentalService @Inject constructor() {
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    suspend fun publishMyCharacter(playerName: String, skills: Set<String>, boostPct: Int) {
        val user = auth.currentUser ?: error("Faça login com Google primeiro")
        val ref = db.collection("character_offers").document(user.uid)
        val previous = runCatching { ref.get().await() }.getOrNull()
        val previousLease = previous?.getTimestamp("leasedUntil") ?: Timestamp(Date(0L))
        val previousLeasedBy = previous?.getString("leasedBy")
        ref.set(
            mapOf(
                "ownerUid" to user.uid,
                "playerName" to playerName.ifBlank { user.displayName ?: "Operador" },
                "boostPct" to boostPct.coerceIn(4, 20),
                "skills" to skills.toList(),
                "updatedAt" to Timestamp.now(),
                "leasedBy" to previousLeasedBy,
                "leasedUntil" to previousLease,
            )
        ).await()
    }

    suspend fun withdrawMyCharacter() {
        val user = auth.currentUser ?: error("Faça login primeiro")
        db.collection("character_offers").document(user.uid).delete().await()
    }

    suspend fun offers(): List<CharacterOffer> {
        val user = auth.currentUser ?: return emptyList()
        val now = System.currentTimeMillis()
        return db.collection("character_offers").limit(50).get().await().documents.mapNotNull { doc ->
            val owner = doc.getString("ownerUid") ?: doc.id
            if (owner == user.uid) return@mapNotNull null
            CharacterOffer(
                ownerUid = owner,
                playerName = doc.getString("playerName") ?: "Operador conectado",
                boostPct = (doc.getLong("boostPct") ?: 4L).toInt().coerceIn(4, 20),
                skills = (doc.get("skills") as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet(),
                leasedBy = doc.getString("leasedBy"),
                leasedUntil = doc.getTimestamp("leasedUntil")?.toDate()?.time ?: 0L,
            )
        }.filter { it.leasedUntil <= now }
    }

    suspend fun hire(offer: CharacterOffer): RemoteHireResult {
        val user = auth.currentUser ?: error("Faça login com Google primeiro")
        require(user.uid != offer.ownerUid) { "Você não pode contratar seu próprio personagem" }
        val now = System.currentTimeMillis()
        val endsAt = now + 48L * 60L * 60L * 1000L
        val offerRef = db.collection("character_offers").document(offer.ownerUid)

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
                )
            )
        }.await()

        db.collection("character_rentals").add(
            mapOf(
                "ownerUid" to offer.ownerUid,
                "renterUid" to user.uid,
                "playerName" to offer.playerName,
                "boostPct" to offer.boostPct,
                "startedAt" to Timestamp(Date(now)),
                "endsAt" to Timestamp(Date(endsAt)),
            )
        ).await()
        return RemoteHireResult(offer.ownerUid, offer.playerName, offer.boostPct, endsAt)
    }
}
