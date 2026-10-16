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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.heightIn
import android.text.format.DateUtils
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sophiel.R
import dev.sophiel.settings.appLabel
import dev.sophiel.capture.CaptureMode
import dev.sophiel.core.Sensitivity
import dev.sophiel.log.GapReason
import dev.sophiel.log.OffReason
import dev.sophiel.settings.ParentPreset
import dev.sophiel.ui.theme.Ease
import dev.sophiel.ui.theme.Palette
import dev.sophiel.ui.theme.motion
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/** What the Status screen can do; the activity owns the Android side (intents, permission request, controller). */
class StatusActions(
    val start: () -> Unit,
    val stop: () -> Unit,
    val fix: (Need) -> Unit,
    val openSettings: () -> Unit,
    val seeLog: () -> Unit,
)

@DrawableRes
internal fun Need.icon() = when (this) {
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
internal fun Need.okTag() = when (this) {
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

@StringRes
private fun gapTitle(reason: String) = when (reason) {
    GapReason.APP_CLOSED -> R.string.stopped_app_closed
    GapReason.RESTARTED -> R.string.stopped_restarted
    else -> R.string.gap_accessibility
}

@StringRes
private fun gapText(reason: String) = when (reason) {
    GapReason.APP_CLOSED -> R.string.gap_app_closed_text
    GapReason.RESTARTED -> R.string.stopped_restarted_text
    else -> R.string.gap_accessibility_text
}

/**
 * Status (D42, D44, D45): draws a [StatusModel]. Shows only what is wrong; nothing animates when it
 * opens, only state changes (fixing a row, starting, stopping) cross-fade.
 */
@Composable
fun StatusScreen(model: StatusModel, door: Door, actions: StatusActions, modifier: Modifier = Modifier, blocks: Map<String, Long> = emptyMap(), week: LogModel? = null) {
    val context = LocalContext.current
    val time = remember { DateFormat.getTimeFormat(context) }
    val stoppedReason = model.stopped?.fields?.firstOrNull().orEmpty()
    Column(modifier.fillMaxSize().background(Palette.Chalk)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val viewport = maxHeight
        var sheet by remember { mutableStateOf(0.dp) }
        val density = LocalDensity.current
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Hero(model, (model.gap?.at ?: model.stopped?.at)?.let { time.format(Date(it)) }.orEmpty(), if (sheet > 0.dp) viewport - sheet else null) { GearButton(door, actions.openSettings, onDark = true) }
        // The sheet rides 20 dp over the hero: one boundary, no hard seam.
        Column(
            // layout, not offset: the 20 dp it rides over the hero leave no 20 dp gap at the bottom.
            Modifier.fillMaxWidth().onSizeChanged { sheet = with(density) { it.height.toDp() } }.layout { m, c ->
                val p = m.measure(c)
                val lip = 20.dp.roundToPx()
                layout(p.width, p.height - lip) { p.place(0, -lip) }
            }.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Palette.Chalk)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UnlockedBanner(door)
            androidx.compose.runtime.CompositionLocalProvider(LocalRowMin provides 48.dp) {
            val now by tickingNow(System::currentTimeMillis, active = blocks.isNotEmpty())
            val active = blocks.filterValues { it > now }
            // D59: one orange line per escalation block, counting down.
            if (active.isNotEmpty()) PreferenceGroup {
                active.forEach { (pkg, until) ->
                    item {
                        PrefRow(
                            stringResource(R.string.status_app_blocked, appLabel(context.packageManager, pkg), (until - now + 59_999) / 60_000),
                            icon = R.drawable.ic_block, iconTint = Palette.Orange,
                        )
                    }
                }
            }
            // On every page the Historial is one tap away; actions.seeLog goes through the door. It joins the health card when there is one.
            val logRow: GroupScope.() -> Unit = {
                item { PrefRow(stringResource(R.string.status_see_log), icon = R.drawable.ic_list, onClick = actions.seeLog, end = { SIcon(R.drawable.ic_chev) }) }
            }
            var merged = false
            when (model.page) {
                Page.PROTECTED, Page.READY -> {
                    FallbackNote(model)
                    Health(model, actions.fix, titled = model.page == Page.READY, failingOnly = true)
                    merged = true
                    Proof(week, actions.seeLog) { healthy(model, actions.fix) }
                }
                Page.OFF -> Health(model, actions.fix, titled = true)
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
                    }
                    if (model.missing.isNotEmpty()) PreferenceGroup(title = stringResource(R.string.status_to_restart)) {
                        model.missing.forEach { n -> item { NeedRow(n, false, actions.fix, forcedStop = stoppedReason == OffReason.APP_CLOSED) } }
                    }
                }
                Page.INTERRUPTED -> { // D54: protection is still on; Start brings capture back
                    val gap = model.gap!!
                    PreferenceGroup(title = stringResource(R.string.status_what_happened)) {
                        item {
                            PrefRow(
                                stringResource(gapTitle(gap.reason)), description = stringResource(gapText(gap.reason)), icon = R.drawable.ic_warn, iconTint = Palette.RedFg,
                                end = { Text(time.format(Date(gap.at)), style = MaterialTheme.typography.bodyMedium, color = Palette.Dim) },
                            )
                        }
                    }
                    if (model.missing.isNotEmpty()) PreferenceGroup(title = stringResource(R.string.status_to_restart)) {
                        model.missing.forEach { n -> item { NeedRow(n, false, actions.fix, forcedStop = gap.reason == GapReason.APP_CLOSED) } }
                    }
                }
            }
            if (!merged) PreferenceGroup(content = logRow)
            }
        }
        }
        }
        Column(
            Modifier.fillMaxWidth().drawBehind {
                val fade = 28.dp.toPx() // the content fades into the bar instead of ending on a line
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Palette.Chalk), startY = -fade, endY = 0f), Offset(0f, -fade), Size(size.width, fade))
            }.background(Palette.Chalk).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The screen's one action: full width up to 360 dp, so Start, Starting and Stop share one slot.
            val buttonWidth = Modifier.widthIn(max = 360.dp).fillMaxWidth()
            when (model.page) {
                Page.PROTECTED -> {
                    Note(R.string.door_footnote)
                    // D38: Stop goes through the door; the lock says so while it is shut.
                    MainButton(
                        stringResource(R.string.action_stop), actions.stop, buttonWidth, MainKind.Stop, busy = model.stopBusy,
                        icon = if (door.unlocked) R.drawable.ic_stop else R.drawable.ic_lock,
                    )
                }
                Page.STARTING -> {
                    Note(R.string.status_starting_note)
                    MainButton(stringResource(R.string.status_starting), {}, buttonWidth, MainKind.Go, busy = true)
                }
                else -> {
                    when (model.note) {
                        StartNote.TURN_ON_OVERLAY -> Note(R.string.note_overlay)
                        StartNote.PARTIAL_MASK -> Note(R.string.note_partial)
                        null -> Unit
                    }
                    MainButton(stringResource(R.string.action_start), actions.start, buttonWidth, MainKind.Go, enabled = model.startEnabled, icon = R.drawable.ic_play)
                }
            }
        }
    }
}

