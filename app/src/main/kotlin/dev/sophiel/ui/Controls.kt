package dev.sophiel.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sophiel.ui.theme.ButtonBig
import dev.sophiel.ui.theme.Ease
import dev.sophiel.ui.theme.Palette
import dev.sophiel.ui.theme.motion

/** 46 x 26 round switch. Pass [onChange] to make it tappable itself; SwitchRow passes null and toggles from the whole row. */
@Composable
fun RoundSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val track by animateColorAsState(
        if (checked) Palette.GreenFg else Palette.Fg.copy(alpha = .15f),
        motion(tween(200)),
        label = "track",
    )
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, motion(tween(200, easing = Ease)), label = "thumb")
    Box(
        modifier
            .size(46.dp, 26.dp)
            .clip(CircleShape)
            .background(track)
            .let { if (onChange != null) it.toggleable(checked, role = Role.Switch, onValueChange = onChange) else it },
    ) {
        Box(Modifier.offset(x, 2.dp).size(22.dp).shadow(2.dp, CircleShape).background(Color.White, CircleShape))
    }
}

/** Pill styles are the quiet ones; Go/Stop are Status's 56 dp bottom action, Fix a failing row's small tinted button, OnDark an icon on the hero. */
enum class BtnStyle { Default, Suggested, Destructive, Go, Stop, Fix, OnDark }

/**
 * The one button. With [text] it is a labelled button; with only [icon] and [label] it is a 48 dp icon button.
 * While [busy] it shows a spinner and ignores taps. [large] is a screen's one main action (Start, Stop).
 */
@Composable
fun SButton(
    text: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BtnStyle = BtnStyle.Default,
    busy: Boolean = false,
    enabled: Boolean = true,
    small: Boolean = false,
    large: Boolean = false,
    @androidx.annotation.DrawableRes icon: Int? = null,
    label: String? = null,
    tint: Color = Color.Unspecified,
    fill: Color = Color.Unspecified,
) {
    val primary = MaterialTheme.colorScheme.primary
    val moss = Palette.Moss
    val dark = LocalOnDark.current
    val iconOnly = text == null
    var (bg, fg) = when {
        style == BtnStyle.Go || style == BtnStyle.Stop -> when {
            busy -> Palette.MossBusy to Palette.OnMoss
            !enabled -> moss.copy(alpha = .08f) to Palette.MossOff
            style == BtnStyle.Stop -> moss.copy(alpha = .10f) to moss
            else -> moss to Palette.OnMoss
        }
        style == BtnStyle.Suggested -> primary to MaterialTheme.colorScheme.onPrimary
        style == BtnStyle.Destructive -> Palette.tint(Palette.Red, .12f) to Palette.RedFg
        style == BtnStyle.Fix -> Palette.tint(Palette.Orange, .15f) to Palette.OrangeFg
        style == BtnStyle.OnDark || (iconOnly && dark) -> Palette.DarkFill to Palette.DarkInk
        iconOnly -> Color.Transparent to Palette.Fg
        else -> Palette.Btn to Palette.Fg
    }
    if (tint != Color.Unspecified) fg = tint
    if (fill != Color.Unspecified) bg = fill
    val main = style == BtnStyle.Go || style == BtnStyle.Stop
    val shape = when {
        main || (iconOnly && bg != Color.Transparent) -> RoundedCornerShape(12.dp)
        iconOnly -> RoundedCornerShape(10.dp)
        style == BtnStyle.Fix -> RoundedCornerShape(8.dp)
        else -> RoundedCornerShape(12.dp) // D74: one shape for every text button, the main ones' (pills read as another app)
    }
    val big = large || main
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled && !busy) .97f else 1f, motion(tween(if (main) 100 else 120, easing = Ease)), label = "press")
    val height = when {
        iconOnly -> if (bg == Color.Transparent) 48.dp else 40.dp
        big -> 56.dp
        small -> 32.dp
        else -> 40.dp
    }
    Row(
        modifier
            .scale(scale)
            .minimumInteractiveComponentSize() // 48 dp to hit, whatever the button draws
            .clickable(source, indication = null, enabled = enabled && !busy, role = Role.Button, onClick = onClick)
            .then(if (iconOnly) Modifier.size(height).semantics { contentDescription = label.orEmpty() } else Modifier.heightIn(min = height))
            .clip(shape)
            .background(bg.copy(alpha = bg.alpha * (if (!enabled && !main) .45f else if (busy && !main) .8f else 1f)))
            .let { if (style == BtnStyle.Stop && enabled && !busy) it.border(1.dp, moss.copy(alpha = .2f), shape) else it }
            .let { if (style == BtnStyle.Fix && enabled && !busy) it.border(1.dp, Palette.OrangeFg.copy(alpha = .55f), shape) else it } // outlined, so it reads as a button on the pale tint
            .focusRing(source, shape)
            .padding(horizontal = if (iconOnly || main) 0.dp else if (style == BtnStyle.Fix) 14.dp else if (small) 14.dp else if (large) 24.dp else 20.dp),
        horizontalArrangement = Arrangement.spacedBy(if (big) 10.dp else 8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val glyph = if (main) 22.dp else if (large) 20.dp else 16.dp
        if (busy) CircularProgressIndicator(Modifier.size(glyph), color = fg, trackColor = fg.copy(alpha = .4f), strokeWidth = 2.dp)
        if (icon != null && !busy) SIcon(icon, fg, if (iconOnly) 18.dp else glyph)
        if (text != null) Text(
            text,
            style = when {
                main || large -> ButtonBig
                style == BtnStyle.Fix -> MaterialTheme.typography.titleSmall
                else -> MaterialTheme.typography.labelLarge
            },
            color = fg.copy(alpha = if (enabled || main) 1f else .45f), textAlign = TextAlign.Center,
        )
    }
}

