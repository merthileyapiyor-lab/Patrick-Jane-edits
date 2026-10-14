package com.overdrive.quest.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.overdrive.quest.MainActivity
import com.overdrive.quest.R
import com.overdrive.quest.core.model.AppSettings
import com.overdrive.quest.core.model.BoostMode
import com.overdrive.quest.core.monitor.Metrics

object EngineNotification {
    const val CHANNEL_ID = "engine"
    const val NOTIFICATION_ID = 7042

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) },
        )
    }

    fun build(context: Context, metrics: Metrics, settings: AppSettings): Notification {
        val title = if (settings.auto.enabled) {
            context.getString(
                R.string.notif_title_auto,
                context.getString(
                    when (settings.auto.mode) {
                        BoostMode.ADAPTIVE -> R.string.mode_adaptive
                        BoostMode.MAX_SUSTAIN -> R.string.mode_max
                        BoostMode.ECO_SMART -> R.string.mode_eco
                    },
                ),
            )
        } else {
            context.getString(R.string.notif_title)
        }
        val dash = "–"
        val text = context.getString(
            R.string.notif_text,
            metrics.cpuLevel?.toString() ?: dash,
            metrics.gpuLevel?.toString() ?: dash,
            metrics.fps?.toString() ?: dash,
            (metrics.socTempC ?: metrics.battery.tempC)?.let { "%.0f".format(it) } ?: dash,
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_overdrive)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.preset_overdrive), action(context, OverdriveService.ACTION_PRESET_OVERDRIVE, 1))
            .addAction(0, context.getString(R.string.preset_balanced), action(context, OverdriveService.ACTION_PRESET_BALANCED, 2))
            .addAction(0, context.getString(R.string.notif_stop), action(context, OverdriveService.ACTION_STOP, 3))
            .build()
    }

    private fun action(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            context, code, ServiceController.intent(context, action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
