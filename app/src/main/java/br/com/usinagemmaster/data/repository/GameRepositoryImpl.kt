package br.com.usinagemmaster.data.repository
import br.com.usinagemmaster.data.preferences.WorkLifeRepository
import br.com.usinagemmaster.domain.expansion.ContractProgression
import br.com.usinagemmaster.domain.expansion.ExpansionProgression

import br.com.usinagemmaster.domain.expansion.ExpansionState

import br.com.usinagemmaster.data.preferences.ExpansionRepository

import br.com.usinagemmaster.data.local.dao.*
import br.com.usinagemmaster.data.local.entity.*
import br.com.usinagemmaster.data.preferences.GamePreferences
import br.com.usinagemmaster.data.preferences.WorkforceState
import br.com.usinagemmaster.domain.catalog.EmployeeCatalog
import br.com.usinagemmaster.domain.catalog.MachineCatalog
import br.com.usinagemmaster.domain.catalog.LegendaryEmployeeCatalog
import br.com.usinagemmaster.domain.catalog.LegendaryMissionCatalog
import br.com.usinagemmaster.domain.model.*
import br.com.usinagemmaster.domain.repository.GameRepository
import br.com.usinagemmaster.domain.repository.OfflineReport
import br.com.usinagemmaster.domain.simulation.ProductionEngine
import br.com.usinagemmaster.domain.simulation.LegendaryMissionProgressEngine
import br.com.usinagemmaster.domain.simulation.SimulationCadence
import br.com.usinagemmaster.domain.simulation.EconomyBalance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.random.Random

