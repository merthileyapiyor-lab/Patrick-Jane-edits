package com.overdrive.quest.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.overdrive.quest.core.AppGraph
import com.overdrive.quest.core.model.Preset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the engine alive while the user is in a game: auto overclock, per-game profiles,
 * the floating HUD and session recording all run from here.
 */
class OverdriveService : LifecycleService() {
    private lateinit var coordinator: EngineCoordinator
    private lateinit var overlay: OverlayController

    override fun onCreate() {
        super.onCreate()
        EngineNotification.ensureChannel(this)
        goForeground()

        coordinator = EngineCoordinator(lifecycleScope).also { it.start() }
        overlay = OverlayController(this)

        lifecycleScope.launch {
            AppGraph.settings.settingsFlow.map { it.overlay }.distinctUntilChanged().collect { overlay.update(it) }
        }
        lifecycleScope.launch {
            AppGraph.serviceNeeded.filterNotNull().collect { needed -> if (!needed) stopSelf() }
        }
        lifecycleScope.launch {
            while (isActive) {
                delay(NOTIFICATION_REFRESH_MS)
                val s = AppGraph.settings.settings.value
                // Picks up an overlay permission granted while the service was running.
                if (s.overlay.enabled && !overlay.isShowing) overlay.update(s.overlay)
                runCatching {
                    NotificationManagerCompat.from(this@OverdriveService).notify(
                        EngineNotification.NOTIFICATION_ID,
                        EngineNotification.build(this@OverdriveService, AppGraph.metrics.metrics.value, s),
                    )
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PRESET_OVERDRIVE -> applyPreset(Preset.OVERDRIVE)
            ACTION_PRESET_BALANCED -> applyPreset(Preset.BALANCED)
            ACTION_STOP -> lifecycleScope.launch {
                AppGraph.sessions.stop()
                AppGraph.settings.update {
                    it.copy(
                        auto = it.auto.copy(enabled = false),
                        overlay = it.overlay.copy(enabled = false),
                        perAppProfiles = false,
                    )
                }
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun applyPreset(preset: Preset) {
        lifecycleScope.launch { AppGraph.applyGlobalProfile(preset.profile(AppGraph.caps)) }
    }

    private fun goForeground() {
        val notification = EngineNotification.build(this, AppGraph.metrics.metrics.value, AppGraph.settings.settings.value)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                EngineNotification.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(EngineNotification.NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        coordinator.stop()
        overlay.hide()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PRESET_OVERDRIVE = "com.overdrive.quest.action.PRESET_OVERDRIVE"
        const val ACTION_PRESET_BALANCED = "com.overdrive.quest.action.PRESET_BALANCED"
        const val ACTION_STOP = "com.overdrive.quest.action.STOP"
        private const val NOTIFICATION_REFRESH_MS = 4_000L
    }
}
