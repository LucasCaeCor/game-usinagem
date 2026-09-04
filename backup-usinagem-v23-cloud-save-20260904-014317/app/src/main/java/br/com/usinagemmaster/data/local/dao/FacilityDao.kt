package br.com.usinagemmaster.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import br.com.usinagemmaster.data.local.entity.FacilityUpgradeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FacilityDao {
    @Query("SELECT * FROM facility_upgrades") fun observeAll(): Flow<List<FacilityUpgradeEntity>>
    @Upsert suspend fun upsert(value: FacilityUpgradeEntity)
}
