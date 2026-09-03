package br.com.usinagemmaster.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Closed production periods awaiting a physical delivery; not part of available cash. */
@Entity(tableName = "production_cargo", indices = [Index("deliveredAt")])
data class ProductionCargoEntity(
    @PrimaryKey val id: String,
    val valueCents: Long,
    val unitsMilli: Long,
    val cycles: Long,
    val createdAt: Long,
    val deliveredAt: Long? = null,
)
