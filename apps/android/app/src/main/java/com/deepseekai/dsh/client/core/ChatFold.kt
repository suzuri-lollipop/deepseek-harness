package com.deepseekai.dsh.client.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.UUID

/**
 * Tolerant fold from SessionEvent JSON into display rows. Core message and
 * tool events become rows; unknown (plugin-merged) event types are ignored.
 * A fold instance serves one open chat surface: history pages seed it, live
 * mux frames extend it, and seq dedupe keeps the two in one order.
 *
 * Rows are immutable values: every mutation replaces the row instance at its
 * index. Compose skips item content whose row reference is unchanged, so
 * in-place field mutation would never re-render; replacement is what makes
 * streaming chunks and tool results visible to the chat surface.
 */
class ChatFold {

    /** One displayable chat row; [id] is stable across history prepends. */
    sealed interface Row {
        val id: String
        val timeMs: Long
    }

    data class UserRow(override val id: String, val text: String, override val timeMs: Long) : Row

    data class AssistantRow(
        override val id: String,
        val text: String,
        val reasoning: String,
        val streaming: Boolean,
        val interrupted: Boolean,
        override val timeMs: Long,
    ) : Row

    data class ToolRow(
        override val id: String,
        val callId: String?,
        val name: String,
        val args: String,
        val result: String?,
        val isError: Boolean?,
        val pending: Boolean,
        override val timeMs: Long,
    ) : Row

    data class NoteRow(override val id: String, val text: String, val isError: Boolean, override val timeMs: Long) : Row

    private val _rows = mutableListOf<Row>()

    /** Bumped on every mutation; the chat surface reads [rows] after observing it. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private var lastSeq = -1L
    private var currentStep: Int? = null
    private var streamingRowIndex: Int? = null
    private val toolRowIndex = HashMap<String, Int>()

    /** All rows in display order. */
    val rows: List<Row>
        get() = _rows

    /** Applies one event; false when it was dropped as stale (seq not advancing). */
    fun apply(event: JSONObject): Boolean {
        val seq = event.optLong("seq", -1L)
        if (seq >= 0L && seq <= lastSeq) {
            return false
        }
        lastSeq = seq
        val type = event.optString("type")
        val data = event.optJSONObject("data") ?: return true
        when (type) {
            "user/message" -> onUserMessage(data)
            "assistant/chunk" -> onChunk(data)
            "assistant/message" -> onAssistantMessage(data)
            "tool/call" -> onToolCall(data)
            "tool/result" -> onToolResult(data)
            "turn/end" -> onTurnEnd(data)
            else -> return true
        }
        _version.value++
        return true
    }

    /** Prepends a page of older history (ascending seq) without re-deriving the tail. */
    fun prepend(events: List<JSONObject>) {
        val older = ChatFold()
        for (event in events) older.apply(event)
        if (older._rows.isEmpty()) return
        _rows.addAll(0, older._rows)
        reindexTools()
        _version.value++
    }

    /** Adds a synthesized note (agent error, connectivity). */
    fun note(text: String, isError: Boolean) {
        _rows.add(NoteRow(UUID.randomUUID().toString(), text, isError, System.currentTimeMillis()))
        _version.value++
    }

    private fun reindexTools() {
        toolRowIndex.clear()
        for ((index, row) in _rows.withIndex()) {
            val tool = row as? ToolRow ?: continue
            val id = tool.callId ?: continue
            if (id.isNotEmpty()) toolRowIndex[id] = index
        }
    }

    private fun onUserMessage(data: JSONObject) {
        val kind = data.optJSONObject("source")?.optString("kind") ?: ""
        if (kind != "user") return
        val text = textOf(data.optJSONArray("content"))
        if (text.isBlank()) return
        _rows.add(UserRow(UUID.randomUUID().toString(), text, System.currentTimeMillis()))
    }

    private fun onChunk(data: JSONObject) {
        val chunk = data.optJSONObject("chunk") ?: return
        val delta = chunk.strOrNull("text") ?: ""
        if (delta.isEmpty()) return
        val index = ensureStreamingRow(data.optInt("step", -1))
        val row = _rows[index] as AssistantRow
        _rows[index] = when (chunk.optString("type")) {
            "text-delta" -> row.copy(text = row.text + delta)
            "reasoning-delta" -> row.copy(reasoning = row.reasoning + delta)
            // block-start/end, tool-call-delta, usage, finish carry no
            // display text of their own; tool/call carries full arguments.
            else -> row
        }
    }

