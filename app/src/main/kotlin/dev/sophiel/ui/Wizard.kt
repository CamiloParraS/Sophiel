package dev.sophiel.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import dev.sophiel.AppContainer
import dev.sophiel.R
import dev.sophiel.capture.MaskWindowService
import dev.sophiel.settings.ParentPreset
import dev.sophiel.ui.theme.Ease
import dev.sophiel.ui.theme.Palette
import dev.sophiel.ui.theme.motion
import kotlinx.coroutines.delay

private const val STEPS = 7

/**
 * Setup wizard (D42, D44), shown while no PIN exists: PIN -> overlay -> accessibility -> keep awake ->
 * notifications -> preset -> start. Linear with Back; a step granted while on it shows done and moves on.
 * Needs no unlock. [fix] opens the same places Status's fix buttons do; [start] leaves the wizard and starts.
 */
@Composable
fun SetupWizard(container: AppContainer, resumes: Int, fix: (Need) -> Unit, start: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    var forward by rememberSaveable { mutableStateOf(true) }
    BackHandler(step > 1) { forward = false; step-- }
    Crossfade(step, animationSpec = motion(tween(160)), label = "step") { s ->
        // Absolute, not step++: the outgoing step stays composed during the fade and its auto-advance may still fire.
        val next = { forward = true; step = s + 1 }
        val back = { forward = false; step = s - 1 }
        if (s == 1) {
            CreatePinScreen(
                onCreate = { container.pin.set(it); step = 2 },
                title = stringResource(R.string.wiz_title), subtitle = stringResource(R.string.wiz_step, 1, STEPS),
                top = { Dots(1, Modifier.padding(top = 18.dp)) },
            )
            return@Crossfade
        }
        val context = LocalContext.current
        val appInfo = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
        when (s) {
            2 -> {
                val granted = remember(resumes) { Settings.canDrawOverlays(context) }
                advanceWhenGranted(granted, forward, next)
                StepFrame(s, back, R.string.need_overlay, R.string.wiz_overlay_text, bottom = {
                    if (granted) PrimaryPill(R.string.pin_continue, next) else PrimaryPill(R.string.fix_allow, { fix(Need.OVERLAY) })
                }) {
                    PreferenceGroup {
                        item { NeedStepRow(Need.OVERLAY, R.string.need_overlay, R.string.wiz_overlay_row, granted) }
                    }
                }
            }
            3 -> {
                val bound = MaskWindowService.bound.collectAsState().value != null // live: binds ~2 s after an update (D42)
                advanceWhenGranted(bound, forward, next)
                StepFrame(s, back, R.string.need_service, R.string.wiz_service_text, bottom = {
                    if (bound) PrimaryPill(R.string.pin_continue, next) else {
                        PrimaryPill(R.string.wiz_open_accessibility, { fix(Need.SERVICE) })
                        Skip(R.string.wiz_service_skip, next)
                    }
                }) {
                    PreferenceGroup {
                        item { NeedStepRow(Need.SERVICE, R.string.wiz_service_row, R.string.wiz_service_row_text, bound) }
                        item { PrefRow(stringResource(R.string.wiz_shortcut), description = stringResource(R.string.wiz_shortcut_text), icon = R.drawable.ic_warn) }
                        item { GreyedSwitchHelp(appInfo) }
                    }
                }
            }
            4 -> StepFrame(s, back, R.string.wiz_awake_title, R.string.wiz_awake_text, bottom = {
                PillButton(stringResource(R.string.wiz_app_info), appInfo, Modifier.widthIn(min = 200.dp))
                PrimaryPill(R.string.pin_continue, next) // not detectable (D42), so always on
            }) {
                PreferenceGroup(footnote = stringResource(R.string.wiz_awake_note)) {
                    item { PrefRow(stringResource(R.string.wiz_awake_battery), description = stringResource(R.string.wiz_awake_battery_text), icon = R.drawable.ic_power) }
                    if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) item {
                        PrefRow(stringResource(R.string.wiz_awake_samsung), description = stringResource(R.string.wiz_awake_samsung_text), icon = R.drawable.ic_moon)
                    }
                }
            }
            5 -> {
                val granted = remember(resumes) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
                advanceWhenGranted(granted, forward, next)
                StepFrame(s, back, R.string.need_notifications, R.string.wiz_notif_text, bottom = {
                    if (granted) PrimaryPill(R.string.pin_continue, next) else {
                        PrimaryPill(R.string.fix_allow, { fix(Need.NOTIFICATIONS) })
                        Skip(R.string.need_notifications_missing, next)
                    }
                }) {
                    PreferenceGroup {
                        item { NeedStepRow(Need.NOTIFICATIONS, R.string.need_notifications, R.string.need_notifications_missing, granted) }
                    }
                }
            }
            6 -> StepFrame(s, back, R.string.wiz_preset_title, R.string.wiz_preset_text, bottom = { PrimaryPill(R.string.pin_continue, next) }) {
                PresetStep(container)
            }
            else -> StepFrame(s, back, R.string.wiz_start_title, R.string.wiz_start_text, bottom = {
                PillButton(stringResource(R.string.action_start), start, Modifier.widthIn(min = 200.dp), style = PillStyle.Suggested, icon = R.drawable.ic_play)
                Text(stringResource(R.string.status_starting_note), style = MaterialTheme.typography.bodySmall, color = Palette.Dim, textAlign = TextAlign.Center)
            }) {
                PreferenceGroup {
                    item { PrefRow(stringResource(R.string.status_capture_permission), description = stringResource(R.string.status_capture_permission_text), icon = R.drawable.ic_layers) }
                    // D21: Android 14 QPR2+ offers "A single app"; only the whole screen protects everything.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) item {
                        PrefRow(stringResource(R.string.wiz_entire), description = stringResource(R.string.wiz_entire_text), icon = R.drawable.ic_shield)
                    }
                }
            }
        }
    }
}

