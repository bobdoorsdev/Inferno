package to.eyed.inferno.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Brain
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Images
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Square
import com.composables.icons.lucide.X
import to.eyed.inferno.data.Attachment
import to.eyed.inferno.data.FileAttachment
import to.eyed.inferno.engine.EngineState
import to.eyed.inferno.ui.S
import to.eyed.inferno.ui.components.GlassMenu
import to.eyed.inferno.ui.components.MenuActionRow
import to.eyed.inferno.ui.components.QuietNotice
import to.eyed.inferno.ui.components.surfaceRaised
import to.eyed.inferno.ui.theme.ChipLabel
import to.eyed.inferno.ui.theme.Ink
import to.eyed.inferno.ui.theme.Radii
import to.eyed.inferno.ui.theme.RowMeta
import to.eyed.inferno.ui.theme.Typography
import to.eyed.inferno.ui.theme.defaultEffectsSpec
import to.eyed.inferno.ui.theme.defaultSpatialSpec
import to.eyed.inferno.ui.theme.fastEffectsSpec
import to.eyed.inferno.ui.theme.fastSpatialSpec
import to.eyed.inferno.ui.theme.pressScale
import to.eyed.inferno.ui.theme.rememberHaptics
import to.eyed.inferno.ui.theme.whiteA
import to.eyed.inferno.vm.GenState
import to.eyed.inferno.vm.isBusy

/** Composer: fade, notices, thumb strip, raised card with the field and the control row. */
@Composable
fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    attachments: List<Attachment>,
    onRemoveAttachment: (Int) -> Unit,
    files: List<FileAttachment>,
    onRemoveFile: (Int) -> Unit,
    canAttachImages: Boolean,
    cameraAvailable: Boolean,
    noVisionReason: String?,
    onPickPhotos: () -> Unit,
    onCapturePhoto: () -> Unit,
    onPickFile: () -> Unit,
    onNoVision: (String) -> Unit,
    calibrating: Boolean = false,
    gen: GenState,
    engine: EngineState,
    /** Non-null disables the card and becomes the placeholder ("Choose a model to start"). */
    disabledReason: String?,
    imageBusy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    thinkingAvailable: Boolean,
    thinkingOn: Boolean,
    onToggleThinking: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val idle = disabledReason != null
    val loading = engine is EngineState.Loading
    val streaming = gen.isBusy && gen !is GenState.Queued
    val hasContent = value.isNotBlank() || attachments.isNotEmpty() || files.isNotEmpty()
    val enabledCard by animateFloatAsState(if (idle) 0.6f else 1f, fastEffectsSpec(), label = "composerAlpha")
    val placeholder = when {
        disabledReason != null -> disabledReason
        engine is EngineState.Loading -> S.loadingModel(engine.model.displayName)
        else -> S.composerPlaceholder
    }
    Column(modifier.fillMaxWidth()) {
        // The one gradient in the app: lets the list fade under the composer.
        Box(Modifier.fillMaxWidth().height(32.dp).background(Brush.verticalGradient(0f to Color.Transparent, 1f to Ink.Pitch)))
        Column(Modifier.fillMaxWidth().background(Ink.Pitch).padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            ComposerNotice(gen, imageBusy)
            if (attachments.isNotEmpty()) ThumbStrip(attachments, onRemoveAttachment)
            if (files.isNotEmpty()) FileStrip(files, onRemoveFile)
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = enabledCard }
                    .animateContentSize(tween(220))
                    .surfaceRaised(RoundedCornerShape(Radii.composer)),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = !idle,
                    textStyle = TextStyle(color = Ink.White, fontSize = 16.sp, lineHeight = 22.sp),
                    cursorBrush = SolidColor(Ink.White),
                    maxLines = 7,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    decorationBox = { inner ->
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            if (value.isEmpty()) Text(placeholder, color = Ink.I500, fontSize = 16.sp, lineHeight = 22.sp)
                            inner()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp, max = 170.dp)
                        .onPreviewKeyEvent { e ->
                            // Hardware keyboards: Ctrl+Enter sends; plain Enter stays a newline.
                            if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && e.isCtrlPressed && hasContent && !streaming) { onSend(); true } else false
                        },
                )
                Row(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AttachButton(canAttachImages, cameraAvailable, noVisionReason, idle, onPickPhotos, onCapturePhoto, onPickFile, onNoVision)
                    Spacer(Modifier.width(2.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (thinkingAvailable) ThinkChip(thinkingOn, onToggleThinking)
                    }
                    Spacer(Modifier.width(2.dp))
                    SendButton(
                        hasContent = hasContent, streaming = streaming, loading = loading, queued = gen is GenState.Queued,
                        enabled = !idle && !imageBusy, prefill = gen as? GenState.Prefill, calibrating = calibrating,
                        onSend = onSend, onStop = onStop,
                    )
                }
            }
        }
    }
}

