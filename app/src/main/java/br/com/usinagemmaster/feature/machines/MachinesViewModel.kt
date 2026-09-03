package br.com.usinagemmaster.feature.machines
import br.com.usinagemmaster.data.repository.PremiumMachineInstaller
import br.com.usinagemmaster.data.preferences.ExpansionRepository
import br.com.usinagemmaster.data.preferences.WorkLifeRepository
import br.com.usinagemmaster.domain.worklife.WorkLifeState
import br.com.usinagemmaster.domain.simulation.FactorySimulation
import br.com.usinagemmaster.domain.simulation.FactoryFrame
import br.com.usinagemmaster.domain.simulation.FactoryInput
import br.com.usinagemmaster.domain.simulation.FactoryMachineInput
import br.com.usinagemmaster.domain.simulation.FactoryWorkerInput
import br.com.usinagemmaster.domain.simulation.FactoryOwnerSimulation
import br.com.usinagemmaster.domain.simulation.OwnerActivity
import br.com.usinagemmaster.data.local.entity.ProductionCargoEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.usinagemmaster.core.util.Formatters
import br.com.usinagemmaster.data.local.entity.EmployeeEntity
import br.com.usinagemmaster.data.local.entity.MachineEntity
import br.com.usinagemmaster.data.preferences.EngagementState
import br.com.usinagemmaster.data.preferences.GamePreferences
import br.com.usinagemmaster.data.preferences.GameSettings
import br.com.usinagemmaster.data.preferences.WorkforceState
import br.com.usinagemmaster.data.preferences.PlayerProfilePreferences
import br.com.usinagemmaster.domain.social.LocalPlayerProfile
import br.com.usinagemmaster.domain.social.SocialRepository
import br.com.usinagemmaster.domain.model.DashboardStatus
import br.com.usinagemmaster.domain.model.ProductionSnapshot
import br.com.usinagemmaster.domain.repository.GameRepository
import br.com.usinagemmaster.domain.simulation.EconomyBalance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToLong

