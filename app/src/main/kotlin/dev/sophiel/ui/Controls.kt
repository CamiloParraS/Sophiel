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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sophiel.ui.theme.Ease
import dev.sophiel.ui.theme.Palette
import dev.sophiel.ui.theme.motion

/** 46 x 26 round switch. Pass [onChange] to make it tappable itself; SwitchRow passes null and toggles from the whole row. */
@Composable
fun RoundSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val track by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else Palette.Fg.copy(alpha = .15f),
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

enum class PillStyle { Default, Suggested, Destructive }

/** Pill button. While [busy] it shows a spinner and ignores taps. [large] is a screen's one main action (Start, Stop). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Default,
    busy: Boolean = false,
    enabled: Boolean = true,
    small: Boolean = false,
    large: Boolean = false,
    @androidx.annotation.DrawableRes icon: Int? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    val (bg, fg) = when (style) {
        PillStyle.Default -> Palette.Btn to Palette.Fg
        PillStyle.Suggested -> primary to MaterialTheme.colorScheme.onPrimary
        PillStyle.Destructive -> Palette.tint(Palette.Red, .12f) to Palette.RedFg
    }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, motion(tween(120, easing = Ease)), label = "press")
    Row(
        modifier
            .scale(scale)
            .heightIn(min = if (small) 32.dp else if (large) 56.dp else 40.dp)
            .clip(CircleShape)
            .background(bg.copy(alpha = bg.alpha * (if (!enabled) .45f else if (busy) .8f else 1f)))
            .clickable(source, indication = null, enabled = enabled && !busy, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 14.dp else if (large) 24.dp else 20.dp),
        horizontalArrangement = Arrangement.spacedBy(if (large) 10.dp else 8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val glyph = if (large) 20.dp else 16.dp
        if (busy) CircularProgressIndicator(Modifier.size(glyph), color = fg, trackColor = fg.copy(alpha = .4f), strokeWidth = 2.dp)
        if (icon != null && !busy) SIcon(icon, fg, glyph)
        Text(
            text, style = MaterialTheme.typography.labelLarge.let { if (large) it.copy(fontSize = 17.sp, lineHeight = 22.sp) else it },
            color = fg.copy(alpha = if (enabled) 1f else .45f), textAlign = TextAlign.Center,
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
        BoxWithConstraints(Modifier.fillMaxWidth().clip(CircleShape).background(Palette.Btn).padding(3.dp).selectableGroup()) {
            val thumbWidth = (maxWidth - 2.dp * (options.size - 1)) / options.size
            val pos by animateFloatAsState(selected.toFloat(), motion(tween(280, easing = Ease)), label = "thumb")
            Box(
                Modifier
                    .matchParentSize()
                    .padding(start = (thumbWidth + 2.dp) * pos, end = (maxWidth - thumbWidth - (thumbWidth + 2.dp) * pos).coerceAtLeast(0.dp))
                    .shadow(1.dp, CircleShape)
                    .background(Color.White, CircleShape),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEachIndexed { i, label ->
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 32.dp)
                            .clip(CircleShape)
                            .selectable(i == selected, enabled = i !in disabled, role = Role.RadioButton, onClick = { onSelect(i) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label, Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelLarge,
                            color = (if (i == selected) Palette.Fg else Palette.Dim).copy(alpha = if (i in disabled) .45f else 1f), textAlign = TextAlign.Center,
                            maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize),
                        )
                    }
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
