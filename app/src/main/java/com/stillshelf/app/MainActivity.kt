package com.stillshelf.app

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.stillshelf.app.ui.StillShelfApp
import com.stillshelf.app.ui.StartupViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val startupViewModel: StartupViewModel by viewModels()
    @Volatile
    private var keepSplashOnScreen: Boolean = true
    private var startupUpdateDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            startupViewModel.isReady.collect { isReady ->
                keepSplashOnScreen = !isReady
            }
        }
        lifecycleScope.launch {
            startupViewModel.startupUpdatePrompt.collect { release ->
                if (release == null) {
                    startupUpdateDialog?.dismiss()
                    startupUpdateDialog = null
                    return@collect
                }
                if (startupUpdateDialog?.isShowing == true) {
                    return@collect
                }
                startupUpdateDialog = AlertDialog.Builder(this@MainActivity)
                    .setTitle("Update available")
                    .setMessage("StillShelf ${release.versionName} is available. Update now?")
                    .setPositiveButton("Update") { _, _ ->
                        startupViewModel.installStartupUpdate()
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        startupViewModel.dismissStartupUpdatePrompt()
                    }
                    .setOnDismissListener {
                        startupUpdateDialog = null
                    }
                    .show()
            }
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.parseColor("#F2F2F2"),
                darkScrim = Color.parseColor("#1F1F1F")
            )
        )

        setContent {
            StillShelfApp()
        }
    }
}
