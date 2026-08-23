package com.deepseekai.dsh.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/**
 * The app is bilingual by design: Japanese primary, English alongside.
 * Every app-owned string is written as a (ja, en) pair.
 */
object L {
    const val APP_TITLE_JA = "DeepSeek Harness"
    const val APP_TITLE_EN = "遠隔エージェントクライアント / Remote agent client"

    const val SERVER_URL_JA = "サーバー URL"
    const val SERVER_URL_EN = "Server URL"
    const val CONNECT_JA = "接続"
    const val CONNECT_EN = "Connect"
    const val DISCONNECT_JA = "切断"
    const val DISCONNECT_EN = "Disconnect"
    const val CONNECTING_JA = "接続中…"
    const val CONNECTING_EN = "Connecting…"
    const val READY_JA = "接続済み"
    const val READY_EN = "Connected"
    const val NOT_CONNECTED_JA = "未接続"
    const val NOT_CONNECTED_EN = "Not connected"
    const val SESSIONS_JA = "セッション"
    const val SESSIONS_EN = "Sessions"
    const val NEW_SESSION_JA = "新規セッション"
    const val NEW_SESSION_EN = "New session"
    const val REFRESH_JA = "更新"
    const val REFRESH_EN = "Refresh"
    const val NO_SESSIONS_JA = "セッションがありません"
    const val NO_SESSIONS_EN = "No sessions"
    const val STOP_JA = "停止"
    const val STOP_EN = "Stop"
    const val SEND_JA = "送信"
    const val SEND_EN = "Send"
    const val INPUT_PLACEHOLDER_JA = "メッセージを入力…"
    const val INPUT_PLACEHOLDER_EN = "Type a message…"
    const val RUNNING_JA = "実行中"
    const val RUNNING_EN = "Running"
    const val IDLE_JA = "待機中"
    const val IDLE_EN = "Idle"
    const val TOOL_RUNNING_JA = "実行中…"
    const val TOOL_RUNNING_EN = "Running…"
    const val TOOL_DONE_JA = "完了"
    const val TOOL_DONE_EN = "Done"
    const val TOOL_FAILED_JA = "失敗"
    const val TOOL_FAILED_EN = "Failed"
    const val REASONING_JA = "思考"
    const val REASONING_EN = "Reasoning"
    const val ARGS_JA = "引数"
    const val ARGS_EN = "Arguments"
    const val RESULT_JA = "結果"
    const val RESULT_EN = "Result"
    const val APPROVAL_JA = "ツール承認を求めています"
    const val APPROVAL_EN = "Tool approval requested"
    const val APPROVE_JA = "一度だけ許可"
    const val APPROVE_EN = "Allow once"
    const val REJECT_JA = "拒否"
    const val REJECT_EN = "Reject"
    const val QUESTION_JA = "質問に答えてください"
    const val QUESTION_EN = "Please answer"
    const val OTHER_JA = "その他（自由入力）"
    const val OTHER_EN = "Other (free text)"
    const val SUBMIT_JA = "送信"
    const val SUBMIT_EN = "Submit"
    const val LOAD_OLDER_JA = "より古いメッセージを読み込む"
    const val LOAD_OLDER_EN = "Load older messages"
    const val LOADING_JA = "読み込み中…"
    const val LOADING_EN = "Loading…"
    const val ERROR_JA = "エラー"
    const val ERROR_EN = "Error"
    const val TRUST_HINT_JA =
        "サーバーは dsh web で起動し、このホスト名を --trusted-host で許可している必要があります。"
    const val TRUST_HINT_EN =
        "Start the server with dsh web and allow this hostname via --trusted-host."
    const val REFETCHED_JA = "再接続しました。履歴を再取得します"
    const val REFETCHED_EN = "Reconnected; refetching history"
    const val BLANK_TITLE_JA = "新しいセッション"
    const val BLANK_TITLE_EN = "New session"
}

/** Last path segment of [path] for either separator style; null when absent. */
internal fun basenameOf(path: String?): String? =
    path?.split('/', '\\')?.lastOrNull()?.takeIf { it.isNotEmpty() }

/** Japanese primary line with the English translation alongside in a smaller muted style. */
@Composable
fun BiText(
    ja: String,
    en: String,
    modifier: Modifier = Modifier,
    jaSize: Float = 14f,
    enSize: Float = 11f,
    weight: FontWeight = FontWeight.Normal,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = ja,
            fontSize = jaSize.sp,
            fontWeight = weight,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = en,
            fontSize = enSize.sp,
            color = Color(0xFF9E9E9E),
        )
    }
}
