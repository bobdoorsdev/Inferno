package to.eyed.inferno.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lightbulb
import com.composables.icons.lucide.Table
import com.composables.icons.lucide.FileDown
import com.composables.icons.lucide.Archive
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.GitBranch
import com.composables.icons.lucide.Bookmark
import com.composables.icons.lucide.BookmarkCheck
import com.composables.icons.lucide.Wand
import com.composables.icons.lucide.NotebookPen
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.Volume2
import kotlinx.coroutines.delay
import to.eyed.inferno.data.AssistantToolRouter
import to.eyed.inferno.data.Attachment
import to.eyed.inferno.data.ChatMessage
import to.eyed.inferno.data.ChatRepository
import to.eyed.inferno.data.MessageStats
import to.eyed.inferno.ui.S
import to.eyed.inferno.ui.components.GhostIconButton
import to.eyed.inferno.ui.components.GlassMenu
import to.eyed.inferno.ui.components.Hairline
import to.eyed.inferno.ui.components.MenuActionRow
import to.eyed.inferno.ui.components.copyToClipboard
import to.eyed.inferno.ui.components.surfaceHigh
import to.eyed.inferno.ui.components.surfaceLow
import to.eyed.inferno.ui.theme.Ink
import to.eyed.inferno.ui.theme.Meta
import to.eyed.inferno.ui.theme.Radii
import to.eyed.inferno.ui.theme.RowMeta
import to.eyed.inferno.ui.theme.Typography
import to.eyed.inferno.ui.theme.whiteA
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import to.eyed.inferno.ui.theme.defaultEffectsSpec
import to.eyed.inferno.ui.theme.defaultSpatialSpec
import to.eyed.inferno.ui.theme.fastSpatialSpec
import to.eyed.inferno.ui.theme.rememberHaptics
import to.eyed.inferno.vm.GenState
import to.eyed.inferno.vm.isBusy
import java.util.Locale

/** Readable ceiling for the conversation column on wide windows. */
val ChatContentMaxWidth = 800.dp

/**
 * Which developer surfaces the list may draw (SettingsState.dev* readers, all false for a normal user): the
 * meta line under the latest answer, the Details action, and the live token counter while streaming.
 * [tokenWords] (power user and up) lets the prefill line and the trimmed divider say "tokens" and "context window";
 * a normal user reads plain words instead.
 */
data class ChatDevFlags(val generationStats: Boolean = false, val turnDetails: Boolean = false, val tokenCounter: Boolean = false, val tokenWords: Boolean = false)

/** What the list shows for the latest turn while it is in flight. */
private sealed interface Live {
    data object None : Live
    /** The last persisted assistant row is the one streaming: draw it with the live text and a caret. */
    data class Row(val id: String) : Live
    /** No assistant row yet (prefill / thinking / first tokens before Room catches up): an extra item at the bottom. */
    data object Item : Live
}

private fun liveFor(messages: List<ChatMessage>, gen: GenState): Live {
    if (!gen.isBusy || gen is GenState.Queued) return Live.None
    val last = messages.lastOrNull()
    return if (last != null && last.role == ChatRepository.ROLE_ASSISTANT && gen is GenState.Streaming && gen.text.isNotBlank()) Live.Row(last.id) else Live.Item
}

/**
 * Reversed list: index 0 sits at the visual bottom so the streaming turn stays pinned with no scroll
 * math. Keys are message ids; the width cap is applied through content padding (not a narrower list)
 * so scroll gestures keep working across the whole pane.
 */
