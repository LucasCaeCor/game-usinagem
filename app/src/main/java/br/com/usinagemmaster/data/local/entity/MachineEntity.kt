package br.com.usinagemmaster.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "machines", indices = [Index("machineType"), Index("sectorType")])
data class MachineEntity(
    @PrimaryKey val id: String,
    val machineType: String,
    val customName: String?,
    val sectorType: String,
    val level: Int,
    val condition: Int,
    val accumulatedWorkMinutes: Long,
    val installed: Boolean,
    val gridX: Int,
    val gridY: Int,
    val purchasedAt: Long
)
