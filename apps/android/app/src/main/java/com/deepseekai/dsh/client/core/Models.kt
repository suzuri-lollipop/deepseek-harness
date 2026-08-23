package com.deepseekai.dsh.client.core

import org.json.JSONObject

/** host.describe value (the readiness probe). */
data class HostInfo(
    val version: String,
    val cwd: String?,
    val provider: String?,
    val model: String?,
    val attachedSessions: Int,
)

/** One session.list item, with the list-baseline title flattened out of projections. */
data class SessionSummary(
    val sessionId: String,
    val updatedAt: Long,
    val running: Boolean,
    val blank: Boolean,
    val cwd: String?,
    val agentPreset: String?,
    val title: String?,
)

/** A still-pending approval server-request; the envelope rpcId is the answer correlation id. */
data class PendingApproval(
    val rpcId: String,
    val sessionId: String,
    val approvalId: String,
    val toolName: String,
    val callId: String?,
    val reason: String?,
)

/** One ask-user-question item as it arrives in question/requested. */
data class Question(
    val id: String,
    val question: String,
    val detail: String?,
    val header: String?,
    val options: List<QuestionOption>,
    val multiSelect: Boolean,
)

data class QuestionOption(
    val label: String,
    val description: String?,
)

/** A still-pending question server-request; one answer settles the whole batch. */
data class PendingQuestion(
    val rpcId: String,
    val sessionId: String,
    val questions: List<Question>,
)

/**
 * One live frame on a session's buffered channel. Channels are keyed by
 * session, so frames carry no sessionId: the chat screen of that session is
 * the single consumer, and frames that arrive before its history seed
 * completes are queued ahead of later ones.
 */
sealed interface LiveFrame {
    data class Event(val event: JSONObject) : LiveFrame
    data class AgentError(val message: String) : LiveFrame
}

/** Connection lifecycle as observed by the UI. */
sealed interface ConnState {
    data object Disconnected : ConnState
    data class Connecting(val url: String, val detail: String? = null) : ConnState
    data class Ready(val host: HostInfo) : ConnState
}

/** A wire RPC failure: either a transport problem or an RpcResult error carried in a 200 body. */
class DshRpcException(val code: String, message: String) : Exception(message)
