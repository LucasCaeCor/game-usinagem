package br.com.usinagemmaster.data.local.dao

import androidx.room.*
import br.com.usinagemmaster.data.local.entity.MachineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MachineDao {
    @Query("SELECT * FROM machines ORDER BY purchasedAt DESC") fun observeAll(): Flow<List<MachineEntity>>
    @Query("SELECT * FROM machines") suspend fun getAll(): List<MachineEntity>
    @Query("SELECT COUNT(*) FROM machines WHERE installed = 1") fun observeCount(): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(value: MachineEntity)
    @Update suspend fun update(value: MachineEntity)
    @Delete suspend fun delete(value: MachineEntity)
}
