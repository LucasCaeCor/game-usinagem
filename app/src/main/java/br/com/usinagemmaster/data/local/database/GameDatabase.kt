package br.com.usinagemmaster.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import br.com.usinagemmaster.data.local.dao.*
import br.com.usinagemmaster.data.local.entity.*

@Database(
    entities = [
        CompanyEntity::class, MachineEntity::class, EmployeeEntity::class,
        ContractEntity::class, FinancialTransactionEntity::class,
        FacilityUpgradeEntity::class, GoalEntity::class, LegendaryMissionEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class GameDatabase : RoomDatabase() {
    abstract fun companyDao(): CompanyDao
    abstract fun machineDao(): MachineDao
    abstract fun employeeDao(): EmployeeDao
    abstract fun contractDao(): ContractDao
    abstract fun financeDao(): FinanceDao
    abstract fun facilityDao(): FacilityDao
    abstract fun goalDao(): GoalDao
    abstract fun legendaryMissionDao(): LegendaryMissionDao
}
