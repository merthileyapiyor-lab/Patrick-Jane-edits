package com.overdrive.quest.core.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isVr: Boolean,
    val isSystem: Boolean,
)

/** Installed games and apps, with VR titles detected through the Horizon OS launcher category. */
class AppCatalog(private val context: Context) {
    private val pm = context.packageManager
    private val icons = LruCache<String, ImageBitmap>(96)
    private val labels = mutableMapOf<String, String>()

    suspend fun load(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val vrInfos = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(VR_CATEGORY), 0)
        val launcherInfos = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        )
        val vrPackages = vrInfos.map { it.activityInfo.packageName }.toSet()
        (vrInfos + launcherInfos)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .map { ri ->
                val pkg = ri.activityInfo.packageName
                val label = ri.loadLabel(pm).toString()
                synchronized(labels) { labels[pkg] = label }
                InstalledApp(
                    packageName = pkg,
                    label = label,
                    isVr = pkg in vrPackages,
                    isSystem = (ri.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
            .sortedWith(compareByDescending<InstalledApp> { it.isVr }.thenBy { it.label.lowercase() })
    }

    fun label(packageName: String): String {
        synchronized(labels) { labels[packageName]?.let { return it } }
        return runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName).also { synchronized(labels) { labels[packageName] = it } }
    }

    fun icon(packageName: String): ImageBitmap? {
        icons.get(packageName)?.let { return it }
        return runCatching {
            pm.getApplicationIcon(packageName).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
        }.getOrNull()?.also { icons.put(packageName, it) }
    }

    fun launch(packageName: String): Boolean {
        val intent = pm.getLaunchIntentForPackage(packageName) ?: run {
            val vr = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(VR_CATEGORY).setPackage(packageName), 0,
            ).firstOrNull() ?: return false
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(VR_CATEGORY)
                component = ComponentName(vr.activityInfo.packageName, vr.activityInfo.name)
            }
        }
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    companion object {
        const val VR_CATEGORY = "com.oculus.intent.category.VR"
        private const val ICON_PX = 128
    }
}