/**
 * A granted step shows done and moves on by itself, unless the Parent came Back to it already granted:
 * then it waits for Continuar, or Back could never pass it. Saved, so a trip to Settings that recreates the Activity keeps it.
 */
@Composable
private fun advanceWhenGranted(granted: Boolean, forward: Boolean, next: () -> Unit) {
    var armed by rememberSaveable { mutableStateOf(forward) }
    if (!granted) armed = true
    LaunchedEffect(granted) {
        if (granted && armed) {
            delay(600) // long enough to see the done tag
            next()
        }
    }
}

@Composable
private fun Dots(step: Int, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)) {
        repeat(STEPS) { i -> Box(Modifier.size(8.dp).clip(CircleShape).background(if (i < step) primary else Palette.Fg.copy(alpha = .2f))) }
    }
}

/** The mockup's wizard frame (D44); steps it doesn't draw are built from the same parts. */
@Composable
private fun StepFrame(step: Int, back: () -> Unit, @StringRes title: Int, @StringRes text: Int,bottom: @Composable ColumnScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        HeaderBar(
            stringResource(R.string.wiz_title), subtitle = stringResource(R.string.wiz_step, step, STEPS),
            start = { FlatIconButton(R.drawable.ic_back, stringResource(R.string.back), back) },
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Dots(step)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(stringResource(text), Modifier.widthIn(max = 300.dp), style = MaterialTheme.typography.bodyLarge, color = Palette.Dim, textAlign = TextAlign.Center)
            }
            content()
        }
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = bottom,
        )
    }
}

@Composable
private fun PrimaryPill(@StringRes text: Int, onClick: () -> Unit) =
    PillButton(stringResource(text), onClick, Modifier.widthIn(min = 200.dp), style = PillStyle.Suggested)

