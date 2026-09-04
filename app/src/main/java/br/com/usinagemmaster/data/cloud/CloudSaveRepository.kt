package br.com.usinagemmaster.data.cloud

import android.content.Context
import android.util.Base64
import androidx.room.withTransaction
import br.com.usinagemmaster.data.local.dao.*
import br.com.usinagemmaster.data.local.database.GameDatabase
import br.com.usinagemmaster.data.local.entity.*
import br.com.usinagemmaster.data.preferences.*
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val CLOUD_SCHEMA = 1
private const val CHUNK_CHARS = 620_000
private const val CLOUD_PREFS = "usinagem_cloud_sync_v1"
private const val KEY_UID = "uid"
private const val KEY_SAVE_ID = "save_id"
private const val KEY_REVISION = "revision"
private const val KEY_FINGERPRINT = "fingerprint"
private const val KEY_SYNCED_AT = "synced_at"

enum class CloudSyncAction { UPLOADED, RESTORED, UP_TO_DATE, CONFLICT }

data class CloudSyncResult(
    val action: CloudSyncAction,
    val message: String,
    val saveId: String,
    val revision: Long,
    val syncedAt: Long = System.currentTimeMillis(),
)

data class CloudSaveStatus(
    val uid: String? = null,
    val saveId: String? = null,
    val revision: Long = 0L,
    val fingerprint: String = "",
    val syncedAt: Long = 0L,
)

private data class RemoteMeta(
    val saveId: String,
    val revision: Long,
    val chunkPrefix: String,
    val chunkCount: Int,
    val checksum: String,
    val schema: Int,
)

private data class LocalPayload(
    val json: String,
    val fingerprint: String,
    val compressedBase64: String,
)

/**
 * Save privado da conta Google.
 *
 * O Firestore recebe chunks comprimidos em /cloud_saves/{uid}/chunks e um ponteiro
 * atômico em /cloud_saves/{uid}/meta/main. Como os IDs dos chunks contêm a revisão,
 * um upload interrompido nunca corrompe a revisão que já estava válida.
 */
