package br.com.usinagemmaster.data.local.dao

import androidx.room.*
import br.com.usinagemmaster.data.local.entity.ContractEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContractDao {
    @Query("SELECT * FROM contracts ORDER BY generatedAt DESC") fun observeAll(): Flow<List<ContractEntity>>
    @Query("SELECT * FROM contracts WHERE status = 'ACTIVE' ORDER BY startedAt ASC") suspend fun getActive(): List<ContractEntity>
    @Query("SELECT COUNT(*) FROM contracts WHERE status = 'ACTIVE'") fun observeActiveCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM contracts WHERE status = 'AVAILABLE'") suspend fun availableCount(): Int
    @Query("SELECT * FROM contracts WHERE id = :id") suspend fun byId(id: String): ContractEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(values: List<ContractEntity>)
    @Update suspend fun update(value: ContractEntity)
}
