package dev.sophiel.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import androidx.activity.BackEventCompat
import dev.sophiel.ui.theme.reducedMotion
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
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
 * Setup wizard (D42, D44, D66), shown while no PIN exists: welcome -> PIN -> overlay -> accessibility -> keep awake ->
 * notifications -> preset -> start. Linear with Back; a step granted while on it shows done and moves on.
 * Needs no unlock. [fix] opens the same places Status's fix buttons do; [start] leaves the wizard and starts,
 * [finish] leaves it without starting.
 */
@Composable
fun SetupWizard(container: AppContainer, resumes: Int, fix: (Need) -> Unit, start: () -> Unit, finish: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var forward by rememberSaveable { mutableStateOf(true) }
    // Predictive Back: the step leans toward the swipe edge while the finger is down. Committing runs the usual slide;
    // the lean eases out under it. Cancelling eases it back.
    val lean = remember { Animatable(0f) }
    var edge by remember { mutableIntStateOf(1) }
    val reduce = reducedMotion()
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(step > 0) { events ->
        try {
            events.collect { if (!reduce) { edge = if (it.swipeEdge == BackEventCompat.EDGE_RIGHT) -1 else 1; lean.snapTo(it.progress) } }
            forward = false; step--
            scope.launch { lean.animateTo(0f, tween(240, easing = Ease)) }
        } catch (e: CancellationException) {
            scope.launch { lean.animateTo(0f, tween(200, easing = Ease)) }
        }
    }
    // Forward slides in from the right, Back from the left: a quarter width with a fade. Reduced motion jumps.
    val slide = motion<IntOffset>(tween(240, easing = Ease))
    val fade = motion<Float>(tween(160))
    Column(Modifier.fillMaxSize().background(Palette.Chalk)) {
        // The tiles stay put while the steps slide under them.
        AnimatedVisibility(
            step >= 1,
            enter = expandVertically(motion(tween(240, easing = Ease))) + fadeIn(motion(tween(160))),
            exit = shrinkVertically(motion(tween(240, easing = Ease))) + fadeOut(motion(tween(160))),
        ) { WizardTop(step) { forward = false; step-- } }
        AnimatedContent(
            step,
            Modifier.weight(1f).graphicsLayer {
                val k = 1f - .06f * lean.value
                scaleX = k; scaleY = k; translationX = edge * lean.value * size.width * .06f
            },
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(slide) { dir * it / 4 } + fadeIn(fade)) togetherWith (slideOutHorizontally(slide) { -dir * it / 4 } + fadeOut(fade))
            },
            label = "step",
        ) { s ->
            // Absolute, not step++: the outgoing step stays composed during the fade and its auto-advance may still fire.
            val next = { forward = true; step = s + 1 }
            if (s == 0) {
                Welcome(next)
                return@AnimatedContent
            }
            if (s == 1) {
                CreatePinScreen(onCreate = { container.pin.set(it); step = 2 }, title = stringResource(R.string.wiz_title), bare = true)
                return@AnimatedContent
            }
            val context = LocalContext.current
            val appInfo = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
            when (s) {
                2 -> {
                    val granted = remember(resumes) { Settings.canDrawOverlays(context) }
                    advanceWhenGranted(granted, forward, next)
                    StepFrame(VisualKind.OVERLAY, R.string.need_overlay, R.string.wiz_overlay_text, bottom = {
                        if (granted) PrimaryPill(R.string.pin_continue, next) else PrimaryPill(R.string.fix_allow, { fix(Need.OVERLAY) })
                    }) {
                        PreferenceGroup {
                            item { NeedStepRow(Need.OVERLAY, R.string.need_overlay, R.string.wiz_overlay_row, granted, todoAsTitle = true) }
                        }
                    }
                }
                3 -> {
                    val bound = MaskWindowService.bound.collectAsState().value != null // live: binds ~2 s after an update (D42)
                    advanceWhenGranted(bound, forward, next)
                    StepFrame(VisualKind.SERVICE, R.string.need_service, R.string.wiz_service_text, bottom = {
                        if (bound) PrimaryPill(R.string.pin_continue, next) else {
                            PrimaryPill(R.string.wiz_open_accessibility, { fix(Need.SERVICE) })
                            Skip(R.string.wiz_service_skip, next)
                        }
                    }) {
                        PreferenceGroup {
                            item { NeedStepRow(Need.SERVICE, R.string.wiz_service_row, R.string.wiz_service_row_text, bound) }
                            item {
                                val settings = container.settings.state.collectAsState().value
                                SwitchRow(
                                    stringResource(R.string.set_keep), settings.keepProtecting && bound,
                                    { v -> container.settings.update { it.copy(keepProtecting = v) } },
                                    description = stringResource(if (bound) R.string.wiz_keep_text else R.string.needs_full_mask),
                                    icon = R.drawable.ic_shield, enabled = bound,
                                )
                            }
                            item { PrefRow(stringResource(R.string.wiz_shortcut), description = stringResource(R.string.wiz_shortcut_text), icon = R.drawable.ic_warn) }
                            item { GreyedSwitchHelp(appInfo) }
                        }
                    }
                }
                4 -> StepFrame(VisualKind.AWAKE, R.string.wiz_awake_title, R.string.wiz_awake_text, bottom = {
                    SButton(stringResource(R.string.wiz_app_info), appInfo, Modifier.widthIn(max = 360.dp).fillMaxWidth())
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
                    StepFrame(VisualKind.NOTIFICATIONS, R.string.need_notifications, R.string.wiz_notif_text, bottom = {
                        if (granted) PrimaryPill(R.string.pin_continue, next) else {
                            PrimaryPill(R.string.fix_allow, { fix(Need.NOTIFICATIONS) })
                            Skip(R.string.need_notifications_missing, next)
                        }
                    }) {
                        PreferenceGroup {
                            item { NeedStepRow(Need.NOTIFICATIONS, R.string.need_notifications, R.string.wiz_notif_row, granted, todoAsTitle = true) }
                        }
                    }
                }
                6 -> StepFrame(
                    VisualKind.PRESET, R.string.wiz_preset_title, R.string.wiz_preset_text, bottom = { PrimaryPill(R.string.pin_continue, next) },
                    visual = { TileVisual(VisualKind.PRESET, height = 104.dp, preset = container.settings.state.collectAsState().value.preset) },
                ) {
                    PresetStep(container)
                }
                else -> StepFrame(VisualKind.START, R.string.wiz_start_title, R.string.wiz_start_text, bottom = {
                    SButton(stringResource(R.string.action_start), start, Modifier.widthIn(max = 360.dp).fillMaxWidth(), style = BtnStyle.Suggested, large = true, icon = R.drawable.ic_play)
                    SButton(stringResource(R.string.wiz_go_app), finish, Modifier.widthIn(max = 360.dp).fillMaxWidth())
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
}

/** Step 0: the Status hero, protected, with one button. No step count: it isn't a step. */
@Composable
private fun Welcome(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Palette.Chalk)) {
        // The hero takes what the button area leaves.
        BoxWithConstraints(Modifier.weight(1f)) {
            GridHero(Page.PROTECTED, stringResource(R.string.wiz_welcome_title), stringResource(R.string.wiz_welcome_text), fit = maxHeight)
        }
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().cutoutPadding().padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SButton(stringResource(R.string.wiz_welcome_go), onStart, Modifier.widthIn(max = 360.dp).fillMaxWidth(), style = BtnStyle.Suggested, large = true)
            Text(stringResource(R.string.wiz_welcome_note, STEPS), style = MaterialTheme.typography.bodySmall, color = Palette.Dim, textAlign = TextAlign.Center)
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
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(granted) {
        if (granted && armed) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm) // with the done tag's fade
            delay(600) // long enough to see the done tag
            next()
        }
    }
}

/** Back chip and a thin progress bar, filled in green up to the current step. */
@Composable
private fun WizardTop(step: Int, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 6.dp, end = 22.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        SButton(null, back, icon = R.drawable.ic_back, label = stringResource(R.string.back))
        val label = stringResource(R.string.wiz_step, step, STEPS)
        val fill by animateFloatAsState(step / STEPS.toFloat(), motion(tween(320, easing = Ease)), label = "progress")
        Box(
            Modifier.weight(1f).padding(start = 12.dp).height(4.dp).clip(CircleShape).background(Palette.Moss.copy(alpha = .1f))
                .semantics { contentDescription = label }
                .drawBehind { drawRect(Palette.Moss, size = Size(size.width * fill, size.height)) },
        )
    }
}