    private fun onAssistantMessage(data: JSONObject) {
        val message = data.optJSONObject("message") ?: return
        val content = message.optJSONArray("content")
        val text = textOf(content)
        val reasoning = reasoningOf(content)
        val interrupted = data.optBoolean("interrupted", false)
        val step = data.optInt("step", -1)
        val index = streamingRowIndex
        if (step == currentStep && index != null && index < _rows.size && _rows[index] is AssistantRow) {
            val row = _rows[index] as AssistantRow
            _rows[index] = row.copy(text = text, reasoning = reasoning, streaming = false, interrupted = interrupted)
        } else {
            _rows.add(
                AssistantRow(
                    id = UUID.randomUUID().toString(),
                    text = text,
                    reasoning = reasoning,
                    streaming = false,
                    interrupted = interrupted,
                    timeMs = System.currentTimeMillis(),
                ),
            )
        }
        streamingRowIndex = null
        if (content != null) {
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") != "tool-call") continue
                ensureToolRow(
                    block.strOrNull("id"),
                    block.strOrNull("name") ?: "tool",
                    block.strOrNull("arguments") ?: "",
                )
            }
        }
    }

    private fun onToolCall(data: JSONObject) {
        ensureToolRow(
            data.strOrNull("callId"),
            data.strOrNull("name") ?: "tool",
            data.strOrNull("arguments") ?: "",
        )
    }

    private fun onToolResult(data: JSONObject) {
        val message = data.optJSONObject("message") ?: return
        val content = message.optJSONArray("content")
        var toolCallId = ""
        val result = StringBuilder()
        var isError: Boolean? = null
        if (content != null) {
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") != "tool-result") continue
                toolCallId = block.strOrNull("toolCallId") ?: ""
                val inner = block.optJSONArray("content")
                if (inner != null) {
                    for (j in 0 until inner.length()) {
                        val part = inner.getJSONObject(j)
                        if (part.optString("type") == "text") result.append(part.strOrNull("text") ?: "")
                    }
                }
                isError = block.optBoolean("isError", false)
            }
        }
        if (data.optJSONObject("error") != null) isError = true
        val index = toolCallId.takeIf { it.isNotEmpty() }?.let { toolRowIndex[it] }
        if (index != null && index < _rows.size && _rows[index] is ToolRow) {
            val row = _rows[index] as ToolRow
            _rows[index] = row.copy(result = result.toString(), isError = isError, pending = false)
        } else {
            _rows.add(
                ToolRow(
                    id = UUID.randomUUID().toString(),
                    callId = toolCallId.ifEmpty { null },
                    name = "tool",
                    args = "",
                    result = result.toString(),
                    isError = isError,
                    pending = false,
                    timeMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun onTurnEnd(data: JSONObject) {
        val reason = data.optJSONObject("reason")
        val rowIndex = streamingRowIndex
        if (rowIndex != null && rowIndex < _rows.size) {
            val row = _rows[rowIndex] as? AssistantRow
            if (row != null && row.streaming) {
                _rows[rowIndex] = row.copy(streaming = false)
            }
        }
        streamingRowIndex = null
        val kind = reason?.optString("kind") ?: ""
        when (kind) {
            "error" -> {
                val error = reason?.optJSONObject("error")
                val message = error?.strOrNull("message") ?: "turn failed"
                _rows.add(NoteRow(UUID.randomUUID().toString(), message, true, System.currentTimeMillis()))
            }

            "aborted" ->
                _rows.add(NoteRow(UUID.randomUUID().toString(), "Turn stopped / ターンを停止しました", false, System.currentTimeMillis()))

            "max-tokens" ->
                _rows.add(NoteRow(UUID.randomUUID().toString(), "Max tokens reached / 最大トークンに到達", false, System.currentTimeMillis()))

            else -> Unit
        }
    }

    /** Creates the streaming row for [step] when needed; returns its index. */
    private fun ensureStreamingRow(step: Int): Int {
        val index = streamingRowIndex
        if (step == currentStep && index != null && index < _rows.size && _rows[index] is AssistantRow) {
            return index
        }
        if (index != null && index < _rows.size) {
            val row = _rows[index] as? AssistantRow
            if (row != null && row.streaming) {
                _rows[index] = row.copy(streaming = false)
            }
        }
        currentStep = step
        _rows.add(
            AssistantRow(
                id = UUID.randomUUID().toString(),
                text = "",
                reasoning = "",
                streaming = true,
                interrupted = false,
                timeMs = System.currentTimeMillis(),
            ),
        )
        val newIndex = _rows.size - 1
        streamingRowIndex = newIndex
        return newIndex
    }

    /** Creates or updates the tool row for [callId]; returns its index. */
    private fun ensureToolRow(callId: String?, name: String, args: String): Int {
        val id = callId.orEmpty()
        if (id.isNotEmpty()) {
            val existing = toolRowIndex[id]
            if (existing != null && existing < _rows.size && _rows[existing] is ToolRow) {
                val row = _rows[existing] as ToolRow
                val nameUpdated = if (row.name == "tool" && name.isNotEmpty()) name else row.name
                val argsUpdated = if (row.args.isEmpty() && args.isNotEmpty()) args else row.args
                if (nameUpdated != row.name || argsUpdated != row.args) {
                    _rows[existing] = row.copy(name = nameUpdated, args = argsUpdated)
                }
                return existing
            }
        }
        _rows.add(
            ToolRow(
                id = UUID.randomUUID().toString(),
                callId = id.ifEmpty { null },
                name = name.ifEmpty { "tool" },
                args = args,
                result = null,
                isError = null,
                pending = true,
                timeMs = System.currentTimeMillis(),
            ),
        )
        val index = _rows.size - 1
        if (id.isNotEmpty()) toolRowIndex[id] = index
        return index
    }

    private fun textOf(blocks: org.json.JSONArray?): String {
        if (blocks == null) return ""
        val out = StringBuilder()
        for (i in 0 until blocks.length()) {
            val block = blocks.getJSONObject(i)
            if (block.optString("type") != "text") continue
            if (out.isNotEmpty()) out.append('\n')
            out.append(block.optString("text"))
        }
        return out.toString()
    }

    private fun reasoningOf(blocks: org.json.JSONArray?): String {
        if (blocks == null) return ""
        val out = StringBuilder()
        for (i in 0 until blocks.length()) {
            val block = blocks.getJSONObject(i)
            if (block.optString("type") != "reasoning") continue
            if (out.isNotEmpty()) out.append('\n')
            out.append(block.optString("text"))
        }
        return out.toString()
    }
}