@Composable
fun SharedTransitionScope.MessagesList(
    messages: List<ChatMessage>,
    gen: GenState,
    listState: LazyListState,
    trimmedBefore: Int,
    announceId: String?,
    imageScope: AnimatedVisibilityScope,
    onRegenerate: () -> Unit,
    onBranch: (ChatMessage) -> Unit,
    onOpenImage: (Attachment) -> Unit,
    onOpenTurnDetails: (ChatMessage) -> Unit,
    onEdit: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
    /** Height of the composer overlaid on the list, so the last turn can always scroll clear of it. */
    bottomInset: Dp = 132.dp,
    dev: ChatDevFlags = ChatDevFlags(),
    toolStatus: String? = null,
    toolSearchNotes: String? = null,
    citationUrls: List<String> = emptyList(),
    streamingAnimations: Boolean = true,
    savedAnswerIds: Set<String> = emptySet(),
    onSaveAnswer: (ChatMessage) -> Unit = {},
    onFollowUp: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val live = liveFor(messages, gen)
    val liveMeta = (gen as? GenState.Streaming)?.takeIf { dev.tokenCounter && it.tokens > 0 }?.let { S.liveTokens(it.tokens, String.format(Locale.US, "%.1f", it.tokPerSec)) }
    val lastAssistantId = messages.lastOrNull { it.role == ChatRepository.ROLE_ASSISTANT }?.id
    BoxWithConstraints(modifier) {
        val sidePad = ((maxWidth - ChatContentMaxWidth) / 2).coerceAtLeast(0.dp) + 16.dp
        val bubbleMax = if (maxWidth >= 600.dp) 460.dp else 300.dp
        // Placement springs make a send glide; while an answer streams the live item grows every frame, and
        // a lagging spring would drag the rows above it over the new text, so they snap instead.
        val placement: FiniteAnimationSpec<IntOffset>? = if (gen.isBusy) null else defaultSpatialSpec()
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = sidePad, end = sidePad, top = 12.dp, bottom = bottomInset),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.Bottom),
        ) {
            if (live is Live.Item) item(key = "live", contentType = "live") {
                if (gen is GenState.Streaming && gen.text.isNotBlank()) {
                    AssistantMessage(
                        text = gen.text, reasoning = gen.reasoning, thinkingMs = gen.thinkingMs, streaming = true, streamingAnimations = streamingAnimations,
                        stats = null, showActions = false, showRegenerate = false, announce = false,
                        searchNotes = toolSearchNotes, toolStatus = toolStatus, citationUrls = citationUrls,
                        onRegenerate = {}, onBranch = {}, onDetails = {}, liveMeta = liveMeta,
                    )
                } else if (toolStatus != null || toolSearchNotes != null) {
                    AssistantActivityPanel(
                        reasoning = (gen as? GenState.Thinking)?.reasoning.orEmpty(),
                        searchNotes = toolSearchNotes,
                        status = toolStatus,
                        live = true,
                        thinkingMs = (gen as? GenState.Thinking)?.thinkingMs ?: 0L,
                    )
                } else ThinkingIndicator(gen, tokenWords = dev.tokenWords)
            }
            // Summary rows are appended after the turns they cover; they are drawn at their compaction point instead
            // (right below the last covered message), carried-over ones at the very top.
            val summaries = messages.filter { it.isSummary }
            val turns = messages.filter { !it.isSummary }
            val anchored = summaries.groupBy { it.compactedThrough }
            for (i in turns.indices.reversed()) {
                val m = turns[i]
                anchored[m.orderIndex]?.forEach { sm -> item(key = sm.id, contentType = "summary") { SummaryCard(sm, Modifier.animateItem(fadeInSpec = tween(180), fadeOutSpec = tween(120), placementSpec = placement)) } }
                item(key = m.id, contentType = m.role) {
                    if (m.role == ChatRepository.ROLE_USER) {
                        UserBubble(m, bubbleMax, imageScope, onOpenImage, onEdit, Modifier.animateItem(fadeInSpec = tween(180), fadeOutSpec = tween(120), placementSpec = placement))
                    } else {
                        val streamingRow = (live as? Live.Row)?.id == m.id
                        val s = gen as? GenState.Streaming
                        AssistantMessage(
                            text = if (streamingRow && s != null) s.text else m.content,
                            reasoning = if (streamingRow && s != null) s.reasoning else m.thinking.orEmpty(),
                            thinkingMs = if (streamingRow && s != null) s.thinkingMs else m.stats?.thinkingMs ?: 0L,
                            streaming = streamingRow,
                            streamingAnimations = streamingAnimations,
                            searchNotes = if (streamingRow) toolSearchNotes else null,
                            toolStatus = if (streamingRow) toolStatus else null,
                            citationUrls = citationUrls,
                            stats = m.stats,
                            showActions = !gen.isBusy,
                            showRegenerate = m.id == lastAssistantId,
                            showMeta = m.id == lastAssistantId && !streamingRow,
                            announce = m.id == announceId,
                            onRegenerate = onRegenerate,
                            onBranch = { onBranch(m) },
                            onDetails = { onOpenTurnDetails(m) },
                            saved = m.id in savedAnswerIds,
                            onToggleSaved = { onSaveAnswer(m) },
                            onFollowUp = onFollowUp,
                            modifier = Modifier.animateItem(fadeInSpec = tween(180), fadeOutSpec = tween(120), placementSpec = placement),
                            showDetails = dev.turnDetails,
                            techMeta = dev.generationStats,
                            liveMeta = if (streamingRow) liveMeta else null,
                        )
                    }
                }
                if (trimmedBefore > 0 && m.orderIndex == trimmedBefore) item(key = "trimmed", contentType = "trimmed") { TrimmedDivider(dev.tokenWords) }
            }
            val turnIndexes = turns.map { it.orderIndex }.toSet()
            summaries.filter { it.compactedThrough !in turnIndexes }.forEach { sm ->
                item(key = sm.id, contentType = "summary") { SummaryCard(sm, Modifier.animateItem(fadeInSpec = tween(180), fadeOutSpec = tween(120), placementSpec = placement)) }
            }
        }
    }
}

