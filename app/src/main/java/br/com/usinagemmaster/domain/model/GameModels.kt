package br.com.usinagemmaster.domain.model

import br.com.usinagemmaster.domain.simulation.EconomyBalance

enum class MachineType {
    MECHANICAL_LATHE, UNIVERSAL_MILL, COLUMN_DRILL, CYLINDRICAL_GRINDER, WELDING_BENCH,
    CNC_LATHE, CNC_MACHINING_CENTER_3_AXIS, CNC_MACHINING_CENTER_5_AXIS,
    CNC_GRINDER, CNC_DRILL, ROBOTIC_WELDING, EDM, LASER_CUTTER, PLASMA_CUTTER
}

enum class EmployeeSpecialty {
    TURNER, MILLER, WELDER, CNC_PROGRAMMER, GRINDER_OPERATOR, DRILL_OPERATOR,
    QUALITY_INSPECTOR, STOCK_ASSISTANT
}

enum class SectorType { TURNING, MILLING, DRILLING, GRINDING, BOILERMAKING, CNC_PROGRAMMING, QUALITY_CONTROL, WAREHOUSE }
enum class ContractStatus { AVAILABLE, ACTIVE, COMPLETED, FAILED, EXPIRED }
enum class TransactionType { INCOME, EXPENSE }
enum class TransactionCategory { CONTRACT, MACHINE, SALARY, ENERGY, MAINTENANCE, FACILITY, BONUS, PRODUCTION }

data class MachineDefinition(
    val type: MachineType,
    val name: String,
    val priceCents: Long,
    val baseProductionPerHour: Double,
    val quality: Int,
    val powerKw: Double,
    val maintenanceCents: Long,
    val space: Int,
    val specialty: EmployeeSpecialty
)

data class DashboardStatus(
    val companyName: String = "Minha Usinagem",
    val cashCents: Long = 0,
    val reputation: Int = 0,
    val companyLevel: Int = 1,
    val machines: Int = 0,
    val employees: Int = 0,
    val activeContracts: Int = 0,
    val warehouseSpace: Int = 100,
    val usedWarehouseSpace: Int = 0,
    val lastSimulationAt: Long = 0L
)

data class MachineRuntime(
    val id: String,
    val machineType: String,
    val level: Int,
    val condition: Int
)

data class EmployeeRuntime(
    val id: String,
    val specialty: String,
    val skillLevel: Int,
    val morale: Int,
    val trait: String,
    val assignedMachineId: String?,
    val legendaryCode: String? = null
)

data class MachineProduction(
    val machineId: String,
    val employeeId: String?,
    val unitsPerHour: Double,
    val quality: Int,
    val powerKw: Double,
    val isOperating: Boolean
) {
    val unitsPer10Minutes: Double get() = unitsPerHour / 6.0
}

data class ProductionSnapshot(
    val totalUnitsPerHour: Double = 0.0,
    val grossPerHourCents: Long = 0,
    val energyPerHourCents: Long = 0,
    val netPerHourCents: Long = 0,
    val operatingMachines: Int = 0,
    val idleMachines: Int = 0,
    val averageQuality: Int = 0,
    val machineProduction: List<MachineProduction> = emptyList()
) {
    val totalUnitsPer10Minutes: Double get() = totalUnitsPerHour / 6.0
    val grossPer10MinutesCents: Long get() = grossPerHourCents / 6L
    val energyPer10MinutesCents: Long get() = energyPerHourCents / 6L
    val netPer10MinutesCents: Long get() = EconomyBalance.boostedProfit(netPerHourCents / 6L)
}
