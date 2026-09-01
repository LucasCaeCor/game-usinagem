package br.com.usinagemmaster.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "employees", indices = [Index("specialty"), Index("legendaryCode", unique = true)])
data class EmployeeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val specialty: String,
    val skillLevel: Int,
    val experience: Long,
    val salaryCents: Long,
    val morale: Int,
    val trait: String,
    val hiredAt: Long,
    val assignedMachineId: String?,
    val isLegendary: Boolean = false,
    val legendaryCode: String? = null
)
