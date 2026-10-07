package dev.sophiel.pin

/** "0:30", "1:48": whole seconds, rounded up so a countdown never shows 0:00 while still locked. */
fun formatCountdown(ms: Long): String {
    val s = (ms.coerceAtLeast(0) + 999) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

/** The door (D38): one function for Stop and Ajustes. Unlocked -> runs [action] and extends the window; locked -> false, the caller shows the sheet. */
fun Unlock.gate(action: () -> Unit): Boolean {
    if (!unlocked()) return false
    extend()
    action()
    return true
}

enum class CreateStep { ENTER, CONFIRM }

/** Create + confirm (D38). [result] is the PIN to save once both entries match. */
data class CreateState(val step: CreateStep = CreateStep.ENTER, val first: String = "", val mismatch: Boolean = false, val result: String? = null) {
    fun submit(pin: String) = when {
        step == CreateStep.ENTER -> CreateState(CreateStep.CONFIRM, pin)
        pin == first -> copy(mismatch = false, result = pin)
        else -> copy(mismatch = true)
    }
}
