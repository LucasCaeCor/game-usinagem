package br.com.usinagemmaster.domain.simulation

import org.junit.Assert.*
import org.junit.Test

class FactorySimulationTest {
    private val machine = FactoryMachineInput("lathe", 1, 1, productive = true, unitsPerHour = 240.0)
    private val worker = FactoryWorkerInput("ana", machine.id, skill = 5)
    private val input = FactoryInput(listOf(machine), listOf(worker))

    private fun engine(value: FactoryInput = input) = FactorySimulation().apply { update(value) }
    private fun run(engine: FactorySimulation, seconds: Int): FactoryFrame {
        repeat(seconds * 20) { engine.advance(.05) }
        return engine.snapshot()
    }

    @Test fun allThirtyBaysRemainReachableWithoutCrossingMachines() {
        val machines = (0..4).flatMap { x -> (0..5).map { y -> FactoryMachineInput("$x-$y", x, y) } }
        val floor = FactoryFloor(machines)
        machines.forEach { machine ->
            val route = floor.route(FactoryFloor.STOCK, FactoryFloor.bay(machine))
            assertTrue("No route to ${machine.id}", route.isNotEmpty())
            route.forEach { point -> assertTrue(floor.walkable(FloorCell(point.x.toInt(), point.y.toInt()))) }
            route.zipWithNext().forEach { (a, b) -> assertEquals(1f, a.distanceTo(b), .0001f) }
        }
    }

    @Test fun unreachableOrInvalidDestinationsDoNotProduceUnsafeDirectRoutes() {
        val floor = FactoryFloor(listOf(machine))
        assertTrue(floor.route(FactoryFloor.ENTRY, FloorCell(6, 6)).isEmpty())
        assertTrue(floor.route(FloorCell(-1, 0), FactoryFloor.STOCK).isEmpty())
    }

    @Test fun operatorCompletesTheWholeMaterialToShippingRoutine() {
        val engine = engine()
        val activities = mutableSetOf<WorkerActivity>()
        val states = mutableSetOf<FactoryMachineState>()
        repeat(6000) {
            val frame = engine.advance(.05)
            activities.add(frame.workers.single().activity)
            states.add(frame.machines.single().state)
        }
        assertTrue(activities.containsAll(listOf(WorkerActivity.FETCHING_MATERIAL,
            WorkerActivity.FETCHING_TOOLS, WorkerActivity.SETTING_UP, WorkerActivity.WORKING,
            WorkerActivity.CARRYING_PART, WorkerActivity.INSPECTING, WorkerActivity.PACKING)))
        assertTrue(states.containsAll(listOf(FactoryMachineState.SETUP, FactoryMachineState.RUNNING, FactoryMachineState.WAITING_MATERIAL)))
    }

    @Test fun framePartitionDoesNotChangeTheSimulation() {
        val frequent = engine()
        val sparse = engine()
        repeat(600) { frequent.advance(.05) }
        repeat(300) { sparse.advance(.10) }
        assertEquals(frequent.snapshot(), sparse.snapshot())
    }

    @Test fun suspensionAndInvalidDeltasCannotTeleportAWorker() {
        val engine = engine()
        run(engine, 2)
        val before = engine.snapshot().workers.single().position
        val after = engine.advance(3600.0).workers.single().position
        assertTrue(before.distanceTo(after) <= 1f)
        val valid = engine.snapshot()
        engine.advance(Double.NaN)
        engine.advance(Double.POSITIVE_INFINITY)
        engine.advance(-1.0)
        assertEquals(valid, engine.snapshot())
    }

    @Test fun breakKeepsWorkerVisibleAndStopsMachine() {
        val engine = engine()
        run(engine, 30)
        val before = engine.snapshot().workers.single().position
        engine.update(input.copy(workers = listOf(worker.copy(resting = true))))
        assertEquals(before, engine.snapshot().workers.single().position)
        val rested = run(engine, 90)
        assertEquals(WorkerActivity.BREAK, rested.workers.single().activity)
        assertFalse(rested.machines.single().state == FactoryMachineState.RUNNING)
        engine.update(input)
        val after = run(engine, 1)
        assertTrue(after.workers.single().walking)
    }

    @Test fun shiftClosureHasPriorityOverPhoneAndBreak() {
        val engine = engine()
        run(engine, 30)
        engine.update(input.copy(open = false, workers = listOf(worker.copy(resting = true, onPhone = true))))
        assertEquals(FactoryMachineState.OFF, engine.snapshot().machines.single().state)
        val frame = run(engine, 90)
        assertEquals(WorkerActivity.OFF_SHIFT, frame.workers.single().activity)
        assertEquals(FactoryFloor.ENTRY.point(), frame.workers.single().position)
    }

    @Test fun phoneAndBrokenMachineInterruptProduction() {
        val engine = engine()
        engine.update(input.copy(workers = listOf(worker.copy(onPhone = true))))
        assertEquals(WorkerActivity.PHONE, run(engine, 60).workers.single().activity)
        engine.update(input.copy(machines = listOf(machine.copy(condition = 80))))
        assertEquals(FactoryMachineState.BROKEN, run(engine, 60).machines.single().state)
        assertEquals(WorkerActivity.IDLE, engine.snapshot().workers.single().activity)
    }

    @Test fun removingMachineOrEmployeeDoesNotLeaveGhosts() {
        val engine = engine()
        run(engine, 20)
        engine.update(input.copy(machines = emptyList()))
        assertTrue(run(engine, 60).machines.isEmpty())
        assertEquals(WorkerActivity.IDLE, engine.snapshot().workers.single().activity)
        engine.update(FactoryInput())
        assertTrue(engine.snapshot().workers.isEmpty())
    }

    @Test fun changedLayoutRebuildsRoutesAndKeepsWorkersInFreeCorridors() {
        val engine = engine()
        run(engine, 12)
        val moved = machine.copy(gridX = 0, gridY = 0)
        engine.update(input.copy(machines = listOf(moved)))
        val floor = FactoryFloor(listOf(moved))
        val frame = engine.snapshot()
        val person = frame.workers.single()
        assertTrue(floor.walkable(FloorCell(person.position.x.toInt(), person.position.y.toInt())))
        person.route.forEach { assertTrue(floor.walkable(FloorCell(it.x.toInt(), it.y.toInt()))) }
        assertNotEquals(WorkerActivity.BLOCKED, run(engine, 120).workers.single().activity)
    }

    @Test fun noOperatorMeansNoMachineAnimation() {
        val engine = engine(input.copy(workers = emptyList()))
        assertEquals(FactoryMachineState.IDLE, run(engine, 20).machines.single().state)
    }

    @Test fun fatigueChangesWalkingSpeedIndividually() {
        val rested = engine()
        val tired = engine(input.copy(workers = listOf(worker.copy(fatigue = 95))))
        val a = run(rested, 1).workers.single().position
        val b = run(tired, 1).workers.single().position
        assertTrue(a.distanceTo(FactoryFloor.ENTRY.point()) > b.distanceTo(FactoryFloor.ENTRY.point()))
    }

    @Test fun reorderedInputsDoNotRestartTasks() {
        val other = FactoryWorkerInput("bia")
        val engine = engine(input.copy(workers = listOf(worker, other)))
        run(engine, 20)
        val before = engine.snapshot()
        engine.update(input.copy(workers = listOf(other, worker)))
        assertEquals(before, engine.snapshot())
    }
}
