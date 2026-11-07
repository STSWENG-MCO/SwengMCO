package com.enil.logez.core.wellness

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/** Health Connect's own package, the one the manifest's `<queries>` block names. */
private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

/**
 * Whether it is worth offering to install or update Health Connect here (Play-readiness audit,
 * 2026-09-25: a buyer without it never learned the feature existed, because the Profile card
 * only ever showed when Health Connect was already usable).
 *
 * An update is always worth offering. A missing provider is only installable on Android 9-13: the
 * Health Connect app needs API 28+, and from Android 14 it is part of the system, so "unavailable"
 * there means the device does not support it and there is nothing to install.
 */
fun HealthConnectAvailability.canInstallOrUpdate(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = when (this) {
    HealthConnectAvailability.UpdateRequired -> true
    HealthConnectAvailability.Unavailable -> sdkInt in Build.VERSION_CODES.P until Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    HealthConnectAvailability.Available -> false
}

/**
 * Opens Health Connect's Play Store listing, the install/update route Android's Health Connect
 * guide documents (the `url=healthconnect://onboarding` parameter returns the user to Health
 * Connect's onboarding once it is installed). Falls back to the web listing on a device without
 * the Play Store app.
 */
fun openHealthConnectInPlayStore(context: Context) {
    val market = Intent(Intent.ACTION_VIEW).apply {
        setPackage("com.android.vending")
        data = Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE&url=healthconnect%3A%2F%2Fonboarding")
        putExtra("overlay", true)
        putExtra("callerId", context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(market)
    } catch (_: ActivityNotFoundException) {
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(web) }
    }
}

/**
 * Opens Health Connect's own settings, where the user can grant a type they left unticked or
 * withdraw one. `ACTION_HEALTH_CONNECT_SETTINGS` resolves to the right action for the platform
 * (the system settings page on Android 14+, the Health Connect app below that).
 */
fun openHealthConnectSettings(context: Context) {
    val intent = Intent(androidx.health.connect.client.HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
