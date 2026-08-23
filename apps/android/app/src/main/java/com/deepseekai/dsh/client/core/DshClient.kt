package com.deepseekai.dsh.client.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

/** IANA "Area/Location" zone ids, e.g. "Asia/Tokyo" or "America/Argentina/Buenos_Aires". */
private val IANA_ZONE_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_+\\-]*(/[A-Za-z0-9_+\\-]+)+")

/**
 * Transport + stream client for the /api protocol. Owns the two downlink
 * WebSockets (session-mux and host), the unary HTTP client, and reconnect
 * with backoff. Ready means both sockets are open and host.describe
 * succeeded; recovery is reopening both streams plus refetching history,
 * which the chat surface does by watching [reconnectNonce].
 */
class DshClient(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow<ConnState>(ConnState.Disconnected)
    val state: StateFlow<ConnState> = _state

    private val _errorNote = MutableStateFlow<String?>(null)
    val errorNote: StateFlow<String?> = _errorNote

    private val _sessions = MutableStateFlow<List<SessionSummary>>(emptyList())
    val sessions: StateFlow<List<SessionSummary>> = _sessions

    /** Live attached-agent running bits from host frames, keyed by sessionId. */
    private val _running = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val running: StateFlow<Map<String, Boolean>> = _running

    private val _pendingApprovals = MutableStateFlow<Map<String, PendingApproval>>(emptyMap())
    val pendingApprovals: StateFlow<Map<String, PendingApproval>> = _pendingApprovals

    private val _pendingQuestions = MutableStateFlow<Map<String, PendingQuestion>>(emptyMap())
    val pendingQuestions: StateFlow<Map<String, PendingQuestion>> = _pendingQuestions

    /** Bumped on every Ready transition (initial connect and each successful reconnect). */
    private val _reconnectNonce = MutableStateFlow(0)
    val reconnectNonce: StateFlow<Int> = _reconnectNonce

    /**
     * Live frames per session, buffered while no chat collector is attached.
     * The web runtime drops frames for sessions it has not instantiated; the
     * app instead keeps a bounded backlog that the chat screen drains after
     * its history seed, so a slow seed never loses live events. ChatFold's
     * seq dedupe discards backlog frames the seed page already contains.
     */
    private val liveChannels = java.util.concurrent.ConcurrentHashMap<String, Channel<LiveFrame>>()

    /** The session's live channel; created on first frame or first read. */
    fun liveFrames(sessionId: String): Channel<LiveFrame> =
        liveChannels.getOrPut(sessionId) { Channel(4096, BufferOverflow.DROP_OLDEST) }

    private val unaryHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Whole-call ceiling so a stalled large history page can never leave
        // the chat surface in a permanent loading state.
        .callTimeout(2, TimeUnit.MINUTES)
        .build()

    private val streamHttp = unaryHttp.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var url: String? = null
    private var muxWs: WebSocket? = null
    private var hostWs: WebSocket? = null
    private var muxOpen = false
    private var hostOpen = false
    private var readyJob: Job? = null
    private var reconnectJob: Job? = null
    private var backoffMs = 2_000L

    private val json = "application/json".toMediaType()

    /** Starts (or restarts) a connection to [rawUrl]. */
    fun connect(rawUrl: String) {
        val normalized = normalizeUrl(rawUrl)
        if (normalized == null) {
            _errorNote.value = "Invalid URL (need http:// or https://)"
            return
        }
        url = normalized
        _errorNote.value = null
        backoffMs = 2_000L
        reconnectJob?.cancel()
        scope.launch { openStreams(normalized) }
    }

    /** Stops the connection and clears live state. */
    fun disconnect() {
        url = null
        backoffMs = 2_000L
        readyJob?.cancel()
        reconnectJob?.cancel()
        closeStreams()
        _state.value = ConnState.Disconnected
        _errorNote.value = null
        _pendingApprovals.value = emptyMap()
        _pendingQuestions.value = emptyMap()
    }

    /**
     * One unary RPC: POST /api/<method> with a client-request envelope.
     * Resolves to the ok value; throws [DshRpcException] for RpcResult errors
     * and transport failures.
     */
    suspend fun call(method: String, payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val base = url ?: throw DshRpcException("disconnected", "not connected")
        val rpcId = UUID.randomUUID().toString()
        val body = Protocol.clientRequest(rpcId, method, payload).toRequestBody(json)
        val request = Request.Builder().url("$base/api/$method").post(body).build()
        unaryHttp.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                throw DshRpcException(
                    code = when (response.code) {
                        403 -> "trust-fence"
                        404 -> "unknown-method"
                        else -> "http-${response.code}"
                    },
                    message = "HTTP ${response.code}: ${text.take(300)}",
                )
            }
            Protocol.unwrap(text)
        }
    }

    /** Sends a client-response for a server-request; throws when the receipt is not accepted. */
    suspend fun sendResponse(rpcId: String, value: JSONObject) = withContext(Dispatchers.IO) {
        val base = url ?: throw DshRpcException("disconnected", "not connected")
        val body = Protocol.clientResponse(rpcId, value).toRequestBody(json)
        val request = Request.Builder().url("$base/api/respond").post(body).build()
        unaryHttp.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful || !Protocol.receiptAccepted(text)) {
                val reason = try {
                    JSONObject(text).optString("reason").ifEmpty { "" }
                } catch (e: Exception) {
                    ""
                }
                throw DshRpcException(
                    code = if (reason.isEmpty()) "respond-failed" else reason,
                    message = if (response.isSuccessful) "respond ${reason.ifEmpty { "rejected" }}" else "HTTP ${response.code}",
                )
            }
        }
    }

    suspend fun createSession(cwd: String?): String {
        val payload = JSONObject()
        if (cwd != null) payload.put("cwd", cwd)
        val value = call("session.create", payload)
        return value.optString("sessionId")
    }

    suspend fun refreshSessions() {
        val value = call("session.list", JSONObject())
        val items = value.optJSONArray("items") ?: JSONArray()
        val summaries = ArrayList<SessionSummary>(items.length())
        val runningBits = HashMap<String, Boolean>()
        for (i in 0 until items.length()) {
            val summary = parseSummary(items.getJSONObject(i))
            summaries.add(summary)
            runningBits[summary.sessionId] = summary.running
        }
        _sessions.value = summaries
        _running.value = runningBits
    }

    /** One history page; the tail page (beforeSeq absent) carries the projection baseline. */
    suspend fun history(sessionId: String, beforeSeq: Long?, maxMessages: Int): HistoryPage =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("sessionId", sessionId)
                .put("maxMessages", maxMessages)
            if (beforeSeq != null) payload.put("beforeSeq", beforeSeq)
            val value = call("session.history", payload)
            val events = value.optJSONArray("events") ?: JSONArray()
            val titles = HashMap<String, String>()
            val projections = value.optJSONObject("projections")?.optJSONObject("values")
            val title = projections?.strOrNull("title")
            if (!title.isNullOrEmpty()) titles[sessionId] = title
            // History rows are wrapped: each item is { event: <session log event> };
            // the inner event is what ChatFold applies.
            HistoryPage(
                events = (0 until events.length()).mapNotNull { events.getJSONObject(it).optJSONObject("event") },
                hasMore = value.optBoolean("hasMore", false),
                title = title,
            )
        }

    suspend fun prompt(sessionId: String, text: String): String? {
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", text))
        val payload = JSONObject()
            .put("sessionId", sessionId)
            .put("mode", "queue")
            .put("content", content)
        // The server accepts "UTC" or an IANA Area/Location name; device zones
        // like the AOSP emulator's "GMT" are legacy names and must be omitted.
        val zoneId = java.time.ZoneId.systemDefault().id
        if (zoneId == "UTC" || IANA_ZONE_PATTERN.matches(zoneId)) payload.put("clientTimeZone", zoneId)
        val value = call("session.prompt", payload)
        return value.optString("command").ifEmpty { null }
    }

    suspend fun cancel(sessionId: String) {
        call("session.cancel", JSONObject().put("sessionId", sessionId))
    }

    /** Answers an approval server-request: allowed-once or rejected. */
    suspend fun respondApproval(approval: PendingApproval, allow: Boolean) {
        val value = JSONObject()
            .put("sessionId", approval.sessionId)
            .put("approvalId", approval.approvalId)
            .put("outcome", if (allow) "allowed-once" else "rejected")
        sendResponse(approval.rpcId, value)
        _pendingApprovals.value = _pendingApprovals.value - approval.rpcId
    }

    /** Answers a question server-request; one answer settles the whole batch. */
    suspend fun respondQuestion(pending: PendingQuestion, answers: List<QuestionAnswer>) {
        val answerItems = JSONArray()
        for (answer in answers) {
            val selected = JSONArray()
            for (label in answer.selected) selected.put(label)
            val item = JSONObject()
                .put("id", answer.id)
                .put("selected", selected)
            if (answer.custom != null) item.put("custom", answer.custom)
            answerItems.put(item)
        }
        val value = JSONObject()
            .put("sessionId", pending.sessionId)
            .put("answer", JSONObject().put("answers", answerItems))
        sendResponse(pending.rpcId, value)
        _pendingQuestions.value = _pendingQuestions.value - pending.rpcId
    }

    // ---- streams ----

    private fun closeStreams() {
        muxOpen = false
        hostOpen = false
        muxWs?.cancel()
        hostWs?.cancel()
        muxWs = null
        hostWs = null
    }

    private fun openStreams(normalized: String) {
        _state.value = ConnState.Connecting(normalized)
        closeStreams()
        val wsBase = if (normalized.startsWith("https://")) {
            "wss://${normalized.removePrefix("https://")}"
        } else {
            "ws://${normalized.removePrefix("http://")}"
        }
        val muxRequest = Request.Builder().url("$wsBase/api/events.mux").build()
        val hostRequest = Request.Builder().url("$wsBase/api/events.host").build()
        muxWs = streamHttp.newWebSocket(muxRequest, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== muxWs) return
                muxOpen = true
                maybeReady()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== muxWs) return
                onMuxFrame(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket === muxWs) webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket !== muxWs) return
                drop("mux", code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket !== muxWs) return
                drop("mux", -1, t.message ?: t.javaClass.simpleName)
            }
        })
        hostWs = streamHttp.newWebSocket(hostRequest, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== hostWs) return
                hostOpen = true
                maybeReady()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== hostWs) return
                onHostFrame(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket === hostWs) webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket !== hostWs) return
                drop("host", code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket !== hostWs) return
                drop("host", -1, t.message ?: t.javaClass.simpleName)
            }
        })
    }

    private fun maybeReady() {
        if (!muxOpen || !hostOpen) return
        if (readyJob?.isActive == true) return
        readyJob = scope.launch {
            try {
                val value = call("host.describe", JSONObject())
                val host = HostInfo(
                    version = value.strOrNull("version") ?: "?",
                    cwd = value.strOrNull("cwd"),
                    provider = value.strOrNull("provider"),
                    model = value.strOrNull("model"),
                    attachedSessions = value.optInt("attachedSessions", 0),
                )
                val base = url ?: return@launch
                backoffMs = 2_000L
                _errorNote.value = null
                _state.value = ConnState.Ready(host)
                _reconnectNonce.value++
                try {
                    refreshSessions()
                } catch (e: Exception) {
                    _errorNote.value = "session.list failed: ${e.message}"
                }
            } catch (e: Exception) {
                val base = url ?: return@launch
                _state.value = ConnState.Connecting(base)
                _errorNote.value = describeError(e)
                scheduleReconnect()
            }
        }
    }

    private fun drop(which: String, code: Int, reason: String) {
        val base = url ?: return
        muxOpen = false
        hostOpen = false
        closeStreams()
        _state.value = ConnState.Connecting(base, "disconnected ($which $code $reason)")
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(backoffMs)
            backoffMs = minOf(backoffMs * 2, 15_000L)
            val base = url ?: return@launch
            openStreams(base)
        }
    }

    private fun onMuxFrame(text: String) {
        val frame = Protocol.serverRequest(text) ?: run {
            android.util.Log.d("Dsh", "mux frame rejected by parser: ${text.take(120)}")
            return
        }
        val (rpcId, payload) = frame
        when (payload.optString("type")) {
            "session/event" -> {
                val sessionId = payload.optString("sessionId")
                val event = payload.optJSONObject("event") ?: return
                if (sessionId.isNotEmpty()) liveFrames(sessionId).trySend(LiveFrame.Event(event))
            }

            "session/subscribed" ->
                // Baseline marker; the chat surface refetches history on Ready.
                Unit

            "session/queue" ->
                // Tracked by the web client; out of scope for v1.
                Unit

            "session/jobs" -> Unit

            "session/projection" -> {
                val sessionId = payload.optString("sessionId")
                if (payload.optString("key") == "title" && sessionId.isNotEmpty()) {
                    val title = payload.opt("value")
                    val newTitle = (title as? String)?.takeIf { it.isNotEmpty() }
                    updateTitle(sessionId, newTitle)
                }
            }

            "approval/requested" -> {
                val approval = PendingApproval(
                    rpcId = rpcId,
                    sessionId = payload.optString("sessionId"),
                    approvalId = payload.optString("approvalId"),
                    toolName = payload.strOrNull("toolName") ?: "tool",
                    callId = payload.strOrNull("callId"),
                    reason = payload.strOrNull("reason"),
                )
                _pendingApprovals.value = _pendingApprovals.value + (approval.rpcId to approval)
            }

            "approval/resolved" -> {
                // Covers outcomes settled elsewhere (another client, cancel);
                // our own answer already removed the entry on receipt.
                val approvalId = payload.strOrNull("approvalId")
                if (approvalId != null) {
                    _pendingApprovals.value =
                        _pendingApprovals.value.filterValues { it.approvalId != approvalId }
                }
            }

            "question/requested" -> {
                val questions = parseQuestions(payload.optJSONArray("questions"))
                val pending = PendingQuestion(
                    rpcId = rpcId,
                    sessionId = payload.optString("sessionId"),
                    questions = questions,
                )
                _pendingQuestions.value = _pendingQuestions.value + (pending.rpcId to pending)
            }

            "question/resolved" -> {
                // The payload carries the original request's rpcId (the map
                // key); covers cancels and answers settled elsewhere.
                val questionRpcId = payload.strOrNull("questionRpcId")
                if (questionRpcId != null) {
                    _pendingQuestions.value = _pendingQuestions.value - questionRpcId
                }
            }

            "stream/error" ->
                _errorNote.value = payload.optJSONObject("error")?.strOrNull("message") ?: "stream error"
        }
    }

    private fun refreshSessionsOnHostChange() {
        scope.launch {
            try {
                refreshSessions()
            } catch (e: Exception) {
                _errorNote.value = "session.list failed: ${e.message}"
            }
        }
    }

    private fun onHostFrame(text: String) {
        val frame = Protocol.serverRequest(text) ?: return
        val payload = frame.second
        when (payload.optString("type")) {
            "host/session-added" -> refreshSessionsOnHostChange()
            "host/session-removed" -> refreshSessionsOnHostChange()

            "host/session-status" -> {
                val sessionId = payload.optString("sessionId")
                if (sessionId.isNotEmpty()) {
                    _running.value = _running.value + (sessionId to payload.optBoolean("running"))
                }
            }

            "host/agent-error" -> {
                val sessionId = payload.optString("sessionId")
                val message = payload.strOrNull("message") ?: "agent error"
                if (sessionId.isNotEmpty()) liveFrames(sessionId).trySend(LiveFrame.AgentError(message))
            }

            else ->
                // workspace/order/archived/remote frames are out of scope for v1.
                Unit
        }
    }

    private fun updateTitle(sessionId: String, title: String?) {
        _sessions.value = _sessions.value.map {
            if (it.sessionId == sessionId) it.copy(title = title) else it
        }
    }

    private fun describeError(e: Throwable): String = when (e) {
        is DshRpcException -> when (e.code) {
            "trust-fence" -> "The server rejected this host (trust fence). Start it with --trusted-host <this-hostname>."
            else -> "${e.code}: ${e.message}"
        }

        else -> e.message ?: e.javaClass.simpleName
    }

    private fun normalizeUrl(raw: String): String? {
        var s = raw.trim().removeSuffix("/")
        if (s.isEmpty()) return null
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        return try {
            val uri = URI(s)
            val scheme = uri.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") {
                if (uri.host.isNullOrEmpty()) null else s
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseSummary(obj: JSONObject): SessionSummary {
        val values = obj.optJSONObject("projections")?.optJSONObject("values")
        return SessionSummary(
            sessionId = obj.optString("sessionId"),
            updatedAt = obj.optLong("updatedAt"),
            running = obj.optBoolean("running"),
            blank = obj.optBoolean("blank"),
            cwd = obj.strOrNull("cwd"),
            agentPreset = obj.strOrNull("agentPreset"),
            title = values?.strOrNull("title"),
        )
    }

    private fun parseQuestions(array: JSONArray?): List<Question> {
        if (array == null) return emptyList()
        val questions = ArrayList<Question>(array.length())
        for (i in 0 until array.length()) {
            val q = array.getJSONObject(i)
            val options = ArrayList<QuestionOption>()
            val optArray = q.optJSONArray("options")
            if (optArray != null) {
                for (j in 0 until optArray.length()) {
                    val o = optArray.getJSONObject(j)
                    options.add(
                        QuestionOption(
                            label = o.optString("label"),
                            description = o.strOrNull("description"),
                        ),
                    )
                }
            }
            questions.add(
                Question(
                    id = q.optString("id"),
                    question = q.optString("question"),
                    detail = q.strOrNull("detail"),
                    header = q.strOrNull("header"),
                    options = options,
                    multiSelect = q.optBoolean("multiSelect", false),
                ),
            )
        }
        return questions
    }
}

/** One user answer in a question response; selected option labels plus optional custom text. */
data class QuestionAnswer(
    val id: String,
    val selected: List<String>,
    val custom: String? = null,
)

/** One history page: raw events in ascending seq order. */
data class HistoryPage(
    val events: List<JSONObject>,
    val hasMore: Boolean,
    val title: String?,
)
