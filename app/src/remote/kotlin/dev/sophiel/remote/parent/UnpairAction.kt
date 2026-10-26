package dev.sophiel.remote.parent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.sophiel.R
import dev.sophiel.ui.ConfirmDialog
import dev.sophiel.ui.SButton
import kotlinx.coroutines.launch

/** "Desvincular" for one Child phone, behind a confirm (D79): DELETE /pairings/:id, then the entry and its key go. */
@Composable
internal fun UnpairButton(name: String, id: String, home: HomeCards) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirm by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    SButton(stringResource(R.string.pair_unpair), { confirm = true }, busy = busy)
    if (confirm) ConfirmDialog(
        title = stringResource(R.string.par_unpair_title, name), text = stringResource(R.string.par_unpair_text),
        confirmLabel = stringResource(R.string.pair_unpair), cancelLabel = stringResource(R.string.pin_cancel),
        onConfirm = {
            confirm = false
            if (!busy) { busy = true; scope.launch { if (!home.unpair(id)) Toast.makeText(context, R.string.par_out_failed, Toast.LENGTH_LONG).show(); busy = false } }
        },
        onDismiss = { confirm = false },
    )
}
