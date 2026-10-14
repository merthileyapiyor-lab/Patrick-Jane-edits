package com.overdrive.quest.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.overdrive.quest.R
import com.overdrive.quest.core.apps.InstalledApp
import com.overdrive.quest.core.model.DeviceCaps
import com.overdrive.quest.core.model.PerformanceProfile
import com.overdrive.quest.core.model.Preset
import com.overdrive.quest.ui.MainViewModel
import com.overdrive.quest.ui.components.ButtonStyle
import com.overdrive.quest.ui.components.ChipGroup
import com.overdrive.quest.ui.components.GlassCard
import com.overdrive.quest.ui.components.LabeledSlider
import com.overdrive.quest.ui.components.LevelSelector
import com.overdrive.quest.ui.components.NeonButton
import com.overdrive.quest.ui.components.NeonChip
import com.overdrive.quest.ui.components.Pill
import com.overdrive.quest.ui.components.SectionHeader
import com.overdrive.quest.ui.components.ToggleRow
import com.overdrive.quest.ui.components.caps
import com.overdrive.quest.ui.theme.LocalPalette
import com.overdrive.quest.ui.theme.OD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class AppFilter { VR, ALL, PROFILED }

@Composable
fun ProfilesScreen(vm: MainViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val profiles by vm.appProfiles.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val caps by vm.caps.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AppFilter.VR) }
    var editing by remember { mutableStateOf<InstalledApp?>(null) }

    LaunchedEffect(Unit) { vm.loadApps() }

    val list = apps.orEmpty().filter { app ->
        val matches = query.isBlank() || app.label.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true)
        val inFilter = when (filter) {
            AppFilter.VR -> app.isVr
            AppFilter.ALL -> true
            AppFilter.PROFILED -> app.packageName in profiles
        }
        matches && inFilter
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(300.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            GlassCard(Modifier.fillMaxWidth()) {
                ToggleRow(
                    title = stringResource(R.string.profiles_auto_switch),
                    subtitle = stringResource(R.string.profiles_auto_switch_sub),
                    checked = settings.perAppProfiles,
                    onCheckedChange = { on -> vm.updateSettings { it.copy(perAppProfiles = on) } },
                    icon = Icons.Rounded.SportsEsports,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SearchField(query, { query = it }, Modifier.weight(1f))
                    Spacer(Modifier.width(14.dp))
                    NeonChip(stringResource(R.string.filter_vr), filter == AppFilter.VR, { filter = AppFilter.VR })
                    Spacer(Modifier.width(8.dp))
                    NeonChip(stringResource(R.string.filter_all), filter == AppFilter.ALL, { filter = AppFilter.ALL })
                    Spacer(Modifier.width(8.dp))
                    NeonChip(stringResource(R.string.filter_profiled, profiles.size), filter == AppFilter.PROFILED, { filter = AppFilter.PROFILED })
                    Spacer(Modifier.width(8.dp))
                    NeonButton("", onClick = { vm.loadApps(force = true) }, icon = Icons.Rounded.Refresh, style = ButtonStyle.GHOST, height = 42.dp)
                }
            }
        }

        if (apps == null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LocalPalette.current.primary)
                }
            }
        } else if (list.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.profiles_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OD.TextDim,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }

        items(list, key = { it.packageName }) { app ->
            AppCard(
                app = app,
                profile = profiles[app.packageName]?.profile,
                vm = vm,
                onEdit = { editing = app },
                onLaunch = { vm.launchWithProfile(app) },
            )
        }
    }

    editing?.let { app ->
        ProfileEditor(
            app = app,
            initial = profiles[app.packageName]?.profile,
            caps = caps,
            vm = vm,
            onDismiss = { editing = null },
            onSave = { p ->
                vm.saveAppProfile(app, p)
                editing = null
            },
            onRemove = {
                vm.removeAppProfile(app.packageName)
                editing = null
            },
        )
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = OD.TextDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(stringResource(R.string.profiles_search), style = MaterialTheme.typography.bodyLarge, color = OD.TextFaint)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = OD.Text),
                cursorBrush = SolidColor(palette.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AppIcon(packageName: String, vm: MainViewModel, size: Int = 64) {
    val bitmap by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) { vm.icon(packageName) }
    }
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.Apps, null, tint = OD.TextDim)
        }
    }
}

