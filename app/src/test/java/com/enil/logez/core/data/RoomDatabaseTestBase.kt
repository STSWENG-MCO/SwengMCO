package com.enil.logez.core.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before

/**
 * Shared in-memory Room setup for every DAO test (PHASE2_PLAN.md §10.2 layer 2 — Robolectric).
 * Concrete subclasses still need their own `@RunWith(RobolectricTestRunner::class)`.
 */
abstract class RoomDatabaseTestBase {
    protected lateinit var database: LogEzDatabase
    protected val rawDb: SupportSQLiteDatabase get() = database.openHelper.writableDatabase

    @Before
    fun setUpDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LogEzDatabase::class.java,
        ).build()
    }

    @After
    fun tearDownDatabase() {
        database.close()
    }
}
