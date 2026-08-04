package com.daygle.aicamera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.daygle.aicamera.ui.DaygleNavHost
import com.daygle.aicamera.ui.theme.DaygleTheme
import com.daygle.aicamera.vpn.TunnelManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tunnelManager: TunnelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DaygleTheme {
                DaygleNavHost()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Raise the tunnel when the app is foregrounded (no-op unless VPN-only
        // mode is on and consent has already been granted).
        lifecycleScope.launch { tunnelManager.ensureUp() }
    }
}
