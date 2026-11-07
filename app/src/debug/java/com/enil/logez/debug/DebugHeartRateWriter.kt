package com.enil.logez.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug-only test aid: writes one watch-style heart-rate record into Health Connect, so the
 * heart-rate paths can be exercised on an emulator (2026-09-26). It can mimic what made a Galaxy
 * Watch's heart rate disappear: a record that starts before the run, and a record that arrives
 * after the run was saved. Needs WRITE_HEART_RATE granted to the debug app first.
 *
 * ```
 * adb shell am broadcast -n com.enil.logez/.debug.DebugHeartRateWriter \
 *   -a com.enil.logez.debug.WRITE_HEART_RATE \
 *   --el recordStart <epoch ms> --el from <epoch ms> --el to <epoch ms> \
 *   --ei stepSeconds 5 --ei bpm 140
 * ```
 * The record spans recordStart..to and holds one sample every stepSeconds from `from` to `to`,
 * wobbling around bpm. Output goes to logcat under DebugHeartRateWriter.
 */
class DebugHeartRateWriter : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val to = intent.getLongExtra("to", System.currentTimeMillis())
        val from = intent.getLongExtra("from", to - 60_000L)
        val recordStart = intent.getLongExtra("recordStart", from)
        val stepSeconds = intent.getIntExtra("stepSeconds", 5).coerceAtLeast(1)
        val bpm = intent.getIntExtra("bpm", 140)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val samples = generateSequence(from) { it + stepSeconds * 1000L }
                    .takeWhile { it <= to }
                    .mapIndexed { i, at -> HeartRateRecord.Sample(Instant.ofEpochMilli(at), (bpm + (i % 9) - 4).toLong()) }
                    .toList()
                val record = HeartRateRecord(
                    startTime = Instant.ofEpochMilli(minOf(recordStart, from)),
                    startZoneOffset = null,
                    endTime = Instant.ofEpochMilli(to),
                    endZoneOffset = null,
                    samples = samples,
                    metadata = Metadata.autoRecorded(Device(type = Device.TYPE_WATCH)),
                )
                HealthConnectClient.getOrCreate(context).insertRecords(listOf(record))
                Log.i(TAG, "Wrote a heart-rate record from $recordStart to $to with ${samples.size} samples")
            } catch (e: Exception) {
                Log.e(TAG, "Writing the test heart-rate record failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "DebugHeartRateWriter"
    }
}
