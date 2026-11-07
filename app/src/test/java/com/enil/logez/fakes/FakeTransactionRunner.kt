package com.enil.logez.fakes

import com.enil.logez.core.domain.repository.TransactionRunner

/**
 * Pass-through [TransactionRunner]. The fakes are in-memory, so there is nothing to commit or roll
 * back — this exists so tests can exercise the real save sequence without a database, and it
 * deliberately does not simulate rollback: atomicity is Room's contract, verified against a real
 * database rather than asserted against a fake that would only be re-stating this fake's own code.
 *
 * [failNextCalls] throws on that many calls before passing through — used to model a save attempt
 * that fails once (a Room error, a disk-full) and must be retryable on the next attempt.
 */
class FakeTransactionRunner(private var failNextCalls: Int = 0) : TransactionRunner {
    var transactionCount = 0
        private set

    override suspend fun <T> runInTransaction(block: suspend () -> T): T {
        transactionCount++
        if (failNextCalls > 0) {
            failNextCalls--
            throw IllegalStateException("simulated transaction failure")
        }
        return block()
    }
}
