/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.console

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.zeroclaw.android.model.ChatMessage
import com.zeroclaw.android.model.ProcessedImage
import com.zeroclaw.android.ui.component.LoadingIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** Bubble corner radius in dp. */
private const val BUBBLE_CORNER_DP = 16

/** Bubble padding in dp. */
private const val BUBBLE_PADDING_DP = 12

/** Message spacing in dp. */
private const val MESSAGE_SPACING_DP = 8

/** Bottom input bar padding in dp. */
private const val INPUT_BAR_PADDING_DP = 8

/** Max bubble width fraction of screen. */
private const val MAX_BUBBLE_WIDTH_FRACTION = 0.8f

/** Loading indicator size in dp. */
private const val LOADING_INDICATOR_DP = 24

/** Small spacing in dp. */
private const val SMALL_SPACING_DP = 4

/** Max bubble width multiplier. */
private const val BUBBLE_WIDTH_MULTIPLIER = 400

/** Pending thumbnail size in dp. */
private const val THUMBNAIL_SIZE_DP = 56

/** Thumbnail corner radius in dp. */
private const val THUMBNAIL_CORNER_DP = 8

/** Dismiss badge size in dp. */
private const val DISMISS_BADGE_DP = 18

/** Message image grid item size in dp. */
private const val IMAGE_GRID_SIZE_DP = 80

/** Maximum images per picker invocation. */
private const val MAX_PICKER_IMAGES = 5

/** Counter badge corner radius in dp. */
private const val COUNTER_BADGE_CORNER_DP = 12

/** Counter badge horizontal padding in dp. */
private const val COUNTER_BADGE_H_PAD_DP = 8

/** Counter badge vertical padding in dp. */
private const val COUNTER_BADGE_V_PAD_DP = 2

/** Timestamp format for chat messages. */
private val chatTimeFormat = SimpleDateFormat("HH:mm", Locale.US)

/**
 * Global daemon console screen for sending messages to the ZeroClaw gateway.
 *
 * Displays a chronological message list with user messages right-aligned
 * and daemon responses left-aligned. Includes a text input bar at the
 * bottom with an image attach button, a pending thumbnail strip, a clear
 * history action, and timestamps on each message. Long-press a message
 * to copy its content to the clipboard.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param consoleViewModel The [ConsoleViewModel] for message state.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun ConsoleScreen(
    edgeMargin: Dp,
    consoleViewModel: ConsoleViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val messages by consoleViewModel.messages.collectAsStateWithLifecycle()
    val isLoading by consoleViewModel.isLoading.collectAsStateWithLifecycle()
    val pendingImages by consoleViewModel.pendingImages.collectAsStateWithLifecycle()
    val isProcessingImages by consoleViewModel.isProcessingImages.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }

    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKER_IMAGES),
        ) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                consoleViewModel.attachImages(uris)
            }
        }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding(),
        ) {
            ClearHistoryBar(
                onClear = { consoleViewModel.clearHistory() },
                hasMessages = messages.isNotEmpty(),
                modifier = Modifier.padding(horizontal = edgeMargin),
            )

            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = edgeMargin),
                verticalArrangement = Arrangement.spacedBy(MESSAGE_SPACING_DP.dp),
            ) {
                items(
                    items = messages,
                    key = { it.id },
                    contentType = { if (it.isFromUser) "user" else "daemon" },
                ) { message ->
                    ChatBubble(
                        message = message,
                        onCopy = {
                            copyToClipboard(context, message.content)
                            scope.launch {
                                snackbarHostState.showSnackbar("Copied to clipboard")
                            }
                        },
                        onRetry =
                            if (!message.isFromUser && message.content.startsWith("Error:")) {
                                {
                                    val lastUserMsg =
                                        messages
                                            .lastOrNull { it.isFromUser && it.id < message.id }
                                    lastUserMsg?.let { consoleViewModel.sendMessage(it.content) }
                                }
                            } else {
                                null
                            },
                    )
                }

                if (isLoading) {
                    item(key = "loading", contentType = "loading") {
                        TypingIndicator()
                    }
                }
            }

            if (pendingImages.isNotEmpty() || isProcessingImages) {
                PendingImageStrip(
                    images = pendingImages,
                    isProcessing = isProcessingImages,
                    onRemove = { consoleViewModel.removeImage(it) },
                    modifier = Modifier.padding(horizontal = edgeMargin),
                )
            }

            ChatInputBar(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = {
                    consoleViewModel.sendMessage(inputText)
                    inputText = ""
                },
                onAttach = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                isLoading = isLoading,
                hasImages = pendingImages.isNotEmpty(),
                modifier =
                    Modifier.padding(
                        horizontal = edgeMargin,
                        vertical = INPUT_BAR_PADDING_DP.dp,
                    ),
            )
        }
    }
}

/**
 * Horizontal strip of pending image thumbnails with dismiss buttons.
 *
 * Shows a count badge (e.g. "3/5") and a loading indicator while images
 * are being processed.
 *
 * @param images Currently staged images.
 * @param isProcessing Whether images are still being processed.
 * @param onRemove Callback to remove an image by index.
 * @param modifier Modifier applied to the strip.
 */
