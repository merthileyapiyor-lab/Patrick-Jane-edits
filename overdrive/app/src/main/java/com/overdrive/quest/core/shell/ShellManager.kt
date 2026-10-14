package com.overdrive.quest.core.shell

import android.content.Context
import android.content.pm.PackageManager
import com.overdrive.quest.core.tweaks.QuestCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicLong

enum class AccessState {
    CHECKING, NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, OUTDATED, READY_SHIZUKU, READY_ROOT;

    val ready: Boolean get() = this == READY_SHIZUKU || this == READY_ROOT
}

data class ShellResult(val code: Int, val out: String, val err: String) {
    val ok: Boolean get() = code == 0
}

data class LogEntry(
    val id: Long,
    val timeMs: Long,
    val label: String?,
    val command: String,
    val result: ShellResult,
    val backend: AccessState,
)

/**
 * Runs commands with shell (ADB) or root privileges. Shizuku is the main path on Quest:
 * it is started once over ADB and then lends its shell-uid process to apps it trusts.
 */
class ShellManager(private val context: Context) {
    private val _state = MutableStateFlow(AccessState.CHECKING)
    val state: StateFlow<AccessState> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log.asStateFlow()

    @Volatile
    private var rootEnabled = false
    private val ids = AtomicLong()

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    // Shizuku 13 hides newProcess; it is still the lightest way to run a one-off command.
    private val newProcess: Method by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        ).apply { isAccessible = true }
    }

    fun start() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun refresh() {
        _state.value = computeState()
    }

    private fun computeState(): AccessState {
        if (rootEnabled) return AccessState.READY_ROOT
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (running) {
            if (runCatching { Shizuku.isPreV11() }.getOrDefault(false)) return AccessState.OUTDATED
            val granted = runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            return if (granted) AccessState.READY_SHIZUKU else AccessState.NO_PERMISSION
        }
        return if (isInstalled(QuestCommands.SHIZUKU_PACKAGE)) AccessState.NOT_RUNNING else AccessState.NOT_INSTALLED
    }

    fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun requestShizukuPermission() {
        runCatching {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            }
        }
        refresh()
    }

    /** Asks the root manager for access. Returns true when `su` really gives uid 0. */
    suspend fun enableRoot(): Boolean {
        val r = runProcess(arrayOf("su", "-c", "id"), 20_000)
        rootEnabled = r.ok && r.out.contains("uid=0")
        refresh()
        return rootEnabled
    }

    fun disableRoot() {
        rootEnabled = false
        refresh()
    }

    suspend fun exec(
        command: String,
        label: String? = null,
        log: Boolean = true,
        timeoutMs: Long = 10_000,
    ): ShellResult {
        val backend = _state.value
        val result = when (backend) {
            AccessState.READY_ROOT -> runProcess(arrayOf("su", "-c", command), timeoutMs)
            AccessState.READY_SHIZUKU -> runShizuku(command, timeoutMs)
            else -> ShellResult(-1, "", "No privileged access (start Shizuku first)")
        }
        if (log) append(label, command, result, backend)
        if (backend == AccessState.READY_SHIZUKU && result.code == CODE_BINDER_FAILURE) refresh()
        return result
    }

    fun clearLog() {
        _log.value = emptyList()
    }

    private fun append(label: String?, command: String, result: ShellResult, backend: AccessState) {
        val entry = LogEntry(ids.incrementAndGet(), System.currentTimeMillis(), label, command, result, backend)
        _log.update { (listOf(entry) + it).take(MAX_LOG) }
    }

    private suspend fun runShizuku(command: String, timeoutMs: Long): ShellResult = withContext(Dispatchers.IO) {
        val process = try {
            newProcess.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        } catch (t: Throwable) {
            val cause = t.cause ?: t
            return@withContext ShellResult(CODE_BINDER_FAILURE, "", cause.message ?: cause.toString())
        }
        collect(process, timeoutMs)
    }

    private suspend fun runProcess(argv: Array<String>, timeoutMs: Long): ShellResult = withContext(Dispatchers.IO) {
        val process = try {
            Runtime.getRuntime().exec(argv)
        } catch (t: Throwable) {
            return@withContext ShellResult(-1, "", t.message ?: t.toString())
        }
        collect(process, timeoutMs)
    }

    private suspend fun collect(process: Process, timeoutMs: Long): ShellResult = coroutineScope {
        runCatching { process.outputStream.close() }
        val out = async(Dispatchers.IO) {
            runCatching { process.inputStream.bufferedReader().use { it.readText() } }.getOrDefault("")
        }
        val err = async(Dispatchers.IO) {
            runCatching { process.errorStream.bufferedReader().use { it.readText() } }.getOrDefault("")
        }
        val result = withTimeoutOrNull(timeoutMs) {
            val o = out.await()
            val e = err.await()
            val code = runInterruptible(Dispatchers.IO) { process.waitFor() }
            ShellResult(code, o, e)
        }
        if (result == null) {
            runCatching { process.destroy() }
            ShellResult(CODE_TIMEOUT, "", "Timed out after ${timeoutMs}ms")
        } else {
            result
        }
    }

    companion object {
        const val PERMISSION_REQUEST_CODE = 4242
        const val CODE_BINDER_FAILURE = -2
        const val CODE_TIMEOUT = -3
        private const val MAX_LOG = 150
    }
}
