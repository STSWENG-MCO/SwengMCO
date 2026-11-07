package com.enil.logez.core.data.repository

import androidx.room.withTransaction
import com.enil.logez.core.data.LogEzDatabase
import com.enil.logez.core.domain.repository.TransactionRunner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TransactionRunner] over the real database. `withTransaction` is coroutine-aware: it pins the
 * block to Room's transaction dispatcher, so suspending DAO calls inside it join the same
 * transaction instead of deadlocking on a different thread.
 *
 * Only DAO work belongs inside the block — DataStore reads and other non-Room suspension points
 * would hold the transaction open across unrelated I/O, so callers resolve those first.
 */
@Singleton
class RoomTransactionRunner @Inject constructor(
    private val db: LogEzDatabase,
) : TransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T = db.withTransaction(block)
}
