package com.deepseekai.dsh.client.core

import org.json.JSONObject

/**
 * Wire helpers for the /api four-quadrant protocol: unary client-requests over
 * POST /api/<method>, client-responses over POST /api/respond, and server-request
 * downlink frames over the two event WebSockets.
 */
object Protocol {

    fun clientRequest(rpcId: String, method: String, payload: JSONObject): String =
        JSONObject()
            .put("type", "client-request")
            .put("rpcId", rpcId)
            .put("method", method)
            .put("payload", payload)
            .toString()

    /** Unwraps a server-response body to its ok value, or throws [DshRpcException] for an RpcResult error. */
    fun unwrap(body: String): JSONObject {
        val obj = JSONObject(body)
        if (obj.optString("type") != "server-response") {
            throw DshRpcException("bad-response", "unexpected wire type: ${obj.optString("type").ifEmpty { "absent" }}")
        }
        val result = obj.optJSONObject("result") ?: throw DshRpcException("bad-response", "missing result slot")
        return if (result.optBoolean("ok", false)) {
            result.optJSONObject("value") ?: JSONObject()
        } else {
            val error = result.optJSONObject("error")
            val code = error?.optString("code")?.ifEmpty { null } ?: "unknown"
            val message = error?.optString("message")?.ifEmpty { null } ?: "request failed"
            throw DshRpcException(code, message)
        }
    }

    /** Parses a downlink text frame into (rpcId, payload); null when not a well-formed server-request. */
    fun serverRequest(text: String): Pair<String, JSONObject>? {
        val obj = try {
            JSONObject(text)
        } catch (e: Exception) {
            return null
        }
        if (obj.optString("type") != "server-request") return null
        val rpcId = obj.optString("rpcId").ifEmpty { return null }
        val payload = obj.optJSONObject("payload") ?: return null
        return rpcId to payload
    }

    /** Builds a client-response body answering a server-request with an ok value. */
    fun clientResponse(rpcId: String, value: JSONObject): String =
        JSONObject()
            .put("type", "client-response")
            .put("rpcId", rpcId)
            .put("result", JSONObject().put("ok", true).put("value", value))
            .toString()

    /** Whether a respond receipt was accepted. */
    fun receiptAccepted(body: String): Boolean =
        try {
            JSONObject(body).optBoolean("accepted", false)
        } catch (e: Exception) {
            false
        }
}

/**
 * String value of [key], or null when the key is absent, holds JSON null, or
 * is empty. org.json's optString would return the literal "null" for an
 * explicit JSON null, which must not reach the UI.
 */
fun JSONObject.strOrNull(key: String): String? =
    (opt(key) as? String)?.takeIf { it.isNotEmpty() }
