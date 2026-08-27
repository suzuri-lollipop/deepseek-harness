package com.deepseekai.dsh.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepseekai.dsh.client.core.ConnState
import com.deepseekai.dsh.client.core.DshClient
import com.deepseekai.dsh.client.core.SessionSummary
import com.deepseekai.dsh.client.core.WorkspaceView
import kotlinx.coroutines.launch

/** Subagent-origin marker on session.list rows; those sessions are hidden in the tree. */
private const val SUBAGENT_ORIGIN = "subagent"

/** Group key of the trailing Ungrouped bucket (sessions outside every workspace). */
private const val UNGROUPED_KEY = ""

/**
 * One group section of the session tree: a Workspace in Host order, or the
 * trailing Ungrouped bucket. [sessions] holds only the visible top-level
 * rows for the section.
 */
internal data class GroupSection(
    val key: String,
    val workspaceId: String?,
    val label: String,
    val cwd: String?,
    val sessions: List<SessionSummary>,
)

/** One flat row of the tree list: a group header or one of its sessions. */
private sealed interface TreeRow {
    val key: String

    data class Header(val group: GroupSection, val expanded: Boolean) : TreeRow {
        override val key: String get() = "g:${group.key}"
    }

    data class Session(val summary: SessionSummary) : TreeRow {
        override val key: String get() = "s:${summary.sessionId}"
    }
}

/**
 * Derive the grouped session tree from the session and workspace mirrors,
 * mirroring the web sidebar: workspaces in Host order with the Ungrouped
 * bucket trailing; subagent-origin sessions and archived sessions are hidden
 * everywhere; blank sessions stay hidden except the last-opened one (the
 * provisional New Session row).
 */
internal fun deriveGroups(
    sessions: List<SessionSummary>,
    workspaces: List<WorkspaceView>,
    archived: Set<String>,
    currentId: String?,
): List<GroupSection> {
    fun visible(summary: SessionSummary): Boolean =
        summary.origin != SUBAGENT_ORIGIN &&
            summary.sessionId !in archived &&
            (!summary.blank || summary.sessionId == currentId)

    val byId = sessions.associateBy { it.sessionId }
    val accounted = HashSet<String>()
    val groups = workspaces.map { workspace ->
        val members = workspace.sessionIds.mapNotNull { byId[it] }
        members.forEach { accounted.add(it.sessionId) }
        GroupSection(
            key = workspace.workspaceId,
            workspaceId = workspace.workspaceId,
            label = workspace.title,
            cwd = workspace.path,
            sessions = members.filter { visible(it) },
        )
    }
    val stray = sessions
        .filter { it.sessionId !in accounted && visible(it) }
        .sortedByDescending { it.updatedAt }
    return groups + GroupSection(
        key = UNGROUPED_KEY,
        workspaceId = null,
        label = L.UNGROUPED_JA,
        cwd = null,
        sessions = stray,
    )
}

/**
 * Target of the global new-session action: the last-opened session's
 * workspace first, then the most recently active workspace (newest member
 * session, falling back to the workspace creation time, ties keeping Host
 * order), then no workspace (host cwd).
 */
internal fun newSessionTarget(
    workspaces: List<WorkspaceView>,
    sessions: List<SessionSummary>,
    lastSessionId: String?,
): WorkspaceView? {
    if (workspaces.isEmpty()) return null
    lastSessionId?.let { id ->
        workspaces.firstOrNull { it.sessionIds.contains(id) }?.let { return it }
    }
    val byId = sessions.associateBy { it.sessionId }
    var selected: WorkspaceView? = null
    var selectedTime = Long.MIN_VALUE
    for (workspace in workspaces) {
        var latest = Long.MIN_VALUE
        for (sessionId in workspace.sessionIds) {
            val session = byId[sessionId]
            if (session != null) latest = maxOf(latest, session.updatedAt)
        }
        if (latest == Long.MIN_VALUE) latest = workspace.createdAt
        if (selected == null || latest > selectedTime) {
            selected = workspace
            selectedTime = latest
        }
    }
    return selected
}

