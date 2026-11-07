package com.enil.logez.core.common

import android.util.Log
import javax.inject.Inject

/** The real, production [AppLogger] — a thin wrapper over `android.util.Log`. */
class AndroidAppLogger @Inject constructor() : AppLogger {
    override fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
    }
}