/**
 * Collapsed "Summary of earlier messages" card at a compaction point (ContextPolicy.COMPACT): the model's own notes
 * on everything above it. Tap to read; "Compacted N messages" says how much history it stands for.
 */
@Composable
private fun SummaryCard(msg: ChatMessage, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(msg.id) { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, fastSpatialSpec(), label = "summaryChevron")
    val subtitle = if (msg.compactedThrough < 0) S.carriedOverSummary else S.compactedMessages(msg.compactedCount)
    Column(
        modifier
            .fillMaxWidth()
            .surfaceLow(RoundedCornerShape(Radii.row))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { haptics.tap(); expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Lucide.NotebookPen, null, Modifier.size(16.dp), tint = Ink.I300)
            Column(Modifier.weight(1f)) {
                Text(S.summaryOfEarlier, style = Typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Ink.I100)
                Text(subtitle, style = RowMeta, color = Ink.I500)
            }
            Icon(Lucide.ChevronDown, null, Modifier.size(16.dp).graphicsLayer { rotationZ = rotation }, tint = Ink.I500)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(defaultSpatialSpec()) + fadeIn(defaultEffectsSpec()),
            exit = shrinkVertically(defaultSpatialSpec()) + fadeOut(defaultEffectsSpec()),
        ) {
            Box(Modifier.padding(top = 10.dp)) { MarkdownBody(msg.content) }
        }
    }
}

/** Right-aligned SurfaceHigh bubble; images above the text open the viewer (shared bounds). Long-press: Edit / Copy. */
@Composable
private fun SharedTransitionScope.UserBubble(
    msg: ChatMessage,
    maxBubbleWidth: Dp,
    imageScope: AnimatedVisibilityScope,
    onOpenImage: (Attachment) -> Unit,
    onEdit: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var menu by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (msg.images.isNotEmpty()) {
            val imageTile = ((maxBubbleWidth - 8.dp) / 2).coerceAtMost(160.dp)
            Row(
                Modifier.widthIn(max = maxBubbleWidth).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                msg.images.forEachIndexed { index, att ->
                    Box {
                        AsyncImage(
                            model = att.thumbPath,
                            contentDescription = "Attached image ${index + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(imageTile)
                                .sharedBounds(rememberSharedContentState(imageSharedKey(att)), imageScope)
                                .clip(RoundedCornerShape(Radii.row))
                                .border(1.dp, whiteA(0.10f), RoundedCornerShape(Radii.row))
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Image,
                                    onClick = { onOpenImage(att) },
                                ),
                        )
                        Box(
                            Modifier.align(Alignment.BottomStart).padding(7.dp)
                                .clip(RoundedCornerShape(7.dp)).background(Ink.Pitch.copy(alpha = 0.70f))
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                        ) {
                            Text("IMAGE ${index + 1}", style = RowMeta.copy(fontWeight = FontWeight.SemiBold), color = Ink.White)
                        }
                    }
                }
            }
            if (msg.content.isNotBlank()) Spacer(Modifier.height(8.dp))
        }
        if (msg.files.isNotEmpty()) {
            Column(Modifier.widthIn(max = maxBubbleWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                msg.files.forEach { file ->
                    val kind = file.displayName.substringAfterLast('.', file.mimeType.substringAfterLast('/')).uppercase().take(6)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(Ink.SurfaceHigh).border(1.dp, whiteA(0.07f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 11.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Ink.I900), contentAlignment = Alignment.Center) {
                            Text(kind, style = RowMeta.copy(fontWeight = FontWeight.SemiBold), color = Ink.I100, maxLines = 1)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(file.displayName, style = Typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Ink.White, maxLines = 1)
                            Text("${file.mimeType.substringAfter('/').substringBefore(';').uppercase()} · ${android.text.format.Formatter.formatShortFileSize(context, file.sizeBytes)}",
                                style = RowMeta, color = Ink.I500, maxLines = 1)
                        }
                        Icon(Lucide.FileText, null, Modifier.size(17.dp), tint = Ink.I500)
                    }
                }
            }
            if (msg.content.isNotBlank()) Spacer(Modifier.height(8.dp))
        }
        if (msg.content.isNotBlank()) Box {
            Box(
                Modifier
                    .widthIn(max = maxBubbleWidth)
                    .surfaceHigh(RoundedCornerShape(Radii.card))
                    .animateContentSize(tween(135))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                        onLongClickLabel = S.messageActions,
                        onLongClick = { haptics.longPress(); menu = true },
                    )
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                // No SelectionContainer here: its long-press would fight the bubble menu (Copy lives in the menu).
                Text(msg.content, style = Typography.bodyLarge.copy(textDirection = TextDirection.Content), color = Ink.White)
            }
            GlassMenu(expanded = menu, onDismiss = { menu = false }, minWidth = 160.dp) {
                MenuActionRow(Lucide.Pencil, S.edit) { menu = false; onEdit(msg) }
                MenuActionRow(Lucide.Copy, S.copy) { menu = false; copyToClipboard(context, msg.content, "Inferno message") }
            }
        }
    }
}

