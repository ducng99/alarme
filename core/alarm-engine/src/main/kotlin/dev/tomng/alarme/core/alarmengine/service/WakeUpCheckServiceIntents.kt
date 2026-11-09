package dev.tomng.alarme.core.alarmengine.service

import android.content.Context
import android.content.Intent

/** Intent contract for the wake-up check service. */
object WakeUpCheckServiceIntents {

    const val EXTRA_ALARM_ID = "dev.tomng.alarme.extra.WUC_ALARM_ID"

    fun startIntent(context: Context, alarmId: Long): Intent =
        Intent(context, WakeUpCheckService::class.java).putExtra(EXTRA_ALARM_ID, alarmId)

    fun alarmId(intent: Intent): Long = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
}
