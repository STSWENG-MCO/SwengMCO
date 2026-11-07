package com.enil.logez.core.common

/**
 * Minimal, injectable logging seam. Callers depend on this interface, never directly on
 * `android.util.Log` -- calling that class from a plain (non-Robolectric) JVM unit test throws
 * ("Method e in android.util.Log not mocked"), which is why this codebase had no logging seam at
 * all before this (see the pre-fork tech-debt audit, 2026-09-19). Constructor parameters of this
 * type should default to [NoOp] so existing tests that construct a class directly don't need to
 * wire one up just to exercise an unrelated code path.
 */
interface AppLogger {
    fun e(tag: String, message: String, throwable: Throwable? = null)

    object NoOp : AppLogger {
        override fun e(tag: String, message: String, throwable: Throwable?) = Unit
    }
}
