package com.overdrive.quest.core.tweaks

import com.overdrive.quest.core.model.CaptureConfig
import com.overdrive.quest.core.model.DeviceCaps
import com.overdrive.quest.core.model.PerformanceProfile
import com.overdrive.quest.core.shell.ShellManager
import com.overdrive.quest.core.shell.ShellResult

/** Every privileged action Overdrive performs goes through here. */
class TweakEngine(
    private val shell: ShellManager,
    private val caps: () -> DeviceCaps,
) {
    suspend fun applyProfile(profile: PerformanceProfile, label: String): ShellResult {
        val p = profile.clampedTo(caps())
        return shell.exec(QuestCommands.script(QuestCommands.profile(p, caps())), label)
    }

    suspend fun setLevels(cpu: Int?, gpu: Int?, label: String): ShellResult =
        shell.exec(QuestCommands.script(QuestCommands.levels(cpu, gpu)), label)

    suspend fun applyCapture(config: CaptureConfig, label: String): ShellResult =
        shell.exec(QuestCommands.script(QuestCommands.capture(config)), label)

    suspend fun resetAll(label: String): ShellResult =
        shell.exec(QuestCommands.script(QuestCommands.resetAll()), label)

    suspend fun killBackground(label: String): ShellResult =
        shell.exec(QuestCommands.KILL_BACKGROUND, label)

    suspend fun dropCaches(label: String): ShellResult =
        shell.exec(QuestCommands.DROP_CACHES_ROOT, label)

    suspend fun proximity(disableSensor: Boolean, label: String): ShellResult =
        shell.exec(
            if (disableSensor) QuestCommands.PROXIMITY_DISABLE else QuestCommands.PROXIMITY_RESTORE,
            label,
        )

    suspend fun ovrMetricsOverlay(enable: Boolean, label: String): ShellResult =
        shell.exec(QuestCommands.ovrMetricsOverlay(enable), label)

    suspend fun selfGrant(packageName: String, sdkInt: Int, label: String): ShellResult =
        shell.exec(QuestCommands.script(QuestCommands.selfGrants(packageName, sdkInt)), label)

    suspend fun forceStop(packageName: String, label: String): ShellResult =
        shell.exec(QuestCommands.forceStop(packageName), label)
}
