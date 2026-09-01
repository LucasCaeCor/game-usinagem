package br.com.usinagemmaster.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "company")
data class CompanyEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val cashCents: Long,
    val reputation: Int,
    val companyLevel: Int,
    val experience: Long,
    val warehouseSpace: Int,
    val usedWarehouseSpace: Int,
    val lastSimulationAt: Long,
    val createdAt: Long
)