@Singleton
class CloudSaveRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: GameDatabase,
    private val companyDao: CompanyDao,
    private val machineDao: MachineDao,
    private val employeeDao: EmployeeDao,
    private val contractDao: ContractDao,
    private val financeDao: FinanceDao,
    private val facilityDao: FacilityDao,
    private val goalDao: GoalDao,
    private val legendaryMissionDao: LegendaryMissionDao,
    private val productionCargoDao: ProductionCargoDao,
    private val gamePreferences: GamePreferences,
    private val playerProfilePreferences: PlayerProfilePreferences,
    private val expansionRepository: ExpansionRepository,
    private val activeGameplayRepository: ActiveGameplayRepository,
    private val workLifeRepository: WorkLifeRepository,
) {
    private val cloudPrefs get() = context.getSharedPreferences(CLOUD_PREFS, Context.MODE_PRIVATE)

    fun localStatus(): CloudSaveStatus = CloudSaveStatus(
        uid = cloudPrefs.getString(KEY_UID, null),
        saveId = cloudPrefs.getString(KEY_SAVE_ID, null),
        revision = cloudPrefs.getLong(KEY_REVISION, 0L),
        fingerprint = cloudPrefs.getString(KEY_FINGERPRINT, "").orEmpty(),
        syncedAt = cloudPrefs.getLong(KEY_SYNCED_AT, 0L),
    )

    suspend fun synchronize(user: FirebaseUser, localSaveId: String): CloudSyncResult {
        requireGoogle(user)
        require(localSaveId.isNotBlank()) { "Save local inválido." }

        val db = FirebaseFirestore.getInstance()
        val remote = readRemoteMeta(db, user.uid)
        val local = capturePayload()
        val remembered = localStatus()

        if (remote == null) {
            return upload(db, user, localSaveId, 1L, local)
        }
        require(remote.schema <= CLOUD_SCHEMA) {
            "Este save foi criado por uma versão mais nova do jogo. Atualize o aplicativo antes de restaurar."
        }

        // Outro aparelho / instalação nova: a identidade do slot é a que está na nuvem.
        if (remote.saveId != localSaveId) {
            return restore(db, user, remote)
        }

        val sameTrackedSlot = remembered.uid == user.uid && remembered.saveId == remote.saveId
        if (!sameTrackedSlot) {
            // Sem histórico local confiável, a nuvem é a fonte segura.
            return restore(db, user, remote)
        }

        if (remote.revision > remembered.revision) {
            return if (local.fingerprint == remembered.fingerprint) {
                restore(db, user, remote)
            } else {
                CloudSyncResult(
                    CloudSyncAction.CONFLICT,
                    "Há progresso novo neste aparelho e um save mais novo na nuvem. Nenhum dos dois foi sobrescrito.",
                    remote.saveId,
                    remote.revision,
                )
            }
        }

        // Dois aparelhos podem tentar publicar a mesma próxima revisão ao mesmo tempo.
        // Se a revisão coincide mas o checksum não, preservamos ambos e pedimos decisão.
        if (remote.revision == remembered.revision && remote.checksum != remembered.fingerprint) {
            return CloudSyncResult(
                CloudSyncAction.CONFLICT,
                "Dois aparelhos sincronizaram versões diferentes ao mesmo tempo. Nenhum progresso local foi apagado.",
                remote.saveId,
                remote.revision,
            )
        }

        if (local.fingerprint != remembered.fingerprint || remote.revision < remembered.revision) {
            val nextRevision = maxOf(remote.revision, remembered.revision) + 1L
            return upload(db, user, remote.saveId, nextRevision, local)
        }

        remember(user.uid, remote.saveId, remote.revision, local.fingerprint)
        return CloudSyncResult(
            CloudSyncAction.UP_TO_DATE,
            "Save da nuvem está atualizado.",
            remote.saveId,
            remote.revision,
        )
    }

    suspend fun forceUpload(user: FirebaseUser, saveId: String): CloudSyncResult {
        requireGoogle(user)
        val db = FirebaseFirestore.getInstance()
        val remote = readRemoteMeta(db, user.uid)
        val payload = capturePayload()
        return upload(db, user, remote?.saveId ?: saveId, (remote?.revision ?: 0L) + 1L, payload)
    }

    suspend fun forceRestore(user: FirebaseUser): CloudSyncResult {
        requireGoogle(user)
        val db = FirebaseFirestore.getInstance()
        val remote = readRemoteMeta(db, user.uid) ?: error("Ainda não existe backup desta conta na nuvem.")
        return restore(db, user, remote)
    }

    private fun requireGoogle(user: FirebaseUser) {
        require(user.providerData.any { it.providerId == "google.com" }) {
            "O backup privado exige uma conta Google autenticada."
        }
    }

    private suspend fun readRemoteMeta(db: FirebaseFirestore, uid: String): RemoteMeta? {
        val doc = db.collection("cloud_saves").document(uid).collection("meta").document("main").get().await()
        if (!doc.exists()) return null
        val saveId = doc.getString("saveId").orEmpty()
        val revision = (doc.get("revision") as? Number)?.toLong() ?: 0L
        val chunkCount = (doc.get("chunkCount") as? Number)?.toInt() ?: 0
        val chunkPrefix = doc.getString("chunkPrefix") ?: revisionPrefix(revision)
        val checksum = doc.getString("checksum").orEmpty()
        val schema = (doc.get("schema") as? Number)?.toInt() ?: 1
        require(saveId.isNotBlank() && revision > 0L && chunkCount > 0 && checksum.isNotBlank()) {
            "Backup da nuvem está incompleto. Nenhum dado local foi alterado."
        }
        return RemoteMeta(saveId, revision, chunkPrefix, chunkCount, checksum, schema)
    }

    private suspend fun upload(
        db: FirebaseFirestore,
        user: FirebaseUser,
        saveId: String,
        revision: Long,
        payload: LocalPayload,
    ): CloudSyncResult {
        val root = db.collection("cloud_saves").document(user.uid)
        val chunks = payload.compressedBase64.chunked(CHUNK_CHARS)
        require(chunks.isNotEmpty()) { "Não foi possível gerar o backup." }
        val prefix = "${revisionPrefix(revision)}_${payload.fingerprint.take(16)}"

        chunks.chunked(350).forEachIndexed { groupIndex, group ->
            val batch = db.batch()
            group.forEachIndexed { indexInGroup, value ->
                val index = groupIndex * 350 + indexInGroup
                val id = "${prefix}_c${index.toString().padStart(4, '0')}"
                batch.set(root.collection("chunks").document(id), mapOf(
                    "revision" to revision,
                    "index" to index,
                    "data" to value,
                ))
            }
            batch.commit().await()
        }

        // O meta é o commit lógico: só depois dele a revisão nova passa a ser restaurável.
        root.collection("meta").document("main").set(mapOf(
            "uid" to user.uid,
            "saveId" to saveId,
            "schema" to CLOUD_SCHEMA,
            "revision" to revision,
            "chunkPrefix" to prefix,
            "chunkCount" to chunks.size,
            "checksum" to payload.fingerprint,
            "compressedChars" to payload.compressedBase64.length,
            "clientUpdatedAtMs" to System.currentTimeMillis(),
            "serverUpdatedAt" to FieldValue.serverTimestamp(),
        ), SetOptions.merge()).await()

        remember(user.uid, saveId, revision, payload.fingerprint)
        cleanupOldChunks(db, user.uid, revision)
        return CloudSyncResult(
            CloudSyncAction.UPLOADED,
            "Progresso salvo na nuvem.",
            saveId,
            revision,
        )
    }

    private suspend fun restore(db: FirebaseFirestore, user: FirebaseUser, meta: RemoteMeta): CloudSyncResult {
        val root = db.collection("cloud_saves").document(user.uid)
        val prefix = meta.chunkPrefix
        val joined = buildString {
            for (i in 0 until meta.chunkCount) {
                val id = "${prefix}_c${i.toString().padStart(4, '0')}"
                val doc = root.collection("chunks").document(id).get().await()
                require(doc.exists()) { "Backup incompleto: bloco ${i + 1}/${meta.chunkCount} ausente." }
                append(doc.getString("data") ?: error("Backup corrompido no bloco ${i + 1}."))
            }
        }
        val json = gunzipBase64(joined)
        val fingerprint = sha256(json.toByteArray(Charsets.UTF_8))
        require(fingerprint == meta.checksum) {
            "A verificação do backup falhou. O save local foi preservado."
        }

        val beforeRestore = capturePayload()
        try {
            applyJson(JSONObject(json))
        } catch (cause: Throwable) {
            val rollback = runCatching { applyJson(JSONObject(beforeRestore.json)) }
            if (rollback.isFailure) {
                error("A restauração falhou e o rollback local também falhou. Feche o jogo sem continuar e restaure novamente. Detalhe: ${cause.message}")
            }
            throw cause
        }
        val finalPayload = capturePayload()
        remember(user.uid, meta.saveId, meta.revision, finalPayload.fingerprint)
        return CloudSyncResult(
            CloudSyncAction.RESTORED,
            "Progresso restaurado da nuvem neste aparelho.",
            meta.saveId,
            meta.revision,
        )
    }

    private suspend fun capturePayload(): LocalPayload {
        val root = JSONObject()
        root.put("schema", CLOUD_SCHEMA)
        root.put("company", companyDao.get()?.toJson() ?: JSONObject.NULL)
        root.put("machines", jsonArray(machineDao.getAll().sortedBy { it.id }) { it.toJson() })
        root.put("employees", jsonArray(employeeDao.getAll().sortedBy { it.id }) { it.toJson() })
        root.put("contracts", jsonArray(contractDao.getAll().sortedBy { it.id }) { it.toJson() })
        root.put("finance", jsonArray(financeDao.getAll().sortedWith(compareBy<FinancialTransactionEntity> { it.createdAt }.thenBy { it.id })) { it.toJson() })
        root.put("facilities", jsonArray(facilityDao.getAll().sortedBy { it.upgradeType }) { it.toJson() })
        root.put("goals", jsonArray(goalDao.getAll().sortedBy { it.id }) { it.toJson() })
        root.put("legendaryMissions", jsonArray(legendaryMissionDao.getAll().sortedBy { it.id }) { it.toJson() })
        root.put("productionCargo", jsonArray(productionCargoDao.getAll().sortedBy { it.id }) { it.toJson() })
        root.put("preferences", JSONObject().apply {
            put("game", mapToJson(gamePreferences.exportCloudState()))
            put("profile", mapToJson(playerProfilePreferences.exportCloudState()))
            put("expansion", mapToJson(expansionRepository.exportCloudState()))
            put("activeGameplay", mapToJson(activeGameplayRepository.exportCloudState()))
            put("workLife", mapToJson(workLifeRepository.exportCloudState()))
        })

        val json = root.toString()
        val fingerprint = sha256(json.toByteArray(Charsets.UTF_8))
        return LocalPayload(json, fingerprint, gzipBase64(json))
    }

    private suspend fun applyJson(root: JSONObject) {
        val schema = root.optInt("schema", 1)
        require(schema <= CLOUD_SCHEMA) { "Save de versão futura. Atualize o jogo." }

        val company = root.optJSONObject("company")?.toCompany()
            ?: error("Backup sem dados da empresa. O save local foi preservado.")
        val machines = root.optJSONArray("machines").objects().map { it.toMachine() }
        val employees = root.optJSONArray("employees").objects().map { it.toEmployee() }
        val contracts = root.optJSONArray("contracts").objects().map { it.toContract() }
        val finance = root.optJSONArray("finance").objects().map { it.toFinance() }
        val facilities = root.optJSONArray("facilities").objects().map { it.toFacility() }
        val goals = root.optJSONArray("goals").objects().map { it.toGoal() }
        val missions = root.optJSONArray("legendaryMissions").objects().map { it.toLegendaryMission() }
        val cargo = root.optJSONArray("productionCargo").objects().map { it.toCargo() }
        val prefs = root.optJSONObject("preferences") ?: JSONObject()

        database.withTransaction {
            productionCargoDao.deleteAll()
            legendaryMissionDao.deleteAll()
            goalDao.deleteAll()
            facilityDao.deleteAll()
            financeDao.deleteAll()
            contractDao.deleteAll()
            employeeDao.deleteAll()
            machineDao.deleteAll()
            companyDao.deleteAll()

            companyDao.upsert(company)
            if (machines.isNotEmpty()) machineDao.insertAll(machines)
            if (employees.isNotEmpty()) employeeDao.insertAll(employees)
            if (contracts.isNotEmpty()) contractDao.insertAll(contracts)
            if (finance.isNotEmpty()) financeDao.insertAll(finance)
            if (facilities.isNotEmpty()) facilityDao.upsertAll(facilities)
            if (goals.isNotEmpty()) goalDao.insertAll(goals)
            if (missions.isNotEmpty()) legendaryMissionDao.upsertAll(missions)
            if (cargo.isNotEmpty()) productionCargoDao.insertAll(cargo)
        }

        gamePreferences.importCloudState(prefs.optJSONObject("game").toMap())
        playerProfilePreferences.importCloudState(prefs.optJSONObject("profile").toMap())
        expansionRepository.importCloudState(prefs.optJSONObject("expansion").toMap())
        activeGameplayRepository.importCloudState(prefs.optJSONObject("activeGameplay").toMap())
        workLifeRepository.importCloudState(prefs.optJSONObject("workLife").toMap())
    }

    private suspend fun cleanupOldChunks(db: FirebaseFirestore, uid: String, keepRevision: Long) {
        runCatching {
            val collection = db.collection("cloud_saves").document(uid).collection("chunks")
            // Não apaga chunks da MESMA revisão: outro aparelho pode estar concluindo
            // um upload concorrente. Órfãos da mesma revisão somem no próximo revisionamento.
            val old = collection.get().await().documents.filter { doc ->
                ((doc.get("revision") as? Number)?.toLong() ?: Long.MIN_VALUE) < keepRevision
            }
            old.chunked(350).forEach { group ->
                val batch = db.batch()
                group.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
    }

    private fun remember(uid: String, saveId: String, revision: Long, fingerprint: String) {
        cloudPrefs.edit()
            .putString(KEY_UID, uid)
            .putString(KEY_SAVE_ID, saveId)
            .putLong(KEY_REVISION, revision)
            .putString(KEY_FINGERPRINT, fingerprint)
            .putLong(KEY_SYNCED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun revisionPrefix(revision: Long) = "r${revision.toString().padStart(12, '0')}"

    private fun gzipBase64(value: String): String {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).bufferedWriter(Charsets.UTF_8).use { it.write(value) }
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    private fun gunzipBase64(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        return GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}

private fun <T> jsonArray(values: List<T>, mapper: (T) -> JSONObject): JSONArray =
    JSONArray().also { array -> values.forEach { array.put(mapper(it)) } }

private fun mapToJson(values: Map<String, Any?>): JSONObject = JSONObject().also { out ->
    values.toSortedMap().forEach { (key, value) -> out.put(key, anyToJson(value)) }
}

private fun anyToJson(value: Any?): Any = when (value) {
    null -> JSONObject.NULL
    is Map<*, *> -> mapToJson(value.entries.mapNotNull { (k, v) -> (k as? String)?.let { it to v } }.toMap())
    is Iterable<*> -> JSONArray().also { a -> value.forEach { a.put(anyToJson(it)) } }
    else -> value
}

private fun JSONObject?.toMap(): Map<String, Any?> {
    if (this == null) return emptyMap()
    return keys().asSequence().associateWith { key -> jsonToAny(opt(key)) }
}

private fun jsonToAny(value: Any?): Any? = when (value) {
    null, JSONObject.NULL -> null
    is JSONObject -> value.toMap()
    is JSONArray -> (0 until value.length()).map { jsonToAny(value.opt(it)) }
    else -> value
}

private fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it) }
}

private fun JSONObject.long(name: String, default: Long = 0L): Long = (opt(name) as? Number)?.toLong() ?: default
private fun JSONObject.int(name: String, default: Int = 0): Int = (opt(name) as? Number)?.toInt() ?: default
private fun JSONObject.nullableLong(name: String): Long? = if (isNull(name)) null else (opt(name) as? Number)?.toLong()
private fun JSONObject.nullableString(name: String): String? = if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

private fun CompanyEntity.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("cashCents", cashCents); put("reputation", reputation)
    put("companyLevel", companyLevel); put("experience", experience); put("warehouseSpace", warehouseSpace)
    put("usedWarehouseSpace", usedWarehouseSpace); put("lastSimulationAt", lastSimulationAt); put("createdAt", createdAt)
}
private fun JSONObject.toCompany() = CompanyEntity(
    id = int("id", 1), name = optString("name", "Minha Usinagem"), cashCents = long("cashCents"), reputation = int("reputation"),
    companyLevel = int("companyLevel", 1), experience = long("experience"), warehouseSpace = int("warehouseSpace"),
    usedWarehouseSpace = int("usedWarehouseSpace"), lastSimulationAt = long("lastSimulationAt"), createdAt = long("createdAt"),
)

private fun MachineEntity.toJson() = JSONObject().apply {
    put("id", id); put("machineType", machineType); put("customName", customName ?: JSONObject.NULL); put("sectorType", sectorType)
    put("level", level); put("condition", condition); put("accumulatedWorkMinutes", accumulatedWorkMinutes); put("installed", installed)
    put("gridX", gridX); put("gridY", gridY); put("purchasedAt", purchasedAt)
}
private fun JSONObject.toMachine() = MachineEntity(
    id = getString("id"), machineType = getString("machineType"), customName = nullableString("customName"), sectorType = getString("sectorType"),
    level = int("level", 1), condition = int("condition", 100), accumulatedWorkMinutes = long("accumulatedWorkMinutes"), installed = optBoolean("installed"),
    gridX = int("gridX"), gridY = int("gridY"), purchasedAt = long("purchasedAt"),
)

private fun EmployeeEntity.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("specialty", specialty); put("skillLevel", skillLevel); put("experience", experience)
    put("salaryCents", salaryCents); put("morale", morale); put("trait", trait); put("hiredAt", hiredAt)
    put("assignedMachineId", assignedMachineId ?: JSONObject.NULL); put("isLegendary", isLegendary); put("legendaryCode", legendaryCode ?: JSONObject.NULL)
}
private fun JSONObject.toEmployee() = EmployeeEntity(
    id = getString("id"), name = getString("name"), specialty = getString("specialty"), skillLevel = int("skillLevel", 1),
    experience = long("experience"), salaryCents = long("salaryCents"), morale = int("morale", 100), trait = getString("trait"), hiredAt = long("hiredAt"),
    assignedMachineId = nullableString("assignedMachineId"), isLegendary = optBoolean("isLegendary"), legendaryCode = nullableString("legendaryCode"),
)

