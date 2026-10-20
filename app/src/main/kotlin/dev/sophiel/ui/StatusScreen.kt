package dev.sophiel.ui

import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.material3.minimumInteractiveComponentSize
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
        Hero(model, (model.gap?.at ?: model.stopped?.at)?.let { time.format(Date(it)) }.orEmpty(), if (sheet > 0.dp) viewport - sheet else null) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { UnlockedChip(door); GearButton(door, actions.openSettings, onDark = true) }
        }
        // The sheet rides 20 dp over the hero: one boundary, no hard seam.
        Column(
            // layout, not offset: the 20 dp it rides over the hero leave no 20 dp gap at the bottom.
            Modifier.fillMaxWidth().onSizeChanged { sheet = with(density) { it.height.toDp() } }.layout { m, c ->
                val p = m.measure(c)
                val lip = 20.dp.roundToPx()
                layout(p.width, p.height - lip) { p.place(0, -lip) }
            }.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Palette.Chalk)
                .cutoutPadding().pageWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
                    // One Revisión card: what needs fixing on top, the rest folded under it. The week's proof follows.
                    Health(model, actions.fix)
                    merged = true
                    // D70: unlocked, the Parent gets the week's figures and apps; locked, only the proof anyone may see.
                    Crossfade(week.takeIf { door.unlocked }, animationSpec = motion(tween(200, easing = Ease)), label = "glance") { w ->
                        if (w != null) Glance(w, actions.seeLog) else Proof(week, actions.seeLog)
                    }
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
            }.background(Palette.Chalk).navigationBarsPadding().cutoutPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The screen's one action: full width up to 360 dp, so Start, Starting and Stop share one slot.
            val buttonWidth = Modifier.widthIn(max = 360.dp).fillMaxWidth()
            when (model.page) {
                Page.PROTECTED -> {
                    Note(R.string.door_footnote)
                    // D38: Stop goes through the door; the lock says so while it is shut.
                    SButton(
                        stringResource(R.string.action_stop), actions.stop, buttonWidth, BtnStyle.Stop, busy = model.stopBusy,
                        icon = if (door.unlocked) R.drawable.ic_stop else R.drawable.ic_lock,
                    )
                }
                Page.STARTING -> {
                    Note(R.string.status_starting_note)
                    SButton(stringResource(R.string.status_starting), {}, buttonWidth, BtnStyle.Go, busy = true)
                }
                else -> {
                    if (model.page == Page.READY) Note(presetLine(model), dim = true)
                    when (model.note) {
                        StartNote.TURN_ON_OVERLAY -> Note(R.string.note_overlay)
                        StartNote.PARTIAL_MASK, null -> Unit // the Full mask row above says it once
                    }
                    SButton(stringResource(R.string.action_start), actions.start, buttonWidth, BtnStyle.Go, enabled = model.startEnabled, icon = R.drawable.ic_play)
                }
            }
        }
    }
}

@Composable
private fun Note(@StringRes id: Int) = Note(stringResource(id))

@Composable
private fun Note(text: String, dim: Boolean = false) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = if (dim) Palette.Dim else Palette.Fg, textAlign = TextAlign.Center)

@Composable
private fun Hero(model: StatusModel, since: String, fit: Dp?, gear: @Composable () -> Unit) {
    val blocking = StatusModel.REQUIRED.count { it in model.missing }
    // Ready with a recommended check off: the hero says so instead of looking all clear.
    val caution = model.page == Page.READY && StatusModel.RECOMMENDED.any { it in model.missing }
    val (title, text) = when (model.page) {
        Page.PROTECTED -> stringResource(R.string.status_protected) to
            stringResource(if (model.mode == CaptureMode.ACCESSIBILITY) R.string.status_mode_a11y else R.string.status_mode_share)
        Page.OFF -> stringResource(R.string.status_idle) to pluralStringResource(R.plurals.status_missing, blocking, blocking)
        Page.READY -> stringResource(R.string.status_ready) to stringResource(
            when {
                Need.SERVICE in model.missing -> R.string.status_ready_caution_service
                caution -> R.string.status_ready_caution_other
                else -> R.string.status_ready_promise
            },
        )
        Page.STARTING -> stringResource(R.string.status_starting) to stringResource(R.string.status_starting_text)
        Page.STOPPED -> stringResource(R.string.status_stopped) to stringResource(R.string.status_stopped_text, since)
        Page.INTERRUPTED -> stringResource(R.string.status_interrupted) to stringResource(R.string.status_interrupted_text, since)
    }
    GridHero(model.page, title, text, fit = fit, caution = caution, gear = gear)
}

