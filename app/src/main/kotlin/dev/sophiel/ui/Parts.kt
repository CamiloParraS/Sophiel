package dev.sophiel.ui

import android.app.Activity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sophiel.ui.theme.Palette
import dev.sophiel.ui.theme.lift
import dev.sophiel.ui.theme.motion

// Shared parts, cut from mockups/gnome2.html (D44). Controls are in Controls.kt, sheet and dialog in Overlays.kt.

/** Landscape: keep content out of a side camera cutout. Backgrounds stay full-bleed when this comes after them. */
@Composable
fun Modifier.cutoutPadding(): Modifier = windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))

/** Reading width on tablets and landscape: content centred, at most 640 dp. Fills first, so it centres even under a parent that measures loosely (a Column). Put it after the background so that stays full-bleed. */
fun Modifier.pageWidth(): Modifier = fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp).fillMaxWidth()

@Composable
fun SIcon(@DrawableRes id: Int, tint: Color = Palette.Dim, size: Dp = 18.dp, modifier: Modifier = Modifier) =
    Icon(painterResource(id), contentDescription = null, tint = tint, modifier = modifier.size(size))

/** Marks a title as a heading for TalkBack's heading navigation. */
fun Modifier.asHeading(): Modifier = semantics { heading() }

/** A visible keyboard/switch-access focus ring for controls drawn with `indication = null`. */
@Composable
fun Modifier.focusRing(source: InteractionSource, shape: Shape): Modifier {
    val focused by source.collectIsFocusedAsState()
    return if (focused) border(2.dp, Palette.GreenFg, shape) else this
}

/** True under [HeaderBar]: header buttons wear the glass-on-dark look Status and Log use. */
internal val LocalOnDark = androidx.compose.runtime.compositionLocalOf { false }

private val onDarkBars = java.util.concurrent.atomic.AtomicInteger()

/** Light status-bar icons while any dark header is composed (a counter: wizard steps cross-fade, so two can overlap). */
@Composable
internal fun LightStatusIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val bars = WindowCompat.getInsetsController((view.context as Activity).window, view)
        onDarkBars.incrementAndGet()
        bars.isAppearanceLightStatusBars = false
        onDispose { if (onDarkBars.decrementAndGet() == 0) bars.isAppearanceLightStatusBars = true }
    }
}

/**
 * The header of every screen but Status's own hero: the dark tile grid, back chip and title at the left (as Log),
 * optional subtitle, end slot (the unlocked chip on parent screens).
 */
@Composable
fun HeaderBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    start: @Composable () -> Unit = {},
    end: @Composable () -> Unit = {},
) {
    LightStatusIcons()
    Box(modifier.fillMaxWidth().clipToBounds().background(Moss)) {
        Canvas(Modifier.matchParentSize()) { drawGrid(Page.OFF, Palette.HeroOff, 0L, -100_000L, true, emptyList()) }
        androidx.compose.runtime.CompositionLocalProvider(LocalOnDark provides true) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().cutoutPadding().heightIn(min = 64.dp).padding(start = 6.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                start()
                Column(Modifier.weight(1f).padding(start = 10.dp, end = 8.dp)) {
                    Text(title, Modifier.asHeading(), style = MaterialTheme.typography.titleMedium, color = Text0)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Text1)
                }
                end()
            }
        }
    }
}

class GroupScope {
    internal val items = mutableListOf<@Composable () -> Unit>()
    fun item(content: @Composable () -> Unit) { items += content }
}

/** AdwPreferencesGroup: small title, boxed rows with 1 px dividers, optional footnote. */
@Composable
fun PreferenceGroup(modifier: Modifier = Modifier, title: String? = null, footnote: String? = null, content: GroupScope.() -> Unit) {
    val scope = GroupScope().apply(content)
    Column(modifier) {
        if (title != null) Text(title, Modifier.asHeading().padding(start = 4.dp, end = 4.dp, bottom = 6.dp), style = MaterialTheme.typography.labelMedium, color = Palette.Dim)
        val shape = RoundedCornerShape(12.dp)
        Column(Modifier.fillMaxWidth().lift(shape).clip(shape).background(Palette.Card)) {
            scope.items.forEachIndexed { i, row ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Border))
                row()
            }
        }
        if (footnote != null) Text(footnote, Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Dim)
    }
}

