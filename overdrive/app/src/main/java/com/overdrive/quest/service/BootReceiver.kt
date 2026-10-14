package com.overdrive.quest.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.overdrive.quest.core.AppGraph
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Brings the engine back after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val pending = goAsync()
        AppGraph.scope.launch {
            try {
                val needed = withTimeoutOrNull(8_000) { AppGraph.serviceNeeded.filterNotNull().first() }
                if (needed == true) ServiceController.start(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
        )
    }
}
