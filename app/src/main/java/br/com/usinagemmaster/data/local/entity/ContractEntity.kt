package br.com.usinagemmaster.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "contracts", indices = [Index("status"), Index("deadlineAt")])
data class ContractEntity(
    @PrimaryKey val id: String,
    val clientName: String,
    val contractType: String,
    val quantity: Int,
    val completedQuantity: Int,
    val difficulty: Int,
    val requiredQuality: Int,
    val rewardCents: Long,
    val penaltyCents: Long,
    val reputationReward: Int,
    val reputationPenalty: Int,
    val generatedAt: Long,
    val startedAt: Long?,
    val deadlineAt: Long,
    val status: String,
    val productionProgressMilli: Long = 0L
)
