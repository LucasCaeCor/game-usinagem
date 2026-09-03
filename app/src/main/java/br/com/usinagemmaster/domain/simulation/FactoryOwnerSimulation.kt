package br.com.usinagemmaster.domain.simulation

enum class OwnerActivity(val label: String) {
    IDLE("Aguardando carga"), COLLECTING("Indo buscar a carga"),
    LOADING("Carregando caixas"), DELIVERING("Levando à entrega"),
    UNLOADING("Descarregando"), AWAITING_PAYMENT("Concluindo entrega"),
    RETURNING("Voltando ao galpão")
}

data class FactoryOwnerFrame(
    val position: FloorPoint = FactoryFloor.ENTRY.point(),
    val activity: OwnerActivity = OwnerActivity.IDLE,
    val walking: Boolean = false,
    val carrying: Boolean = false,
) {
    val busy: Boolean get() = activity != OwnerActivity.IDLE
}

/** Only controls the owner's trip. The repository alone can consume cargo or pay money. */
class FactoryOwnerSimulation {
    private var floor = FactoryFloor(emptyList())
    private var topology = emptyList<Triple<String, Int, Int>>()
    private var position = FactoryFloor.ENTRY.point()
    private var activity = OwnerActivity.IDLE
    private var route = emptyList<FloorPoint>()
    private var index = 0
    private var elapsed = 0.0
    private var destination = FactoryFloor.ENTRY

    fun update(machines: List<FactoryMachineInput>) {
        val next = machines.filter { it.installed }.map { Triple(it.id, it.gridX, it.gridY) }.sortedBy { it.first }
        if (next == topology) return
        topology = next
        floor = FactoryFloor(machines)
        position = floor.nearestWalkable(position).point()
        if (index < route.size) travel(destination, activity)
    }

    fun start(): Boolean {
        if (activity != OwnerActivity.IDLE) return false
        travel(FactoryFloor.STAGING, OwnerActivity.COLLECTING)
        return true
    }

    fun advance(seconds: Double): FactoryOwnerFrame {
        val dt = if (seconds.isFinite()) seconds.coerceIn(0.0, .25) else 0.0
        if (index < route.size) {
            var movement = (dt * 5.0).toFloat()
            while (index < route.size) {
                val target = route[index]
                val distance = position.distanceTo(target)
                if (distance > movement) {
                    position = FloorPoint(position.x + (target.x - position.x) * movement / distance,
                        position.y + (target.y - position.y) * movement / distance)
                    break
                }
                position = target
                movement -= distance
                index++
            }
            if (index == route.size) {
                activity = when (activity) {
                    OwnerActivity.COLLECTING -> OwnerActivity.LOADING
                    OwnerActivity.DELIVERING -> OwnerActivity.UNLOADING
                    OwnerActivity.RETURNING -> OwnerActivity.IDLE
                    else -> activity
                }
                elapsed = 0.0
            }
        } else {
            elapsed += dt
            if (activity == OwnerActivity.LOADING && elapsed >= 1.2) travel(FactoryFloor.SHIPPING, OwnerActivity.DELIVERING)
            if (activity == OwnerActivity.UNLOADING && elapsed >= 1.0) activity = OwnerActivity.AWAITING_PAYMENT
        }
        return snapshot()
    }

    fun paymentRecorded() {
        if (activity == OwnerActivity.AWAITING_PAYMENT) travel(FactoryFloor.ENTRY, OwnerActivity.RETURNING)
    }

    fun cancel() {
        position = FactoryFloor.ENTRY.point()
        activity = OwnerActivity.IDLE
        route = emptyList()
        index = 0
        elapsed = 0.0
    }

    fun snapshot() = FactoryOwnerFrame(position, activity, index < route.size,
        activity == OwnerActivity.DELIVERING || activity == OwnerActivity.UNLOADING || activity == OwnerActivity.AWAITING_PAYMENT)

    private fun travel(target: FloorCell, state: OwnerActivity) {
        destination = target
        route = floor.route(floor.nearestWalkable(position), target)
        check(route.isNotEmpty()) { "Expedição sem acesso" }
        index = 0
        elapsed = 0.0
        activity = state
    }
}
