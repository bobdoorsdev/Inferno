package to.eyed.inferno.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.delay
import to.eyed.inferno.data.AssistantToolRouter
import to.eyed.inferno.ui.S
import to.eyed.inferno.ui.components.MorphingMark
import to.eyed.inferno.ui.components.QuietNotice
import to.eyed.inferno.ui.components.surfaceLow
import to.eyed.inferno.ui.theme.Ink
import to.eyed.inferno.ui.theme.Radii
import to.eyed.inferno.ui.theme.Typography
import to.eyed.inferno.ui.theme.animationsEnabled
import to.eyed.inferno.ui.theme.caretBlinkSpec
import to.eyed.inferno.ui.theme.defaultEffectsSpec
import to.eyed.inferno.ui.theme.defaultSpatialSpec
import to.eyed.inferno.ui.theme.fastSpatialSpec
import to.eyed.inferno.ui.theme.pulseSpec
import to.eyed.inferno.ui.theme.rememberHaptics
import to.eyed.inferno.vm.GenState
import java.util.Locale

// The small live pieces of a turn: the reasoning panel, the pre-token line, the dots and the caret.
// Every decorative piece is `clearAndSetSemantics {}`; every loop honours animationsEnabled().

/** Reasoning body: 13 sp with a slightly looser 19 sp leading than rows. */
private val ReasoningText = Typography.bodyMedium.copy(lineHeight = 19.sp)

/** "8.1 s" / "1 min 8 s" - the only two duration shapes in the app. */
fun formatDuration(ms: Long): String {
    val s = ms / 1000.0
    return if (s < 60) String.format(Locale.US, "%.1f s", s)
    else "${(s / 60).toInt()} min ${(s % 60).toInt()} s"
}

/**
 * One persistent activity frame with compact Think/Search tabs. It stays in the transcript after
 * completion, collapses automatically, and can be reopened without opening two stacked cards.
 */
@Composable
fun AssistantActivityPanel(
    reasoning: String,
    searchNotes: String?,
    status: String?,
    live: Boolean,
    thinkingMs: Long,
    modifier: Modifier = Modifier,
) {
    val parsed = remember(reasoning) { AssistantToolRouter.splitNotes(reasoning) }
    val search = searchNotes?.takeIf { it.isNotBlank() } ?: parsed.search
    val think = listOfNotNull(parsed.reasoning.takeIf { it.isNotBlank() }, parsed.local?.takeIf { it.isNotBlank() }).joinToString("\n\n")
    val hasThink = think.isNotBlank()
    val hasSearch = !search.isNullOrBlank() || status != null
    var expanded by rememberSaveable { mutableStateOf(live) }
    var selected by rememberSaveable { mutableStateOf(if (hasSearch && !hasThink) "search" else "think") }
    LaunchedEffect(live) { if (live) expanded = true else expanded = false }
    LaunchedEffect(status, hasSearch, hasThink) {
        if (status != null || (hasSearch && !hasThink)) selected = "search"
        else if (hasThink && selected == "search" && !hasSearch) selected = "think"
    }
    val haptics = rememberHaptics()
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, fastSpatialSpec(), label = "activityChevron")
    val activeSearch = selected == "search" && hasSearch
    val title = when {
        activeSearch && status != null -> "Searching…"
        activeSearch -> if (search.isNullOrBlank()) "Search" else "Search results"
        live -> S.thinking
        thinkingMs >= 1_000 -> S.thoughtFor(formatDuration(thinkingMs))
        else -> S.reasoning
    }
    val tabFade = defaultEffectsSpec<Float>()
    Column(
        modifier.fillMaxWidth()
            .surfaceLow(RoundedCornerShape(Radii.row))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (hasThink && hasSearch) {
                    ActivityTab("Think", selected == "think") { haptics.tap(); selected = "think"; expanded = true }
                    ActivityTab("Search", selected == "search") { haptics.tap(); selected = "search"; expanded = true }
                } else {
                    Text(title, style = Typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Ink.I100)
                }
                if (live && (status != null || selected == "think")) PulsingDots()
            }
            androidx.compose.foundation.layout.Box(
                Modifier.size(32.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                ) { haptics.tap(); expanded = !expanded },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.ChevronDown, null, Modifier.size(16.dp).graphicsLayer { rotationZ = rotation }, tint = Ink.I500)
            }
        }
        if (hasThink && hasSearch) {
            Text(title, style = Typography.labelSmall, color = Ink.I500, modifier = Modifier.padding(top = 1.dp))
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(defaultSpatialSpec()) + fadeIn(defaultEffectsSpec()),
            exit = shrinkVertically(defaultSpatialSpec()) + fadeOut(defaultEffectsSpec()),
        ) {
            AnimatedContent(
                targetState = activeSearch,
                transitionSpec = { fadeIn(tabFade) togetherWith fadeOut(tabFade) },
                label = "activityTabContent",
            ) { showSearch ->
                val content = if (showSearch) search.orEmpty() else think
                if (content.isNotBlank()) {
                    val scroll = rememberScrollState()
                    LaunchedEffect(content, showSearch) { if (live) scroll.scrollTo(scroll.maxValue) }
                    Box(
                        Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(max = 260.dp).verticalScroll(scroll),
                    ) {
                        MarkdownBody(content, Modifier.padding(end = 4.dp))
                    }
                } else if (showSearch && status != null) {
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MorphingMark(20.dp, active = true)
                        Text("Finding relevant pages and sources", style = Typography.bodySmall, color = Ink.I500)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Tab,
            onClick = onClick,
        ).padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = Typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal), color = if (selected) Ink.White else Ink.I500)
        Box(Modifier.fillMaxWidth().heightIn(min = 2.dp).background(if (selected) Ink.White else Ink.SurfaceHigh, RoundedCornerShape(2.dp)))
    }
}

