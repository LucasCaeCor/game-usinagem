package br.com.usinagemmaster.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import br.com.usinagemmaster.data.local.entity.FinancialTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
    @Query("SELECT * FROM financial_transactions ORDER BY createdAt DESC LIMIT 100") fun observeRecent(): Flow<List<FinancialTransactionEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(value: FinancialTransactionEntity)
}
