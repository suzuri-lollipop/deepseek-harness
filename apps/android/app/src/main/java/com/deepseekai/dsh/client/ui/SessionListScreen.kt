package com.deepseekai.dsh.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepseekai.dsh.client.core.ConnState
import com.deepseekai.dsh.client.core.DshClient
import com.deepseekai.dsh.client.core.SessionSummary
import kotlinx.coroutines.launch

/** Session list over session.list, refreshed by host frames and the refresh action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    client: DshClient,
    onOpenSession: (String) -> Unit,
    onDisconnect: () -> Unit,
) {
    val sessions by client.sessions.collectAsStateWithLifecycle()
    val state by client.state.collectAsStateWithLifecycle()
    val error by client.errorNote.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var creating by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    val visible = remember(sessions) {
        val nonBlank = sessions.filter { !it.blank }
        if (nonBlank.isEmpty()) sessions else nonBlank
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { BiText(L.SESSIONS_JA, L.SESSIONS_EN) },
                actions = {
                    if (refreshing) {
                        CircularProgressIndicator()
                    }
                    IconButton(onClick = {
                        refreshing = true
                        scope.launch {
                            try {
                                client.refreshSessions()
                            } catch (e: Exception) {
                                // surfaced via client.errorNote
                            } finally {
                                refreshing = false
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = L.REFRESH_JA)
                    }
                    IconButton(onClick = onDisconnect) {
                        Text(L.DISCONNECT_JA, fontSize = 12.sp)
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    creating = true
                    scope.launch {
                        try {
                            val id = client.createSession(null)
                            onOpenSession(id)
                        } catch (e: Exception) {
                            // surfaced via client.errorNote on the chat screen
                        } finally {
                            creating = false
                        }
                    }
                },
                icon = {
                    if (creating) CircularProgressIndicator() else Icon(Icons.Filled.Add, contentDescription = null)
                },
                text = { BiText(L.NEW_SESSION_JA, L.NEW_SESSION_EN, jaSize = 13f, enSize = 10f) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val host = (state as? ConnState.Ready)?.host
            if (host != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "v${host.version} · ${host.model ?: "model n/a"} · ${host.attachedSessions}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (error != null) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (visible.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(L.NO_SESSIONS_JA)
                    Text(L.NO_SESSIONS_EN, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                ) {
                    items(visible, key = { it.sessionId }) { summary ->
                        SessionRow(
                            summary = summary,
                            onClick = { onOpenSession(summary.sessionId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(summary: SessionSummary, onClick: () -> Unit) {
    val running = summary.running
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = summary.title?.takeIf { it.isNotEmpty() }
                    ?: (basenameOf(summary.cwd) ?: summary.sessionId.take(8)),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (running) {
                Text(
                    text = L.RUNNING_JA,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = buildString {
                append(relativeTime(summary.updatedAt))
                summary.cwd?.let { append(" · ").append(it) }
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun relativeTime(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    val delta = System.currentTimeMillis() - epochMillis
    return when {
        delta < 60_000 -> "now"
        delta < 3_600_000 -> "${delta / 60_000}min"
        delta < 86_400_000 -> "${delta / 3_600_000}h"
        else -> "${delta / 86_400_000}d"
    }
}