private data class AssistantCodeFile(val path: String, val content: String)
private data class AssistantFileExport(val files: List<AssistantCodeFile>, val zip: Boolean)

private fun extractCodeArtifacts(answer: String): List<AssistantCodeFile> {
    val fence = Regex("""(?ms)^\x60{3,}([A-Za-z0-9_+.-]+)(?:[ \t]+([^\r\n]+))?[ \t]*\r?\n(.*?)^\x60{3,}\s*$""")
    val extensions = mapOf(
        "lua" to "lua", "luau" to "lua", "python" to "py", "javascript" to "js", "js" to "js",
        "typescript" to "ts", "ts" to "ts", "kotlin" to "kt", "java" to "java", "json" to "json",
        "html" to "html", "css" to "css", "xml" to "xml", "bash" to "sh", "shell" to "sh",
        "sh" to "sh", "markdown" to "md", "md" to "md", "sql" to "sql", "yaml" to "yml",
        "yml" to "yml", "toml" to "toml", "rust" to "rs", "go" to "go", "c" to "c", "cpp" to "cpp",
    )
    val files = mutableListOf<AssistantCodeFile>()
    fence.findAll(answer).forEachIndexed { index, match ->
        val language = match.groupValues[1].lowercase()
        val extension = extensions[language] ?: "txt"
        val hinted = match.groupValues[2].trim()
        if (hinted.isBlank()) return@forEachIndexed
        val safe = hinted.replace('\\', '/').split('/')
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString("/") { it.replace(Regex("""[^A-Za-z0-9._-]"""), "_") }
        if (safe.isBlank()) return@forEachIndexed
        val path = if (safe.substringAfterLast('/').contains('.')) safe else safe + "." + extension
        if (files.none { it.path == path }) files += AssistantCodeFile(path, match.groupValues[3].trimEnd())
    }
    return files
}

/**
 * Assistant turn, no bubble: reasoning panel, markdown, caret while streaming, then the action row
 * (fades in once the engine is idle) and - on the latest turn only - the meta line from MessageStats.
 */
