package br.com.usinagemmaster.domain.repository

import br.com.usinagemmaster.data.local.entity.*
import br.com.usinagemmaster.domain.model.DashboardStatus
import br.com.usinagemmaster.domain.model.ProductionSnapshot
import kotlinx.coroutines.flow.Flow

data class OfflineReport(
    val minutes: Long,
    val earnedCents: Long,
    val producedUnits: Double = 0.0,
    val completedContracts: Int = 0
)

interface GameRepository {
    fun dashboard(): Flow<DashboardStatus>
    fun production(): Flow<ProductionSnapshot>
    fun machines(): Flow<List<MachineEntity>>
    fun employees(): Flow<List<EmployeeEntity>>
    fun contracts(): Flow<List<ContractEntity>>
    fun finances(): Flow<List<FinancialTransactionEntity>>
    fun facilities(): Flow<List<FacilityUpgradeEntity>>
    fun goals(): Flow<List<GoalEntity>>
    fun legendaryMissions(): Flow<List<LegendaryMissionEntity>>

    suspend fun initialize(): OfflineReport?
    suspend fun tickProduction()
    suspend fun accelerateProduction10Minutes(): Result<Long>
    suspend fun grantBonusCash(amountCents: Long, description: String): Result<Unit>
    suspend fun buyTeamSnack(): Result<Long>
    suspend fun buyMachine(machineType: String): Result<Unit>
    suspend fun sellMachine(machineId: String): Result<Long>
    suspend fun moveMachine(machineId: String, gridX: Int, gridY: Int): Result<Unit>
    suspend fun assignEmployee(machineId: String, employeeId: String?): Result<Unit>
    suspend fun repairMachine(machineId: String): Result<Unit>
    suspend fun hireRandomEmployee(): Result<Unit>
    suspend fun hireLegendaryEmployee(): Result<String>
    suspend fun fireEmployee(employee: EmployeeEntity)
    suspend fun generateContractsIfNeeded()
    suspend fun acceptContract(contract: ContractEntity): Result<Unit>
    suspend fun completeContract(contract: ContractEntity): Result<Unit>
    suspend fun recoverContractReward(contractId: String): Result<Long>
    suspend fun upgradeWarehouse(): Result<Unit>
    suspend fun claimGoal(goal: GoalEntity): Result<Unit>
    suspend fun claimLegendaryMission(mission: LegendaryMissionEntity): Result<Unit>

suspend fun cancelContract(contractId: String): Result<Long>
    suspend fun dismissFailedContract(contractId: String): Result<Unit>


    // V10_COMPANY_NAME_API
    suspend fun renameCompany(newName: String): Result<Unit>
}