/** Revisión and Cuánto tapa, labelled ("Revisión Reforzado · Cuánto tapa Normal"), the note above Start; a Precise fallback says so. */
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
    return stringResource(R.string.status_preset_line, review, care)
}

/** Why a Precise preset fell back, under nothing else; silent otherwise. */
@Composable
private fun FallbackNote(model: StatusModel) {
    val why = when (model.fallback) {
        Fallback.ANDROID_BELOW_14 -> R.string.fallback_android
        Fallback.ACCESSIBILITY_OFF, null -> return // the Full mask row says why
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
    val precise = model.fallback == Fallback.ACCESSIBILITY_OFF
    if (titled) {
        listOf(StatusModel.REQUIRED to R.string.group_required, StatusModel.RECOMMENDED to R.string.group_recommended).forEach { (needs, title) ->
            val missing = needs.filter { it in model.missing }
            if (missing.isNotEmpty()) PreferenceGroup(title = stringResource(title)) { missing.forEach { n -> item { NeedRow(n, false, fix, precise = precise) } } }
        }
        if (!failingOnly && failing.size < Need.entries.size) PreferenceGroup { healthy(model, fix) }
    } else if (failing.isNotEmpty() || (!failingOnly && failing.size < Need.entries.size)) PreferenceGroup {
        failing.forEach { n -> item { NeedRow(n, false, fix, precise = precise) } }
        if (!failingOnly) healthy(model, fix)
    }
}

/** The "Todo en orden" row, folding every check that passes; opens to list them. Adds nothing when none pass. */
private fun GroupScope.healthy(model: StatusModel, fix: (Need) -> Unit) {
    val healthy = Need.entries.filter { it !in model.missing }
    if (healthy.isEmpty()) return
    item {
        var open by rememberSaveable { mutableStateOf(false) }
        val turn by animateFloatAsState(if (open) 90f else 0f, motion(tween(240, easing = Ease)), label = "chevron")
        Column {
            val all = healthy.size == Need.entries.size // some failing: don't say "all fine" above them
            PrefRow(
                if (all) stringResource(R.string.health_ok) else stringResource(R.string.health_rest),
                description = if (all) stringResource(R.string.health_ok_text) else healthy.map { stringResource(it.title()) }.joinToString(" · "),
                icon = R.drawable.ic_shield_ok, iconTint = Palette.GreenFg,
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
private fun NeedRow(need: Need, ok: Boolean, fix: (Need) -> Unit, forcedStop: Boolean = false, precise: Boolean = false) {
    Crossfade(ok, animationSpec = motion(tween(160)), label = "need") { good ->
        PrefRow(
            stringResource(need.title()),
            description = if (good) null else stringResource(
                when {
                    need == Need.OVERLAY -> R.string.need_overlay_missing
                    need == Need.SERVICE && forcedStop -> R.string.need_service_forced
                    need == Need.SERVICE && precise -> R.string.need_service_precise
                    need == Need.SERVICE -> R.string.need_service_missing
                    else -> R.string.need_notifications_missing
                },
            ),
            icon = need.icon(),
            iconTint = if (good) Palette.GreenFg else Palette.Orange,
            end = {
                if (good) Tag(stringResource(need.okTag()), TagKind.Ok)
                else SButton(stringResource(need.fix()), { fix(need) }, style = BtnStyle.Fix)
            },
        )
    }
}

/**
 * What the app did today: the count, the last cover, a bar per day of the week, and the way into the Historial.
 */
@Composable
private fun Proof(week: LogModel?, seeLog: () -> Unit) {
    val days = week?.days.orEmpty()
    val today = days.lastOrNull()
    val last = today?.rows?.firstOrNull { it is LogRow.Masked }?.at
    PreferenceGroup {
        // Nothing covered all week: no zero and no flat bars, just the way into the Historial.
        if (days.none { it.masked > 0 }) {
            item { PrefRow(stringResource(R.string.proof_see), description = stringResource(R.string.proof_none), icon = R.drawable.ic_list, onClick = seeLog, end = { SIcon(R.drawable.ic_chev) }) }
            return@PreferenceGroup
        }
        item {
            // The number stays under the hero headline (38 sp): one headline per screen.
            // From 1.3x font scale the week of bars drops under the count instead of squeezing the text.
            val stacked = LocalDensity.current.fontScale >= 1.3f
            val most = (days.maxOfOrNull { it.masked } ?: 0).coerceAtLeast(1)
            // A week of bars with their day letters; an empty day is a flat 2 dp line, not a dot that reads as "loading".
            val bars: @Composable () -> Unit = {
                val week = stringResource(R.string.proof_week, days.sumOf { it.masked })
                Row(Modifier.clearAndSetSemantics { contentDescription = week }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    days.forEach { d ->
                        val ink = if (d.today && d.masked > 0) Palette.GreenFg else if (d.masked > 0) Palette.Dim.copy(alpha = .5f) else Palette.Quiet // a day with covers reads as data, not as the empty dash
                        Column(Modifier.widthIn(min = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.height(40.dp), contentAlignment = Alignment.BottomCenter) {
                                Box(Modifier.width(8.dp).height(if (d.masked == 0) 2.dp else (8 + 32 * d.masked / most).dp).clip(RoundedCornerShape(4.dp)).background(ink))
                            }
                            Text(
                                d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.labelMedium, color = if (d.today) Palette.GreenFg else Palette.Dim,
                            )
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { // the row below is the one way in
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    CappedFontScale(1.3f) { Text((today?.masked ?: 0).toString(), style = MaterialTheme.typography.displayMedium, color = Palette.Fg) }
                    Column(Modifier.weight(1f).padding(bottom = 2.dp)) {
                        Text(stringResource(R.string.proof_today), style = MaterialTheme.typography.titleMedium)
                        // The week's total is the number the bars can't give; the last cover says it is recent.
                        val total = days.sumOf { it.masked }
                        Text(
                            if (last == null) stringResource(R.string.proof_week_only, total)
                            else stringResource(R.string.proof_week_last, total, DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)),
                            style = MaterialTheme.typography.bodyMedium, color = Palette.Dim,
                        )
                    }
                    if (!stacked) bars()
                }
                if (stacked) bars()
            }
        }
        item { PrefRow(stringResource(R.string.proof_see), icon = R.drawable.ic_list, onClick = seeLog, end = { SIcon(R.drawable.ic_chev) }) }
    }
}

/** D70: the week at a glance, behind the door: four figures, the apps with the most covered (their real icons), the way into the Historial. */
@Composable
private fun Glance(week: LogModel, seeLog: () -> Unit) {
    val counts = week.week
    val pm = LocalContext.current.packageManager
    PreferenceGroup(title = stringResource(R.string.glance_title)) {
        item {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlanceTile((week.days.lastOrNull()?.masked ?: 0).toString(), stringResource(R.string.glance_today), R.drawable.ic_shield_ok, Palette.GreenFg)
                    GlanceTile(week.weekMasked.toString(), stringResource(R.string.glance_week), R.drawable.ic_list, Palette.Fg)
                }
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlanceTile(
                        if (counts.unprotectedSeconds == 0) "0" else LogModel.length(counts.unprotectedSeconds), stringResource(R.string.log_stat_gap),
                        R.drawable.ic_shield_off, if (counts.unprotected > 0) Palette.RedFg else Palette.Dim,
                    )
                    GlanceTile(counts.intentos.toString(), stringResource(R.string.log_stat_attempts), R.drawable.ic_lock, if (counts.intentos > 0) Palette.OrangeFg else Palette.Dim)
                }
            }
        }
        week.covered.take(3).forEach { c ->
            item {
                Row(
                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(c.app, 32.dp)
                    Text(remember(c.app) { appLabel(pm, c.app) }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(c.count.toString(), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
                }
            }
        }
        item { PrefRow(stringResource(R.string.proof_see), icon = R.drawable.ic_list, onClick = seeLog, end = { SIcon(R.drawable.ic_chev) }) }
    }
}

/** One figure: big number, its icon and label under it. [tint] marks a figure that needs a look. */
@Composable
private fun RowScope.GlanceTile(value: String, label: String, @DrawableRes icon: Int, tint: Color) {
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.weight(1f).fillMaxHeight().clip(shape).background(Palette.Chalk).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SIcon(icon, tint, 20.dp)
        CappedFontScale(1.3f) { Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), color = if (tint == Palette.Dim) Palette.Fg else tint, maxLines = 1) }
        Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.Dim, maxLines = 2)
    }
}