/** Minimum row height; Status packs its rows tighter. */
val LocalRowMin = androidx.compose.runtime.compositionLocalOf { 56.dp }

/** Prefix icon, title, description, end slot. With [onClick], tapping anywhere on the row runs it. */
@Composable
fun PrefRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    @DrawableRes icon: Int? = null,
    iconTint: Color = Palette.Dim,
    onClick: (() -> Unit)? = null,
    end: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .heightIn(min = LocalRowMin.current)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) SIcon(icon, iconTint)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = Palette.Dim)
        }
        if (end != null) end()
    }
}

/** A switch row toggles from anywhere on it. */
@Composable
fun SwitchRow(
    title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, description: String? = null,
    @DrawableRes icon: Int? = null, enabled: Boolean = true,
) =
    PrefRow(
        title,
        modifier.alpha(if (enabled) 1f else Palette.DISABLED).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        description,
        icon,
        end = { RoundSwitch(checked, onChange = null) },
    )

enum class TagKind { Neutral, Ok, Recommended, VerySafe, Safe, Doubtful }

@Composable
fun Tag(text: String, kind: TagKind = TagKind.Neutral, modifier: Modifier = Modifier) {
    val primary = Palette.GreenFg
    val (bg, fg) = when (kind) {
        TagKind.Neutral -> Palette.Btn to Palette.Dim
        TagKind.Ok -> Palette.tint(Palette.Green, .2f) to Palette.GreenFg
        TagKind.Recommended -> Palette.tint(primary) to primary
        TagKind.VerySafe -> Palette.tint(Palette.Red, .12f) to Palette.RedFg
        TagKind.Safe -> Palette.tint(Palette.Orange, .14f) to Palette.OrangeFg
        TagKind.Doubtful -> Palette.tint(primary, .14f) to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Text(text, modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall, color = fg)
}

enum class Tone { Ok, Off, Ready, Warn, Alert }

/** AdwStatusPage: big symbolic icon in a disc, title, one line. Colours and icon cross-fade when the state changes. */
@Composable
fun StatusPage(@DrawableRes icon: Int, title: String, text: String, tone: Tone, modifier: Modifier = Modifier, spinner: Boolean = false) {
    val primary = Palette.GreenFg
    val (bg, fg) = when (tone) {
        Tone.Ok -> Palette.tint(Palette.Green) to Palette.GreenFg
        Tone.Off -> Palette.Btn to Palette.Dim
        Tone.Ready -> Palette.tint(primary) to primary
        Tone.Warn -> Palette.tint(Palette.Orange, .15f) to Palette.Orange
        Tone.Alert -> Palette.tint(Palette.Red, .15f) to Palette.RedFg
    }
    val fade = motion<Color>(tween(200))
    val disc by animateColorAsState(bg, fade, label = "disc")
    val glyph by animateColorAsState(fg, fade, label = "glyph")
    Column(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(disc), contentAlignment = Alignment.Center) {
            if (spinner) CircularProgressIndicator(Modifier.size(40.dp), color = glyph, strokeWidth = 3.dp)
            else Crossfade(icon, animationSpec = motion(tween(200)), label = "icon") { SIcon(it, glyph, 48.dp) }
        }
        Crossfade(title to text, animationSpec = motion(tween(120)), label = "headline") { (t, d) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t, Modifier.asHeading(), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(d, Modifier.widthIn(max = 280.dp), style = MaterialTheme.typography.bodyLarge, color = Palette.Dim, textAlign = TextAlign.Center)
            }
        }
    }
}
