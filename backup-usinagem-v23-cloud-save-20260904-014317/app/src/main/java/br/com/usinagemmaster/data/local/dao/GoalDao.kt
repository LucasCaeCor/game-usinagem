package br.com.usinagemmaster.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import br.com.usinagemmaster.data.local.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals") fun observeAll(): Flow<List<GoalEntity>>
    @Query("SELECT COUNT(*) FROM goals") suspend fun count(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(values: List<GoalEntity>)
    @Update suspend fun update(value: GoalEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(goals: List<GoalEntity>)
}
