package br.com.usinagemmaster.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import br.com.usinagemmaster.data.local.database.GameDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE contracts ADD COLUMN productionProgressMilli INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE employees ADD COLUMN isLegendary INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE employees ADD COLUMN legendaryCode TEXT DEFAULT NULL")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_employees_legendaryCode ON employees(legendaryCode)")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS legendary_missions (
                    id TEXT NOT NULL PRIMARY KEY,
                    legendaryCode TEXT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL,
                    metric TEXT NOT NULL,
                    target INTEGER NOT NULL,
                    progress INTEGER NOT NULL,
                    rewardCents INTEGER NOT NULL,
                    claimed INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_legendary_missions_legendaryCode ON legendary_missions(legendaryCode)"
            )
        }
    }

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): GameDatabase =
        Room.databaseBuilder(context, GameDatabase::class.java, "usinagem_master.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()

    @Provides fun companyDao(db: GameDatabase) = db.companyDao()
    @Provides fun machineDao(db: GameDatabase) = db.machineDao()
    @Provides fun employeeDao(db: GameDatabase) = db.employeeDao()
    @Provides fun contractDao(db: GameDatabase) = db.contractDao()
    @Provides fun financeDao(db: GameDatabase) = db.financeDao()
    @Provides fun facilityDao(db: GameDatabase) = db.facilityDao()
    @Provides fun goalDao(db: GameDatabase) = db.goalDao()
    @Provides fun legendaryMissionDao(db: GameDatabase) = db.legendaryMissionDao()
}
