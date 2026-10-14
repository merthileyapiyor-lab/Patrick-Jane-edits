package com.overdrive.quest.core

import android.app.Application
import android.os.Build
import com.overdrive.quest.core.apps.AppCatalog
import com.overdrive.quest.core.auto.BoostLog
import com.overdrive.quest.core.data.SettingsRepository
import com.overdrive.quest.core.model.DeviceCaps
import com.overdrive.quest.core.model.PerformanceProfile
import com.overdrive.quest.core.monitor.MetricsHub
import com.overdrive.quest.core.session.SessionRepository
import com.overdrive.quest.core.shell.ShellManager
import com.overdrive.quest.core.shell.ShellResult
import com.overdrive.quest.core.tweaks.TweakEngine
import com.overdrive.quest.service.ServiceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Process-wide singletons. Small enough that a DI framework would be overkill. */
object AppGraph {
    lateinit var app: Application
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var baseCaps: DeviceCaps
        private set
    lateinit var shell: ShellManager
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var tweaks: TweakEngine
        private set
    lateinit var metrics: MetricsHub
        private set
    lateinit var sessions: SessionRepository
        private set
    lateinit var apps: AppCatalog
        private set
    val boostLog = BoostLog()

    /** Null until settings are loaded, then whether the background engine has work to do. */
    lateinit var serviceNeeded: StateFlow<Boolean?>
        private set

    val caps: DeviceCaps
        get() = baseCaps.withExtendedRange(settings.settings.value.extendedRange)

    private var initialized = false

    fun init(application: Application) {
        if (initialized) return
        initialized = true
        app = application
        baseCaps = DeviceCaps.detect(Build.MODEL ?: "", Build.DEVICE ?: "")
        shell = ShellManager(application)
        settings = SettingsRepository(application, scope)
        tweaks = TweakEngine(shell) { caps }
        metrics = MetricsHub(application, shell, scope) { settings.settings.value.sampleIntervalMs }
        sessions = SessionRepository(application, scope, metrics)
        apps = AppCatalog(application)

        serviceNeeded = combine(settings.settingsFlow, settings.profilesFlow, sessions.recording) { s, profiles, rec ->
            s.auto.enabled || s.overlay.enabled || (s.perAppProfiles && profiles.isNotEmpty()) || rec != null
        }.stateIn<Boolean?>(scope, SharingStarted.Eagerly, null)

        shell.start()
        sessions.load()

        scope.launch {
            val s = settings.loaded()
            if (s.useRoot) shell.enableRoot()
        }

        // Debug props are wiped on reboot and Shizuku must be restarted after one, so the
        // moment access comes back is the moment to restore the user's setup.
        scope.launch {
            shell.state.map { it.ready }.distinctUntilChanged().collect { ready ->
                if (!ready) return@collect
                val s = settings.loaded()
                if (!s.applyOnConnect) return@collect
                if (!s.profile.isStock) tweaks.applyProfile(s.profile, "Restore profile")
                if (!s.capture.isStock) tweaks.applyCapture(s.capture, "Restore capture")
            }
        }

        scope.launch {
            serviceNeeded.collect { needed -> if (needed == true) ServiceController.start(application) }
        }
    }

    /** Applies and remembers a profile as the global one. */
    suspend fun applyGlobalProfile(profile: PerformanceProfile): ShellResult {
        val p = profile.clampedTo(caps)
        settings.update { it.copy(profile = p) }
        val r = tweaks.applyProfile(p, "Apply profile")
        metrics.pokeSoon()
        return r
    }

    suspend fun resetToStock(): ShellResult {
        settings.update { it.copy(profile = PerformanceProfile(), capture = com.overdrive.quest.core.model.CaptureConfig()) }
        val r = tweaks.resetAll("Reset to stock")
        metrics.pokeSoon()
        return r
    }
}