/** "Saltar", with what is lost under it. */
@Composable
private fun Skip(@StringRes lost: Int, onClick: () -> Unit) {
    Text(
        stringResource(R.string.wiz_skip),
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
    )
    Text(stringResource(lost), style = MaterialTheme.typography.bodySmall, color = Palette.Dim, textAlign = TextAlign.Center)
}

/** A permission row: what to do while missing, Status's done tag once granted. */
@Composable
private fun NeedStepRow(need: Need, @StringRes title: Int, @StringRes todo: Int, granted: Boolean) {
    Crossfade(granted, animationSpec = motion(tween(160)), label = "granted") { ok ->
        PrefRow(
            stringResource(title),
            description = if (ok) null else stringResource(todo),
            icon = need.icon(), iconTint = if (ok) Palette.GreenFg else Palette.Dim,
            end = { if (ok) Tag(stringResource(need.okTag()), TagKind.Ok) },
        )
    }
}

/** D41: always there, never opened by itself (a restricted install can't be detected). */
@Composable
private fun GreyedSwitchHelp(appInfo: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 90f else 0f, motion(tween(240, easing = Ease)), label = "chevron")
    Column {
        PrefRow(stringResource(R.string.wiz_grey), icon = R.drawable.ic_lock, onClick = { open = !open }, end = { SIcon(R.drawable.ic_chev, modifier = Modifier.rotate(turn)) })
        AnimatedVisibility(open, enter = expandVertically(motion(tween(240, easing = Ease))), exit = shrinkVertically(motion(tween(240, easing = Ease)))) {
            Column(Modifier.fillMaxWidth().padding(start = 44.dp, end = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.wiz_grey_text), style = MaterialTheme.typography.bodyMedium, color = Palette.Dim)
                PillButton(stringResource(R.string.wiz_app_info), appInfo, small = true)
            }
        }
    }
}

/** The mockup's step 6: Cómo revisa as an option list, Cuidado as Ajustes's segmented control. Writes settings directly (no door). */
@Composable
private fun PresetStep(container: AppContainer) {
    val settings by container.settings.state.collectAsState()
    val canShoot = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE // D35
    PreferenceGroup(Modifier.selectableGroup(), title = stringResource(R.string.set_how)) {
        ParentPreset.entries.forEach { p ->
            val (icon, name, help) = when (p) {
                ParentPreset.LIGHT -> Triple(R.drawable.ic_sliders, R.string.preset_light, R.string.how_help_light)
                ParentPreset.BALANCED -> Triple(R.drawable.ic_shield, R.string.preset_balanced, R.string.wiz_how_balanced)
                ParentPreset.THOROUGH -> Triple(R.drawable.ic_layers, R.string.preset_thorough, R.string.how_help_thorough)
                // "beta" only where it can run; below API 34 the reason instead, as in the mockup.
                ParentPreset.PRECISE -> Triple(R.drawable.ic_eye, R.string.preset_precise, if (canShoot) R.string.how_help_precise else R.string.wiz_how_precise_disabled)
            }
            val enabled = p != ParentPreset.PRECISE || canShoot
            val selected = settings.preset == p
            item {
                PrefRow(
                    stringResource(name),
                    Modifier.alpha(if (enabled) 1f else .5f).selectable(selected, enabled = enabled, role = Role.RadioButton) { container.settings.update { it.copy(preset = p) } },
                    description = stringResource(help),
                    icon = icon, iconTint = if (selected) MaterialTheme.colorScheme.primary else Palette.Dim,
                    end = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (p == ParentPreset.BALANCED) Tag(stringResource(R.string.group_recommended), TagKind.Recommended)
                            SIcon(R.drawable.ic_check, MaterialTheme.colorScheme.primary, modifier = Modifier.alpha(if (selected) 1f else 0f))
                        }
                    },
                )
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.set_care), Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelMedium, color = Palette.Dim)
        CareControl(settings.sensitivity, container::pickSensitivity)
    }
}
