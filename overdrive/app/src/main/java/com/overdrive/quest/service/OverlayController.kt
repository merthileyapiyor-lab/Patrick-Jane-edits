package com.overdrive.quest.service

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.overdrive.quest.core.AppGraph
import com.overdrive.quest.core.model.HudCorner
import com.overdrive.quest.core.model.OverlayConfig
import com.overdrive.quest.ui.overlay.HudPanel
import com.overdrive.quest.ui.theme.OverdriveTheme
import kotlinx.coroutines.launch

/** The floating HUD: a draggable system overlay window rendered with Compose. */
class OverlayController(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var params: WindowManager.LayoutParams? = null
    private var config = OverlayConfig()

    val isShowing: Boolean get() = view != null

    fun update(cfg: OverlayConfig) {
        config = cfg
        if (!cfg.enabled || !Settings.canDrawOverlays(context)) {
            hide()
            return
        }
        val p = params
        if (view == null || p == null) {
            show(cfg)
        } else {
            applyConfig(p, cfg)
            runCatching { wm.updateViewLayout(view, p) }
        }
    }

    private fun show(cfg: OverlayConfig) {
        val lifecycleOwner = OverlayOwner().also { it.start() }
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        applyConfig(p, cfg)
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val settings by AppGraph.settings.settings.collectAsStateWithLifecycle()
                val metrics by AppGraph.metrics.metrics.collectAsStateWithLifecycle()
                val history by AppGraph.metrics.history.collectAsStateWithLifecycle()
                OverdriveTheme(accent = settings.accent) {
                    HudPanel(
                        metrics = metrics,
                        history = history,
                        config = settings.overlay,
                        onDrag = { dx, dy -> moveBy(dx, dy) },
                        onDragEnd = { persistPosition() },
                    )
                }
            }
        }
        val added = runCatching { wm.addView(composeView, p) }.isSuccess
        if (!added) {
            lifecycleOwner.stop()
            return
        }
        view = composeView
        owner = lifecycleOwner
        params = p
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        owner?.stop()
        view = null
        owner = null
        params = null
    }

    private fun applyConfig(p: WindowManager.LayoutParams, cfg: OverlayConfig) {
        p.gravity = when (cfg.corner) {
            HudCorner.TOP_LEFT -> Gravity.TOP or Gravity.START
            HudCorner.TOP_RIGHT -> Gravity.TOP or Gravity.END
            HudCorner.BOTTOM_LEFT -> Gravity.BOTTOM or Gravity.START
            HudCorner.BOTTOM_RIGHT -> Gravity.BOTTOM or Gravity.END
        }
        p.x = cfg.x
        p.y = cfg.y
        // Android only lets touches pass through overlays that are at most 80% opaque.
        p.alpha = if (cfg.clickThrough) cfg.opacity.coerceAtMost(0.8f) else cfg.opacity
        p.flags = if (cfg.clickThrough) {
            p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
    }

    private fun moveBy(dx: Float, dy: Float) {
        val p = params ?: return
        val v = view ?: return
        val sx = if (config.corner == HudCorner.TOP_RIGHT || config.corner == HudCorner.BOTTOM_RIGHT) -1 else 1
        val sy = if (config.corner == HudCorner.BOTTOM_LEFT || config.corner == HudCorner.BOTTOM_RIGHT) -1 else 1
        p.x = (p.x + sx * dx).toInt().coerceAtLeast(0)
        p.y = (p.y + sy * dy).toInt().coerceAtLeast(0)
        config = config.copy(x = p.x, y = p.y)
        runCatching { wm.updateViewLayout(v, p) }
    }

    private fun persistPosition() {
        val p = params ?: return
        val x = p.x
        val y = p.y
        AppGraph.scope.launch {
            AppGraph.settings.update { it.copy(overlay = it.overlay.copy(x = x, y = y)) }
        }
    }

    /** Minimal lifecycle so Compose can run outside an Activity. */
    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedState = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

        fun start() {
            savedState.performAttach()
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