private enum class MainKind { Go, Stop }

/** The bottom action, 56 dp: filled moss to start, tonal to stop (filled is reserved for Iniciar), flat when it can't be used. */
@Composable
private fun MainButton(text: String, onClick: () -> Unit, modifier: Modifier, kind: MainKind, enabled: Boolean = true, busy: Boolean = false, @DrawableRes icon: Int? = null) {
    val moss = Color(0xFF12201A)
    val (bg, fg) = when {
        busy -> Color(0xFF3A4A41) to Color(0xFFE8F5E4)
        !enabled -> moss.copy(alpha = .08f) to Color(0xFF4D5C54)
        kind == MainKind.Stop -> moss.copy(alpha = .10f) to moss
        else -> moss to Color(0xFFE8F5E4)
    }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled && !busy) .97f else 1f, motion(tween(100, easing = Ease)), label = "press")
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.scale(scale).height(56.dp).clip(shape).background(bg)
            .let { if (kind == MainKind.Stop && enabled && !busy) it.border(1.dp, moss.copy(alpha = .2f), shape) else it }
            .clickable(source, indication = null, enabled = enabled && !busy, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = fg, trackColor = fg.copy(alpha = .4f), strokeWidth = 2.dp)
        else if (icon != null) SIcon(icon, fg, 22.dp)
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, lineHeight = 22.sp), color = fg)
    }
}

@Composable
private fun Note(@StringRes id: Int) =
    Text(stringResource(id), style = MaterialTheme.typography.bodyMedium, color = Palette.Fg, textAlign = TextAlign.Center)

@Composable
private fun Hero(model: StatusModel, since: String, fit: Dp?, gear: @Composable () -> Unit) {
    val blocking = StatusModel.REQUIRED.count { it in model.missing }
    val (title, text) = when (model.page) {
        Page.PROTECTED -> stringResource(R.string.status_protected) to
            stringResource(if (model.mode == CaptureMode.ACCESSIBILITY) R.string.status_mode_a11y else R.string.status_mode_share)
        Page.OFF -> stringResource(R.string.status_idle) to pluralStringResource(R.plurals.status_missing, blocking, blocking)
        Page.READY -> stringResource(R.string.status_ready) to
            if (Need.SERVICE in model.missing) stringResource(R.string.status_ready_partial) else presetLine(model)
        Page.STARTING -> stringResource(R.string.status_starting) to stringResource(R.string.status_starting_text)
        Page.STOPPED -> stringResource(R.string.status_stopped) to stringResource(R.string.status_stopped_text, since)
        Page.INTERRUPTED -> stringResource(R.string.status_interrupted) to stringResource(R.string.status_interrupted_text, since)
    }
    GridHero(model.page, title, text, fit = fit, gear = gear)
}

