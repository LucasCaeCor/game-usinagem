package br.com.usinagemmaster.data.social

import br.com.usinagemmaster.data.preferences.ExpansionRepository
import br.com.usinagemmaster.data.preferences.ActiveGameplayRepository
import br.com.usinagemmaster.data.preferences.PlayerProfilePreferences
import br.com.usinagemmaster.domain.catalog.MachineCatalog
import br.com.usinagemmaster.domain.repository.GameRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class CommunityFactoryMachine(
    val id: String, val type: String, val name: String, val level: Int, val x: Int, val y: Int,
    val premium: Boolean, val operating: Boolean = false, val condition: Int = 1000,
)

data class CommunityFactoryWorker(
    val id: String, val name: String, val specialty: String, val skillLevel: Int, val assignedMachineId: String?,
)

data class CommunityFactory(
    val uid: String, val playerName: String, val companyName: String, val companyLevel: Int,
    val reputation: Int, val specialty: String, val employeeCount: Int, val updatedAt: Long,
    val machines: List<CommunityFactoryMachine>, val workers: List<CommunityFactoryWorker> = emptyList(),
    val productionPer10Minutes: Double = 0.0, val activeContracts: Int = 0, val pendingLots: Int = 0,
    val ownerAvatar: String = "WORKSHOP", val ownerStage: String? = null,
    val ownerMachineId: String? = null, val ownerCarrying: Boolean = false,
)

@Singleton
class CommunityFactoryService @Inject constructor(
    private val gameRepository: GameRepository,
    private val expansionRepository: ExpansionRepository,
    private val playerProfilePreferences: PlayerProfilePreferences,
    private val activeGameplayRepository: ActiveGameplayRepository,
) {
    private val firestore get() = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    private fun googleUser() = auth.currentUser
        ?.takeIf { user -> user.providerData.any { it.providerId == "google.com" } && !user.email.isNullOrBlank() }
        ?: error("Conecte uma conta Google real no Perfil para publicar ou visitar fábricas.")

    suspend fun publishMine() {
        val user = googleUser()
        val dashboard = gameRepository.dashboard().first()
        val machines = gameRepository.machines().first()
        val employees = gameRepository.employees().first()
        val expansion = expansionRepository.snapshot()
        val production = gameRepository.production().first()
        val activeContracts = gameRepository.contracts().first().count { it.status == "ACTIVE" }
        val pendingLots = gameRepository.pendingCargo().first().size
        val profile = playerProfilePreferences.profile.first()
        val career = activeGameplayRepository.snapshot()
        val ownerBatch = career.activeBatch
        val operatingIds = production.machineProduction.filter { it.isOperating }.map { it.machineId }.toSet()

        val machineMaps = machines.filter { it.installed }.take(30).map { m ->
            mapOf(
                "id" to m.id,
                "type" to m.machineType,
                "name" to (MachineCatalog.byType(m.machineType)?.name ?: m.machineType),
                "level" to m.level,
                "x" to m.gridX,
                "y" to m.gridY,
                "premium" to m.id.startsWith("gacha_premium_"),
                "operating" to (m.id in operatingIds),
                "condition" to m.condition,
            )
        }

        val workerMaps = employees.take(40).map { w ->
            mapOf("id" to w.id, "name" to w.name, "specialty" to w.specialty, "skillLevel" to w.skillLevel, "assignedMachineId" to w.assignedMachineId)
        }

        val data = mapOf(
            "uid" to user.uid,
            "playerName" to (user.displayName ?: user.email?.substringBefore("@") ?: "Mestre da Usinagem"),
            "companyName" to dashboard.companyName,
            "companyLevel" to dashboard.companyLevel,
            "reputation" to dashboard.reputation,
            "specialty" to expansion.specialty,
            "employeeCount" to employees.size,
            "updatedAt" to System.currentTimeMillis(),
            "machines" to machineMaps,
            "workers" to workerMaps,
            "productionPer10Minutes" to production.machineProduction.sumOf { it.unitsPer10Minutes },
            "activeContracts" to activeContracts,
            "pendingLots" to pendingLots,
            "ownerAvatar" to profile.avatar.skinStyle,
            "ownerStage" to ownerBatch?.stage?.name,
            "ownerMachineId" to ownerBatch?.machineId,
            "ownerCarrying" to (ownerBatch?.carrying == true),
        )

        firestore.collection("public_factories")
            .document(user.uid)
            .set(data, SetOptions.merge())
            .await()
    }

    suspend fun list(): List<CommunityFactory> {
        val user = googleUser()
        val snap = firestore.collection("public_factories").limit(80).get().await()
        return snap.documents.mapNotNull { d ->
            runCatching {
                val rawMachines = d.get("machines") as? List<*> ?: emptyList<Any>()
                CommunityFactory(
                    uid = d.id,
                    playerName = d.getString("playerName") ?: "Jogador",
                    companyName = d.getString("companyName") ?: "Usinagem",
                    companyLevel = (d.getLong("companyLevel") ?: 1).toInt(),
                    reputation = (d.getLong("reputation") ?: 0).toInt(),
                    specialty = d.getString("specialty") ?: "generalista",
                    employeeCount = (d.getLong("employeeCount") ?: 0).toInt(),
                    updatedAt = d.getLong("updatedAt") ?: 0L,
                    machines = rawMachines.mapNotNull { row ->
                        val m = row as? Map<*, *> ?: return@mapNotNull null
                        CommunityFactoryMachine(
                            id = m["id"] as? String ?: return@mapNotNull null, type = m["type"] as? String ?: "MACHINE",
                            name = m["name"] as? String ?: "Máquina", level = (m["level"] as? Number)?.toInt() ?: 1,
                            x = (m["x"] as? Number)?.toInt() ?: 0, y = (m["y"] as? Number)?.toInt() ?: 0,
                            premium = m["premium"] as? Boolean ?: false, operating = m["operating"] as? Boolean ?: false,
                            condition = (m["condition"] as? Number)?.toInt() ?: 1000,
                        )
                    },
                    workers = (d.get("workers") as? List<*>)?.mapNotNull { row ->
                        val w = row as? Map<*, *> ?: return@mapNotNull null
                        CommunityFactoryWorker(w["id"] as? String ?: return@mapNotNull null, w["name"] as? String ?: "Funcionário", w["specialty"] as? String ?: "PRODUÇÃO", (w["skillLevel"] as? Number)?.toInt() ?: 1, w["assignedMachineId"] as? String)
                    } ?: emptyList(),
                    productionPer10Minutes = d.getDouble("productionPer10Minutes") ?: 0.0,
                    activeContracts = (d.getLong("activeContracts") ?: 0L).toInt(),
                    pendingLots = (d.getLong("pendingLots") ?: 0L).toInt(),
                    ownerAvatar = d.getString("ownerAvatar") ?: "WORKSHOP",
                    ownerStage = d.getString("ownerStage"),
                    ownerMachineId = d.getString("ownerMachineId"),
                    ownerCarrying = d.getBoolean("ownerCarrying") ?: false,
                )
            }.getOrNull()
        }
            .filter { it.uid != user.uid }
            .sortedByDescending { it.updatedAt }
    }
}
