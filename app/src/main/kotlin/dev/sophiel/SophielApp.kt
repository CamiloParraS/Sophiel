package dev.sophiel

import android.app.Application

class SophielApp : Application() {
    val container by lazy { AppContainer() }
}
