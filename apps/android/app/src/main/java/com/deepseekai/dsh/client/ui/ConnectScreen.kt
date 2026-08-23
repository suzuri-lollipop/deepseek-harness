package com.deepseekai.dsh.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepseekai.dsh.client.core.ConnState
import com.deepseekai.dsh.client.core.DshClient

/**
 * Server URL entry and live connection status. Stays on screen while the
 * client connects and reconnects; flips to the session list on Ready.
 */
@Composable
fun ConnectScreen(
    client: DshClient,
    initialUrl: String,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    val state by client.state.collectAsStateWithLifecycle()
    val error by client.errorNote.collectAsStateWithLifecycle()
    val busy = state is ConnState.Connecting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.padding(top = 48.dp))
        Text(
            text = L.APP_TITLE_JA,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = L.APP_TITLE_EN,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.padding(top = 40.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BiText(L.SERVER_URL_JA, L.SERVER_URL_EN)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("https://host[:port]") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        Spacer(modifier = Modifier.padding(top = 12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { onConnect(url) },
                enabled = !busy && url.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                BiText(L.CONNECT_JA, L.CONNECT_EN, jaSize = 15f)
            }
            if (busy) {
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.weight(1f)) {
                    BiText(L.DISCONNECT_JA, L.DISCONNECT_EN, jaSize = 15f)
                }
            }
        }

        Spacer(modifier = Modifier.padding(top = 28.dp))
        when (val s = state) {
            is ConnState.Connecting -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                Column {
                    Text(L.CONNECTING_JA)
                    Text(s.detail ?: L.CONNECTING_EN, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }

            is ConnState.Disconnected -> Text(
                text = L.NOT_CONNECTED_JA,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is ConnState.Ready -> Text(
                text = "${L.READY_JA} ${s.host.version}",
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (error != null) {
            Spacer(modifier = Modifier.padding(top = 16.dp))
            Text(
                text = error!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
            )
        }

        Spacer(modifier = Modifier.padding(top = 28.dp))
        Text(
            text = L.TRUST_HINT_JA,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = L.TRUST_HINT_EN,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}