/**
 * A step: a short tile wall, title and line, what the step needs, then the buttons right under it.
 * A step too tall for the screen scrolls, with the buttons pinned at the bottom.
 */
@Composable
private fun StepFrame(
    kind: VisualKind, @StringRes title: Int, @StringRes text: Int, bottom: @Composable ColumnScope.() -> Unit,
    visual: @Composable () -> Unit = { TileVisual(kind, height = 104.dp) }, content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).cutoutPadding().pageWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            visual()
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), Modifier.asHeading(), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(stringResource(text), Modifier.widthIn(max = 300.dp), style = MaterialTheme.typography.bodyLarge, color = Palette.Dim, textAlign = TextAlign.Center)
            }
            content()
        }
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().cutoutPadding().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = bottom,
        )
    }
}

/** Every step's main button is the welcome's and the last step's: full width up to 360 dp, large. */
@Composable
private fun PrimaryPill(@StringRes text: Int, onClick: () -> Unit) =
    SButton(stringResource(text), onClick, Modifier.widthIn(max = 360.dp).fillMaxWidth(), style = BtnStyle.Suggested, large = true)

/** "Saltar", with what is lost under it. */
@Composable
private fun Skip(@StringRes lost: Int, onClick: () -> Unit) {
    Text(
        stringResource(R.string.wiz_skip),
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 13.dp),
        style = MaterialTheme.typography.labelLarge, color = Palette.Moss,
    )
    Text(stringResource(lost), style = MaterialTheme.typography.bodySmall, color = Palette.Dim, textAlign = TextAlign.Center)
}

