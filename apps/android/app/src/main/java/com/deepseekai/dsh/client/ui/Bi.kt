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
import com.deepseekai.dsh.client.core.PresetOption

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

    const val APP_DOWNLOAD_JA = "アプリのインストール"
    const val APP_DOWNLOAD_EN = "Install the app"
    const val DOWNLOAD_APK_JA = "APK をダウンロード"
    const val DOWNLOAD_APK_EN = "Download APK"
    const val APK_DOWNLOADING_JA = "APK をダウンロード中…"
    const val APK_DOWNLOADING_EN = "Downloading APK…"
    const val INSTALL_APK_JA = "インストール"
    const val INSTALL_APK_EN = "Install"
    const val APK_INSTALL_HINT_JA = "インストール開始時にアプリは一旦終了します。"
    const val APK_INSTALL_HINT_EN = "The app closes briefly while the installer runs."
    const val APK_DOWNLOAD_FAILED_JA = "APK のダウンロードに失敗しました"
    const val APK_DOWNLOAD_FAILED_EN = "APK download failed"
    const val APK_NOT_ON_HOST_JA = "このサーバーは APK を配信していません"
    const val APK_NOT_ON_HOST_EN = "This host does not serve an APK"
    const val APK_INVALID_JA = "ダウンロードしたファイルは APK ではありません"
    const val APK_INVALID_EN = "Downloaded file is not an APK"
    const val APK_INVALID_URL_JA = "サーバー URL が不正です"
    const val APK_INVALID_URL_EN = "Server URL is invalid"
    const val REFETCHED_JA = "再接続しました。履歴を再取得します"
    const val REFETCHED_EN = "Reconnected; refetching history"
    const val BLANK_TITLE_JA = "新しいセッション"
    const val BLANK_TITLE_EN = "New session"
    const val WORKSPACES_JA = "ワークスペース"
    const val WORKSPACES_EN = "Workspaces"
    const val UNGROUPED_JA = "未グループ"
    const val UNGROUPED_EN = "Ungrouped"
    const val ADD_SESSION_JA = "ワークスペースにセッションを追加"
    const val ADD_SESSION_EN = "Add a session to this workspace"

    // Hero (new session) screen.
    const val HERO_HEADLINE_JA = "未知の世界へ"
    const val HERO_HEADLINE_EN = "Into the Unknown"
    const val HERO_PREVIEW_JA = "プレビュー"
    const val HERO_PREVIEW_EN = "Preview"
    const val HERO_PLACEHOLDER_JA = "作成したいことを説明してください"
    const val HERO_PLACEHOLDER_EN = "Describe what you want to build"
    const val CHOOSE_WORKSPACE_JA = "ワークスペースを選択"
    const val CHOOSE_WORKSPACE_EN = "Choose workspace"
    const val WORKSPACE_JA = "ワークスペース"
    const val WORKSPACE_EN = "Workspace"
    const val HOST_CWD_JA = "ホストカレントディレクトリ"
    const val HOST_CWD_EN = "Host cwd"
    const val WORKSPACE_EMPTY_JA = "ワークスペースが登録されていません"
    const val WORKSPACE_EMPTY_EN = "No workspaces registered"
    const val PRESET_JA = "エージェントプリセット"
    const val PRESET_EN = "Agent preset"
    const val PRESET_EMPTY_JA = "プリセットを取得できません"
    const val PRESET_EMPTY_EN = "No presets available"
    const val DEFAULT_PRESET_JA = "デフォルト"
    const val DEFAULT_PRESET_EN = "Default"
    const val BROKEN_JA = "（破損）"
    const val BROKEN_EN = " (broken)"
    const val NEW_CHAT_JA = "新規チャット"
    const val NEW_CHAT_EN = "New chat"
    const val NEW_TERMINAL_JA = "新規ターミナル"
    const val NEW_TERMINAL_EN = "New terminal"
    const val SETTINGS_JA = "設定"
    const val SETTINGS_EN = "Settings"
    const val BACK_JA = "戻る"
    const val BACK_EN = "Back"
    const val NOT_IMPLEMENTED_JA = "まだ実装されていません"
    const val NOT_IMPLEMENTED_EN = "Not implemented yet"
    const val ADD_MORE_JA = "追加"
    const val ADD_MORE_EN = "Add"
    const val PERMISSION_JA = "権限モード"
    const val PERMISSION_EN = "Permission mode"
    const val ATTACH_JA = "ファイルを添付"
    const val ATTACH_EN = "Attach files"
    const val MODEL_JA = "モデル"
    const val MODEL_EN = "Model"
    const val HOME_JA = "ホーム"
    const val HOME_EN = "Home"
    const val NO_WORKSPACE_HINT_JA = "ワークスペースがありません。ホストの作業ディレクトリで作成します。"
    const val NO_WORKSPACE_HINT_EN = "No workspaces; sessions start in the host cwd."
}

/** Last path segment of [path] for either separator style; null when absent. */
internal fun basenameOf(path: String?): String? =
    path?.split('/', '\\')?.lastOrNull()?.takeIf { it.isNotEmpty() }

/**
 * Display name for [preset], mirroring the web client's `presetDisplayText`:
 * a shipped (system) preset uses its localized built-in name, everything else
 * falls back to the preset's own metadata, then its id.
 */
internal fun presetDisplayName(preset: PresetOption): String =
    if (preset.trust == "system") BUILT_IN_PRESET_NAMES[preset.id] ?: preset.name ?: preset.id
    else preset.name ?: preset.id

/** Built-in preset display names; the web client's English locale copy. */
private val BUILT_IN_PRESET_NAMES = mapOf(
    "standard" to "Standard mode",
    "code" to "PTC mode",
    "minimal" to "Minimal mode",
    "cordis" to "Creator mode",
)

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
