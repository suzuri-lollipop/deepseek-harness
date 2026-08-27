package com.deepseekai.dsh.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepseekai.dsh.client.R
import com.deepseekai.dsh.client.core.ConnState
import com.deepseekai.dsh.client.core.DshClient
import com.deepseekai.dsh.client.core.PresetOption
import com.deepseekai.dsh.client.core.WorkspaceView
import kotlinx.coroutines.launch

/**
 * New-session hero: the web GUI's empty-session composition. Left rail (brand
 * fish, new chat, new terminal, sessions, settings), centered headline with
 * fish mark and preview badge, the workspace / agent-preset chip row, and
 * the composer card over the soft blue glow. Sending resolves the workspace
 * target (staged pick, else the shared new-session rule), stages the chosen
 * agent preset onto the blank session, prompts, and opens the chat.
 */
@Composable
fun HeroScreen(
    client: DshClient,
    lastSessionId: String?,
    onOpenSession: (String) -> Unit,
    onOpenSessions: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val sessions by client.sessions.collectAsStateWithLifecycle()
    val workspaces by client.workspaces.collectAsStateWithLifecycle()
    val presets by client.presets.collectAsStateWithLifecycle()
    val state by client.state.collectAsStateWithLifecycle()
    val clientError by client.errorNote.collectAsStateWithLifecycle()
    val host = (state as? ConnState.Ready)?.host

    var draft by rememberSaveable { mutableStateOf("") }
    // null = follow the default new-session rule (last session's workspace,
    // then the most recent workspace, then the host cwd).
    var stagedWorkspaceId by rememberSaveable { mutableStateOf<String?>(null) }
    // null = the host default preset; staging mirrors the web seat-store:
    // the pick applies to the next blank session and is consumed there.
    var stagedPresetId by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var wsDialog by remember { mutableStateOf(false) }
    var presetDialog by remember { mutableStateOf(false) }

    val defaultTarget = remember(workspaces, sessions, lastSessionId) {
        newSessionTarget(workspaces, sessions, lastSessionId)
    }
    val targetWorkspace: WorkspaceView? =
        stagedWorkspaceId?.let { id -> workspaces.firstOrNull { it.workspaceId == id } } ?: defaultTarget
    val workspaceLabel = targetWorkspace?.let { ws -> basenameOf(ws.path) ?: ws.title } ?: L.HOST_CWD_JA

    val selectedPreset: PresetOption? = stagedPresetId
        ?.let { id -> presets.firstOrNull { it.id == id } }
        ?: presets.firstOrNull { it.isDefault }
    val presetLabel = selectedPreset?.let { preset ->
        presetDisplayName(preset) + if (preset.broken != null) L.BROKEN_JA else ""
    } ?: L.DEFAULT_PRESET_JA

    fun notImplemented() {
        scope.launch { snackbar.showSnackbar("${L.NOT_IMPLEMENTED_JA} ${L.NOT_IMPLEMENTED_EN}") }
    }

    /** Rail new-chat: open the target's blank session without a prompt. */
    fun startNewChat() {
        if (busy) return
        busy = true
        sendError = null
        scope.launch {
            try {
                onOpenSession(client.connectWorkspace(targetWorkspace))
            } catch (e: Exception) {
                sendError = e.message
            } finally {
                busy = false
            }
        }
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || busy) return
        busy = true
        sendError = null
        scope.launch {
            try {
                val sessionId = client.connectWorkspace(targetWorkspace)
                stagedPresetId?.let { presetId ->
                    try {
                        client.selectAgentPreset(sessionId, presetId)
                        stagedPresetId = null // the stage is consumed once applied
                    } catch (e: Exception) {
                        // The session still opens; the note below shows the failure.
                    }
                }
                client.prompt(sessionId, text)
                draft = ""
                stagedWorkspaceId = null
                onOpenSession(sessionId)
            } catch (e: Exception) {
                sendError = e.message
            } finally {
                busy = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // The soft blue glow behind the centered stack (web HeroGlow: #6187D8,
        // ~8% center, fading out).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                DswGlow.copy(alpha = 0.10f),
                                DswGlow.copy(alpha = 0.04f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width / 2f, size.height * 0.60f),
                            radius = size.width * 0.55f,
                        ),
                    )
                },
        )

        Row(modifier = Modifier.fillMaxSize()) {
            HeroRail(
                onNewChat = ::startNewChat,
                onNewTerminal = ::notImplemented,
                onSessions = onOpenSessions,
                onSettings = onOpenSettings,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_fish),
                        contentDescription = null,
                        modifier = Modifier.width(34.dp),
                        tint = DswLabelPrimary,
                    )
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = L.HERO_HEADLINE_JA,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Medium,
                            color = DswLabelPrimary,
                        )
                        Text(
                            text = L.HERO_HEADLINE_EN,
                            fontSize = 13.sp,
                            color = DswLabelCaption,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .border(1.dp, DswBorderThin, RoundedCornerShape(24.dp))
                            .background(DswDeepSeek800, RoundedCornerShape(24.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = L.HERO_PREVIEW_JA,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = DswLabelPrimary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeroChip(
                        icon = painterResource(
                            if (targetWorkspace != null) R.drawable.ic_folder_open else R.drawable.ic_folder,
                        ),
                        label = workspaceLabel,
                        onClick = { wsDialog = true },
                    )
                    HeroChip(
                        icon = painterResource(R.drawable.ic_robot),
                        label = presetLabel,
                        onClick = { presetDialog = true },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DswBorderThin, RoundedCornerShape(22.dp))
                        .background(DswBgLayer2, RoundedCornerShape(22.dp))
                        .padding(top = 12.dp, bottom = 10.dp, start = 10.dp, end = 6.dp),
                ) {
                    TextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = {
                            BiText(L.HERO_PLACEHOLDER_JA, L.HERO_PLACEHOLDER_EN)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            disabledLabelColor = Color.Transparent,
                        ),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ToolRoundButton(Icons.Filled.Add, L.ADD_MORE_JA, onClick = ::notImplemented)
                        ToolRoundButton(painterResource(R.drawable.ic_shield), L.PERMISSION_JA, onClick = ::notImplemented)
                        ToolRoundButton(painterResource(R.drawable.ic_attach), L.ATTACH_JA, onClick = ::notImplemented)
                        Spacer(modifier = Modifier.weight(1f))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = host?.model ?: "—",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = DswLabelSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 140.dp),
                            )
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = L.MODEL_JA,
                                modifier = Modifier.size(16.dp),
                                tint = DswLabelCaption,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            onClick = { send() },
                            shape = CircleShape,
                            color = DswDeepSeek400.copy(alpha = if (draft.isNotBlank() && !busy) 1f else 0.4f),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_up),
                                contentDescription = L.SEND_JA,
                                modifier = Modifier.size(20.dp),
                                tint = Color(0xFF101828),
                            )
                        }
                    }
                }

                val note = sendError ?: clientError
                if (note != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = note,
                        color = DswError,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (wsDialog) {
            AlertDialog(
                onDismissRequest = { wsDialog = false },
                title = { Text(L.WORKSPACE_JA) },
                text = {
                    Column {
                        OptionRow(
                            label = L.HOST_CWD_JA,
                            sub = L.HOST_CWD_EN,
                            selected = targetWorkspace == null,
                            onClick = {
                                stagedWorkspaceId = null
                                wsDialog = false
                            },
                        )
                        HorizontalDivider(color = DswBorderThin)
                        if (workspaces.isEmpty()) {
                            Text(
                                text = L.WORKSPACE_EMPTY_JA,
                                color = DswLabelCaption,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                            )
                        }
                        for (workspace in workspaces) {
                            OptionRow(
                                label = workspace.title,
                                sub = workspace.path,
                                selected = targetWorkspace?.workspaceId == workspace.workspaceId,
                                onClick = {
                                    stagedWorkspaceId = workspace.workspaceId
                                    wsDialog = false
                                },
                            )
                        }
                    }
                },
                confirmButton = {},
            )
        }

        if (presetDialog) {
            AlertDialog(
                onDismissRequest = { presetDialog = false },
                title = { Text(L.PRESET_JA) },
                text = {
                    Column {
                        OptionRow(
                            label = L.DEFAULT_PRESET_JA,
                            sub = L.DEFAULT_PRESET_EN,
                            selected = stagedPresetId == null,
                            onClick = {
                                stagedPresetId = null
                                presetDialog = false
                            },
                        )
                        HorizontalDivider(color = DswBorderThin)
                        if (presets.isEmpty()) {
                            Text(
                                text = L.PRESET_EMPTY_JA,
                                color = DswLabelCaption,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                            )
                        }
                        for (preset in presets) {
                            OptionRow(
                                label = presetDisplayName(preset) +
                                    if (preset.broken != null) " — ${preset.broken}" else "",
                                sub = preset.description ?: preset.id,
                                selected = selectedPreset?.id == preset.id && stagedPresetId != null,
                                onClick = {
                                    stagedPresetId = preset.id
                                    presetDialog = false
                                },
                            )
                        }
                    }
                },
                confirmButton = {},
            )
        }
    }
}