@Composable
private fun AssistantMessage(
    text: String,
    reasoning: String,
    thinkingMs: Long,
    streaming: Boolean,
    streamingAnimations: Boolean = true,
    stats: MessageStats?,
    showActions: Boolean,
    showRegenerate: Boolean,
    announce: Boolean,
    onRegenerate: () -> Unit,
    onBranch: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
    showMeta: Boolean = false,
    /** Developer mode: the Details action, the numbers in the meta line, and the live counter under the caret. */
    showDetails: Boolean = false,
    techMeta: Boolean = false,
    liveMeta: String? = null,
    searchNotes: String? = null,
    toolStatus: String? = null,
    citationUrls: List<String> = emptyList(),
    saved: Boolean = false,
    onToggleSaved: () -> Unit = {},
    onFollowUp: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val codeArtifacts = remember(text) { extractCodeArtifacts(text) }
    var markdownToExport by remember(text) { mutableStateOf<String?>(null) }
    var filesToExport by remember(text) { mutableStateOf<AssistantFileExport?>(null) }
    val markdownLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val payload = markdownToExport
        if (uri != null && payload != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(payload) }
                ?: error("Unable to write the Markdown file")
        }.onFailure { Toast.makeText(context, "Could not export Markdown", Toast.LENGTH_SHORT).show() }
        markdownToExport = null
    }
    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val payload = filesToExport
        if (uri != null && payload != null) runCatching {
            val output = context.contentResolver.openOutputStream(uri) ?: error("Unable to open destination")
            if (payload.zip) {
                ZipOutputStream(output).use { zip ->
                    payload.files.forEach { file ->
                        zip.putNextEntry(ZipEntry(file.path))
                        zip.write(file.content.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                }
            } else {
                output.use { it.write(payload.files.first().content.toByteArray(Charsets.UTF_8)) }
            }
        }.onFailure { Toast.makeText(context, "Could not export files", Toast.LENGTH_SHORT).show() }
        filesToExport = null
    }
    fun startMarkdownExport() {
        markdownToExport = text
        markdownLauncher.launch("answer.md")
    }
    fun startCodeExport() {
        if (codeArtifacts.isEmpty()) return
        val zipped = codeArtifacts.size > 1
        filesToExport = AssistantFileExport(codeArtifacts, zipped)
        filesLauncher.launch(if (zipped) "project.zip" else codeArtifacts.first().path.substringAfterLast('/'))
    }
    Column(modifier.fillMaxWidth()) {
        val activityContent = reasoning.isNotBlank() || !searchNotes.isNullOrBlank() || toolStatus != null
        if (activityContent) {
            AssistantActivityPanel(
                reasoning = reasoning,
                searchNotes = searchNotes,
                status = toolStatus,
                live = streaming,
                thinkingMs = thinkingMs.takeIf { it > 0 } ?: 0L,
            )
            Spacer(Modifier.height(10.dp))
        }
        if (text.isNotBlank()) MarkdownBody(text, streaming = streaming, animateStreaming = streamingAnimations, citationUrls = citationUrls)
        if (!streaming && stats?.finishReason == "CANCELLED") {
            Text("Stopped...", modifier = Modifier.padding(top = 5.dp), style = Typography.labelSmall, color = Ink.I500)
        }
        if (streaming && liveMeta != null) {
            Text(liveMeta, modifier = Modifier.padding(top = 5.dp), style = Meta, color = Ink.I500)
        }
        // TalkBack hears "Answer complete" exactly once, on the Done transition - never per token.
        if (announce) Box(Modifier.size(1.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = S.answerComplete })
        AnimatedVisibility(showActions, enter = fadeIn(defaultEffectsSpec()), exit = fadeOut(defaultEffectsSpec())) {
            var moreMenu by rememberSaveable(text) { mutableStateOf(false) }
            Row(
                Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedCopyButton(S.copyMessage) { copyToClipboard(context, text, "Inferno message") }
                AnimatedActionIcon(Lucide.Share2, "Share response") {
                    haptics.tap()
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    runCatching { context.startActivity(Intent.createChooser(intent, "Share response")) }
                }
                AnimatedActionIcon(Lucide.GitBranch, "Branch from this reply") { haptics.tap(); onBranch() }
                AnimatedActionIcon(if (saved) Lucide.BookmarkCheck else Lucide.Bookmark, if (saved) "Remove saved answer" else "Save answer", active = saved) { haptics.tap(); onToggleSaved() }
                if (showRegenerate) AnimatedActionIcon(Lucide.RefreshCw, S.regenerate) { haptics.tap(); onRegenerate() }
                Box {
                    AnimatedActionIcon(Lucide.ChevronDown, "More answer actions") { haptics.tap(); moreMenu = true }
                    GlassMenu(expanded = moreMenu, onDismiss = { moreMenu = false }, minWidth = 218.dp) {
                        MenuActionRow(Lucide.Search, "Verify key claims") { moreMenu = false; onFollowUp("Verify the important factual claims in your previous answer using current primary and independent sources. Correct anything unsupported and provide citations.") }
                        MenuActionRow(Lucide.ShieldCheck, "Find risks and gaps") { moreMenu = false; onFollowUp("Audit your previous answer for missing information, assumptions, risks, and possible errors. Be specific.") }
                        MenuActionRow(Lucide.ListChecks, "Turn into a checklist") { moreMenu = false; onFollowUp("Convert your previous answer into a practical, ordered checklist with actionable items.") }
                        MenuActionRow(Lucide.Lightbulb, "Give a concrete example") { moreMenu = false; onFollowUp("Add a realistic worked example that demonstrates the key points in your previous answer.") }
                        MenuActionRow(Lucide.Table, "Compare alternatives") { moreMenu = false; onFollowUp("Compare the practical alternatives mentioned or implied in your previous answer in a clear decision table.") }
                        MenuActionRow(Lucide.FileDown, "Export answer as Markdown") { moreMenu = false; startMarkdownExport() }
                        if (codeArtifacts.isNotEmpty()) MenuActionRow(Lucide.Archive, if (codeArtifacts.size == 1) "Download code file" else "Download project ZIP") { moreMenu = false; startCodeExport() }
                    }
                }
                if (stats != null && showDetails) AnimatedActionIcon(Lucide.Info, S.details) { haptics.tap(); onDetails() }
                if (showMeta && stats != null) metaLine(stats, techMeta)?.let { meta ->
                    Spacer(Modifier.size(6.dp))
                    Text(meta, style = Meta, color = Ink.I500)
                }
            }
        }
    }
}