/**
 * Collapsible reasoning panel above the answer. Live: title "Thinking…" with dots and a window that
 * follows the tail (never truncated, so earlier thoughts never slide away). Done: "Thought for 12 s"
 * from the real thinkingMs, collapsed by default.
 */
@Composable
fun ReasoningPanel(reasoning: String, live: Boolean, thinkingMs: Long, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val hasToolNotes = reasoning.contains(AssistantToolRouter.NOTES_MARKER)
    val title = when {
        live -> S.thinking
        hasToolNotes -> S.usedTools
        thinkingMs >= 1_000 -> S.thoughtFor(formatDuration(thinkingMs))
        else -> S.reasoning
    }
    val chevronSpec = fastSpatialSpec<Float>()
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, chevronSpec, label = "chev")
    Column(
        modifier
            .fillMaxWidth()
            .surfaceLow(RoundedCornerShape(Radii.row))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
            ) { haptics.tap(); expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = Typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Ink.I300)
            if (live) { Spacer(Modifier.width(8.dp)); PulsingDots() }
            Spacer(Modifier.weight(1f))
            Icon(Lucide.ChevronDown, null, Modifier.size(16.dp).graphicsLayer { rotationZ = rotation }, tint = Ink.I500)
        }
        AnimatedVisibility(
            visible = expanded || live,
            enter = expandVertically(defaultSpatialSpec()) + fadeIn(defaultEffectsSpec()),
            exit = shrinkVertically(defaultSpatialSpec()) + fadeOut(defaultEffectsSpec()),
        ) {
            if (live && !expanded) {
                val scroll = rememberScrollState()
                LaunchedEffect(reasoning.length) { scroll.scrollTo(scroll.maxValue) }
                Box(Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(max = 160.dp).verticalScroll(scroll)) {
                    MarkdownBody(reasoning, Modifier.padding(end = 4.dp))
                }
            } else {
                MarkdownBody(reasoning, Modifier.padding(top = 10.dp))
            }
        }
    }
}

/**
 * The single pre-token affordance in the list. Prefill: one 13 sp line after a 250 ms grace so a
 * cache-hit turn (~100 ms) shows nothing at all; the progress itself lives in the Stop button ring.
 * Thinking: the live reasoning panel. Streaming with no text yet: the loading indicator alone.
 */
@Composable
fun ThinkingIndicator(gen: GenState, modifier: Modifier = Modifier, tokenWords: Boolean = true) {
    when (gen) {
        is GenState.Prefill -> {
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(250); shown = true }
            if (!shown) return
            val label = when {
                gen.encodingImage && gen.expectedMs > 0 -> "${S.readingImage} · ~${(gen.expectedMs + 999) / 1000} s"
                gen.encodingImage -> S.readingImage
                gen.total > 0 && tokenWords -> S.readingTokens(gen.total)
                gen.total > 0 -> S.readingMessages
                else -> S.preparing
            }
            Row(modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = Typography.bodyMedium, color = Ink.I300)
                PulsingDots()
            }
        }
        is GenState.Thinking -> ReasoningPanel(gen.reasoning, live = true, thinkingMs = gen.thinkingMs, modifier = modifier)
        is GenState.Streaming -> if (gen.text.isBlank()) {
            Column(modifier) {
                if (gen.reasoning.isNotBlank()) ReasoningPanel(gen.reasoning, live = false, thinkingMs = gen.thinkingMs, modifier = Modifier.padding(bottom = 10.dp))
                LoadingIndicator(Modifier.size(24.dp), color = Ink.White)
            }
        }
        GenState.Trimming -> QuietNotice(S.trimmingOlder, modifier)
        GenState.Compacting -> CompactingIndicator(modifier)
        else -> Unit
    }
}

/** The breathing mark next to "Compacting…": the model is writing its own notes, not an answer (ContextPolicy.COMPACT). */
@Composable
fun CompactingIndicator(modifier: Modifier = Modifier) {
    Row(modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MorphingMark(28.dp, active = true)
        Column {
            Text(S.compacting, style = Typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Ink.I100)
            Text(S.compactingDesc, style = Typography.bodySmall, color = Ink.I500)
        }
    }
}

/** Three 3 dp dots, 600 ms pulse staggered 200 ms; static at 70 % when animations are off. */
@Composable
fun PulsingDots(modifier: Modifier = Modifier) {
    val animations = animationsEnabled()
    val t = rememberInfiniteTransition(label = "dots")
    Row(modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { i ->
            val alpha by t.animateFloat(
                initialValue = if (animations) 0.2f else 0.7f,
                targetValue = if (animations) 1f else 0.7f,
                animationSpec = pulseSpec(i * 200),
                label = "dot$i",
            )
            Box(Modifier.size(3.dp).graphicsLayer { this.alpha = alpha }.background(Ink.I500, CircleShape))
        }
    }
}

/** 2 x 16 dp white caret, 450 ms blink; solid when animations are off. */
@Composable
fun BlinkingCaret(modifier: Modifier = Modifier) {
    val animations = animationsEnabled()
    val t = rememberInfiniteTransition(label = "caret")
    val alpha by t.animateFloat(
        initialValue = 1f,
        targetValue = if (animations) 0.15f else 1f,
        animationSpec = caretBlinkSpec(),
        label = "caretAlpha",
    )
    Box(
        modifier
            .clearAndSetSemantics {}
            .size(width = 2.dp, height = 16.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(Ink.White, CircleShape),
    )
}