@HiltViewModel
class MachinesViewModel @Inject constructor(
    private val repo: GameRepository,
    private val prefs: GamePreferences,
    playerProfilePreferences: PlayerProfilePreferences,
    private val social: SocialRepository,
    private val expansionRepository: ExpansionRepository,
    private val premiumMachineInstaller: PremiumMachineInstaller,
    workLifeRepository: WorkLifeRepository,
) : ViewModel() {
    val machines = repo.machines().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<MachineEntity>())
    val employees = repo.employees().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<EmployeeEntity>())
    val production = repo.production().stateIn(viewModelScope, SharingStarted.Eagerly, ProductionSnapshot())
    val dashboard = repo.dashboard().stateIn(viewModelScope, SharingStarted.Eagerly, DashboardStatus())
    val settings = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, GameSettings())
    val engagement = prefs.engagement.stateIn(viewModelScope, SharingStarted.Eagerly, EngagementState())
    val workforce = prefs.workforce.stateIn(viewModelScope, SharingStarted.Eagerly, WorkforceState())
    val playerProfile = playerProfilePreferences.profile.stateIn(viewModelScope, SharingStarted.Eagerly, LocalPlayerProfile())

    private val workLife = workLifeRepository.state.stateIn(viewModelScope, SharingStarted.Eagerly, WorkLifeState())
    private val factorySimulation = FactorySimulation()
    private val ownerSimulation = FactoryOwnerSimulation()
    val pendingCargo = repo.pendingCargo().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<ProductionCargoEntity>())
    private val deliveryRequest = MutableStateFlow<List<String>?>(null)
    private val _delivering = MutableStateFlow(false)
    val delivering = _delivering.asStateFlow()
    private var tripStarted = false

    fun deliverCargo() {
        val ids = pendingCargo.value.map { it.id }
        if (ids.isNotEmpty() && deliveryRequest.compareAndSet(null, ids)) _delivering.value = true
    }

    // One owner, monotonic clock, no database writes per frame. Stops when the UI stops collecting.
    val factoryFrame = flow {
        var previousTime = System.nanoTime()
        while (currentCoroutineContext().isActive) {
            val now = System.currentTimeMillis()
            val schedule = workLife.value
            val rates = production.value.machineProduction.associateBy { it.machineId }
            val phoneId = workforce.value.activeIdleEmployeeId(now)
            val input = FactoryInput(
                machines = machines.value.filter { it.installed }.map { machine ->
                    val rate = rates[machine.id]
                    FactoryMachineInput(machine.id, machine.gridX, machine.gridY, machine.installed,
                        machine.condition, rate?.isOperating == true, rate?.unitsPerHour ?: 0.0)
                },
                workers = employees.value.map { worker ->
                    FactoryWorkerInput(worker.id, worker.assignedMachineId, worker.skillLevel,
                        schedule.exhaustion(worker.id), schedule.isResting(worker.id, now), worker.id == phoneId)
                },
                open = schedule.factoryOpen(now),
                cycleStartedAt = dashboard.value.lastSimulationAt,
            )
            factorySimulation.update(input)
            ownerSimulation.update(input.machines)
            val request = deliveryRequest.value
            if (request != null && !tripStarted) tripStarted = ownerSimulation.start()
            val currentTime = System.nanoTime()
            val dt = (currentTime - previousTime) / 1_000_000_000.0
            val owner = ownerSimulation.advance(dt)
            if (request != null && owner.activity == OwnerActivity.AWAITING_PAYMENT) {
                repo.deliverCargo(request).fold(
                    onSuccess = { amount ->
                        ownerSimulation.paymentRecorded()
                        _message.value = if (amount > 0L) "Carga entregue • +${Formatters.money(amount)} no caixa"
                            else "Entrega registrada"
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        ownerSimulation.cancel()
                        _message.value = error.message ?: "Não foi possível entregar. A carga continua guardada."
                    },
                )
            }
            if (tripStarted && !ownerSimulation.snapshot().busy) {
                tripStarted = false
                deliveryRequest.value = null
                _delivering.value = false
            }
            val ownerFrame = ownerSimulation.snapshot()
            emit(factorySimulation.advance(dt).copy(owner = ownerFrame,
                cargoInTransit = if (ownerFrame.carrying) request.orEmpty() else emptyList()))
            previousTime = currentTime
            delay(50L)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), FactoryFrame())

    private var lastPresenceAt = 0L
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    // V5_PREMIUM_MACHINE_SYNC
    init {
        viewModelScope.launch {
            runCatching { premiumMachineInstaller.syncOwned(expansionRepository.snapshot().premiumMachines) }
        }
    }

    fun move(machineId: String, x: Int, y: Int) = viewModelScope.launch {
        _message.value = repo.moveMachine(machineId, x, y).exceptionOrNull()?.message
    }

    fun assign(machineId: String, employeeId: String?) = viewModelScope.launch {
        _message.value = repo.assignEmployee(machineId, employeeId).fold(
            { if (employeeId == null) "Operador removido" else "Operador atribuído" },
            { it.message ?: "Não foi possível atribuir o operador" }
        )
    }

    fun repair(machineId: String) = viewModelScope.launch {
        _message.value = repo.repairMachine(machineId).fold(
            { "Manutenção concluída" },
            { it.message ?: "Falha na manutenção" }
        )
    }

    fun sell(machineId: String, onSold: () -> Unit = {}) = viewModelScope.launch {
        _message.value = repo.sellMachine(machineId).fold(
            onSuccess = { value ->
                onSold()
                "Máquina vendida por ${Formatters.money(value)}"
            },
            onFailure = { it.message ?: "Não foi possível vender a máquina" }
        )
    }

    fun tickProduction() = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val candidates = employees.value
            .filter { it.assignedMachineId != null }
            .flatMap { employee ->
                val weight = when {
                    employee.trait == "Distraído" -> 4
                    employee.trait == "Falta muito" -> 3
                    employee.morale < 55 -> 3
                    employee.morale < 75 -> 2
                    employee.trait == "Perfeccionista" || employee.trait == "Cuidadoso" -> 1
                    else -> 1
                }
                List(weight) { employee.id }
            }
        val startedIdle = prefs.maybeStartEmployeeIdleness(candidates, now)
        if (startedIdle != null) {
            val name = employees.value.firstOrNull { it.id == startedIdle }?.name ?: "Um funcionário"
            _message.value = "📱 $name parou para mexer no celular. Toque nele na fábrica para ir dar bronca."
        }
        repo.tickProduction()
        if (now - lastPresenceAt >= 60_000L && social.isFirebaseConfigured() && playerProfile.value.onboardingComplete) {
            social.publishProfile(playerProfile.value, dashboard.value, production.value)
            lastPresenceAt = now
        }
    }

    fun claimDailyReward() = viewModelScope.launch {
        if (!prefs.claimDailyReward()) {
            _message.value = "A recompensa diária de hoje já foi resgatada"
            return@launch
        }
        val cashReward = maxOf(150_000L, production.value.netPer10MinutesCents)
        repo.grantBonusCash(cashReward, "Recompensa diária").fold(
            onSuccess = {
                _message.value = "Recompensa diária: +${Formatters.money(cashReward)} e +${EconomyBalance.DAILY_BOOST_TOKENS} impulsos"
            },
            onFailure = {
                _message.value = it.message ?: "Não foi possível resgatar a recompensa"
            }
        )
    }

    /**
     * score vai de 0 a 1. O minigame sempre premia quem conclui, mas precisão
     * alta rende mais caixa e um impulso extra.
     */
    fun completeMinigame(score: Float) = viewModelScope.launch {
        if (!engagement.value.minigameAvailable) {
            _message.value = "O minigame ainda está recarregando"
            return@launch
        }

        val safeScore = score.coerceIn(0f, 1f)
        val tokens = if (safeScore >= .82f) 2 else 1
        if (!prefs.recordMinigameReward(tokens)) {
            _message.value = "O minigame ainda está recarregando"
            return@launch
        }

        val baseCycle = production.value.netPer10MinutesCents.coerceAtLeast(80_000L)
        val cashReward = (baseCycle * (.30 + safeScore * .70)).roundToLong()
        repo.grantBonusCash(cashReward, "Minigame de produção • precisão ${(safeScore * 100).toInt()}%").fold(
            onSuccess = {
                _message.value = "Minigame: +${Formatters.money(cashReward)} e +$tokens impulso${if (tokens > 1) "s" else ""}"
            },
            onFailure = {
                _message.value = it.message ?: "Falha ao entregar prêmio do minigame"
            }
        )
    }

    fun accelerateProduction() = viewModelScope.launch {
        if (!prefs.consumeBoostToken()) {
            _message.value = "Sem impulsos. Ganhe mais no minigame ou na recompensa diária."
            return@launch
        }

        repo.accelerateProduction10Minutes().fold(
            onSuccess = { earned ->
                _message.value = "+10 min produzidos • ${Formatters.money(earned)} em carga para entregar"
            },
            onFailure = { error ->
                // Devolve a ficha se não foi possível produzir.
                prefs.addBoostTokens(1)
                _message.value = error.message ?: "Não foi possível acelerar a produção"
            }
        )
    }

    fun reprimand(employeeId: String) = viewModelScope.launch {
        val name = employees.value.firstOrNull { it.id == employeeId }?.name ?: "Funcionário"
        val ok = prefs.reprimandEmployee(employeeId)
        _message.value = if (ok) {
            "👷 Você foi até $name e deu a bronca. Ele voltou ao trabalho."
        } else {
            "$name já voltou ao trabalho."
        }
    }

    fun buyTeamSnack() = viewModelScope.launch {
        repo.buyTeamSnack().fold(
            onSuccess = { cost ->
                prefs.activateSnackImmunity()
                _message.value = "🥟 Cento de salgados comprado por ${Formatters.money(cost)}. Equipe imune à ociosidade por 8 horas."
            },
            onFailure = { _message.value = it.message ?: "Não foi possível comprar os salgados" }
        )
    }

    fun clearMessage() { _message.value = null }
}