@Composable
private fun PendingImageStrip(
    images: List<ProcessedImage>,
    isProcessing: Boolean,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = SMALL_SPACING_DP.dp),
        ) {
            Text(
                text = "${images.size}/$MAX_PICKER_IMAGES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(COUNTER_BADGE_CORNER_DP.dp),
                        ).padding(
                            horizontal = COUNTER_BADGE_H_PAD_DP.dp,
                            vertical = COUNTER_BADGE_V_PAD_DP.dp,
                        ),
            )
            if (isProcessing) {
                Spacer(modifier = Modifier.width(MESSAGE_SPACING_DP.dp))
                LoadingIndicator(modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(SMALL_SPACING_DP.dp))
                Text(
                    text = "Processing...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(MESSAGE_SPACING_DP.dp),
            contentPadding = PaddingValues(vertical = SMALL_SPACING_DP.dp),
        ) {
            itemsIndexed(
                items = images,
                key = { _, img -> img.originalUri },
            ) { index, image ->
                PendingThumbnail(
                    image = image,
                    onRemove = { onRemove(index) },
                )
            }
        }
    }
}

/**
 * Single pending image thumbnail with a dismiss badge.
 *
 * @param image The processed image to display.
 * @param onRemove Callback when the dismiss badge is tapped.
 */
@Composable
private fun PendingThumbnail(
    image: ProcessedImage,
    onRemove: () -> Unit,
) {
    Box(modifier = Modifier.size(THUMBNAIL_SIZE_DP.dp)) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalContext.current)
                    .data(Uri.parse(image.originalUri))
                    .build(),
            contentDescription = image.displayName,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(THUMBNAIL_CORNER_DP.dp)),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .size(DISMISS_BADGE_DP.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
                    .clickable(onClick = onRemove)
                    .semantics { contentDescription = "Remove ${image.displayName}" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/**
 * Image grid displayed inside a user message bubble.
 *
 * Shows thumbnails of attached images in a horizontal row.
 *
 * @param imageUris Content URIs of the attached images.
 */
@Composable
private fun MessageImageGrid(imageUris: List<String>) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(SMALL_SPACING_DP.dp),
        contentPadding = PaddingValues(bottom = MESSAGE_SPACING_DP.dp),
    ) {
        items(
            items = imageUris,
            key = { it },
        ) { uriString ->
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(Uri.parse(uriString))
                        .build(),
                contentDescription = "Attached image",
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(IMAGE_GRID_SIZE_DP.dp)
                        .clip(RoundedCornerShape(THUMBNAIL_CORNER_DP.dp)),
            )
        }
    }
}

/**
 * Top bar with a clear history button aligned to the end.
 *
 * @param onClear Callback when the clear button is tapped.
 * @param hasMessages Whether there are messages to clear.
 * @param modifier Modifier applied to the bar.
 */
