package br.com.usinagemmaster.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    val title: String,
    val target: Int,
    val progress: Int,
    val rewardCents: Long,
    val claimed: Boolean
)