/** The left icon rail of the hero composition. */
@Composable
private fun HeroRail(
    onNewChat: () -> Unit,
    onNewTerminal: () -> Unit,
    onSessions: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(56.dp)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Brand fish (home): visual anchor, not an action.
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(DswBorderThin),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_fish),
                contentDescription = L.HOME_JA,
                modifier = Modifier.width(26.dp),
                tint = DswLabelPrimary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        RailButton(painterResource(R.drawable.ic_chat_add), L.NEW_CHAT_JA, onClick = onNewChat)
        RailButton(painterResource(R.drawable.ic_terminal_add), L.NEW_TERMINAL_JA, onClick = onNewTerminal)
        RailButton(painterResource(R.drawable.ic_search), L.SESSIONS_JA, onClick = onSessions)
        Spacer(modifier = Modifier.weight(1f))
        RailButton(painterResource(R.drawable.ic_settings), L.SETTINGS_JA, onClick = onSettings)
    }
}

@Composable
private fun RailButton(painter: Painter, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        contentColor = DswLabelCaption,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painter, contentDescription = description, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * The workspace / preset chip above the composer card: icon + label +
 * chevron, transparent at rest with a press fill, mirroring the web chip.
 */
@Composable
private fun HeroChip(icon: Painter, label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(28.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        contentColor = DswLabelPrimary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = DswLabelCaption,
            )
        }
    }
}

/** The circular tool buttons of the composer accessory row (+, shield, clip). */
@Composable
private fun ToolRoundButton(image: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = DswBgLayer3,
        contentColor = DswLabelPrimary,
        modifier = Modifier.size(32.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(image, contentDescription = description, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun ToolRoundButton(painter: Painter, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = DswBgLayer3,
        contentColor = DswLabelPrimary,
        modifier = Modifier.size(32.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painter, contentDescription = description, modifier = Modifier.size(17.dp))
        }
    }
}

/** One row of the workspace / preset pick dialogs. */
@Composable
private fun OptionRow(label: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) DswBorderThin else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = DswLabelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!sub.isNullOrBlank()) {
                Text(
                    text = sub,
                    fontSize = 11.sp,
                    color = DswLabelCaption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = DswDeepSeek400,
            )
        }
    }
}