/** Queued turn / image generation notices above the card; they expand and collapse with the spatial spec. */
@Composable
private fun ComposerNotice(gen: GenState, imageBusy: Boolean) {
    val text = when {
        gen is GenState.Queued -> S.willSendWhenReady
        imageBusy -> S.generatingImage
        else -> null
    }
    AnimatedVisibility(
        visible = text != null,
        enter = expandVertically(defaultSpatialSpec()) + fadeIn(defaultEffectsSpec()),
        exit = shrinkVertically(defaultSpatialSpec()) + fadeOut(defaultEffectsSpec()),
    ) {
        var last by remember { mutableStateOf(text ?: "") }
        if (text != null) last = text
        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
            QuietNotice(last)
        }
    }
}

@Composable
private fun ThumbStrip(attachments: List<Attachment>, onRemove: (Int) -> Unit) {
    val haptics = rememberHaptics()
    val tileShape = RoundedCornerShape(15.dp)
    LazyRow(Modifier.padding(bottom = 9.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        items(attachments.size, key = { attachments[it].id }) { i ->
            Box(Modifier.animateItem().size(88.dp)) {
                AsyncImage(
                    model = attachments[i].thumbPath,
                    contentDescription = S.attachedImage,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(88.dp).clip(tileShape).border(1.dp, whiteA(0.10f), tileShape).background(Ink.SurfaceHigh),
                )
                Box(
                    Modifier.align(Alignment.BottomStart).padding(6.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.58f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text("IMAGE", style = RowMeta.copy(fontWeight = FontWeight.SemiBold), color = Ink.White)
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp)
                        .shadow(3.dp, CircleShape).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.72f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { haptics.tap(); onRemove(i) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Lucide.X, S.removeImage, Modifier.size(13.dp), tint = Ink.White) }
            }
        }
    }
}

/** The plus remains visible even when a model cannot accept images; unavailable image actions are disabled. */
@Composable
private fun FileStrip(files: List<FileAttachment>, onRemove: (Int) -> Unit) {
    val haptics = rememberHaptics()
    val context = LocalContext.current
    LazyRow(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        items(files.size, key = { files[it].id }) { index ->
            val file = files[index]
            val kind = file.displayName.substringAfterLast('.', file.mimeType.substringAfterLast('/')).uppercase().take(6)
            Row(
                Modifier.widthIn(min = 224.dp, max = 286.dp).clip(RoundedCornerShape(16.dp))
                    .background(Ink.SurfaceHigh).border(1.dp, whiteA(0.07f), RoundedCornerShape(16.dp))
                    .padding(start = 10.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Ink.I900), contentAlignment = Alignment.Center) {
                    Text(kind, style = RowMeta.copy(fontWeight = FontWeight.SemiBold), color = Ink.I100, maxLines = 1)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(file.displayName, color = Ink.White, style = Typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${file.mimeType.substringAfter('/').substringBefore(';').uppercase()} · ${android.text.format.Formatter.formatShortFileSize(context, file.sizeBytes)}",
                        color = Ink.I500, style = RowMeta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.size(29.dp).clip(CircleShape).background(whiteA(0.07f)).clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button,
                ) { haptics.tap(); onRemove(index) }, contentAlignment = Alignment.Center) {
                    Icon(Lucide.X, "Remove ${file.displayName}", Modifier.size(14.dp), tint = Ink.I300)
                }
            }
        }
    }
}