@Composable
private fun ClearHistoryBar(
    onClear: () -> Unit,
    hasMessages: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        IconButton(
            onClick = onClear,
            enabled = hasMessages,
            modifier =
                Modifier.semantics {
                    contentDescription = "Clear chat history"
                },
        ) {
            Icon(
                Icons.Outlined.DeleteSweep,
                contentDescription = null,
                tint =
                    if (hasMessages) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
            )
        }
    }
}

/**
 * Chat bubble displaying a single message with timestamp and optional image grid.
 *
 * User messages are right-aligned with primary container color.
 * Daemon messages are left-aligned with surface variant color.
 * Long-press to copy message content. Error responses show a retry button.
 * Messages with images show a thumbnail grid above the text.
 *
 * @param message The chat message to display.
 * @param onCopy Callback invoked when the user copies the message.
 * @param onRetry Optional callback for retrying a failed message.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    message: ChatMessage,
    onCopy: () -> Unit,
    onRetry: (() -> Unit)?,
) {
    val isUser = message.isFromUser
    val isError = !isUser && message.content.startsWith("Error:")

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            Surface(
                shape = RoundedCornerShape(BUBBLE_CORNER_DP.dp),
                color =
                    when {
                        isError -> MaterialTheme.colorScheme.errorContainer
                        isUser -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                modifier =
                    Modifier
                        .widthIn(max = MAX_BUBBLE_WIDTH_FRACTION.dp * BUBBLE_WIDTH_MULTIPLIER)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = onCopy,
                        ).semantics {
                            contentDescription =
                                if (isUser) "Your message" else "Daemon response"
                        },
            ) {
                Column(modifier = Modifier.padding(BUBBLE_PADDING_DP.dp)) {
                    if (message.imageUris.isNotEmpty()) {
                        MessageImageGrid(message.imageUris)
                    }
                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                when {
                                    isError -> MaterialTheme.colorScheme.onErrorContainer
                                    isUser -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(SMALL_SPACING_DP.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = SMALL_SPACING_DP.dp),
                    ) {
                        Text(
                            text = chatTimeFormat.format(Date(message.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                when {
                                    isError ->
                                        MaterialTheme.colorScheme.onErrorContainer.copy(
                                            alpha = 0.7f,
                                        )
                                    isUser ->
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                            alpha = 0.7f,
                                        )
                                    else ->
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = 0.7f,
                                        )
                                },
                        )
                        if (onRetry != null) {
                            IconButton(
                                onClick = onRetry,
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Replay,
                                    contentDescription = "Retry message",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Typing indicator shown while waiting for the daemon's response.
 */
@Composable
private fun TypingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = SMALL_SPACING_DP.dp),
    ) {
        LoadingIndicator(
            modifier = Modifier.size(LOADING_INDICATOR_DP.dp),
        )
        Spacer(modifier = Modifier.width(MESSAGE_SPACING_DP.dp))
        Text(
            text = "Thinking...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Input bar with an attach button, text field, and send button.
 *
 * @param value Current input text.
 * @param onValueChange Callback when text changes.
 * @param onSend Callback when the send button is tapped.
 * @param onAttach Callback when the attach button is tapped.
 * @param isLoading Whether a response is in progress (disables send).
 * @param hasImages Whether images are currently attached.
 * @param modifier Modifier applied to the input bar.
 */
@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    isLoading: Boolean,
    hasImages: Boolean,
    modifier: Modifier = Modifier,
) {
    val canSend = (value.isNotBlank() || hasImages) && !isLoading

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onAttach,
            enabled = !isLoading,
            modifier =
                Modifier.semantics {
                    contentDescription = "Attach images"
                },
        ) {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                tint =
                    if (!isLoading) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("Type a message") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(MESSAGE_SPACING_DP.dp))
        IconButton(
            onClick = onSend,
            enabled = canSend,
            modifier =
                Modifier.semantics {
                    contentDescription = "Send message"
                },
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint =
                    if (canSend) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

/**
 * Copies the given text to the system clipboard.
 *
 * @param context Android context for system service access.
 * @param text The text to copy.
 */
private fun copyToClipboard(
    context: Context,
    text: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Console message", text)
    clipboard.setPrimaryClip(clip)
}
