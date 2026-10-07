package dev.sophiel.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** "Remove animations" sets the animator scale to 0. */
fun isReducedMotion(animatorScale: Float) = animatorScale == 0f

/** Read on every composition (the setting is cached in-process), so it follows a change without a restart. */
@Composable
fun reducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return isReducedMotion(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
}

/** D44: the thumb, sheet, collapse, cross-fades and the wrong-PIN shake jump to their end state. */
@Composable
fun <T> motion(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> = if (reducedMotion()) snap() else spec

/** The mockup's --ease and --drawer curves. */
val Ease = CubicBezierEasing(.23f, 1f, .32f, 1f)
val Drawer = CubicBezierEasing(.32f, .72f, 0f, 1f)
