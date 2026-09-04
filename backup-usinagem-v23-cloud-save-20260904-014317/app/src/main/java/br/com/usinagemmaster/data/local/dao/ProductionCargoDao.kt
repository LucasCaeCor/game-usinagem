package br.com.usinagemmaster.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import br.com.usinagemmaster.data.local.entity.FinancialTransactionEntity
import br.com.usinagemmaster.data.local.entity.ProductionCargoEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
abstract class ProductionCargoDao {
    @Query("SELECT * FROM production_cargo WHERE deliveredAt IS NULL ORDER BY createdAt, id")
    abstract fun observePending(): Flow<List<ProductionCargoEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(cargo: ProductionCargoEntity)

    @Query("SELECT * FROM production_cargo WHERE deliveredAt IS NULL AND id IN (:ids)")
    abstract suspend fun pendingByIds(ids: List<String>): List<ProductionCargoEntity>

    @Query("UPDATE production_cargo SET deliveredAt = :now WHERE deliveredAt IS NULL AND id IN (:ids)")
    abstract suspend fun markDelivered(ids: List<String>, now: Long): Int

    @Query("SELECT cashCents FROM company WHERE id = 1")
    abstract suspend fun cash(): Long?

    @Query("UPDATE company SET cashCents = cashCents + :amount WHERE id = 1")
    abstract suspend fun credit(amount: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun receipt(value: FinancialTransactionEntity)

    /** Cargo consumption, money and receipt either all commit or all roll back. */
    @Transaction
    open suspend fun deliver(ids: List<String>, now: Long): Long {
        val selected = ids.distinct().chunked(200).flatMap { pendingByIds(it) }
        if (selected.isEmpty()) return 0L // A repeated callback is a successful no-op.
        val value = selected.fold(0L) { total, cargo -> Math.addExact(total, cargo.valueCents) }
        require(value >= 0L) { "Valor de carga inválido" }
        val balance = cash() ?: error("Empresa não inicializada")
        Math.addExact(balance, value) // Fail before writing on numeric overflow.
        val delivered = selected.map { it.id }.chunked(200).sumOf { markDelivered(it, now) }
        check(delivered == selected.size) { "A carga mudou durante a entrega" }
        check(credit(value) == 1) { "Empresa não encontrada" }
        receipt(FinancialTransactionEntity(
            id = "cargo-delivery:${UUID.randomUUID()}", type = "INCOME", category = "PRODUCTION",
            amountCents = value, description = "Entrega de carga • ${selected.sumOf { it.cycles }} ciclo(s) de produção",
            createdAt = now,
        ))
        return value
    }
}
