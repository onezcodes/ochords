package com.onezcodes.ochords

import android.app.Application
import com.onezcodes.ochords.app.AppState

class OChordsApplication : Application() {
    /// Global app state: session, preferences, navigation. One per process, like the iOS `AppState`.
    lateinit var app: AppState
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        app = AppState(this)
    }

    companion object {
        lateinit var instance: OChordsApplication
            private set
    }
}