private fun ContractEntity.toJson() = JSONObject().apply {
    put("id", id); put("clientName", clientName); put("contractType", contractType); put("quantity", quantity); put("completedQuantity", completedQuantity)
    put("difficulty", difficulty); put("requiredQuality", requiredQuality); put("rewardCents", rewardCents); put("penaltyCents", penaltyCents)
    put("reputationReward", reputationReward); put("reputationPenalty", reputationPenalty); put("generatedAt", generatedAt)
    put("startedAt", startedAt ?: JSONObject.NULL); put("deadlineAt", deadlineAt); put("status", status); put("productionProgressMilli", productionProgressMilli)
}
private fun JSONObject.toContract() = ContractEntity(
    id = getString("id"), clientName = getString("clientName"), contractType = getString("contractType"), quantity = int("quantity"),
    completedQuantity = int("completedQuantity"), difficulty = int("difficulty", 1), requiredQuality = int("requiredQuality"), rewardCents = long("rewardCents"),
    penaltyCents = long("penaltyCents"), reputationReward = int("reputationReward"), reputationPenalty = int("reputationPenalty"), generatedAt = long("generatedAt"),
    startedAt = nullableLong("startedAt"), deadlineAt = long("deadlineAt"), status = getString("status"), productionProgressMilli = long("productionProgressMilli"),
)