/**
 * Segmented control with a sliding thumb. [help], if given, is one line per option, shown under the
 * control and cross-faded with the choice. Options in [disabled] are dimmed and can't be picked.
 */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, help: List<String>? = null, disabled: Set<Int> = emptySet()) {
    Column(modifier) {
        // A word that can't fit its third would break mid-word ("Protecci / ón"): then the options stack, one per row.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val style = MaterialTheme.typography.labelLarge
            val measurer = rememberTextMeasurer()
            val room = with(LocalDensity.current) { (maxWidth / options.size - 16.dp).roundToPx() }
            val fits = remember(options, room, style) { options.all { o -> o.split(' ').all { measurer.measure(it, style).size.width <= room } } }
            if (fits) {
            // No outer padding: the segments fill the control, so each is a 48 dp target. The thumb is inset 3 dp inside its slot.
            BoxWithConstraints(Modifier.fillMaxWidth().clip(CircleShape).background(Palette.Btn).selectableGroup()) {
                val slot = maxWidth / options.size
                val pos by animateFloatAsState(selected.toFloat(), motion(tween(220, easing = Ease)), label = "thumb")
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(start = slot * pos + 3.dp, end = (maxWidth - slot * (pos + 1)).coerceAtLeast(0.dp) + 3.dp, top = 3.dp, bottom = 3.dp)
                        .shadow(1.dp, CircleShape)
                        .background(Palette.Surface, CircleShape),
                )
                // Labels wrap rather than shrink, so the control grows with the font scale; equal-height segments.
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    options.forEachIndexed { i, label ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 48.dp)
                                .clip(CircleShape)
                                .selectable(i == selected, enabled = i !in disabled, role = Role.RadioButton, onClick = { onSelect(i) }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge,
                                color = (if (i == selected) Palette.Fg else Palette.Dim).copy(alpha = if (i in disabled) Palette.DISABLED else 1f), textAlign = TextAlign.Center,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            } else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Btn).padding(3.dp).selectableGroup()) {
                options.forEachIndexed { i, label ->
                    Text(
                        label,
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (i == selected) Palette.Surface else Color.Transparent)
                            .selectable(i == selected, enabled = i !in disabled, role = Role.RadioButton, onClick = { onSelect(i) })
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        style = style,
                        color = (if (i == selected) Palette.Fg else Palette.Dim).copy(alpha = if (i in disabled) Palette.DISABLED else 1f),
                    )
                }
            }
        }
        if (help != null) {
            Crossfade(help[selected], animationSpec = motion(tween(100)), label = "help") {
                Text(it, Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.Dim, minLines = 2)
            }
        }
    }
}
