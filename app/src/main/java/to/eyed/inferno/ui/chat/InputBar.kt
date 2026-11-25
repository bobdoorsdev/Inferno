package to.eyed.inferno.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
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
import to.eyed.inferno.engine.EngineState
import to.eyed.inferno.ui.S
import to.eyed.inferno.ui.components.GhostIconButton
import to.eyed.inferno.ui.components.GlassMenu
import to.eyed.inferno.ui.components.MenuActionRow
import to.eyed.inferno.ui.components.QuietNotice
import to.eyed.inferno.ui.components.surfaceRaised
import to.eyed.inferno.ui.theme.ChipLabel
import to.eyed.inferno.ui.theme.Ink
import to.eyed.inferno.ui.theme.Radii
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
    canAttachImages: Boolean,
    cameraAvailable: Boolean,
    noVisionReason: String?,
    onPickPhotos: () -> Unit,
    onCapturePhoto: () -> Unit,
    onPickFile: () -> Unit,
    onGenerateImage: () -> Unit,
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
    val hasContent = value.isNotBlank() || attachments.isNotEmpty()
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
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = enabledCard }
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
                    AttachButton(canAttachImages, cameraAvailable, noVisionReason, idle, onPickPhotos, onCapturePhoto, onPickFile, onGenerateImage, onNoVision)
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
    LazyRow(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(attachments.size, key = { attachments[it].id }) { i ->
            Box(Modifier.animateItem()) {
                AsyncImage(
                    model = attachments[i].thumbPath,
                    contentDescription = S.attachedImage,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(Radii.control)).background(Ink.SurfaceHigh),
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) { haptics.tap(); onRemove(i) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Lucide.X, S.removeImage, Modifier.size(12.dp), tint = Ink.White) }
            }
        }
    }
}

/** The plus remains visible even when a model cannot accept images; unavailable image actions are disabled. */
@Composable
private fun AttachButton(
    canAttachImages: Boolean, cameraAvailable: Boolean, noVisionReason: String?, idle: Boolean,
    onPickPhotos: () -> Unit, onCapturePhoto: () -> Unit, onPickFile: () -> Unit, onGenerateImage: () -> Unit, onNoVision: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    Box {
        GhostIconButton(Lucide.Plus, S.attach, onClick = { haptics.tap(); open = true }, size = 38.dp, iconSize = 21.dp, tint = Ink.I100, enabled = !idle)
        GlassMenu(expanded = open, onDismiss = { open = false }, minWidth = 196.dp) {
            AttachmentMenuItem(Lucide.FileText, "Choose file", enabled = !idle) { open = false; onPickFile() }
            AttachmentMenuItem(Lucide.Image, S.createImage, enabled = !idle) { open = false; onGenerateImage() }
            AttachmentMenuItem(Lucide.Images, S.photos, enabled = canAttachImages && !idle) {
                open = false
                if (canAttachImages) onPickPhotos() else onNoVision(noVisionReason ?: S.noVision)
            }
            if (cameraAvailable) AttachmentMenuItem(Lucide.Camera, S.camera, enabled = canAttachImages && !idle) {
                open = false
                if (canAttachImages) onCapturePhoto() else onNoVision(noVisionReason ?: S.noVision)
            }
            if (!canAttachImages) {
                Text(noVisionReason ?: S.noVision, style = Typography.bodySmall, color = Ink.I500,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun AttachmentMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
            enabled = enabled, role = Role.Button, onClick = onClick,
        ).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, Modifier.size(17.dp), tint = if (enabled) Ink.I100 else Ink.I500)
        Text(label, style = Typography.bodyMedium, color = if (enabled) Ink.I100 else Ink.I500)
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
    val active = enabled && (hasContent || streaming || queued)
    val subdued = loading || calibrating || prefill != null || !active
    val interaction = remember { MutableInteractionSource() }
    val bg by animateColorAsState(if (subdued) Ink.SurfaceHigh else Ink.White, fastEffectsSpec(), label = "sendBg")
    val restScale by animateFloatAsState(if (active) 1f else 0.94f, fastSpatialSpec(), label = "sendScale")
    Box(
        Modifier.size(38.dp).graphicsLayer { scaleX = restScale; scaleY = restScale }
            .pressScale(interaction, 0.94f).clip(CircleShape).background(bg)
            .clickable(interactionSource = interaction, indication = null, enabled = active, role = Role.Button) {
                if (streaming || queued) onStop() else onSend()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (streaming || queued) Icon(Lucide.Square, S.stop, Modifier.size(16.dp), tint = if (subdued) Ink.I500 else Ink.Pitch)
        else Icon(Lucide.ArrowUp, S.send, Modifier.size(18.dp), tint = if (subdued) Ink.I500 else Ink.Pitch)
    }
}

