package com.enil.logez.core.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat

/**
 * Why a hard-requirement permission (location for GPS tracking, camera for progress photos) was
 * not granted, so the screen can say what to do next rather than one generic "permission needed"
 * line (Play-readiness audit, 2026-09-25).
 */
enum class PermissionDenial {
    /** The user said no this time; asking again later is fine. */
    Declined,

    /** Location only: the user picked "Approximate", which cannot measure a walk or run's distance. */
    ApproximateOnly,

    /** Android will not show the dialog again (denied twice, or "Don't ask again"); only Settings can fix it. */
    Blocked,
}

/**
 * Classifies a denial right after a request returned. Android reports no rationale for a
 * permission it will no longer ask about, which is the only signal an app gets for "blocked".
 */
fun classifyDenial(context: Context, permission: String): PermissionDenial {
    val activity = context.findActivity() ?: return PermissionDenial.Declined
    return if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
        PermissionDenial.Declined
    } else {
        PermissionDenial.Blocked
    }
}

/** Opens this app's page in Android's settings, where a blocked permission can be turned on. */
fun openAppDetailsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
