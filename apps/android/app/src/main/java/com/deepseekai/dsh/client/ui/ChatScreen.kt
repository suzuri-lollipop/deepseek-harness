package com.deepseekai.dsh.client.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepseekai.dsh.client.core.ChatFold
import com.deepseekai.dsh.client.core.DshClient
import com.deepseekai.dsh.client.core.LiveFrame
import kotlinx.coroutines.launch

/**
 * One open session: the history tail seeds the fold, then the session's
 * buffered live channel extends it (frames arriving during the seed are
 * queued and deduped by seq, see [DshClient.liveFrames]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    client: DshClient,
    sessionId: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val reconnectNonce by client.reconnectNonce.collectAsStateWithLifecycle()
    val running by client.running.collectAsStateWithLifecycle()
    val pendingApprovals by client.pendingApprovals.collectAsStateWithLifecycle()
    val pendingQuestions by client.pendingQuestions.collectAsStateWithLifecycle()
    val sessions by client.sessions.collectAsStateWithLifecycle()
    val isRunning = running[sessionId] == true

    var fold by remember(sessionId, reconnectNonce) { mutableStateOf(ChatFold()) }
    // Screen-owned invalidation counter. The fold instance is replaced on
    // seed, so a lifecycle-keyed collector on fold.version would keep
    // observing the replaced instance; keying the collector on the instance
    // follows the replacement.
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(fold) {
        fold.version.collect {
            version += 1
        }
    }
    var loadError by remember(sessionId, reconnectNonce) { mutableStateOf<String?>(null) }
    var hasMore by remember(sessionId, reconnectNonce) { mutableStateOf(false) }
    var oldestSeq by remember(sessionId, reconnectNonce) { mutableLongStateOf(-1L) }
    var loadOlder by remember { mutableStateOf(false) }
    var title by remember(sessionId) { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val sessionSummary = sessions.firstOrNull { it.sessionId == sessionId }
    val displayTitle = title?.takeIf { it.isNotEmpty() }
        ?: sessionSummary?.title?.takeIf { it.isNotEmpty() }
        ?: (basenameOf(sessionSummary?.cwd) ?: sessionId.take(8))

    // Seed the fold from the history tail, then consume this session's
    // buffered live channel in the same coroutine: frames that arrived while
    // the seed was in flight are queued ahead of later ones, and ChatFold's
    // seq dedupe discards any the seed page already contains.
    LaunchedEffect(sessionId, reconnectNonce) {
        loadError = null
        hasMore = false
        oldestSeq = -1L
        try {
            val page = client.history(sessionId, null, PAGE_MESSAGES)
            val fresh = ChatFold()
            for (event in page.events) fresh.apply(event)
            fold = fresh
            hasMore = page.hasMore && page.events.isNotEmpty()
            oldestSeq = page.events.firstOrNull()?.let { seqOf(it) } ?: -1L
            title = page.title
            // Open at the newest message: the follow logic below only keeps
            // the tail in view while already near it, so the seeded list
            // needs this one explicit jump (items: version spacer,
            // optional load-older button, then the rows).
            val tailIndex = 1 + (if (hasMore) 1 else 0) + fresh.rows.size - 1
            listState.scrollToItem(if (tailIndex < 0) 0 else tailIndex)
        } catch (e: Exception) {
            loadError = e.message ?: e.toString()
        }
        for (frame in client.liveFrames(sessionId)) {
            when (frame) {
                is LiveFrame.Event -> fold.apply(frame.event)
                is LiveFrame.AgentError -> fold.note(frame.message, true)
            }
        }
    }

    LaunchedEffect(version) {
        // Follow the tail while the user is near the bottom. The target is
        // the last adapter item, not the last row: approval/question cards
        // sit after the rows, and a turn blocked on a question emits no
        // further events, so a rows-based target would leave the card
        // off-screen forever.
        val count = fold.rows.size
        if (count == 0) return@LaunchedEffect
        val total = listState.layoutInfo.totalItemsCount
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
        if (last == null || last.index >= total - 3) {
            listState.scrollToItem(total - 1)
        }
    }

    val sessionApprovals = remember(pendingApprovals, sessionId) {
        pendingApprovals.values.filter { it.sessionId == sessionId }
    }
    val sessionQuestions = remember(pendingQuestions, sessionId) {
        pendingQuestions.values.filter { it.sessionId == sessionId }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = displayTitle,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (isRunning) {
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    client.cancel(sessionId)
                                } catch (e: Exception) {
                                    loadError = e.message
                                }
                            }
                        }) {
                            Text("${L.STOP_JA} / ${L.STOP_EN}")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loadError != null) {
                Text(
                    text = "${L.ERROR_JA}: ${loadError!!}",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp),
            ) {
                // The lazy content group is invalidated only by state it
                // reads itself; [fold.rows] is a plain list read, which the
                // snapshot system does not track. This always-present
                // zero-height item reads [version] in its key, binding the
                // group to the fold mutation counter so the adapter picks
                // up new rows.
                item(key = "rows-v$version") { Spacer(Modifier.height(0.dp)) }
                if (hasMore && !loadOlder) {
                    item(key = "load-older") {
                        TextButton(
                            onClick = {
                                loadOlder = true
                                scope.launch {
                                    try {
                                        val page = client.history(sessionId, oldestSeq, PAGE_MESSAGES)
                                        fold.prepend(page.events)
                                        hasMore = page.hasMore && page.events.isNotEmpty()
                                        oldestSeq = page.events.firstOrNull()?.let { seqOf(it) } ?: oldestSeq
                                    } catch (e: Exception) {
                                        loadError = e.message
                                    } finally {
                                        loadOlder = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (loadOlder) "${L.LOADING_JA} / ${L.LOADING_EN}"
                                else "${L.LOAD_OLDER_JA} / ${L.LOAD_OLDER_EN}",
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
                items(fold.rows, key = { it.id }) { row ->
                    RowView(row)
                }
                items(sessionApprovals, key = { it.rpcId }) { approval ->
                    ApprovalCard(
                        approval = approval,
                        onAllow = { scope.launch { client.respondApproval(approval, true) } },
                        onReject = { scope.launch { client.respondApproval(approval, false) } },
                    )
                }
                items(sessionQuestions, key = { it.rpcId }) { pending ->
                    QuestionCard(
                        pending = pending,
                        onSubmit = { answers -> scope.launch { client.respondQuestion(pending, answers) } },
                    )
                }
                if (fold.rows.isEmpty() && sessionApprovals.isEmpty() && sessionQuestions.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(L.BLANK_TITLE_JA)
                            Text(L.BLANK_TITLE_EN, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("${L.INPUT_PLACEHOLDER_JA} / ${L.INPUT_PLACEHOLDER_EN}", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                )
                FloatingActionButton(
                    onClick = {
                        val text = input.trim()
                        if (text.isEmpty()) return@FloatingActionButton
                        input = ""
                        scope.launch {
                            try {
                                client.prompt(sessionId, text)
                            } catch (e: Exception) {
                                loadError = e.message
                            }
                        }
                    },
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(44.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = L.SEND_JA)
                }
            }
        }
    }
}

private fun seqOf(event: org.json.JSONObject): Long = event.optLong("seq", -1L)

/** History page size, matching the web runtime's PAGE_MESSAGES. */
private const val PAGE_MESSAGES = 50