/**
 * A permission row: what to do while missing, Status's done tag once granted. With [todoAsTitle] the step heading already says
 * [title], so while missing the row says only the to-do.
 */
@Composable
private fun NeedStepRow(need: Need, @StringRes title: Int, @StringRes todo: Int, granted: Boolean, todoAsTitle: Boolean = false) {
    Crossfade(granted, animationSpec = motion(tween(160)), label = "granted") { ok ->
        PrefRow(
            stringResource(if (todoAsTitle && !ok) todo else title),
            description = if (ok || todoAsTitle) null else stringResource(todo),
            icon = need.icon(), iconTint = if (ok) Palette.Moss else Palette.Dim,
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
                SButton(stringResource(R.string.wiz_app_info), appInfo, small = true)
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
            // Same names and lines as the Ajustes sheet, and no icons: there the sliders icon means Cuánto tapa.
            val (name, help) = when (p) {
                ParentPreset.LIGHT -> R.string.preset_light to R.string.how_help_light
                ParentPreset.BALANCED -> R.string.preset_balanced to R.string.how_help_balanced
                ParentPreset.THOROUGH -> R.string.preset_thorough to R.string.how_help_thorough
                ParentPreset.PRECISE -> R.string.preset_precise to if (canShoot) R.string.how_help_precise else R.string.needs_android14
            }
            val enabled = p != ParentPreset.PRECISE || canShoot
            val selected = settings.preset == p
            item {
                PrefRow(
                    stringResource(name),
                    Modifier.dimmed(enabled).selectable(selected, enabled = enabled, role = Role.RadioButton) { container.settings.update { it.copy(preset = p) } },
                    description = stringResource(help),
                    end = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (p == ParentPreset.BALANCED) Tag(stringResource(R.string.group_recommended), TagKind.Recommended)
                            SelectTick(selected, Palette.Moss)
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
