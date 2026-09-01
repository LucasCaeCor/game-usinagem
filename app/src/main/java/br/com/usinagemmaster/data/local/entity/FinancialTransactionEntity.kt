package br.com.usinagemmaster.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "financial_transactions", indices = [Index("createdAt")])
data class FinancialTransactionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val category: String,
    val amountCents: Long,
    val description: String,
    val createdAt: Long
)