private fun FinancialTransactionEntity.toJson() = JSONObject().apply {
    put("id", id); put("type", type); put("category", category); put("amountCents", amountCents); put("description", description); put("createdAt", createdAt)
}
private fun JSONObject.toFinance() = FinancialTransactionEntity(
    id = getString("id"), type = getString("type"), category = getString("category"), amountCents = long("amountCents"), description = getString("description"), createdAt = long("createdAt"),
)

private fun FacilityUpgradeEntity.toJson() = JSONObject().apply { put("upgradeType", upgradeType); put("level", level) }
private fun JSONObject.toFacility() = FacilityUpgradeEntity(upgradeType = getString("upgradeType"), level = int("level"))

private fun GoalEntity.toJson() = JSONObject().apply {
    put("id", id); put("title", title); put("target", target); put("progress", progress); put("rewardCents", rewardCents); put("claimed", claimed)
}
private fun JSONObject.toGoal() = GoalEntity(
    id = getString("id"), title = getString("title"), target = int("target"), progress = int("progress"), rewardCents = long("rewardCents"), claimed = optBoolean("claimed"),
)

private fun LegendaryMissionEntity.toJson() = JSONObject().apply {
    put("id", id); put("legendaryCode", legendaryCode); put("title", title); put("description", description); put("metric", metric)
    put("target", target); put("progress", progress); put("rewardCents", rewardCents); put("claimed", claimed)
}
private fun JSONObject.toLegendaryMission() = LegendaryMissionEntity(
    id = getString("id"), legendaryCode = getString("legendaryCode"), title = getString("title"), description = getString("description"), metric = getString("metric"),
    target = long("target"), progress = long("progress"), rewardCents = long("rewardCents"), claimed = optBoolean("claimed"),
)

private fun ProductionCargoEntity.toJson() = JSONObject().apply {
    put("id", id); put("valueCents", valueCents); put("unitsMilli", unitsMilli); put("cycles", cycles); put("createdAt", createdAt); put("deliveredAt", deliveredAt ?: JSONObject.NULL)
}
private fun JSONObject.toCargo() = ProductionCargoEntity(
    id = getString("id"), valueCents = long("valueCents"), unitsMilli = long("unitsMilli"), cycles = long("cycles"), createdAt = long("createdAt"), deliveredAt = nullableLong("deliveredAt"),
)
