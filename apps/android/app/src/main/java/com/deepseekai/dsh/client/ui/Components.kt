package com.deepseekai.dsh.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseekai.dsh.client.core.ChatFold
import com.deepseekai.dsh.client.core.PendingApproval
import com.deepseekai.dsh.client.core.PendingQuestion
import com.deepseekai.dsh.client.core.QuestionAnswer
import org.json.JSONObject

/** Renders one folded chat row. */
@Composable
fun RowView(row: ChatFold.Row) {
    when (row) {
        is ChatFold.UserRow -> UserBubble(row.text)
        is ChatFold.AssistantRow -> AssistantBubble(row)
        is ChatFold.ToolRow -> ToolCard(row)
        is ChatFold.NoteRow -> NoteView(row.text, row.isError)
    }
}

@Composable
fun UserBubble(text: String) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        // Cap the bubble at 78% of the row so a long prompt keeps visible
        // breathing room at both screen edges instead of running edge-to-edge.
        Text(
            text = text,
            color = Color.White,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = maxWidth * 0.78f)
                .padding(12.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    shape = MaterialTheme.shapes.medium,
                ),
        )
    }
}

@Composable
fun AssistantBubble(row: ChatFold.AssistantRow) {
    val reasoning = row.reasoning
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp),
    ) {
        if (reasoning.isNotEmpty()) {
            ExpandableSection(
                label = "● ${L.REASONING_JA} / ${L.REASONING_EN}",
                content = reasoning,
                monospace = false,
            )
        }
        val text = row.text
        if (text.isNotEmpty()) {
            Text(text = text, color = MaterialTheme.colorScheme.onSurface)
        }
        if (row.streaming && text.isEmpty() && reasoning.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.height(14.dp).width(14.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(L.LOADING_JA, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (row.interrupted) {
            Text(
                text = "(interrupted) / （中断）",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ToolCard(row: ChatFold.ToolRow) {
    val status = when {
        row.pending -> "⋯ ${L.TOOL_RUNNING_JA} / ${L.TOOL_RUNNING_EN}"
        row.isError == true -> "✗ ${L.TOOL_FAILED_JA} / ${L.TOOL_FAILED_EN}"
        else -> "✓ ${L.TOOL_DONE_JA} / ${L.TOOL_DONE_EN}"
    }
    val statusColor = when {
        row.pending -> MaterialTheme.colorScheme.onSurfaceVariant
        row.isError == true -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                .padding(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = status, fontSize = 11.sp, color = statusColor)
            }
            if (row.args.isNotEmpty()) {
                ExpandableSection(label = "${L.ARGS_JA} / ${L.ARGS_EN}", content = prettyJson(row.args), monospace = true)
            }
            val result = row.result
            if (result != null) {
                ExpandableSection(
                    label = "${L.RESULT_JA} / ${L.RESULT_EN}",
                    content = result,
                    monospace = true,
                    contentColor = if (row.isError == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun NoteView(text: String, isError: Boolean) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp, horizontal = 16.dp),
    )
}

/** Collapsible block with a preview line; expanded content is scroll-capped. */
@Composable
fun ExpandableSection(
    label: String,
    content: String,
    monospace: Boolean,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var expanded by remember { mutableStateOf(false) }
    val preview = content.lineSequence().firstOrNull().orEmpty()
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (expanded) "▾" else "▸",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(16.dp),
            )
            Text(
                text = if (expanded) label else "$label — ${preview.take(80)}",
                fontSize = 11.sp,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (expanded) {
            Text(
                text = content,
                fontSize = 11.sp,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                color = contentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    // A LazyColumn item is measured with unbounded height, so
                    // a nested scroll needs an explicit cap.
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp),
            )
        }
    }
}

/** Approval card: allow-once / reject answered through /api/respond. */
@Composable
fun ApprovalCard(
    approval: PendingApproval,
    onAllow: () -> Unit,
    onReject: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium)
                .padding(12.dp),
        ) {
            Text("${L.APPROVAL_JA} / ${L.APPROVAL_EN}", style = MaterialTheme.typography.titleSmall)
            Text(
                text = approval.toolName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (approval.reason?.isNotEmpty() == true) {
                Text(
                    text = approval.reason,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onAllow, modifier = Modifier.weight(1f)) {
                    Text("${L.APPROVE_JA} / ${L.APPROVE_EN}", fontSize = 12.sp)
                }
                OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                    Text("${L.REJECT_JA} / ${L.REJECT_EN}", fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Question card: one card per pending batch. Each question contributes a
 * selection (single or multi per multiSelect) plus an optional free-text
 * "other" answer; Submit settles the whole batch in one /api/respond.
 */
@Composable
fun QuestionCard(pending: PendingQuestion, onSubmit: (List<QuestionAnswer>) -> Unit) {
    val questions = pending.questions
    // Backed by snapshot state: option taps replace the map value so the
    // markers recompose; a plain mutable map would keep the stale "○".
    var selections: Map<String, List<String>> by remember(questions) {
        mutableStateOf(
            HashMap<String, List<String>>().apply { questions.forEach { put(it.id, emptyList()) } }
        )
    }
    var customs: Map<String, String> by remember(questions) {
        mutableStateOf(HashMap<String, String>())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 16.dp),
    ) {
        // No nested scroll here: a LazyColumn item is measured with
        // unbounded height, so a nested verticalScroll would crash; the
        // list scrolls the (possibly tall) card instead.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.medium)
                .padding(12.dp),
        ) {
            Text(
                "${L.QUESTION_JA} / ${L.QUESTION_EN}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            for ((index, question) in questions.withIndex()) {
                if (index > 0) Spacer(modifier = Modifier.height(12.dp))
                val header = question.header?.takeIf { it.isNotEmpty() }
                if (header != null) {
                    Text(header, fontSize = 11.sp, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
                }
                Text(question.question, style = MaterialTheme.typography.bodyLarge)
                question.options.forEach { option ->
                    val selected = selections[question.id]?.contains(option.label) == true
                    OptionRow(
                        label = option.label,
                        description = option.description,
                        selected = selected,
                        multi = question.multiSelect,
                        onClick = {
                            val current = selections[question.id] ?: emptyList()
                            val next = when {
                                selected -> current - option.label
                                question.multiSelect -> current + option.label
                                else -> listOf(option.label)
                            }
                            selections = selections.toMutableMap().apply { put(question.id, next) }
                        },
                    )
                }
                TextField(
                    value = customs[question.id] ?: "",
                    onValueChange = { customs = customs.toMutableMap().apply { put(question.id, it) } },
                    placeholder = { Text("${L.OTHER_JA} / ${L.OTHER_EN}", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    colors = TextFieldDefaults.colors(),
                )
            }
            Button(
                onClick = {
                    onSubmit(
                        questions.map { q ->
                            QuestionAnswer(
                                id = q.id,
                                selected = (selections[q.id] ?: emptyList()).toList(),
                                custom = customs[q.id]?.takeIf { it.isNotBlank() },
                            )
                        },
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text("${L.SUBMIT_JA} / ${L.SUBMIT_EN}", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    description: String?,
    selected: Boolean,
    multi: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when {
                selected && multi -> "☑"
                selected -> "●"
                multi -> "☐"
                else -> "○"
            },
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp)
            if (description?.isNotEmpty() == true) {
                Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}

private fun prettyJson(raw: String): String {
    return try {
        JSONObject(raw).toString(2)
    } catch (e: Exception) {
        raw
    }
}
