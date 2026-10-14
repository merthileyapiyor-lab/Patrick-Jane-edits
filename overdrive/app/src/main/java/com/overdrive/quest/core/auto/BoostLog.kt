package com.overdrive.quest.core.auto

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

enum class EngineEventKind { BOOST, APP_PROFILE, APP_RESTORE, ENGAGED, DISENGAGED }

data class EngineEvent(
    val id: Long,
    val timeMs: Long,
    val kind: EngineEventKind,
    val decision: BoostDecision? = null,
    val appLabel: String? = null,
)

/** What the engine did and why, shown live on the Auto Boost screen. */
class BoostLog {
    private val ids = AtomicLong()
    private val _events = MutableStateFlow<List<EngineEvent>>(emptyList())
    val events: StateFlow<List<EngineEvent>> = _events.asStateFlow()

    fun add(kind: EngineEventKind, decision: BoostDecision? = null, appLabel: String? = null) {
        val e = EngineEvent(ids.incrementAndGet(), System.currentTimeMillis(), kind, decision, appLabel)
        _events.update { (listOf(e) + it).take(MAX) }
    }

    fun clear() {
        _events.value = emptyList()
    }

    private companion object {
        const val MAX = 80
    }
}
