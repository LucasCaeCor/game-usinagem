package br.com.usinagemmaster.data.repository

import br.com.usinagemmaster.data.local.dao.CompanyDao
import br.com.usinagemmaster.data.local.dao.MachineDao
import br.com.usinagemmaster.data.local.entity.MachineEntity
import br.com.usinagemmaster.domain.expansion.ExpansionCatalog
import br.com.usinagemmaster.domain.catalog.MachineCatalog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Materializa máquinas premium do DataStore no Room.
 * O id é determinístico: a mesma máquina jamais é criada duas vezes.
 * Se não houver espaço, a posse continua no inventário e o jogador pode instalar depois.
 */
@Singleton
class PremiumMachineInstaller @Inject constructor(
    private val machineDao: MachineDao,
    private val companyDao: CompanyDao,
) {
    data class Result(val installed: Boolean, val alreadyInstalled: Boolean = false, val message: String)

    suspend fun install(premiumId: String): Result {
        val premium = ExpansionCatalog.premiumMachines.firstOrNull { it.id == premiumId }
            ?: return Result(false, message = "Máquina premium desconhecida")
        val roomId = roomId(premiumId)
        val currentMachines = machineDao.getAll()
        if (currentMachines.any { it.id == roomId }) {
            return Result(true, alreadyInstalled = true, message = "${premium.name} já está instalada no galpão")
        }

        val machineType = machineTypeFor(premiumId)
        val def = MachineCatalog.byType(machineType)
            ?: return Result(false, message = "Tipo base da máquina premium não existe no catálogo")
        val company = companyDao.get() ?: return Result(false, message = "Empresa não inicializada")

        if (company.usedWarehouseSpace + def.space > company.warehouseSpace) {
            return Result(false, message = "${premium.name} foi adquirida, mas o galpão está sem espaço. Expanda o galpão e toque em Instalar.")
        }
        val position = freePosition(currentMachines)
            ?: return Result(false, message = "${premium.name} está no inventário, mas não há célula livre no layout 5×6.")

        machineDao.insert(
            MachineEntity(
                roomId,
                machineType,
                premium.name,
                sectorFor(machineType),
                levelFor(premiumId),
                1000,
                0,
                true,
                position.first,
                position.second,
                System.currentTimeMillis(),
            )
        )
        companyDao.upsert(company.copy(usedWarehouseSpace = company.usedWarehouseSpace + def.space))
        return Result(true, message = "${premium.name} foi instalada no galpão! Procure pelo selo PREMIUM.")
    }

    suspend fun syncOwned(ids: Set<String>) {
        ids.forEach { runCatching { install(it) } }
    }

    companion object {
        fun roomId(premiumId: String) = "gacha_premium_$premiumId"
        fun premiumId(machineId: String): String? = machineId.takeIf { it.startsWith("gacha_premium_") }?.removePrefix("gacha_premium_")

        private fun machineTypeFor(id: String) = when (id) {
            "torno_hyper" -> "CNC_LATHE"
            "centro_5x_titan" -> "CNC_MACHINING_CENTER_5_AXIS"
            "celula_robotica" -> "CNC_MACHINING_CENTER_5_AXIS"
            "retifica_ultra" -> "CNC_GRINDER"
            "solda_omega" -> "ROBOTIC_WELDING"
            else -> "CNC_LATHE"
        }
        private fun levelFor(id: String) = when (id) {
            "celula_robotica" -> 8
            "centro_5x_titan", "retifica_ultra" -> 7
            "torno_hyper", "solda_omega" -> 6
            else -> 5
        }
        private fun sectorFor(type: String) = when (type) {
            "CNC_LATHE", "MECHANICAL_LATHE" -> "TURNING"
            "CNC_GRINDER", "CYLINDRICAL_GRINDER" -> "GRINDING"
            "ROBOTIC_WELDING", "WELDING_BENCH", "LASER_CUTTER", "PLASMA_CUTTER" -> "BOILERMAKING"
            else -> "MILLING"
        }
        private fun freePosition(machines: List<MachineEntity>): Pair<Int, Int>? {
            val used = machines.filter { it.installed }.map { it.gridX to it.gridY }.toSet()
            for (y in 0 until 6) for (x in 0 until 5) if ((x to y) !in used) return x to y
            return null
        }
    }
}
