package com.overdrive.quest.ui

import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.overdrive.quest.R
import com.overdrive.quest.core.AppGraph
import com.overdrive.quest.core.apps.InstalledApp
import com.overdrive.quest.core.model.AppProfile
import com.overdrive.quest.core.model.AppSettings
import com.overdrive.quest.core.model.AutoBoostConfig
import com.overdrive.quest.core.model.CaptureConfig
import com.overdrive.quest.core.model.DeviceCaps
import com.overdrive.quest.core.model.OverlayConfig
import com.overdrive.quest.core.model.PerformanceProfile
import com.overdrive.quest.core.model.Preset
import com.overdrive.quest.core.shell.ShellResult
import com.overdrive.quest.core.tweaks.QuestCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MessageKind { INFO, SUCCESS, ERROR }

data class UiMessage(val text: String, val kind: MessageKind, val id: Long = System.nanoTime())

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val g = AppGraph

    val settings: StateFlow<AppSettings> = g.settings.settings
    val appProfiles = g.settings.appProfiles
    val metrics = g.metrics.metrics
    val history = g.metrics.history
    val cpuClocks = g.metrics.cpuClocks
    val gpuClocks = g.metrics.gpuClocks
    val access = g.shell.state
    val log = g.shell.log
    val events = g.boostLog.events
    val recording = g.sessions.recording
    val sessions = g.sessions.sessions

    val caps: StateFlow<DeviceCaps> = settings
        .map { g.baseCaps.withExtendedRange(it.extendedRange) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, g.caps)

    private val _apps = MutableStateFlow<List<InstalledApp>?>(null)
    val apps: StateFlow<List<InstalledApp>?> = _apps.asStateFlow()

    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private val _proximityDisabled = MutableStateFlow(false)
    val proximityDisabled: StateFlow<Boolean> = _proximityDisabled.asStateFlow()

    val packageName: String get() = getApplication<Application>().packageName

    private fun str(@StringRes id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private fun say(@StringRes id: Int, kind: MessageKind = MessageKind.SUCCESS, vararg args: Any) {
        _messages.tryEmit(UiMessage(str(id, *args), kind))
    }

    private fun fail(result: ShellResult) {
        val detail = result.err.ifBlank { result.out }.lineSequence().firstOrNull { it.isNotBlank() } ?: "exit ${result.code}"
        _messages.tryEmit(UiMessage(str(R.string.msg_failed, detail.take(120)), MessageKind.ERROR))
    }

    private fun launchBusy(key: String, block: suspend () -> Unit) {
        if (key in _busy.value) return
        _busy.update { it + key }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _busy.update { it - key }
            }
        }
    }

    private fun requireAccess(): Boolean {
        if (access.value.ready) return true
        say(R.string.msg_need_access, MessageKind.ERROR)
        return false
    }

    // ---- Performance -------------------------------------------------------------------

    fun applyPreset(preset: Preset) = applyProfile(preset.profile(caps.value), preset)

    fun applyProfile(profile: PerformanceProfile, preset: Preset? = null) {
        if (!requireAccess()) return
        launchBusy(KEY_APPLY) {
            val previous = settings.value.profile
            val r = g.applyGlobalProfile(profile)
            when {
                !r.ok -> fail(r)
                profile.displayDiffers(previous) -> say(R.string.msg_applied_relaunch)
                preset != null -> say(R.string.msg_preset_applied, MessageKind.SUCCESS, str(presetName(preset)))
                else -> say(R.string.msg_applied)
            }
        }
    }

    fun resetStock() {
        if (!requireAccess()) return
        launchBusy(KEY_APPLY) {
            val r = g.resetToStock()
            if (r.ok) say(R.string.msg_reset) else fail(r)
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { g.settings.update(transform) }
    }

    fun setAuto(transform: (AutoBoostConfig) -> AutoBoostConfig) {
        val next = transform(settings.value.auto)
        if (next.enabled && !settings.value.auto.enabled && !requireAccess()) return
        updateSettings { it.copy(auto = transform(it.auto)) }
    }

    fun setOverlay(transform: (OverlayConfig) -> OverlayConfig) {
        val next = transform(settings.value.overlay)
        if (next.enabled && !Settings.canDrawOverlays(getApplication())) {
            grantOverlayPermission()
        }
        updateSettings { it.copy(overlay = transform(it.overlay)) }
    }

    // ---- Access -------------------------------------------------------------------------

    fun requestShizuku() = g.shell.requestShizukuPermission()

    fun refreshAccess() {
        g.shell.refresh()
    }

    fun enableRoot() = launchBusy(KEY_ROOT) {
        val ok = g.shell.enableRoot()
        g.settings.update { it.copy(useRoot = ok) }
        if (ok) say(R.string.msg_root_ok) else say(R.string.msg_root_failed, MessageKind.ERROR)
    }

    fun disableRoot() {
        g.shell.disableRoot()
        updateSettings { it.copy(useRoot = false) }
    }

    /** Grants overlay, notifications and background running in one go through Shizuku. */
    fun oneTapSetup() {
        if (!requireAccess()) return
        launchBusy(KEY_SETUP) {
            val r = g.tweaks.selfGrant(packageName, Build.VERSION.SDK_INT, "One-tap setup")
            if (r.ok || r.code > 0) say(R.string.msg_setup_done) else fail(r)
        }
    }

    fun grantOverlayPermission() {
        val ctx = getApplication<Application>()
        if (Settings.canDrawOverlays(ctx)) return
        if (access.value.ready) {
            viewModelScope.launch {
                g.shell.exec("appops set $packageName SYSTEM_ALERT_WINDOW allow", "Grant overlay")
                delay(400)
                if (Settings.canDrawOverlays(ctx)) say(R.string.msg_overlay_granted)
            }
            return
        }
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { say(R.string.msg_overlay_manual, MessageKind.ERROR) }
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(getApplication())

    // ---- Game profiles -------------------------------------------------------------------

    fun loadApps(force: Boolean = false) {
        if (_apps.value != null && !force) return
        viewModelScope.launch { _apps.value = g.apps.load() }
    }

    fun icon(packageName: String): ImageBitmap? = g.apps.icon(packageName)

    fun label(packageName: String): String = g.apps.label(packageName)

    fun saveAppProfile(app: InstalledApp, profile: PerformanceProfile) {
        viewModelScope.launch {
            g.settings.putAppProfile(AppProfile(app.packageName, app.label, profile.clampedTo(caps.value)))
            say(R.string.msg_profile_saved, MessageKind.SUCCESS, app.label)
        }
    }

    fun removeAppProfile(packageName: String) {
        viewModelScope.launch { g.settings.removeAppProfile(packageName) }
    }

    /** Display settings only apply at launch, so set them first and then start the game. */
    fun launchWithProfile(app: InstalledApp) {
        launchBusy(KEY_LAUNCH + app.packageName) {
            val profile = appProfiles.value[app.packageName]?.profile
            if (profile != null && access.value.ready) {
                g.tweaks.forceStop(app.packageName, "Restart ${app.label}")
                val r = g.tweaks.applyProfile(profile, "Game profile: ${app.label}")
                if (!r.ok) fail(r)
            }
            if (!g.apps.launch(app.packageName)) say(R.string.msg_launch_failed, MessageKind.ERROR)
        }
    }

    // ---- Toolbox -------------------------------------------------------------------------

    fun boostRam() {
        if (!requireAccess()) return
        launchBusy(KEY_RAM) {
            val before = availMb()
            val r = g.tweaks.killBackground("RAM boost")
            if (settings.value.useRoot) g.tweaks.dropCaches("Drop caches")
            delay(900)
            val freed = (availMb() - before).coerceAtLeast(0)
            if (r.ok) say(R.string.msg_ram_freed, MessageKind.SUCCESS, freed) else fail(r)
            g.metrics.pokeSoon()
        }
    }

    private suspend fun availMb(): Long = withContext(Dispatchers.Default) {
        val am = getApplication<Application>().getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)
        info.availMem / (1024 * 1024)
    }

    fun applyCapture(config: CaptureConfig) {
        if (!requireAccess()) return
        launchBusy(KEY_CAPTURE) {
            g.settings.update { it.copy(capture = config) }
            val r = g.tweaks.applyCapture(config, "Capture settings")
            if (r.ok) say(R.string.msg_capture_applied) else fail(r)
        }
    }

    fun setProximity(disableSensor: Boolean) {
        if (!requireAccess()) return
        launchBusy(KEY_PROX) {
            val r = g.tweaks.proximity(disableSensor, if (disableSensor) "Proximity sensor off" else "Proximity sensor on")
            if (r.ok) {
                _proximityDisabled.value = disableSensor
                say(if (disableSensor) R.string.msg_prox_off else R.string.msg_prox_on)
            } else {
                fail(r)
            }
        }
    }

    fun isOvrMetricsInstalled(): Boolean = g.shell.isInstalled(QuestCommands.OVR_METRICS_PACKAGE)

    fun setOvrMetricsOverlay(enable: Boolean) {
        if (!requireAccess()) return
        launchBusy(KEY_OVR) {
            val r = g.tweaks.ovrMetricsOverlay(enable, if (enable) "OVR Metrics overlay on" else "OVR Metrics overlay off")
            if (r.ok) say(if (enable) R.string.msg_ovr_on else R.string.msg_ovr_off) else fail(r)
        }
    }

    fun startRecording() = g.sessions.start()

    fun stopRecording() = launchBusy(KEY_RECORD) {
        val s = g.sessions.stop()
        if (s != null) say(R.string.msg_session_saved) else say(R.string.msg_session_short, MessageKind.INFO)
    }

    fun exportSession(id: String) = launchBusy(KEY_EXPORT + id) {
        val path = g.sessions.exportCsv(id)
        if (path != null) say(R.string.msg_exported, MessageKind.SUCCESS, path) else say(R.string.msg_export_failed, MessageKind.ERROR)
    }

    fun deleteSession(id: String) {
        viewModelScope.launch { g.sessions.delete(id) }
    }

    fun clearLog() = g.shell.clearLog()

    fun clearEvents() = g.boostLog.clear()

    fun adbScript(): String {
        val s = settings.value
        val cmds = QuestCommands.profile(s.profile.clampedTo(caps.value), caps.value) +
            if (s.capture.isStock) emptyList() else QuestCommands.capture(s.capture)
        return QuestCommands.adbScript(cmds)
    }

    fun notifyCopied() = say(R.string.msg_copied, MessageKind.INFO)

    companion object {
        const val KEY_APPLY = "apply"
        const val KEY_ROOT = "root"
        const val KEY_SETUP = "setup"
        const val KEY_LAUNCH = "launch:"
        const val KEY_RAM = "ram"
        const val KEY_CAPTURE = "capture"
        const val KEY_PROX = "prox"
        const val KEY_OVR = "ovr"
        const val KEY_RECORD = "record"
        const val KEY_EXPORT = "export:"

        @StringRes
        fun presetName(p: Preset): Int = when (p) {
            Preset.ECO -> R.string.preset_eco
            Preset.BALANCED -> R.string.preset_balanced
            Preset.PERFORMANCE -> R.string.preset_performance
            Preset.OVERDRIVE -> R.string.preset_overdrive
            Preset.CLARITY -> R.string.preset_clarity
        }

        @StringRes
        fun presetDescription(p: Preset): Int = when (p) {
            Preset.ECO -> R.string.preset_eco_desc
            Preset.BALANCED -> R.string.preset_balanced_desc
            Preset.PERFORMANCE -> R.string.preset_performance_desc
            Preset.OVERDRIVE -> R.string.preset_overdrive_desc
            Preset.CLARITY -> R.string.preset_clarity_desc
        }
    }
}
