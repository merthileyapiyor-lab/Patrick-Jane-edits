package com.overdrive.quest.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object ServiceController {
    fun start(context: Context) {
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, OverdriveService::class.java))
        }
    }

    fun intent(context: Context, action: String): Intent =
        Intent(context, OverdriveService::class.java).setAction(action)
}