/**
 * The session tree: workspace groups in Host order with per-workspace add,
 * the Ungrouped bucket trailing. Groups expand by default and collapse on
 * tap; the blank row of the last-opened session keeps the New Session title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    client: DshClient,
    lastSessionId: String?,
    onOpenSession: (String) -> Unit,
    onDisconnect: () -> Unit,
    onBack: () -> Unit,
) {
    val sessions by client.sessions.collectAsStateWithLifecycle()
    val workspaces by client.workspaces.collectAsStateWithLifecycle()
    val archived by client.archivedSessionIds.collectAsStateWithLifecycle()
    val state by client.state.collectAsStateWithLifecycle()
    val error by client.errorNote.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var creating by remember { mutableStateOf(false) }
    var creatingKey by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var collapsedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    val groups = remember(sessions, workspaces, archived, lastSessionId) {
        deriveGroups(sessions, workspaces, archived, lastSessionId)
    }
    val rows = remember(groups, collapsedKeys) {
        buildList {
            for (group in groups) {
                val expanded = group.key !in collapsedKeys
                add(TreeRow.Header(group, expanded))
                if (expanded) addAll(group.sessions.map { TreeRow.Session(it) })
            }
        }
    }
    val treeEmpty = workspaces.isEmpty() && groups.last().sessions.isEmpty()

    fun startNewSession(workspace: WorkspaceView?, groupKey: String?) {
        if (creating) return
        creating = true
        creatingKey = groupKey
        scope.launch {
            try {
                val id = client.connectWorkspace(workspace)
                onOpenSession(id)
            } catch (e: Exception) {
                // surfaced via client.errorNote
            } finally {
                creating = false
                creatingKey = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { BiText(L.SESSIONS_JA, L.SESSIONS_EN) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = L.BACK_JA)
                    }
                },
                actions = {
                    if (refreshing) {
                        CircularProgressIndicator()
                    }
                    IconButton(onClick = {
                        refreshing = true
                        scope.launch {
                            try {
                                client.refreshSessions()
                                client.refreshWorkspaces()
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
                onClick = { startNewSession(newSessionTarget(workspaces, sessions, lastSessionId), null) },
                icon = {
                    if (creating && creatingKey == null) CircularProgressIndicator()
                    else Icon(Icons.Filled.Add, contentDescription = null)
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
            if (treeEmpty) {
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
                    contentPadding = PaddingValues(8.dp),
                ) {
                    item(key = "section") {
                        BiText(
                            ja = L.WORKSPACES_JA,
                            en = L.WORKSPACES_EN,
                            jaSize = 12f,
                            enSize = 10f,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is TreeRow.Header -> GroupHeaderRow(
                                group = row.group,
                                expanded = row.expanded,
                                creating = creatingKey == row.group.key,
                                onToggle = {
                                    collapsedKeys =
                                        if (row.group.key in collapsedKeys) collapsedKeys - row.group.key
                                        else collapsedKeys + row.group.key
                                },
                                onCreate = {
                                    val workspace = workspaces.firstOrNull {
                                        it.workspaceId == row.group.workspaceId
                                    }
                                    startNewSession(workspace, row.group.key)
                                },
                            )

                            is TreeRow.Session -> SessionRow(
                                summary = row.summary,
                                current = row.summary.sessionId == lastSessionId,
                                onClick = { onOpenSession(row.summary.sessionId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One group header: chevron + folder, label, path, and the per-workspace add. */
@Composable
private fun GroupHeaderRow(
    group: GroupSection,
    expanded: Boolean,
    creating: Boolean,
    onToggle: () -> Unit,
    onCreate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (group.cwd != null) {
            Icon(
                imageVector = Icons.Filled.Home,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.label,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            group.cwd?.let {
                Text(
                    text = it,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (group.workspaceId != null) {
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                if (creating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onCreate, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = L.ADD_SESSION_JA,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One session row: the stored title (blank rows show the New Session label
 * without a time line), then the relative time and cwd. The last-opened
 * session gets a soft tint.
 */
@Composable
private fun SessionRow(summary: SessionSummary, current: Boolean, onClick: () -> Unit) {
    val running = summary.running
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (current) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (summary.blank) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = L.BLANK_TITLE_JA,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = L.BLANK_TITLE_EN,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = summary.title?.takeIf { it.isNotEmpty() }
                        ?: (basenameOf(summary.cwd) ?: summary.sessionId.take(8)),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            if (running) {
                Text(
                    text = L.RUNNING_JA,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (!summary.blank) {
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
}

/** Web-sidebar time buckets: now / min / h / d / mo / y. */
private fun relativeTime(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    val delta = System.currentTimeMillis() - epochMillis
    return when {
        delta < 60_000 -> "now"
        delta < 3_600_000 -> "${delta / 60_000}min"
        delta < 86_400_000 -> "${delta / 3_600_000}h"
        delta < 30 * 86_400_000 -> "${delta / 86_400_000}d"
        delta < 365 * 86_400_000 -> "${delta / (30 * 86_400_000)}mo"
        else -> "${delta / (365 * 86_400_000)}y"
    }
}
