package br.com.usinagemmaster.domain.simulation

import org.junit.Assert.*
import org.junit.Test

class FactoryOwnerSimulationTest {
    @Test fun collectsLoadsAndReachesShippingBeforeRequestingPayment() {
        val owner = FactoryOwnerSimulation()
        assertTrue(owner.start())
        assertFalse(owner.start())
        val visited = mutableSetOf<OwnerActivity>()
        repeat(800) {
            val frame = owner.advance(.05)
            visited.add(frame.activity)
            if (frame.activity == OwnerActivity.LOADING) assertEquals(FactoryFloor.STAGING.point(), frame.position)
            if (frame.activity == OwnerActivity.AWAITING_PAYMENT) assertEquals(FactoryFloor.SHIPPING.point(), frame.position)
        }
        assertTrue(visited.containsAll(listOf(OwnerActivity.COLLECTING, OwnerActivity.LOADING,
            OwnerActivity.DELIVERING, OwnerActivity.UNLOADING, OwnerActivity.AWAITING_PAYMENT)))
        assertFalse(visited.contains(OwnerActivity.RETURNING))
        assertTrue(owner.snapshot().carrying)
        owner.paymentRecorded()
        assertFalse(owner.snapshot().carrying)
        repeat(800) { owner.advance(.05) }
        assertEquals(FactoryOwnerFrame(), owner.snapshot())
        assertTrue(owner.start())
    }

    @Test fun paymentAcknowledgementCannotSkipTheTrip() {
        val owner = FactoryOwnerSimulation()
        owner.start()
        owner.paymentRecorded()
        assertEquals(OwnerActivity.COLLECTING, owner.snapshot().activity)
        repeat(100) { owner.advance(.05) }
        assertNotEquals(OwnerActivity.AWAITING_PAYMENT, owner.snapshot().activity)
    }

    @Test fun allBaysOccupiedStillLeaveAContinuousSafeDeliveryRoute() {
        val machines = (0..4).flatMap { x -> (0..5).map { y -> FactoryMachineInput("$x-$y", x, y) } }
        val floor = FactoryFloor(machines)
        val owner = FactoryOwnerSimulation().apply { update(machines); start() }
        repeat(800) {
            val frame = owner.advance(.05)
            assertTrue(floor.walkable(FloorCell(kotlin.math.round(frame.position.x).toInt(), kotlin.math.round(frame.position.y).toInt())))
        }
        assertEquals(OwnerActivity.AWAITING_PAYMENT, owner.snapshot().activity)
    }

    @Test fun suspensionCannotJumpToDeliveryAndCancellationAllowsAnotherTrip() {
        val owner = FactoryOwnerSimulation().apply { start() }
        val first = owner.snapshot()
        assertTrue(first.position.distanceTo(owner.advance(600.0).position) <= 1.251f)
        val before = owner.snapshot()
        owner.advance(Double.NaN)
        owner.advance(-1.0)
        assertEquals(before, owner.snapshot())
        owner.cancel()
        assertEquals(FactoryOwnerFrame(), owner.snapshot())
        assertTrue(owner.start())
    }

    @Test fun depositRequiresFinishedPackingAndResetsOnlyOnNewFinancialCycle() {
        val input = FactoryInput(
            machines = listOf(FactoryMachineInput("lathe", 1, 1, productive = true, unitsPerHour = 240.0)),
            workers = listOf(FactoryWorkerInput("ana", "lathe", skill = 5)),
            cycleStartedAt = 1L,
        )
        val simulation = FactorySimulation().apply { update(input) }
        var count = 0
        repeat(6000) {
            val before = simulation.snapshot()
            val after = simulation.advance(.05)
            if (after.depositedLots > count) {
                assertEquals(WorkerActivity.PACKING, before.workers.single().activity)
                assertEquals(FactoryFloor.STAGING.point(), before.workers.single().position)
                count = after.depositedLots
            }
        }
        assertTrue(count > 0)
        simulation.update(input)
        assertEquals(count, simulation.snapshot().depositedLots)
        simulation.update(input.copy(cycleStartedAt = 2L))
        assertEquals(0, simulation.snapshot().depositedLots)
    }
}