@Composable
private fun AttachButton(
    canAttachImages: Boolean, cameraAvailable: Boolean, noVisionReason: String?, idle: Boolean,
    onPickPhotos: () -> Unit, onCapturePhoto: () -> Unit, onPickFile: () -> Unit,
    onNoVision: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val rotation by animateFloatAsState(if (open) 45f else 0f, fastSpatialSpec(), label = "attachPlusRotation")
    Box {
        Box(
            Modifier.size(38.dp).graphicsLayer { rotationZ = rotation }.clip(CircleShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                    enabled = !idle, role = Role.Button) { haptics.tap(); open = !open },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.Plus, if (open) "Close attachments" else S.attach, Modifier.size(21.dp), tint = Ink.I100)
        }
        GlassMenu(expanded = open, onDismiss = { open = false }, offset = DpOffset(0.dp, (-10).dp), minWidth = 242.dp) {
            Column(Modifier.widthIn(min = 242.dp).padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Add to message", style = Typography.titleSmall, color = Ink.White,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 3.dp))
                AttachmentMenuItem(Lucide.Images, "Photos", "Choose from your library", enabled = canAttachImages && !idle) {
                    open = false
                    if (canAttachImages) onPickPhotos() else onNoVision(noVisionReason ?: S.noVision)
                }
                if (cameraAvailable) AttachmentMenuItem(Lucide.Camera, "Camera", "Take a new photo", enabled = canAttachImages && !idle) {
                    open = false
                    if (canAttachImages) onCapturePhoto() else onNoVision(noVisionReason ?: S.noVision)
                }
                AttachmentMenuItem(Lucide.FileText, "Files", "Documents, PDFs, and code", enabled = !idle) {
                    open = false; onPickFile()
                }
                if (!canAttachImages) {
                    Text(noVisionReason ?: S.noVision, style = Typography.bodySmall, color = Ink.I500,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp))
                }
            }
        }
    }
}

@Composable
private fun AttachmentMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
            .background(if (enabled) whiteA(0.035f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                enabled = enabled, role = Role.Button, onClick = { haptics.tap(); onClick() },
            ).padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Ink.I800), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(17.dp), tint = if (enabled) Ink.White else Ink.I500)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = Typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = if (enabled) Ink.White else Ink.I500)
            Text(subtitle, style = RowMeta, color = Ink.I500)
        }
    }
}

/** "Think" chip: white when on. Only shown for models whose template has thinking tags. */
@Composable
private fun ThinkChip(on: Boolean, onToggle: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (on) Ink.White else Ink.SurfaceHigh, fastEffectsSpec(), label = "thinkBg")
    val fg by animateColorAsState(if (on) Ink.Pitch else Ink.I300, fastEffectsSpec(), label = "thinkFg")
    Row(
        Modifier
            .pressScale(interaction, 0.96f)
            .clip(CircleShape)
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, role = Role.Switch) { haptics.toggle(!on); onToggle(!on) }
            .semantics { contentDescription = if (on) S.thinkOn else S.thinkOff }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Lucide.Brain, null, Modifier.size(14.dp), tint = fg)
        Text(S.think, style = ChipLabel, color = fg)
    }
}

/**
 * Send / Stop. Calibration and prefill are quiet, neutral states; loading never gets an attention-grabbing spinner.
 */
@Composable
private fun SendButton(
    hasContent: Boolean, streaming: Boolean, loading: Boolean, queued: Boolean, enabled: Boolean,
    prefill: GenState.Prefill?, calibrating: Boolean, onSend: () -> Unit, onStop: () -> Unit,
) {
    val stopping = streaming || queued
    val active = enabled && (hasContent || stopping)
    val subdued = loading || calibrating || (!stopping && (prefill != null || !active))
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(Ink.White.copy(alpha = if (subdued) 0.62f else 1f), fastEffectsSpec(), label = "sendBg")
    Box(
        Modifier.size(36.dp)
            .pressScale(interaction, 0.92f)
            .clip(CircleShape)
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, enabled = active, role = Role.Button) {
                if (stopping) onStop() else onSend()
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = stopping,
            transitionSpec = {
                (fadeIn(tween(110)) + scaleIn(initialScale = 0.78f)) togetherWith
                    (fadeOut(tween(85)) + scaleOut(targetScale = 0.78f))
            },
            label = "sendButtonIcon",
        ) { isStopping ->
            if (isStopping) {
                Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(Ink.Pitch))
            } else {
                Icon(Lucide.ArrowUp, S.send, Modifier.size(18.dp), tint = Ink.Pitch)
            }
        }
    }
}

