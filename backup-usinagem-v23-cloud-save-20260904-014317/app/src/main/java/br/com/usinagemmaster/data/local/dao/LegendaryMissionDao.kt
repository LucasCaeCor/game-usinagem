package br.com.usinagemmaster.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import br.com.usinagemmaster.data.local.entity.LegendaryMissionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LegendaryMissionDao {
    @Query("SELECT * FROM legendary_missions ORDER BY claimed ASC, progress DESC")
    fun observeAll(): Flow<List<LegendaryMissionEntity>>

    @Query("SELECT * FROM legendary_missions")
    suspend fun getAll(): List<LegendaryMissionEntity>

    @Query("SELECT * FROM legendary_missions WHERE legendaryCode = :legendaryCode LIMIT 1")
    suspend fun getByLegendaryCode(legendaryCode: String): LegendaryMissionEntity?

    @Upsert
    suspend fun upsert(mission: LegendaryMissionEntity)

    @Update
    suspend fun update(mission: LegendaryMissionEntity)
}