/** Revisión and Cuidado in one line ("Reforzado · Normal"), the Ready subtitle; a Precise fallback says so. */
@Composable
private fun presetLine(model: StatusModel): String {
    val review = stringResource(
        when {
            model.fallback != null -> R.string.status_precise_using
            model.preset == ParentPreset.LIGHT -> R.string.preset_light
            model.preset == ParentPreset.BALANCED -> R.string.preset_balanced
            model.preset == ParentPreset.THOROUGH -> R.string.preset_thorough
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
    return "$review · $care"
}

/** Why a Precise preset fell back, under nothing else; silent otherwise. */
@Composable
private fun FallbackNote(model: StatusModel) {
    val why = when (model.fallback) {
        Fallback.ACCESSIBILITY_OFF -> R.string.fallback_accessibility
        Fallback.ANDROID_BELOW_14 -> R.string.fallback_android
        null -> return
    }
    Text(stringResource(why), Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Dim)
}

/**
 * D44: failing items are rows with their fix; the rest fold into one "Todo en orden" row. Before Start
 * ([titled]) the failing ones sit under Requerido / Recomendado, since only Requerido blocks Start.
 * With [failingOnly] the "Todo en orden" row is left to the caller (Protected and Ready put it in the proof card).
 */
@Composable
private fun Health(model: StatusModel, fix: (Need) -> Unit, titled: Boolean = false, failingOnly: Boolean = false) {
    val failing = Need.entries.filter { it in model.missing }
    if (titled) {
        listOf(StatusModel.REQUIRED to R.string.group_required, StatusModel.RECOMMENDED to R.string.group_recommended).forEach { (needs, title) ->
            val missing = needs.filter { it in model.missing }
            if (missing.isNotEmpty()) PreferenceGroup(title = stringResource(title)) { missing.forEach { n -> item { NeedRow(n, false, fix) } } }
        }
    } else if (failing.isNotEmpty()) PreferenceGroup { failing.forEach { n -> item { NeedRow(n, false, fix) } } }
    if (!failingOnly && failing.size < Need.entries.size) PreferenceGroup { healthy(model, fix) }
}

/** The "Todo en orden" row, folding every check that passes; opens to list them. Adds nothing when none pass. */
private fun GroupScope.healthy(model: StatusModel, fix: (Need) -> Unit) {
    val healthy = Need.entries.filter { it !in model.missing }
    if (healthy.isEmpty()) return
    item {
        var open by rememberSaveable { mutableStateOf(false) }
        val turn by animateFloatAsState(if (open) 90f else 0f, motion(tween(240, easing = Ease)), label = "chevron")
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
                else FixButton(stringResource(need.fix())) { fix(need) }
            },
        )
    }
}

/** Mockup .sm: small moss button, 8 dp radius, for the fix on a failing row. */
@Composable
private fun FixButton(text: String, onClick: () -> Unit) = Box(
    Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF12201A)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
    contentAlignment = Alignment.Center,
) { Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), color = Color(0xFFE8F5E4)) }

/**
 * What the app did today: the count, the last cover, a bar per day of the week, and the way into the Historial.
 * [more] adds rows under it in the same card (the "Todo en orden" row), so the sheet is one card, not a form.
 */
@Composable
private fun Proof(week: LogModel?, seeLog: () -> Unit, more: GroupScope.() -> Unit = {}) {
    val days = week?.days.orEmpty()
    val today = days.lastOrNull()
    val last = today?.rows?.firstOrNull { it is LogRow.Masked }?.at
    PreferenceGroup {
        item {
            // The number stays under the hero headline (34 sp): one headline per screen.
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = seeLog).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom,
            ) {
                Text((today?.masked ?: 0).toString(), style = MaterialTheme.typography.headlineMedium.copy(fontSize = 40.sp, lineHeight = 40.sp), color = Palette.Fg)
                Column(Modifier.weight(1f).padding(bottom = 2.dp)) {
                    Text(stringResource(R.string.proof_today), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (last == null) stringResource(R.string.proof_none)
                        else stringResource(R.string.proof_last, DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)),
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Dim,
                    )
                }
                // A week of bars with their day letters; an empty day is a flat 2 dp line, not a dot that reads as "loading".
                val most = (days.maxOfOrNull { it.masked } ?: 0).coerceAtLeast(1)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    days.forEach { d ->
                        val ink = if (d.today) Palette.GreenFg else Color(0x2912201A)
                        Column(Modifier.width(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.height(28.dp), contentAlignment = Alignment.BottomCenter) {
                                Box(Modifier.width(6.dp).height(if (d.masked == 0) 2.dp else (6 + 22 * d.masked / most).dp).clip(RoundedCornerShape(3.dp)).background(ink))
                            }
                            Text(
                                d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp), color = if (d.today) Palette.GreenFg else Palette.Dim,
                            )
                        }
                    }
                }
            }
        }
        item { PrefRow(stringResource(R.string.proof_see), icon = R.drawable.ic_list, onClick = seeLog, end = { SIcon(R.drawable.ic_chev) }) }
        more()
    }
}
