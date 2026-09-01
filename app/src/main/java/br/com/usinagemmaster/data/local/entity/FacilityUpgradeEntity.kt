package br.com.usinagemmaster.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "facility_upgrades")
data class FacilityUpgradeEntity(
    @PrimaryKey val upgradeType: String,
    val level: Int
)
