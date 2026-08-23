package com.deepseekai.dsh.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepseekai.dsh.client.core.ConnState
import com.deepseekai.dsh.client.core.DshClient
import com.deepseekai.dsh.client.core.Prefs
import com.deepseekai.dsh.client.ui.ChatScreen
import com.deepseekai.dsh.client.ui.ConnectScreen
import com.deepseekai.dsh.client.ui.SessionListScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Single-activity host; navigation between connect / list / chat is plain state. */
class MainActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = DshClient(scope)
    private val prefs by lazy { Prefs(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var openSessionId by rememberSaveable { mutableStateOf<String?>(null) }
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val state by client.state.collectAsStateWithLifecycle()
                    if (state is ConnState.Ready) {
                        if (openSessionId != null) {
                            ChatScreen(
                                client = client,
                                sessionId = openSessionId!!,
                                onBack = { openSessionId = null },
                            )
                        } else {
                            SessionListScreen(
                                client = client,
                                onOpenSession = { openSessionId = it },
                                onDisconnect = {
                                    openSessionId = null
                                    client.disconnect()
                                },
                            )
                        }
                    } else {
                        ConnectScreen(
                            client = client,
                            initialUrl = prefs.serverUrl,
                            onConnect = { url ->
                                prefs.serverUrl = url
                                client.connect(url)
                            },
                            onDisconnect = { client.disconnect() },
                        )
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