/** Copy icon smoothly morphs into a checkmark without showing a text label. */
@Composable
private fun AnimatedCopyButton(label: String, onCopy: () -> Unit) {
    val haptics = rememberHaptics()
    var copied by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.90f else 1f, fastSpatialSpec(), label = "copyPress")
    androidx.compose.runtime.LaunchedEffect(copied) {
        if (copied) { delay(1_050); copied = false }
    }
    Box(
        Modifier.size(32.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(9.dp)).clickable(interactionSource = interaction, indication = null,
                role = Role.Button) { haptics.tap(); onCopy(); copied = true },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = copied,
            transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(90)) },
            label = "messageCopy",
        ) { done ->
            Icon(
                if (done) Lucide.Check else Lucide.Copy,
                contentDescription = if (done) "Copied" else label,
                Modifier.size(16.dp),
                tint = if (done) Ink.White else Ink.I500,
            )
        }
    }
}

/** Small press-and-release motion shared by Branch and Redo. */
@Composable
private fun AnimatedActionIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.90f else 1f, fastSpatialSpec(), label = "messageActionScale")
    val alpha by animateFloatAsState(if (pressed) 0.72f else 1f, fastSpatialSpec(), label = "messageActionAlpha")
    Box(
        Modifier.size(32.dp).graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .clip(CircleShape).background(if (active) whiteA(0.15f) else androidx.compose.ui.graphics.Color.Transparent).clickable(interactionSource = interaction, indication = null,
                role = Role.Button) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, Modifier.size(15.dp), tint = if (active) Ink.White else Ink.I500)
    }
}

/**
 * Developer mode: "12.4 tok/s · 843 tokens · 8.1 s · prefill 120 ms · ctx 1,204/8,192 · image 2.1 s", plus
 * "· stopped" / "· interrupted". A normal user ([tech] false) only gets the stopped / interrupted / failed word,
 * so a clean answer has no line at all. Null when there is nothing to say.
 */
internal fun metaLine(s: MessageStats, tech: Boolean = true): String? {
    val parts = mutableListOf<String>()
    if (tech && s.generatedTokens > 0 && s.decodeMs > 0) {
        parts += String.format(Locale.US, "%.1f tok/s", s.decodeTps)
        parts += "${String.format(Locale.US, "%,d", s.generatedTokens)} tokens"
        parts += formatDuration(s.decodeMs + s.prefillMs + s.imageEncodeMs)
        if (s.prefillMs > 0) parts += "prefill ${String.format(Locale.US, "%,d", s.prefillMs)} ms"
        if (s.nCtx > 0) parts += "ctx ${String.format(Locale.US, "%,d", s.kvUsedTokens)}/${String.format(Locale.US, "%,d", s.nCtx)}"
        if (s.imageEncodeMs > 0) parts += "image ${formatDuration(s.imageEncodeMs)}"
    }
    when {
        s.finishReason == null -> parts += S.interrupted
        s.finishReason == "CANCELLED" -> parts += S.stopped
        s.finishReason == "ERROR" -> parts += S.failed.lowercase()
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** Hairline row marking where the context window now starts (shown only when trimmedBefore > 0). */
@Composable
private fun TrimmedDivider(tokenWords: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Hairline(Modifier.weight(1f))
        Text(if (tokenWords) S.olderOutsideContext else S.olderForgotten, style = RowMeta, color = Ink.I500)
        Hairline(Modifier.weight(1f))
    }
}
