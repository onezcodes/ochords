package com.onezcodes.ochords

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.onezcodes.ochords.app.LocalApp
import com.onezcodes.ochords.app.RootView
import com.onezcodes.ochords.design.OChordsTheme
import androidx.compose.runtime.CompositionLocalProvider

class MainActivity : ComponentActivity() {
    private val app get() = (application as OChordsApplication).app

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        app.attach(this)
        intent?.data?.let(app::handleUrl)
        setContent {
            CompositionLocalProvider(LocalApp provides app) {
                OChordsTheme(appearance = app.appearance) {
                    RootView()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let(app::handleUrl)
    }

    override fun onDestroy() {
        app.detach(this)
        super.onDestroy()
    }
}