@Composable
private fun AppCard(
    app: InstalledApp,
    profile: PerformanceProfile?,
    vm: MainViewModel,
    onEdit: () -> Unit,
    onLaunch: () -> Unit,
) {
    val palette = LocalPalette.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    GlassCard(Modifier.fillMaxWidth(), glow = profile != null, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName, vm)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium, color = OD.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = OD.TextFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (app.isVr) Pill("VR", color = palette.secondary)
                    if (profile != null) {
                        val preset = Preset.detect(profile, vm.caps.value)
                        Pill(
                            preset?.let { stringResource(MainViewModel.presetName(it)) } ?: stringResource(R.string.profile_custom),
                            color = preset?.let { presetColor(it) } ?: palette.primary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            profile?.let { profileSummary(it) } ?: stringResource(R.string.profiles_none),
            style = MaterialTheme.typography.bodySmall,
            color = if (profile != null) OD.Text else OD.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeonButton(
                stringResource(if (profile != null) R.string.action_edit else R.string.action_create),
                onClick = onEdit,
                icon = Icons.Rounded.Edit,
                style = ButtonStyle.GHOST,
                height = 42.dp,
                modifier = Modifier.weight(1f),
            )
            NeonButton(
                stringResource(R.string.action_launch),
                onClick = onLaunch,
                icon = Icons.Rounded.PlayArrow,
                height = 42.dp,
                loading = (MainViewModel.KEY_LAUNCH + app.packageName) in busy,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ProfileEditor(
    app: InstalledApp,
    initial: PerformanceProfile?,
    caps: DeviceCaps,
    vm: MainViewModel,
    onDismiss: () -> Unit,
    onSave: (PerformanceProfile) -> Unit,
    onRemove: () -> Unit,
) {
    var draft by remember(app.packageName) { mutableStateOf(initial ?: Preset.PERFORMANCE.profile(caps)) }
    val stock = stringResource(R.string.level_stock)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlassCard(
            Modifier
                .widthIn(max = 860.dp)
                .padding(24.dp),
            glow = true,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.packageName, vm, size = 56)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.profiles_editor_title).caps(), style = MaterialTheme.typography.labelMedium, color = OD.TextDim)
                        Text(app.label, style = MaterialTheme.typography.headlineSmall, color = OD.Text)
                    }
                }
                Spacer(Modifier.height(18.dp))
                SectionHeader(stringResource(R.string.profiles_start_from))
                Spacer(Modifier.height(10.dp))
                val active = Preset.detect(draft, caps)
                ChipGroup(
                    options = Preset.entries,
                    selected = active,
                    label = { stringResource(MainViewModel.presetName(it)) },
                    onSelect = { draft = it.profile(caps) },
                )
                Spacer(Modifier.height(20.dp))
                Text("CPU", style = MaterialTheme.typography.labelMedium, color = OD.TextDim)
                Spacer(Modifier.height(8.dp))
                LevelSelector(draft.cpuLevel, caps.maxCpuLevel, { draft = draft.copy(cpuLevel = it) }, stock)
                Spacer(Modifier.height(14.dp))
                Text("GPU", style = MaterialTheme.typography.labelMedium, color = OD.TextDim)
                Spacer(Modifier.height(8.dp))
                LevelSelector(draft.gpuLevel, caps.maxGpuLevel, { draft = draft.copy(gpuLevel = it) }, stock)
                Spacer(Modifier.height(18.dp))
                Text(stringResource(R.string.tuning_refresh).caps(), style = MaterialTheme.typography.labelMedium, color = OD.TextDim)
                Spacer(Modifier.height(8.dp))
                ChipGroup(
                    options = listOf<Int?>(null) + caps.refreshRates,
                    selected = draft.refreshRate,
                    label = { it?.let { hz -> "$hz Hz" } ?: stock },
                    onSelect = { draft = draft.copy(refreshRate = it) },
                )
                Spacer(Modifier.height(14.dp))
                ToggleRow(
                    title = stringResource(R.string.tuning_resolution),
                    subtitle = draft.resolutionScale?.let { s ->
                        val (w, h) = caps.eyeSize(s)
                        "%.2f× · %d × %d".format(s, w, h)
                    } ?: stringResource(R.string.tuning_resolution_stock),
                    checked = draft.resolutionScale != null,
                    onCheckedChange = { on -> draft = draft.copy(resolutionScale = if (on) 1.2f else null) },
                )
                draft.resolutionScale?.let { s ->
                    LabeledSlider(
                        title = stringResource(R.string.tuning_resolution_scale),
                        valueText = "%.2f×".format(s),
                        value = s,
                        onValueChange = { v -> draft = draft.copy(resolutionScale = (v * 20f).roundToInt() / 20f) },
                        valueRange = DeviceCaps.MIN_SCALE..DeviceCaps.MAX_SCALE,
                        steps = 21,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (initial != null) {
                        NeonButton(stringResource(R.string.action_remove), onClick = onRemove, icon = Icons.Rounded.Delete, style = ButtonStyle.DANGER)
                    }
                    Spacer(Modifier.weight(1f))
                    NeonButton(stringResource(R.string.action_cancel), onClick = onDismiss, style = ButtonStyle.GHOST)
                    NeonButton(stringResource(R.string.action_save), onClick = { onSave(draft) }, icon = Icons.Rounded.Save)
                }
            }
        }
    }
}
