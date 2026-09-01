package br.com.usinagemmaster.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "legendary_missions",
    indices = [Index("legendaryCode", unique = true)]
)
data class LegendaryMissionEntity(
    @PrimaryKey val id: String,
    val legendaryCode: String,
    val title: String,
    val description: String,
    val metric: String,
    val target: Long,
    val progress: Long,
    val rewardCents: Long,
    val claimed: Boolean
)
