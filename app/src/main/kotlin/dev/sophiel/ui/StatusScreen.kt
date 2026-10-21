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
import androidx.compose.foundation.layout.statusBarsPadding
import dev.sophiel.ui.theme.lift
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Spacer
import dev.sophiel.R
import dev.sophiel.remote.Remote
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
    val openApps: () -> Unit,
    val openReview: () -> Unit,
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
 * Status (D42, D44, D75): the state card with its tile wall, a bento of three tiles (Historial, Apps, Revisión), whatever is wrong as rows,
 * and one full-width action. Nothing animates when it opens, only state changes (fixing a row, starting, stopping) cross-fade.
 * [blockedApps]: how many apps carry the Bloqueada rule (D58).
 */
@Composable
fun StatusScreen(
    model: StatusModel, door: Door, actions: StatusActions, modifier: Modifier = Modifier, blocks: Map<String, Long> = emptyMap(), week: LogModel? = null,
    blockedApps: Int = 0,
) {
    val context = LocalContext.current
    val time = remember { DateFormat.getTimeFormat(context) }
    val stoppedReason = model.stopped?.fields?.firstOrNull().orEmpty()
    Column(modifier.fillMaxSize().background(Palette.Chalk)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).statusBarsPadding().cutoutPadding().pageWidth()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), Modifier.weight(1f).asHeading(), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-.5).sp), color = Palette.Moss)
                UnlockedChip(door)
                GearButton(door, actions.openSettings, disc = true)
            }
            Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Hero(model, (model.gap?.at ?: model.stopped?.at)?.let { time.format(Date(it)) }.orEmpty(), week?.days?.lastOrNull()?.masked ?: 0)
                Bento(model, week, blockedApps, actions, door)
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
                    Remote.StatusRecommended() // D82: nothing in offline
                    when (model.page) {
                        Page.PROTECTED, Page.READY -> {
                            FallbackNote(model)
                            // Only what needs fixing gets a card; all clear is the hero's to say.
                            Health(model, actions.fix, failingOnly = true)
                            // D70: unlocked, the Parent also sees what they may act on (time without protection, protected settings opened).
                            if (door.unlocked && week?.week?.look == true) Look(week, actions.seeLog)
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
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().drawBehind {
                val fade = 28.dp.toPx() // the content fades into the bar instead of ending on a line
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Palette.Chalk), startY = -fade, endY = 0f), Offset(0f, -fade), Size(size.width, fade))
            }.background(Palette.Chalk).navigationBarsPadding().cutoutPadding().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The screen's one action: full width up to 360 dp, so Start, Starting and Stop share one slot.
            val buttonWidth = Modifier.widthIn(max = 360.dp).fillMaxWidth()
            Remote.StatusLine() // D83: nothing in offline
            when (model.page) {
                Page.PROTECTED -> {
                    Note(R.string.door_footnote, icon = R.drawable.ic_lock)
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
private fun Note(@StringRes id: Int, @DrawableRes icon: Int? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) SIcon(icon, Palette.Dim, 14.dp)
        Text(stringResource(id), style = MaterialTheme.typography.bodySmall, color = Palette.Dim, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Hero(model: StatusModel, since: String, covered: Int) {
    val blocking = StatusModel.REQUIRED.count { it in model.missing }
    // Ready with a recommended check off: the card says so instead of looking all clear.
    val caution = model.page == Page.READY && StatusModel.RECOMMENDED.any { it in model.missing }
    val (title, text) = when (model.page) {
        Page.PROTECTED -> stringResource(R.string.status_protected) to stringResource(R.string.status_ready_promise)
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
    // D75: no state chip. Protected names how the screen is read (D54) in one quiet line; the other states' sublines carry the time.
    val a11y = model.mode == CaptureMode.ACCESSIBILITY
    GridHero(
        model.page, title, text, caution = caution, covered = covered,
        modeIcon = if (a11y) R.drawable.ic_access else R.drawable.ic_phone,
        mode = if (model.page == Page.PROTECTED) stringResource(if (a11y) R.string.status_mode_a11y_short else R.string.status_mode_share_short) else null,
    )
}

/** D75: the shape of the Apps tile's badge. Flip to false for the rounded square (the mockup's "Tile"). */
private const val APPS_BADGE_COOKIE = true

/** One bento tile: white, 24 dp, a title with a chevron on top. Tapping it goes through the door (the Historial and Apps are the Parent's). */
@Composable
private fun BentoTile(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, fill: Color = Palette.Surface, ink: Color = Palette.Moss, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.lift(shape).clip(shape).background(fill).clickable(role = Role.Button, onClick = onClick).padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).asHeading(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = ink.copy(alpha = .62f).let { if (fill == Palette.Surface) it else ink })
            SIcon(R.drawable.ic_chev, ink.copy(alpha = .62f).let { if (fill == Palette.Surface) it else ink }, 16.dp)
        }
        content()
    }
}

/**
 * D75: Historial (the week's count, today's bars; green only while protected), Apps (how many are blocked), Revisión (where the
 * preset sits between Ahorro and Reforzado). Off states turn the count tile white: green only ever means protected.
 */
@Composable
private fun Bento(model: StatusModel, week: LogModel?, blockedApps: Int, actions: StatusActions, door: Door) {
    val days = week?.days.orEmpty()
    val total = days.sumOf { it.masked }
    val today = days.lastOrNull()?.masked ?: 0
    val most = (days.maxOfOrNull { it.masked } ?: 0).coerceAtLeast(1)
    val lit = model.page == Page.PROTECTED
    val fill = if (lit) Palette.GreenFg else Palette.Surface
    val ink = if (lit) Color.White else Palette.Moss
    val locale = appLocale()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BentoTile(stringResource(R.string.set_history), actions.seeLog, Modifier.weight(1.12f).fillMaxHeight(), fill, ink) {
            val said = stringResource(R.string.proof_week, total)
            CappedFontScale(1.3f) {
                Text(
                    total.toString(), Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp, lineHeight = 66.sp, letterSpacing = (-3.6).sp, fontFeatureSettings = "tnum"), color = ink,
                )
            }
            Text(stringResource(R.string.log_hero_text), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleSmall.copy(lineHeight = 18.sp), color = ink)
            Text(stringResource(R.string.status_today_count, today), style = MaterialTheme.typography.titleSmall.copy(lineHeight = 18.sp), color = ink)
            Spacer(Modifier.weight(1f).heightIn(min = 10.dp))
            // An empty day is a flat 5 dp stub, not a gap that reads as "loading".
            Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = said }, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                days.forEach { d ->
                    val bar = when {
                        d.masked == 0 -> ink.copy(alpha = if (lit) .25f else .15f)
                        d.today || !lit -> ink
                        else -> ink.copy(alpha = .7f)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.fillMaxWidth().height((5 + 22 * d.masked / most).dp).clip(RoundedCornerShape(5.dp)).background(bar))
                        Text(d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale), style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (d.today) FontWeight.Bold else FontWeight.Medium), color = ink)
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BentoTile(stringResource(R.string.set_apps), actions.openApps, Modifier.weight(1f).fillMaxWidth()) {
                Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(if (APPS_BADGE_COOKIE) CookieShape(8) else RoundedCornerShape(14.dp)).background(Palette.Moss),
                        contentAlignment = Alignment.Center,
                    ) { Text(blockedApps.toString(), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"), color = Color.White) }
                    Text(pluralStringResource(R.plurals.status_apps_blocked, blockedApps), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Palette.Moss)
                }
            }
            BentoTile(stringResource(R.string.status_review), actions.openReview, Modifier.weight(1f).fillMaxWidth()) {
                Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
                Text(stringResource(model.preset.label()), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Palette.Moss)
                // Ahorro, Equilibrado, Reforzado: three steps; Detallado is a different mechanism, so no step is lit.
                val step = when (model.preset) { ParentPreset.LIGHT -> 0; ParentPreset.BALANCED -> 1; ParentPreset.THOROUGH -> 2; ParentPreset.PRECISE -> -1 }
                Row(Modifier.padding(top = 8.dp).clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) { Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (it == step) Palette.Moss else Palette.Moss.copy(alpha = .12f))) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 5.dp).clearAndSetSemantics { }, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.preset_light), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = Palette.Moss.copy(alpha = .62f))
                    Text(stringResource(R.string.preset_thorough), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = Palette.Moss.copy(alpha = .62f))
                }
            }
        }
    }
}

@StringRes
private fun ParentPreset.label() = when (this) {
    ParentPreset.LIGHT -> R.string.preset_light
    ParentPreset.BALANCED -> R.string.preset_balanced
    ParentPreset.THOROUGH -> R.string.preset_thorough
    ParentPreset.PRECISE -> R.string.preset_precise
}

/** Unlocked only: the week's time without protection and protected settings opened, one row into the Historial. */
@Composable
private fun Look(week: LogModel, seeLog: () -> Unit) {
    val counts = week.week
    PreferenceGroup {
        item {
            PrefRow(
                stringResource(R.string.log_verdict_look),
                description = listOfNotNull(
                    if (counts.unprotected > 0) stringResource(R.string.log_cause_gap, LogModel.length(counts.unprotectedSeconds)) else null,
                    if (counts.intentos > 0) pluralStringResource(R.plurals.log_cause_attempts, counts.intentos, counts.intentos) else null,
                ).joinToString(" · "),
                icon = if (counts.unprotected > 0) R.drawable.ic_shield_off else R.drawable.ic_lock, iconTint = if (counts.unprotected > 0) Palette.RedFg else Palette.OrangeFg,
                onClick = seeLog, end = { SIcon(R.drawable.ic_chev) },
            )
        }
    }
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
