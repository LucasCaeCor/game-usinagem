package br.com.usinagemmaster.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import br.com.usinagemmaster.data.local.entity.CompanyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyDao {
    @Query("SELECT * FROM company WHERE id = 1 LIMIT 1") fun observe(): Flow<CompanyEntity?>
    @Query("SELECT * FROM company WHERE id = 1 LIMIT 1") suspend fun get(): CompanyEntity?
    @Upsert suspend fun upsert(value: CompanyEntity)
}
