package dev.sophiel.ui

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sophiel.R
import dev.sophiel.core.Sensitivity
import dev.sophiel.log.OffReason
import dev.sophiel.settings.ParentPreset
import dev.sophiel.ui.theme.Ease
import dev.sophiel.ui.theme.Palette
import dev.sophiel.ui.theme.motion
import java.util.Date

/** What the Status screen can do; the activity owns the Android side (intents, permission request, controller). */
class StatusActions(
    val start: () -> Unit,
    val stop: () -> Unit,
    val fix: (Need) -> Unit,
    val openSettings: () -> Unit,
    val seeLog: (() -> Unit)?, // null until the Log screen exists (ticket 18): the row shows disabled
)

@DrawableRes
private fun Need.icon() = when (this) {
    Need.OVERLAY -> R.drawable.ic_layers
    Need.SERVICE -> R.drawable.ic_access
    Need.NOTIFICATIONS -> R.drawable.ic_bell
}

@StringRes
private fun Need.title() = when (this) {
    Need.OVERLAY -> R.string.need_overlay
    Need.SERVICE -> R.string.need_service
    Need.NOTIFICATIONS -> R.string.need_notifications
}

@StringRes
private fun Need.okTag() = when (this) {
    Need.OVERLAY -> R.string.tag_allowed
    Need.SERVICE -> R.string.tag_working
    Need.NOTIFICATIONS -> R.string.tag_enabled
}

@StringRes
private fun Need.fix() = if (this == Need.SERVICE) R.string.fix_enable else R.string.fix_allow

@StringRes
private fun reasonTitle(reason: String) = when (reason) {
    OffReason.SCREEN_OFF -> R.string.stopped_screen_off
    OffReason.RESTARTED -> R.string.stopped_restarted
    OffReason.APP_CLOSED -> R.string.stopped_app_closed
    else -> R.string.stopped_system
}

@StringRes
private fun reasonText(reason: String) = when (reason) {
    OffReason.SCREEN_OFF -> R.string.stopped_screen_off_text
    OffReason.RESTARTED -> R.string.stopped_restarted_text
    OffReason.APP_CLOSED -> R.string.stopped_app_closed_text
    else -> R.string.stopped_system_text
}

/**
 * Status (D42, D44, D45): draws a [StatusModel]. Shows only what is wrong; nothing animates when it
 * opens, only state changes (fixing a row, starting, stopping) cross-fade. [debug] is the debug chips,
 * below the fold until ticket 19 moves them.
 */
