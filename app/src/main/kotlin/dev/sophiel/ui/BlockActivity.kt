package dev.sophiel.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import dev.sophiel.R
import dev.sophiel.SophielApp
import dev.sophiel.settings.AppRule
import dev.sophiel.settings.appLabel
import dev.sophiel.ui.theme.Palette
import dev.sophiel.ui.theme.SophielTheme

/**
 * The guard's screen (D56): over a page that could turn Sophiel off, "Este ajuste está protegido" and the PIN pad.
 * A right PIN starts an Unlock that survives leaving the app and finishes, so Sophiel steps aside, back to the page.
 * Cancel and Back go Home. Its own task (`taskAffinity=""`, single instance), so finishing returns to the page.
 * D58's block screen, over Home: the blocked app's icon and label, "El Padre bloqueó esta app", Ir al inicio, no PIN.
 */
class BlockActivity : ComponentActivity() {
    private var blocked by mutableStateOf<String?>(null) // the blocked app's package; null: the guard

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // a recreation (rotation) must read this one, not the first
        blocked = intent.getStringExtra(EXTRA_APP) // a single instance: a later guard or block lands here
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val container = (application as SophielApp).container
        blocked = intent.getStringExtra(EXTRA_APP)
        setContent {
            SophielTheme {
                BackHandler(onBack = ::goHome)
                Column(
                    Modifier.fillMaxSize().background(Palette.Win).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                ) {
                    val app = blocked
                    if (app != null) {
                        AppIcon(app, 56.dp)
                        Text(appLabel(packageManager, app), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        val blocks by container.escalation.blocks.collectAsState()
                        val now by tickingNow(System::currentTimeMillis)
                        // D58: a rule beats escalation; only an escalation block has a PIN.
                        val until = blocks[app]?.takeIf { container.appRules.rule(app) != AppRule.BLOCKED }
                        val minutes = until?.let { ((it - now).coerceAtLeast(0) + 59_999) / 60_000 }
                        val ended = until != null && until <= now
                        LaunchedEffect(ended) { if (ended) goHome() } // ended while showing
                        Text(
                            if (minutes != null) stringResource(R.string.block_escalation, minutes) else stringResource(R.string.block_rule),
                            style = MaterialTheme.typography.bodyLarge, color = Palette.Dim, textAlign = TextAlign.Center,
                        )
                        PillButton(stringResource(R.string.block_home), ::goHome, style = PillStyle.Suggested)
                        if (until != null) {
                            var pinOpen by remember { mutableStateOf(false) }
                            if (pinOpen) {
                                PinPrompt(
                                    container.pin, SystemClock::elapsedRealtime,
                                    onUnlocked = {
                                        container.escalation.end(app) // the count restarts too
                                        packageManager.getLaunchIntentForPackage(app)?.let(::startActivity) // back to the app, not the Home under this screen
                                        finish()
                                    },
                                    onCancel = { pinOpen = false },
                                )
                            } else {
                                PillButton(stringResource(R.string.block_pin), { pinOpen = true })
                            }
                        }
                    } else {
                        SIcon(R.drawable.ic_lock, Palette.Orange, 40.dp)
                        Text(stringResource(R.string.guard_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        PinPrompt(
                            container.pin, SystemClock::elapsedRealtime,
                            onUnlocked = {
                                container.unlock.extend(survivesLeaving = true)
                                finish()
                            },
                            onCancel = ::goHome,
                        )
                    }
                }
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        private const val EXTRA_APP = "app"

        fun guard(context: Context) =
            context.startActivity(Intent(context, BlockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

        /**
         * D58: [app] came to the front and is blocked: Home, then this screen over it, in one ordered start. A global
         * Home action is asynchronous and could land over the screen.
         */
        fun block(context: Context, app: String) = context.startActivities(
            arrayOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                Intent(context, BlockActivity::class.java).putExtra(EXTRA_APP, app).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ),
        )
    }
}
