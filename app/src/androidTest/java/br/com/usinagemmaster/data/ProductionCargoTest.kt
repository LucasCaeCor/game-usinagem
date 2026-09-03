package br.com.usinagemmaster.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.usinagemmaster.data.local.database.GameDatabase
import br.com.usinagemmaster.data.local.entity.CompanyEntity
import br.com.usinagemmaster.data.local.entity.ProductionCargoEntity
import br.com.usinagemmaster.di.DatabaseModule
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProductionCargoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databases = mutableListOf<GameDatabase>()
    private val files = mutableListOf<String>()

    @After fun close() {
        databases.forEach { it.close() }
        files.forEach { context.deleteDatabase(it) }
    }

    private suspend fun fresh(): GameDatabase {
        val db = Room.inMemoryDatabaseBuilder(context, GameDatabase::class.java).build()
        databases.add(db)
        db.companyDao().upsert(CompanyEntity(1, "Teste", 1000, 0, 1, 0, 100, 0, 600000, 0))
        return db
    }

    @Test fun stagingDoesNotPayAndRepeatedConcurrentDeliveryPaysOnce() = runBlocking {
        val db = fresh()
        val dao = db.productionCargoDao()
        dao.insert(ProductionCargoEntity("one", 700, 5000, 1, 600000))
        assertEquals(1000L, db.companyDao().get()!!.cashCents)
        val payments = List(8) { async { dao.deliver(listOf("one", "one"), 700000) } }.awaitAll()
        assertEquals(700L, payments.sum())
        assertEquals(1700L, db.companyDao().get()!!.cashCents)
        assertTrue(dao.observePending().first().isEmpty())
        assertEquals(1, db.financeDao().observeRecent().first().size)
    }

    @Test fun laterCargoWaitsForNextTrip() = runBlocking {
        val db = fresh()
        val dao = db.productionCargoDao()
        dao.insert(ProductionCargoEntity("first", 700, 5000, 1, 600000))
        val trip = dao.observePending().first().map { it.id }
        dao.insert(ProductionCargoEntity("later", 300, 2000, 1, 1200000))
        assertEquals(700L, dao.deliver(trip, 1300000))
        assertEquals(listOf("later"), dao.observePending().first().map { it.id })
    }

    @Test fun failedReceiptRollsBackPaymentAndKeepsCargoAvailable() = runBlocking {
        val db = fresh()
        val dao = db.productionCargoDao()
        dao.insert(ProductionCargoEntity("one", 700, 5000, 1, 600000))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_receipt BEFORE INSERT ON financial_transactions BEGIN SELECT RAISE(ABORT, 'test'); END")
        assertTrue(runCatching { dao.deliver(listOf("one"), 700000) }.isFailure)
        assertEquals(1000L, db.companyDao().get()!!.cashCents)
        assertEquals(1, dao.observePending().first().size)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_receipt")
        assertEquals(700L, dao.deliver(listOf("one"), 800000))
    }

    @Test fun moreThanSqliteParameterLimitCanBeDeliveredTogether() = runBlocking {
        val db = fresh()
        val dao = db.productionCargoDao()
        val ids = (0..1100).map { it.toString() }
        ids.forEach { dao.insert(ProductionCargoEntity(it, 10, 1000, 1, 600000)) }
        assertEquals(11010L, dao.deliver(ids, 700000))
        assertTrue(dao.observePending().first().isEmpty())
    }

    @Test fun versionFourSaveMigratesAndUndeliveredCargoSurvivesReopen() = runBlocking {
        val name = "cargo-test-${UUID.randomUUID()}.db"
        files.add(name)
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        val json = InstrumentationRegistry.getInstrumentation().context.assets.open("database-v4.json")
            .bufferedReader().use { JSONObject(it.readText()) }.getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            val entities = json.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            old.execSQL("INSERT INTO company VALUES (1, 'Save antigo', 123456, 8, 2, 99, 100, 0, 600000, 0)")
            old.version = 4
        }
        fun open() = Room.databaseBuilder(context, GameDatabase::class.java, name)
            .addMigrations(DatabaseModule.MIGRATION_4_5).build().also { databases.add(it) }
        val db = open()
        assertEquals(123456L, db.companyDao().get()!!.cashCents)
        assertEquals(600000L, db.companyDao().get()!!.lastSimulationAt)
        assertTrue(db.productionCargoDao().observePending().first().isEmpty())
        db.productionCargoDao().insert(ProductionCargoEntity("saved", 500, 3000, 1, 1200000))
        db.close()
        val reopened = open()
        assertEquals(listOf("saved"), reopened.productionCargoDao().observePending().first().map { it.id })
        assertEquals(500L, reopened.productionCargoDao().deliver(listOf("saved"), 1300000))
        assertEquals(123956L, reopened.companyDao().get()!!.cashCents)
    }
}
