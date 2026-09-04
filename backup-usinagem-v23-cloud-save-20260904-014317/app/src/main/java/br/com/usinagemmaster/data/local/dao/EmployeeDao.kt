package br.com.usinagemmaster.data.local.dao

import androidx.room.*
import br.com.usinagemmaster.data.local.entity.EmployeeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees ORDER BY skillLevel DESC") fun observeAll(): Flow<List<EmployeeEntity>>
    @Query("SELECT * FROM employees ORDER BY skillLevel DESC") suspend fun getAll(): List<EmployeeEntity>
    @Query("SELECT COUNT(*) FROM employees") fun observeCount(): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(value: EmployeeEntity)
    @Update suspend fun update(value: EmployeeEntity)
    @Delete suspend fun delete(value: EmployeeEntity)
}