@Singleton
class GameRepositoryImpl @Inject constructor(
    private val companyDao: CompanyDao,
    private val machineDao: MachineDao,
    private val employeeDao: EmployeeDao,
    private val contractDao: ContractDao,
    private val financeDao: FinanceDao,
    private val facilityDao: FacilityDao,
    private val goalDao: GoalDao,
    private val legendaryMissionDao: LegendaryMissionDao,
    private val gamePreferences: GamePreferences,
    private val expansionRepository: ExpansionRepository,
    private val workLifeRepository: WorkLifeRepository
) : GameRepository {

    private val simulationMutex = Mutex()

    override fun dashboard(): Flow<DashboardStatus> = combine(
        companyDao.observe(), machineDao.observeCount(), employeeDao.observeCount(), contractDao.observeActiveCount()
    ) { company, machines, employees, contracts ->
        DashboardStatus(
            companyName = company?.name ?: "Minha Usinagem",
            cashCents = company?.cashCents ?: 0,
            reputation = company?.reputation ?: 0,
            companyLevel = company?.companyLevel ?: 1,
            machines = machines,
            employees = employees,
            activeContracts = contracts,
            warehouseSpace = company?.warehouseSpace ?: 100,
            usedWarehouseSpace = company?.usedWarehouseSpace ?: 0,
            lastSimulationAt = company?.lastSimulationAt ?: 0L
        )
    }

    override fun production(): Flow<ProductionSnapshot> = combine(
        machineDao.observeAll(), employeeDao.observeAll(), gamePreferences.workforce, expansionRepository.state
    ) { machines, employees, workforce, expansion -> calculateProduction(machines, employees, workforce, expansion) }

    override fun machines() = machineDao.observeAll()
    override fun employees() = employeeDao.observeAll()
    override fun contracts(): Flow<List<ContractEntity>> = combine(
        contractDao.observeAll(),
        companyDao.observe(),
    ) { contracts, company ->
        val level = maxOf(
            company?.companyLevel ?: 1,
            1 + (company?.reputation ?: 0) / 20,
        )
        contracts.filter { contract ->
            contract.status != ContractStatus.AVAILABLE.name ||
                ContractProgression.minLevel(contract.difficulty) <= level
        }
    }
    override fun finances() = financeDao.observeRecent()
    override fun facilities() = facilityDao.observeAll()
    override fun goals() = goalDao.observeAll()
    override fun legendaryMissions() = legendaryMissionDao.observeAll()

    override suspend fun initialize(): OfflineReport? {
        val now = System.currentTimeMillis()
        val company = companyDao.get()
        if (company == null) {
            companyDao.upsert(CompanyEntity(1, "Oficina Império do Aço", 3_500_000, 0, 1, 0, 100, 0, now, now))
            seedStarterMachine(now)
            seedGoals()
            syncLegendaryMissionSeeds()
            generateContractsIfNeeded()
            return null
        }

        // V5: sincroniza metas novas em saves antigos sem duplicar as existentes.
        seedGoals()
        // Garante que saves da v4+ ganhem missões antes de calcular o período offline.
        syncLegendaryMissionSeeds()

        // Repara automaticamente qualquer contrato que tenha ficado CONCLUÍDO sem
        // o crédito correspondente em versões anteriores. A liquidação é idempotente.
        repairMissedContractRewards()

        val elapsedMillis = (now - company.lastSimulationAt).coerceAtLeast(0L)
        if (elapsedMillis < SimulationCadence.CYCLE_MILLIS) {
            // Importante: não zera o relógio. Os minutos acumulados continuam contando
            // para o próximo fechamento de 10 minutos mesmo se o jogador reabrir o app.
            generateContractsIfNeeded()
            return null
        }

        val capped = min(elapsedMillis, SimulationCadence.MAX_OFFLINE_MILLIS)
        val settled = SimulationCadence.settledMillis(capped)
        val discardBeyondCap = elapsedMillis > SimulationCadence.MAX_OFFLINE_MILLIS
        val lastSimulationAfter = if (discardBeyondCap) now else company.lastSimulationAt + settled
        val result = simulateProduction(
            elapsedMillis = settled,
            eventTime = now,
            lastSimulationAtAfter = lastSimulationAfter,
            summaryDescription = "Produção offline • ${settled / SimulationCadence.CYCLE_MILLIS} ciclo(s) de 10 min"
        )
        generateContractsIfNeeded()
        return OfflineReport(
            minutes = settled / 60_000L,
            earnedCents = result.earnedCents,
            producedUnits = result.producedUnits,
            completedContracts = result.completedContracts
        )
    }

    override suspend fun tickProduction() {
        simulationMutex.withLock {
            val company = companyDao.get() ?: return@withLock
            val now = System.currentTimeMillis()
            val elapsedMillis = (now - company.lastSimulationAt).coerceAtLeast(0L)
            val settled = SimulationCadence.settledMillis(elapsedMillis)
            if (settled < SimulationCadence.CYCLE_MILLIS) return@withLock

            simulateProduction(
                elapsedMillis = settled,
                eventTime = now,
                lastSimulationAtAfter = company.lastSimulationAt + settled,
                summaryDescription = "Fechamento da produção • ${settled / SimulationCadence.CYCLE_MILLIS} ciclo(s) de 10 min"
            )
        }
    
        // V8_REFRESH_CONTRACTS_AFTER_TICK
        generateContractsIfNeeded()
    }

    override suspend fun accelerateProduction10Minutes(): Result<Long> = runCatching {
        simulationMutex.withLock {
            val company = companyDao.get() ?: error("Empresa não inicializada")
            val snapshot = calculateProduction(machineDao.getAll(), employeeDao.getAll(), gamePreferences.workforce.first(), expansionRepository.snapshot())
            require(snapshot.operatingMachines > 0) { "Nenhuma máquina está produzindo agora" }

            val result = simulateProduction(
                elapsedMillis = EconomyBalance.BOOST_CYCLE_MILLIS,
                eventTime = System.currentTimeMillis(),
                // O impulso é bônus: não altera o relógio do fechamento normal.
                lastSimulationAtAfter = company.lastSimulationAt,
                summaryDescription = "Impulso de produção • +10 min instantâneos • ganhos 3x"
            )
            result.earnedCents
        }
    }

    override suspend fun grantBonusCash(amountCents: Long, description: String): Result<Unit> = runCatching {
        simulationMutex.withLock {
            val amount = amountCents.coerceAtLeast(0L)
            require(amount > 0L) { "Recompensa inválida" }
            val company = companyDao.get() ?: error("Empresa não inicializada")
            companyDao.upsert(company.copy(cashCents = company.cashCents + amount))
            financeDao.insert(
                transaction(
                    TransactionType.INCOME,
                    TransactionCategory.BONUS,
                    amount,
                    description
                )
            )
        }
    }

    override suspend fun buyTeamSnack(): Result<Long> = runCatching {
        simulationMutex.withLock {
            val company = companyDao.get() ?: error("Empresa não inicializada")
            val cost = EconomyBalance.TEAM_SNACK_COST_CENTS
            require(company.cashCents >= cost) { "Caixa insuficiente para comprar o cento de salgados" }
            companyDao.upsert(company.copy(cashCents = company.cashCents - cost))
            financeDao.insert(
                transaction(
                    TransactionType.EXPENSE,
                    TransactionCategory.SALARY,
                    cost,
                    "Cento de salgados • foco da equipe por 8h"
                )
            )
            cost
        }
    }

    private data class SimulationResult(
        val earnedCents: Long,
        val producedUnits: Double,
        val completedContracts: Int
    )

    private suspend fun simulateProduction(
        elapsedMillis: Long,
        eventTime: Long,
        lastSimulationAtAfter: Long,
        summaryDescription: String?
    ): SimulationResult {
        val company = companyDao.get() ?: return SimulationResult(0, 0.0, 0)
        val machines = machineDao.getAll()
        val employees = employeeDao.getAll()
        val workforce = gamePreferences.workforce.first()
                val expansion = expansionRepository.snapshot()
        // V11_WORK_LIFE_SIMULATION
        val workLife = workLifeRepository.snapshot()
        val simulationStart = (eventTime - elapsedMillis.coerceAtLeast(0L)).coerceAtLeast(0L)
        val workSlice = workLifeRepository.slice(simulationStart, eventTime, workLife.mode)

        // No turno 12h, o relógio do contrato NÃO corre enquanto a equipe está em casa.
        if (workSlice.pausedMillis > 0L) {
            contractDao.getActive().forEach { active ->
                contractDao.update(active.copy(deadlineAt = active.deadlineAt + workSlice.pausedMillis))
            }
        }

        val productiveEmployees = employees.filterNot { workLife.isResting(it.id, eventTime) }
        val snapshot = calculateProduction(machines, productiveEmployees, workforce, expansion)
        val activeWorkerIds = productiveEmployees
            .filter { it.assignedMachineId != null }
            .map { it.id }
            .toMutableList()
            .apply { if (machines.isNotEmpty()) add(WorkLifeRepository.PLAYER_ID) }

        val exhaustionMultiplier = workLifeRepository.productivityMultiplier(workLife, activeWorkerIds)
        val elapsedHours = workSlice.workMillis / 3_600_000.0
        val elapsedMinutes = workSlice.workMillis / 60_000L
        val producedUnits = snapshot.totalUnitsPerHour * elapsedHours * exhaustionMultiplier
        val passiveNet = EconomyBalance.boostedProfit(
            (snapshot.netPerHourCents * elapsedHours * exhaustionMultiplier).toLong().coerceAtLeast(0)
        )
        var contractRewards = 0L
        var completedContracts = 0
        var productionMilli = (producedUnits * 1000.0).toLong().coerceAtLeast(0)

        if (productionMilli > 0) {
            for (contract in contractDao.getActive()) {
                if (productionMilli <= 0) break
                val targetMilli = contract.quantity * 1000L
                val currentMilli = contract.productionProgressMilli.coerceAtMost(targetMilli)
                val needed = (targetMilli - currentMilli).coerceAtLeast(0)
                val toolEffect = expansion.toolEffectForContract(contract.id)
            val effectiveQuality = (snapshot.averageQuality + toolEffect.qualityBonus).coerceIn(0, 100)
            val qualityGap = contract.requiredQuality - effectiveQuality
                val qualityFactor = when {
                    qualityGap <= 0 -> 1.0
                    qualityGap <= 10 -> 0.70
                    else -> 0.30
                }
                val acceptedAvailable = (productionMilli * qualityFactor * toolEffect.speedMultiplier).toLong()
                val applied = min(acceptedAvailable, needed)
                val effectiveFactor = qualityFactor * toolEffect.speedMultiplier
            val rawConsumed = if (effectiveFactor <= 0.0) productionMilli else kotlin.math.ceil(applied / effectiveFactor).toLong()
                val newProgress = currentMilli + applied
                productionMilli = (productionMilli - rawConsumed).coerceAtLeast(0)

                if (newProgress >= targetMilli) {
                    val paidNow = contractDao.settleReward(
                        contract = contract.copy(
                            completedQuantity = contract.quantity,
                            productionProgressMilli = targetMilli,
                            status = ContractStatus.COMPLETED.name
                        ),
                        payout = contractPayoutTransaction(
                            contract,
                            "Contrato concluído pela produção: ${contract.clientName}"
                        )
                    )
                    if (paidNow) contractRewards += contract.rewardCents
            // V7_XP_AUTO_CONTRACT
            if (paidNow) expansionRepository.addPlayerXp(ContractProgression.characterXp(contract))
                    completedContracts++
                } else {
                    contractDao.update(
                        contract.copy(
                            completedQuantity = (newProgress / 1000L).toInt(),
                            productionProgressMilli = newProgress
                        )
                    )
                }
            }
        }

        if (elapsedMinutes > 0) {
            val operatingIds = snapshot.machineProduction.filter { it.isOperating }.map { it.machineId }.toSet()
            machines.filter { it.id in operatingIds }.forEach { machine ->
                val newMinutes = machine.accumulatedWorkMinutes + elapsedMinutes
                val wearBefore = machine.accumulatedWorkMinutes / 20L
                val wearAfter = newMinutes / 20L
                val wear = (wearAfter - wearBefore).toInt().coerceAtLeast(0)
                machineDao.update(
                    machine.copy(
                        accumulatedWorkMinutes = newMinutes,
                        condition = (machine.condition - wear).coerceAtLeast(0)
                    )
                )
            }

            employees.filter { it.assignedMachineId in operatingIds }.forEach { employee ->
                val gainedExperience = elapsedMinutes.coerceAtMost(Int.MAX_VALUE.toLong())
                val newExperience = employee.experience + gainedExperience
                val newLevel = (1 + (newExperience / 480L).toInt()).coerceIn(employee.skillLevel, 10)
                employeeDao.update(employee.copy(experience = newExperience, skillLevel = newLevel))
            }

            updateLegendaryMissionProgress(
                elapsedMinutes = elapsedMinutes,
                snapshot = snapshot,
                machines = machines,
                employees = employees
            )
        }

        var penalties = 0L
        var reputationLoss = 0
        for (contract in contractDao.getActive()) {
            if (eventTime > contract.deadlineAt) {
                contractDao.update(contract.copy(status = ContractStatus.FAILED.name))
                penalties += contract.penaltyCents
                reputationLoss += contract.reputationPenalty
                financeDao.insert(
                    transaction(
                        TransactionType.EXPENSE,
                        TransactionCategory.CONTRACT,
                        contract.penaltyCents,
                        "Multa por atraso: ${contract.clientName}"
                    )
                )
            }
        }

        val totalEarned = passiveNet + contractRewards

        // settleReward() pode ter atualizado caixa/reputação dentro de uma transação Room.
        // Releia a empresa antes de aplicar produção passiva/multas para não sobrescrever
        // o prêmio recém-creditado com uma cópia antiga da CompanyEntity.
        val latestCompany = companyDao.get() ?: company
        val newReputation = (latestCompany.reputation - reputationLoss).coerceAtLeast(0)
        val newLevel = (1 + newReputation / 20).coerceAtLeast(latestCompany.companyLevel)
        companyDao.upsert(
            latestCompany.copy(
                cashCents = (latestCompany.cashCents + passiveNet - penalties).coerceAtLeast(0),
                reputation = newReputation,
                companyLevel = newLevel,
                lastSimulationAt = lastSimulationAtAfter
            )
        )

        if (summaryDescription != null && passiveNet > 0) {
            financeDao.insert(
                transaction(
                    TransactionType.INCOME,
                    TransactionCategory.PRODUCTION,
                    passiveNet,
                    summaryDescription
                )
            )
        }

        // V11_WORK_LIFE_ADVANCE
        workLifeRepository.advance(employees, workSlice, eventTime)

        return SimulationResult(totalEarned, producedUnits, completedContracts)
    }

    private fun calculateProduction(
        machines: List<MachineEntity>,
        employees: List<EmployeeEntity>,
        workforce: WorkforceState = WorkforceState(),
        expansion: ExpansionState = ExpansionState()
    ): ProductionSnapshot {
        val now = System.currentTimeMillis()
        val idleIds = if (workforce.snackImmunityActive(now)) {
            emptySet()
        } else {
            workforce.activeIdleEmployeeId(now)?.let(::setOf) ?: emptySet()
        }
        return ProductionEngine.calculate(
            machines = machines.filter { it.installed }.map {
                MachineRuntime(it.id, it.machineType, it.level, it.condition)
            },
            employees = employees.map {
                EmployeeRuntime(it.id, it.specialty, it.skillLevel, it.morale, it.trait, it.assignedMachineId, it.legendaryCode)
            },
            idleEmployeeIds = idleIds,
        modifiers = expansion.productionModifiers()
    )
    }

    private suspend fun seedStarterMachine(now: Long) {
        val def = MachineCatalog.all.first()
        machineDao.insert(
            MachineEntity(
                UUID.randomUUID().toString(), def.type.name, null, SectorType.TURNING.name,
                1, 850, 0, true, 0, 0, now
            )
        )
        val company = companyDao.get() ?: return
        companyDao.upsert(company.copy(usedWarehouseSpace = company.usedWarehouseSpace + def.space))
    }

    private suspend fun seedGoals() {
        goalDao.insertMissing(
            listOf(
                GoalEntity("first_employee", "Contrate seu primeiro funcionário", 1, 0, 250_000, false),
                GoalEntity("three_machines", "Tenha 3 máquinas instaladas", 3, 1, 500_000, false),
                GoalEntity("reputation_10", "Alcance 10 de reputação", 10, 0, 750_000, false),

                GoalEntity("ten_machines", "Complexo industrial • 10 máquinas", 10, 0, 25_000_000, false),
                GoalEntity("twenty_machines", "Parque fabril gigante • 20 máquinas", 20, 0, 75_000_000, false),
                GoalEntity("thirty_machines", "IMPÉRIO INDUSTRIAL • 30 máquinas • +10 fichas", 30, 0, 200_000_000, false),
                GoalEntity("fifteen_employees", "Equipe de elite • 15 funcionários", 15, 0, 50_000_000, false),
                GoalEntity("thirty_employees", "Mega operação • 30 funcionários • +8 fichas", 30, 0, 180_000_000, false),
                GoalEntity("reputation_100", "Referência regional • 100 reputação", 100, 0, 80_000_000, false),
                GoalEntity("reputation_250", "Lenda da indústria • 250 reputação • +10 fichas", 250, 0, 250_000_000, false),
                GoalEntity("reputation_500", "Nome mundial • 500 reputação • +20 fichas", 500, 0, 600_000_000, false),
                GoalEntity("company_level_10", "Empresa nível 10", 10, 0, 100_000_000, false),
                GoalEntity("company_level_20", "Empresa nível 20 • +15 fichas", 20, 0, 500_000_000, false),
                GoalEntity("warehouse_300", "Galpão de 300 m²", 300, 0, 120_000_000, false),
                GoalEntity("warehouse_500", "Mega galpão de 500 m² • +10 fichas", 500, 0, 300_000_000, false),
            )
        )
    }

    override suspend fun buyMachine(machineType: String): Result<Unit> = runCatching {
        val def = MachineCatalog.byType(machineType) ?: error("Máquina desconhecida")
        val company = companyDao.get() ?: error("Empresa não inicializada")
        require(company.cashCents >= def.priceCents) { "Dinheiro insuficiente" }
        require(company.usedWarehouseSpace + def.space <= company.warehouseSpace) { "Sem espaço no galpão" }

        val position = findFreeGridPosition(machineDao.getAll())
        val now = System.currentTimeMillis()
        machineDao.insert(
            MachineEntity(
                UUID.randomUUID().toString(), def.type.name, null, sectorFor(def.type).name,
                1, 1000, 0, true, position.first, position.second, now
            )
        )
        companyDao.upsert(
            company.copy(
                cashCents = company.cashCents - def.priceCents,
                usedWarehouseSpace = company.usedWarehouseSpace + def.space
            )
        )
        financeDao.insert(transaction(TransactionType.EXPENSE, TransactionCategory.MACHINE, def.priceCents, "Compra: ${def.name}"))
    }

    override suspend fun sellMachine(machineId: String): Result<Long> = runCatching {
        simulationMutex.withLock {
            val machine = machineDao.getAll().firstOrNull { it.id == machineId }
                ?: error("Máquina não encontrada")
            val def = MachineCatalog.byType(machine.machineType)
                ?: error("Máquina desconhecida")
            val company = companyDao.get() ?: error("Empresa não inicializada")

            // Revenda: até 60% do preço original em perfeito estado.
            // A conservação reduz gradualmente esse valor até 30% do preço original.
            val conditionFactor = 0.5 + (machine.condition.coerceIn(0, 1000) / 2000.0)
            val resaleCents = (def.priceCents * 0.60 * conditionFactor).toLong().coerceAtLeast(1L)

            // Libera qualquer operador antes de remover a máquina.
            employeeDao.getAll()
                .filter { it.assignedMachineId == machine.id }
                .forEach { employeeDao.update(it.copy(assignedMachineId = null)) }

            machineDao.delete(machine)
            companyDao.upsert(
                company.copy(
                    cashCents = company.cashCents + resaleCents,
                    usedWarehouseSpace = (company.usedWarehouseSpace - def.space).coerceAtLeast(0)
                )
            )
            financeDao.insert(
                transaction(
                    TransactionType.INCOME,
                    TransactionCategory.MACHINE,
                    resaleCents,
                    "Venda: ${def.name}"
                )
            )

            resaleCents
        }
    }

    override suspend fun moveMachine(machineId: String, gridX: Int, gridY: Int): Result<Unit> = runCatching {
        val x = gridX.coerceIn(0, 4)
        val y = gridY.coerceIn(0, 5)
        val machines = machineDao.getAll()
        val machine = machines.firstOrNull { it.id == machineId } ?: error("Máquina não encontrada")
        require(machines.none { it.id != machineId && it.gridX == x && it.gridY == y }) { "Esse espaço já está ocupado" }
        machineDao.update(machine.copy(gridX = x, gridY = y))
    }

    override suspend fun assignEmployee(machineId: String, employeeId: String?): Result<Unit> = runCatching {
        val machine = machineDao.getAll().firstOrNull { it.id == machineId } ?: error("Máquina não encontrada")
        val employees = employeeDao.getAll()

        employees.filter { it.assignedMachineId == machine.id && it.id != employeeId }.forEach {
            employeeDao.update(it.copy(assignedMachineId = null))
        }

        if (employeeId != null) {
            val selected = employees.firstOrNull { it.id == employeeId } ?: error("Funcionário não encontrado")
            employeeDao.update(selected.copy(assignedMachineId = machine.id))
        }
    }

    override suspend fun repairMachine(machineId: String): Result<Unit> = runCatching {
        val machine = machineDao.getAll().firstOrNull { it.id == machineId } ?: error("Máquina não encontrada")
        val def = MachineCatalog.byType(machine.machineType) ?: error("Máquina desconhecida")
        val company = companyDao.get() ?: error("Empresa não inicializada")
        val missingCondition = (1000 - machine.condition).coerceAtLeast(0)
        require(missingCondition > 0) { "A máquina já está em perfeito estado" }
        val cost = (def.maintenanceCents * missingCondition / 1000L).coerceAtLeast(5_000L)
        require(company.cashCents >= cost) { "Caixa insuficiente para manutenção" }
        machineDao.update(machine.copy(condition = 1000))
        companyDao.upsert(company.copy(cashCents = company.cashCents - cost))
        financeDao.insert(transaction(TransactionType.EXPENSE, TransactionCategory.MAINTENANCE, cost, "Manutenção: ${def.name}"))
    }

    private fun findFreeGridPosition(machines: List<MachineEntity>): Pair<Int, Int> {
        for (y in 0..5) for (x in 0..4) {
            if (machines.none { it.gridX == x && it.gridY == y }) return x to y
        }
        return 0 to 0
    }

    override suspend fun hireRandomEmployee(): Result<Unit> = runCatching {
        val company = companyDao.get() ?: error("Empresa não inicializada")
        val specialty = EmployeeSpecialty.entries.random()
        val level = Random.nextInt(1, 5)
        val salary = 180_000L + level * 65_000L
        require(company.cashCents >= salary) { "Caixa insuficiente para a contratação" }
        val name = "${EmployeeCatalog.firstNames.random()} ${EmployeeCatalog.lastNames.random()}"
        val employee = EmployeeEntity(
            UUID.randomUUID().toString(), name, specialty.name, level, 0, salary,
            Random.nextInt(65, 96), EmployeeCatalog.traits.random(), System.currentTimeMillis(), null,
            isLegendary = false,
            legendaryCode = null
        )
        employeeDao.insert(employee)
        companyDao.upsert(company.copy(cashCents = company.cashCents - salary))
        financeDao.insert(transaction(TransactionType.EXPENSE, TransactionCategory.SALARY, salary, "Admissão e primeiro salário: $name"))
    }

    override suspend fun hireLegendaryEmployee(): Result<String> = runCatching {
        val company = companyDao.get() ?: error("Empresa não inicializada")
        val hiredCodes = employeeDao.getAll().mapNotNull { it.legendaryCode }.toSet()
        val available = LegendaryEmployeeCatalog.all.filter {
            it.unlockLevel <= company.companyLevel && it.code !in hiredCodes
        }
        require(available.isNotEmpty()) {
            val nextLevel = LegendaryEmployeeCatalog.all
                .filter { it.code !in hiredCodes }
                .minOfOrNull { it.unlockLevel }
            if (nextLevel != null && nextLevel > company.companyLevel) {
                "Próximo funcionário lendário libera no nível $nextLevel da empresa"
            } else {
                "Todos os funcionários lendários disponíveis já foram contratados"
            }
        }

        val affordable = available.filter { it.salaryCents <= company.cashCents }
        require(affordable.isNotEmpty()) {
            "Caixa insuficiente para os lendários liberados neste nível"
        }
        val legendary = affordable.random()

        val employee = EmployeeEntity(
            id = UUID.randomUUID().toString(),
            name = legendary.name,
            specialty = legendary.specialty.name,
            skillLevel = legendary.skillLevel,
            experience = 0,
            salaryCents = legendary.salaryCents,
            morale = legendary.morale,
            trait = legendary.trait,
            hiredAt = System.currentTimeMillis(),
            assignedMachineId = null,
            isLegendary = true,
            legendaryCode = legendary.code
        )
        employeeDao.insert(employee)
        companyDao.upsert(company.copy(cashCents = company.cashCents - legendary.salaryCents))
        financeDao.insert(
            transaction(
                TransactionType.EXPENSE,
                TransactionCategory.SALARY,
                legendary.salaryCents,
                "Contratação lendária: ${legendary.name}"
            )
        )
        seedLegendaryMission(legendary.code)
        legendary.name
    }

    override suspend fun fireEmployee(employee: EmployeeEntity) {
        employeeDao.delete(employee)
    }

    override suspend fun generateContractsIfNeeded() {
        val company = companyDao.get() ?: return
        val level = maxOf(company.companyLevel, 1 + company.reputation / 20).coerceAtLeast(1)
        if (level != company.companyLevel) {
            companyDao.upsert(company.copy(companyLevel = level))
        }
        val existing = contractDao.getAvailableForProgression()
        val eligibleCount = existing.count { ContractProgression.minLevel(it.difficulty) <= level }
        val target = ContractProgression.targetAvailable(level)
        val missing = (target - eligibleCount).coerceAtLeast(0)
        if (missing == 0) return

        val allowed = mutableListOf<Int>()
        for (difficulty in ContractProgression.allowedDifficulties(level)) {
            if (expansionRepository.contractAccess(difficulty, level).allowed) allowed += difficulty
        }
        val pool = allowed.ifEmpty { listOf(1) }
        val now = System.currentTimeMillis()
        val clients = listOf(
            "Metalúrgica Horizonte", "AutoPeças Brasil", "AgroMec", "Hidráulica Forte",
            "AçoSul", "TecnoBombas", "AeroMec", "MinasTech", "Ferrovia Sul", "Precision Parts"
        )
        val normalTypes = listOf("Peça unitária", "Lote pequeno", "Lote médio", "Retrabalho", "Eixo e flange", "Dispositivo industrial")
        val specialTypes = listOf("Protótipo crítico", "Lote urgente", "Peça aeroespacial", "Recuperação de emergência", "Tolerância extrema")

        val highestDifficulty = pool.maxOrNull() ?: 1
        val values = (1..missing).map { slot ->
            // Garante oportunidades da faixa mais alta recém-liberada, sem eliminar contratos simples.
            val difficulty = if (slot <= 2) highestDifficulty else pool.random()
            val special = Random.nextInt(100) < ContractProgression.specialChancePct(level)
            val qtyUpper = (18 + level * 2).coerceAtMost(70)
            val qty = Random.nextInt(5, qtyUpper) * difficulty
            val quality = (45 + difficulty * 8 + if (special) 7 else 0).coerceAtMost(96)
            val baseReward = 350_000L + difficulty * 280_000L + qty * 13_500L + level * 35_000L
            val reward = if (special) baseReward * 18L / 10L else baseReward
            val reputationReward = ContractProgression.reputationReward(difficulty, special)
            val penalty = if (special) reward / 3L else reward / 4L
            val repPenalty = difficulty + if (special) 2 else 0
            val hours = (8 + difficulty * 3 - if (special) 2 else 0).coerceAtLeast(6)
            val job = if (special) "⭐ Especial • ${specialTypes.random()}" else normalTypes.random()

            ContractEntity(
                UUID.randomUUID().toString(), clients.random(), job, qty, 0, difficulty,
                quality, reward, penalty, reputationReward, repPenalty,
                now, null, now + hours * 60L * 60L * 1000L,
                ContractStatus.AVAILABLE.name,
            )
        }
        contractDao.insertAll(values)
    }

    override suspend fun acceptContract(contract: ContractEntity): Result<Unit> = runCatching {
        require(contract.status == ContractStatus.AVAILABLE.name) { "Contrato indisponível" }
        val companyForContract = companyDao.get() ?: error("Empresa não inicializada")
        val contractAccess = expansionRepository.contractAccess(contract.difficulty, companyForContract.companyLevel)
        require(contractAccess.allowed) { contractAccess.reason }
        contractDao.update(
            contract.copy(
                status = ContractStatus.ACTIVE.name,
                startedAt = System.currentTimeMillis(),
                productionProgressMilli = contract.completedQuantity * 1000L
            )
        )
    }

    override suspend fun cancelContract(contractId: String): Result<Long> = runCatching {
        val penalty = simulationMutex.withLock {
            val current = contractDao.byId(contractId) ?: error("Contrato não encontrado")
            require(current.status == ContractStatus.ACTIVE.name) { "Somente contratos ativos podem ser cancelados" }
            val company = companyDao.get() ?: error("Empresa não inicializada")
            val penalty = current.penaltyCents.coerceAtLeast(1L)
            require(company.cashCents >= penalty) { "Caixa insuficiente para pagar a multa de cancelamento" }

            contractDao.update(current.copy(status = ContractStatus.FAILED.name))
            companyDao.upsert(
                company.copy(
                    cashCents = company.cashCents - penalty,
                    reputation = (company.reputation - current.reputationPenalty).coerceAtLeast(0)
                )
            )
            financeDao.insert(transaction(TransactionType.EXPENSE, TransactionCategory.CONTRACT, penalty, "Multa por descumprimento: ${current.clientName}"))
            runCatching { expansionRepository.bindTool(current.id, null) }
            penalty
        }
        generateContractsIfNeeded()
        penalty
    }

    override suspend fun dismissFailedContract(contractId: String): Result<Unit> = runCatching {
        val current = contractDao.byId(contractId) ?: error("Contrato não encontrado")
        require(current.status == ContractStatus.FAILED.name) { "Somente contratos com falha podem ser excluídos" }
        contractDao.dismissFailed(contractId)
    }

    override suspend fun completeContract(contract: ContractEntity): Result<Unit> = runCatching {
        simulationMutex.withLock {
            val current = contractDao.byId(contract.id) ?: error("Contrato não encontrado")
            require(
                current.status == ContractStatus.ACTIVE.name || current.status == ContractStatus.COMPLETED.name
            ) { "Contrato não está ativo" }
            require(current.completedQuantity >= current.quantity) { "A produção do contrato ainda não terminou" }

            val rewardPaid = contractDao.settleReward(
                contract = current,
                payout = contractPayoutTransaction(
                    current,
                    "Contrato concluído: ${current.clientName}"
                )
            )

            if (rewardPaid) expansionRepository.consumeBoundTool(current.id)
            // V7_XP_MANUAL_CONTRACT
            if (rewardPaid) expansionRepository.addPlayerXp(ContractProgression.characterXp(current))

            val latestCompany = companyDao.get() ?: error("Empresa não inicializada")
            val correctedLevel = (1 + latestCompany.reputation / 20).coerceAtLeast(latestCompany.companyLevel)
            if (correctedLevel != latestCompany.companyLevel) {
                companyDao.upsert(latestCompany.copy(companyLevel = correctedLevel))
            }
        }
        generateContractsIfNeeded()
    }

    override suspend fun recoverContractReward(contractId: String): Result<Long> = runCatching {
        simulationMutex.withLock {
            val contract = contractDao.byId(contractId) ?: error("Contrato não encontrado")
            require(contract.status == ContractStatus.COMPLETED.name) { "O contrato ainda não está concluído" }

            val recovered = contractDao.recoverReward(
                contract = contract,
                payout = contractPayoutTransaction(
                    contract,
                    "Recuperação de prêmio: ${contract.clientName}"
                )
            )
            require(recovered) { "Esse prêmio já foi recuperado nesta versão" }

            val latestCompany = companyDao.get() ?: error("Empresa não inicializada")
            val correctedLevel = (1 + latestCompany.reputation / 20).coerceAtLeast(latestCompany.companyLevel)
            if (correctedLevel != latestCompany.companyLevel) {
                companyDao.upsert(latestCompany.copy(companyLevel = correctedLevel))
            }
            contract.rewardCents
        }
    }

    override suspend fun upgradeWarehouse(): Result<Unit> = runCatching {
        val company = companyDao.get() ?: error("Empresa não inicializada")
        val currentLevel = ((company.warehouseSpace - 100) / 50) + 1
        val cost = 2_000_000L * currentLevel
        require(company.cashCents >= cost) { "Dinheiro insuficiente" }
        companyDao.upsert(company.copy(cashCents = company.cashCents - cost, warehouseSpace = company.warehouseSpace + 50))
        facilityDao.upsert(FacilityUpgradeEntity("WAREHOUSE_EXPANSION", currentLevel + 1))
        financeDao.insert(transaction(TransactionType.EXPENSE, TransactionCategory.FACILITY, cost, "Expansão do galpão"))
    }

    override suspend fun claimGoal(goal: GoalEntity): Result<Unit> = runCatching {
        require(!goal.claimed) { "Recompensa já coletada" }
        val company = companyDao.get() ?: error("Empresa não inicializada")
        val currentProgress = when (goal.id) {
            "first_employee" -> if (employeeDao.getAll().isNotEmpty()) 1 else 0
            "reputation_10", "reputation_100", "reputation_250", "reputation_500" -> company.reputation
            "three_machines", "ten_machines", "twenty_machines", "thirty_machines" -> machineDao.getAll().size
            "fifteen_employees", "thirty_employees" -> employeeDao.getAll().size
            "company_level_10", "company_level_20" -> company.companyLevel
            "warehouse_300", "warehouse_500" -> company.warehouseSpace
            else -> goal.progress
        }
        require(currentProgress >= goal.target) { "Meta ainda não concluída" }
        goalDao.update(goal.copy(progress = currentProgress, claimed = true))
        companyDao.upsert(company.copy(cashCents = company.cashCents + goal.rewardCents))
        financeDao.insert(transaction(TransactionType.INCOME, TransactionCategory.BONUS, goal.rewardCents, "Recompensa de meta: ${goal.title}"))
        val bonusTickets = when (goal.id) {
            "thirty_machines" -> 10
            "thirty_employees" -> 8
            "reputation_250" -> 10
            "reputation_500" -> 20
            "company_level_20" -> 15
            "warehouse_500" -> 10
            else -> 0
        }
        if (bonusTickets > 0) expansionRepository.addTickets(bonusTickets)
    }

    override suspend fun claimLegendaryMission(mission: LegendaryMissionEntity): Result<Unit> = runCatching {
        val current = legendaryMissionDao.getByLegendaryCode(mission.legendaryCode)
            ?: error("Missão lendária não encontrada")
        require(!current.claimed) { "Recompensa já coletada" }
        require(current.progress >= current.target) { "Missão lendária ainda não concluída" }
        val company = companyDao.get() ?: error("Empresa não inicializada")
        legendaryMissionDao.update(current.copy(claimed = true))
        companyDao.upsert(company.copy(cashCents = company.cashCents + current.rewardCents))
        financeDao.insert(
            transaction(
                TransactionType.INCOME,
                TransactionCategory.BONUS,
                current.rewardCents,
                "Missão lendária: ${current.title}"
            )
        )
    }

    private suspend fun syncLegendaryMissionSeeds() {
        employeeDao.getAll().mapNotNull { it.legendaryCode }.distinct().forEach { seedLegendaryMission(it) }
    }

    private suspend fun seedLegendaryMission(legendaryCode: String) {
        if (legendaryMissionDao.getByLegendaryCode(legendaryCode) != null) return
        val def = LegendaryMissionCatalog.byLegendaryCode(legendaryCode) ?: return
        legendaryMissionDao.upsert(
            LegendaryMissionEntity(
                id = def.id,
                legendaryCode = def.legendaryCode,
                title = def.title,
                description = def.description,
                metric = def.metric.name,
                target = def.target,
                progress = 0,
                rewardCents = def.rewardCents,
                claimed = false
            )
        )
    }

    private suspend fun updateLegendaryMissionProgress(
        elapsedMinutes: Long,
        snapshot: ProductionSnapshot,
        machines: List<MachineEntity>,
        employees: List<EmployeeEntity>
    ) {
        if (elapsedMinutes <= 0) return
        val machineRuntime = machines.map { MachineRuntime(it.id, it.machineType, it.level, it.condition) }
        val employeeRuntimeByCode = employees.mapNotNull { employee ->
            employee.legendaryCode?.let { code ->
                code to EmployeeRuntime(
                    employee.id,
                    employee.specialty,
                    employee.skillLevel,
                    employee.morale,
                    employee.trait,
                    employee.assignedMachineId,
                    employee.legendaryCode
                )
            }
        }.toMap()

        for (mission in legendaryMissionDao.getAll()) {
            if (mission.claimed || mission.progress >= mission.target) continue
            val def = LegendaryMissionCatalog.byId(mission.id) ?: continue
            val delta = LegendaryMissionProgressEngine.progressDelta(
                definition = def,
                employee = employeeRuntimeByCode[mission.legendaryCode],
                machines = machineRuntime,
                snapshot = snapshot,
                elapsedMinutes = elapsedMinutes
            )
            if (delta > 0) {
                legendaryMissionDao.update(
                    mission.copy(progress = (mission.progress + delta).coerceAtMost(mission.target))
                )
            }
        }
    }

    /**
     * Recupera pagamentos que, em versões anteriores, podiam ficar entre o status
     * CONCLUÍDO e a atualização do caixa. A checagem de lançamentos legados evita
     * pagar duas vezes contratos que já foram liquidados corretamente.
     */
    private suspend fun repairMissedContractRewards() {
        var repairedAny = false
        for (contract in contractDao.getCompleted()) {
            val repaired = contractDao.repairRewardIfClearlyMissing(
                contract = contract,
                payout = contractPayoutTransaction(
                    contract,
                    "Pagamento recuperado do contrato: ${contract.clientName}"
                )
            )
            repairedAny = repairedAny || repaired
        }

        if (repairedAny) {
            val latestCompany = companyDao.get() ?: return
            val correctedLevel = (1 + latestCompany.reputation / 20).coerceAtLeast(latestCompany.companyLevel)
            if (correctedLevel != latestCompany.companyLevel) {
                companyDao.upsert(latestCompany.copy(companyLevel = correctedLevel))
            }
        }
    }

    private fun contractPayoutTransaction(
        contract: ContractEntity,
        description: String
    ) = FinancialTransactionEntity(
        id = "contract_reward_${contract.id}",
        type = TransactionType.INCOME.name,
        category = TransactionCategory.CONTRACT.name,
        amountCents = contract.rewardCents,
        description = description,
        createdAt = System.currentTimeMillis()
    )

    private fun transaction(type: TransactionType, category: TransactionCategory, amount: Long, description: String) =
        FinancialTransactionEntity(UUID.randomUUID().toString(), type.name, category.name, amount, description, System.currentTimeMillis())

    private fun sectorFor(type: MachineType): SectorType = when (type) {
        MachineType.MECHANICAL_LATHE, MachineType.CNC_LATHE -> SectorType.TURNING
        MachineType.UNIVERSAL_MILL, MachineType.CNC_MACHINING_CENTER_3_AXIS,
        MachineType.CNC_MACHINING_CENTER_5_AXIS, MachineType.EDM -> SectorType.MILLING
        MachineType.COLUMN_DRILL, MachineType.CNC_DRILL -> SectorType.DRILLING
        MachineType.CYLINDRICAL_GRINDER, MachineType.CNC_GRINDER -> SectorType.GRINDING
        MachineType.WELDING_BENCH, MachineType.ROBOTIC_WELDING,
        MachineType.LASER_CUTTER, MachineType.PLASMA_CUTTER -> SectorType.BOILERMAKING
    }


    // V10_COMPANY_NAME_IMPL
    override suspend fun renameCompany(newName: String): Result<Unit> = runCatching {
        val normalized = newName
            .trim()
            .replace(Regex("\\s+"), " ")
        require(normalized.length in 3..32) {
            "O nome da empresa deve ter entre 3 e 32 caracteres"
        }
        require(normalized.any { it.isLetterOrDigit() }) {
            "Digite um nome válido para a empresa"
        }

        val company = companyDao.get() ?: error("Empresa não inicializada")
        if (company.name != normalized) {
            companyDao.upsert(company.copy(name = normalized))
        }
    }
}