@Composable
fun StatusScreen(model: StatusModel, door: Door, actions: StatusActions, modifier: Modifier = Modifier, debug: (@Composable ColumnScope.() -> Unit)? = null) {
    val context = LocalContext.current
    val time = remember { DateFormat.getTimeFormat(context) }
    val stoppedReason = model.stopped?.fields?.firstOrNull().orEmpty()
    Column(modifier.fillMaxSize()) {
        HeaderBar(stringResource(R.string.app_name), end = { GearButton(door, actions.openSettings) })
        UnlockedBanner(door)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Hero(model, model.stopped?.let { time.format(Date(it.at)) }.orEmpty())
            when (model.page) {
                Page.PROTECTED -> {
                    Summary(model)
                    Health(model, actions.fix)
                }
                Page.OFF, Page.READY -> {
                    Summary(model)
                    PreferenceGroup(title = stringResource(R.string.group_required)) {
                        StatusModel.REQUIRED.forEach { n -> item { NeedRow(n, n !in model.missing, actions.fix) } }
                    }
                    PreferenceGroup(title = stringResource(R.string.group_recommended)) {
                        StatusModel.RECOMMENDED.forEach { n -> item { NeedRow(n, n !in model.missing, actions.fix) } }
                    }
                }
                Page.STARTING -> PreferenceGroup(title = stringResource(R.string.status_what_will_happen)) {
                    item {
                        PrefRow(
                            stringResource(R.string.status_capture_permission), description = stringResource(R.string.status_capture_permission_text),
                            icon = R.drawable.ic_layers,
                        )
                    }
                }
                Page.STOPPED -> {
                    PreferenceGroup(title = stringResource(R.string.status_what_happened)) {
                        item {
                            PrefRow(
                                stringResource(reasonTitle(stoppedReason)), description = stringResource(reasonText(stoppedReason)), icon = R.drawable.ic_power,
                                end = { Text(time.format(Date(model.stopped!!.at)), style = MaterialTheme.typography.bodyMedium, color = Palette.Dim) },
                            )
                        }
                        item {
                            PrefRow(
                                stringResource(R.string.status_see_log), Modifier.alpha(if (actions.seeLog == null) .5f else 1f), icon = R.drawable.ic_list,
                                onClick = actions.seeLog, end = { SIcon(R.drawable.ic_chev) },
                            )
                        }
                    }
                    if (model.missing.isNotEmpty()) PreferenceGroup(title = stringResource(R.string.status_to_restart)) {
                        model.missing.forEach { n -> item { NeedRow(n, false, actions.fix, forcedStop = stoppedReason == OffReason.APP_CLOSED) } }
                    }
                }
            }
            if (debug != null) debug()
        }
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val buttonWidth = Modifier.widthIn(min = 200.dp)
            when (model.page) {
                Page.PROTECTED -> {
                    // D38: Stop goes through the door; the lock says so while it is shut.
                    PillButton(
                        stringResource(R.string.action_stop), actions.stop, buttonWidth, style = PillStyle.Destructive, busy = model.stopBusy,
                        icon = if (door.unlocked) R.drawable.ic_stop else R.drawable.ic_lock,
                    )
                    Note(R.string.door_footnote)
                }
                Page.STARTING -> {
                    PillButton(stringResource(R.string.status_starting), {}, buttonWidth, style = PillStyle.Suggested, busy = true)
                    Note(R.string.status_starting_note)
                }
                else -> {
                    PillButton(stringResource(R.string.action_start), actions.start, buttonWidth, style = PillStyle.Suggested, enabled = model.startEnabled, icon = R.drawable.ic_play)
                    when (model.note) {
                        StartNote.TURN_ON_OVERLAY -> Note(R.string.note_overlay)
                        StartNote.PARTIAL_MASK -> Note(R.string.note_partial)
                        null -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun Note(@StringRes id: Int) =
    Text(stringResource(id), style = MaterialTheme.typography.bodySmall, color = Palette.Dim, textAlign = TextAlign.Center)

@Composable
private fun Hero(model: StatusModel, stoppedAt: String) {
    val blocking = StatusModel.REQUIRED.count { it in model.missing }
    when (model.page) {
        Page.PROTECTED -> StatusPage(R.drawable.ic_shield_ok, stringResource(R.string.status_protected), stringResource(R.string.status_protected_text), Tone.Ok)
        Page.OFF -> StatusPage(R.drawable.ic_shield_off, stringResource(R.string.status_idle), pluralStringResource(R.plurals.status_missing, blocking, blocking), Tone.Off)
        Page.READY -> StatusPage(
            R.drawable.ic_shield, stringResource(R.string.status_ready),
            stringResource(if (Need.SERVICE in model.missing) R.string.status_ready_partial else R.string.status_ready_text), Tone.Ready,
        )
        Page.STARTING -> StatusPage(R.drawable.ic_shield, stringResource(R.string.status_starting), stringResource(R.string.status_starting_text), Tone.Ready, spinner = true)
        Page.STOPPED -> StatusPage(R.drawable.ic_shield_off, stringResource(R.string.status_stopped), stringResource(R.string.status_stopped_text, stoppedAt), Tone.Warn)
    }
}

/** Revisión and Cuidado in one row; a Precise fallback says so, with its reason under the row. */
@Composable
private fun Summary(model: StatusModel) {
    val review = stringResource(
        when {
            model.fallback != null -> R.string.status_precise_using
            model.preset == ParentPreset.LIGHT -> R.string.preset_light
            model.preset == ParentPreset.BALANCED -> R.string.preset_balanced
            else -> R.string.preset_precise
        },
    )
    val care = stringResource(
        when (model.sensitivity) {
            Sensitivity.STRICT -> R.string.care_strict
            Sensitivity.NORMAL -> R.string.care_normal
            Sensitivity.RELAXED -> R.string.care_relaxed
        },
    )
    val why = when (model.fallback) {
        Fallback.ACCESSIBILITY_OFF -> stringResource(R.string.fallback_accessibility)
        Fallback.ANDROID_BELOW_14 -> stringResource(R.string.fallback_android)
        null -> null
    }
    PreferenceGroup(footnote = why) {
        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Cell(stringResource(R.string.status_review), review, Modifier.weight(1f))
                Box(Modifier.fillMaxHeight().width(1.dp).background(Palette.Border))
                Cell(stringResource(R.string.status_care), care, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Cell(label: String, value: String, modifier: Modifier) = Column(modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Dim)
    Text(value, style = MaterialTheme.typography.titleMedium)
}

/** Protected: failing items are rows with their fix; the rest fold into one "Todo en orden" row. */
@Composable
private fun Health(model: StatusModel, fix: (Need) -> Unit) {
    val failing = Need.entries.filter { it in model.missing }
    val healthy = Need.entries - failing.toSet()
    if (failing.isNotEmpty()) PreferenceGroup { failing.forEach { n -> item { NeedRow(n, false, fix) } } }
    if (healthy.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 90f else 0f, motion(tween(240, easing = Ease)), label = "chevron")
    PreferenceGroup {
        item {
            Column {
                PrefRow(
                    stringResource(R.string.health_ok), description = stringResource(R.string.health_ok_text), icon = R.drawable.ic_shield_ok, iconTint = Palette.GreenFg,
                    onClick = { open = !open }, end = { SIcon(R.drawable.ic_chev, modifier = Modifier.rotate(turn)) },
                )
                AnimatedVisibility(open, enter = expandVertically(motion(tween(240, easing = Ease))), exit = shrinkVertically(motion(tween(240, easing = Ease)))) {
                    Column {
                        healthy.forEach { n ->
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Border))
                            NeedRow(n, true, fix)
                        }
                    }
                }
            }
        }
    }
}

/** One check: green with its tag when fine, orange with a fix button when not. Fixing cross-fades the row. */
@Composable
private fun NeedRow(need: Need, ok: Boolean, fix: (Need) -> Unit, forcedStop: Boolean = false) {
    Crossfade(ok, animationSpec = motion(tween(160)), label = "need") { good ->
        PrefRow(
            stringResource(need.title()),
            description = if (good) null else stringResource(
                when {
                    need == Need.OVERLAY -> R.string.need_overlay_missing
                    need == Need.SERVICE && forcedStop -> R.string.need_service_forced
                    need == Need.SERVICE -> R.string.need_service_missing
                    else -> R.string.need_notifications_missing
                },
            ),
            icon = need.icon(),
            iconTint = if (good) Palette.GreenFg else Palette.Orange,
            end = {
                if (good) Tag(stringResource(need.okTag()), TagKind.Ok)
                else PillButton(stringResource(need.fix()), { fix(need) }, small = true)
            },
        )
    }
}
