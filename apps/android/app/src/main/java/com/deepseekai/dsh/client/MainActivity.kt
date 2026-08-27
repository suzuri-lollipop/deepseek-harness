package com.deepseekai.dsh.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepseekai.dsh.client.core.ConnState
import com.deepseekai.dsh.client.core.DshClient
import com.deepseekai.dsh.client.core.Prefs
import com.deepseekai.dsh.client.ui.ChatScreen
import com.deepseekai.dsh.client.ui.ConnectScreen
import com.deepseekai.dsh.client.ui.DshTheme
import com.deepseekai.dsh.client.ui.DswBgBase
import com.deepseekai.dsh.client.ui.HeroScreen
import com.deepseekai.dsh.client.ui.SessionListScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Single-activity host. The connected root is the hero (web empty-session
 * composition); the session tree and the settings (connect) screen are
 * pushed from its rail, and a session opens the chat on top.
 */
class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = DshClient(scope)
    private val prefs by lazy { Prefs(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 35+ enforces edge-to-edge, so content draws under the
        // status bar; opt in explicitly, pick light bar icons for the dark UI,
        // and pad the content by the system-bar insets while the Surface
        // background stays full-bleed.
        enableEdgeToEdge()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            DshTheme {
                var openSessionId by rememberSaveable { mutableStateOf<String?>(null) }
                // Last session opened; survives going back so the list can tint it
                // and the hero can aim its new-session target at its workspace.
                var lastSessionId by rememberSaveable { mutableStateOf<String?>(null) }
                var showSettings by rememberSaveable { mutableStateOf(false) }
                var showSessions by rememberSaveable { mutableStateOf(false) }
                Surface(color = DswBgBase, modifier = Modifier.fillMaxSize()) {
                    // Content keeps the system-bar insets; the background stays
                    // edge-to-edge behind the status and navigation bars.
                    Box(modifier = Modifier.systemBarsPadding()) {
                        val state by client.state.collectAsStateWithLifecycle()
                        // System back unwinds the pushed screen toward the hero
                        // root instead of leaving the app from it.
                        BackHandler(
                            enabled = openSessionId != null || showSettings || showSessions,
                        ) {
                            when {
                                openSessionId != null -> openSessionId = null
                                showSettings -> showSettings = false
                                showSessions -> showSessions = false
                            }
                        }
                        when {
                            openSessionId != null -> ChatScreen(
                                client = client,
                                sessionId = openSessionId!!,
                                onBack = { openSessionId = null },
                            )

                            // Disconnected: the connect screen is the root again;
                            // the hero's pushed screens wait for Ready.
                            state !is ConnState.Ready -> ConnectScreen(
                                client = client,
                                initialUrl = prefs.serverUrl,
                                onConnect = { url ->
                                    prefs.serverUrl = url
                                    client.connect(url)
                                },
                                onDisconnect = { client.disconnect() },
                            )

                            showSettings -> ConnectScreen(
                                client = client,
                                initialUrl = prefs.serverUrl,
                                onConnect = { url ->
                                    prefs.serverUrl = url
                                    client.connect(url)
                                },
                                onDisconnect = {
                                    showSettings = false
                                    client.disconnect()
                                },
                                onBack = { showSettings = false },
                            )

                            showSessions -> SessionListScreen(
                                client = client,
                                lastSessionId = lastSessionId,
                                onOpenSession = {
                                    openSessionId = it
                                    lastSessionId = it
                                    showSessions = false
                                },
                                onDisconnect = {
                                    showSessions = false
                                    client.disconnect()
                                },
                                onBack = { showSessions = false },
                            )

                            else -> HeroScreen(
                                client = client,
                                lastSessionId = lastSessionId,
                                onOpenSession = {
                                    openSessionId = it
                                    lastSessionId = it
                                    showSessions = false
                                    showSettings = false
                                },
                                onOpenSessions = { showSessions = true },
                                onOpenSettings = { showSettings = true },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        client.disconnect()
        scope.cancel()
        super.onDestroy()
    }
}
