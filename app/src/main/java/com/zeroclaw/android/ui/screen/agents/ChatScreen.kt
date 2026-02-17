/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.screen.agents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeroclaw.android.model.ChatMessage

/** Bubble corner radius in dp. */
private const val BUBBLE_CORNER_DP = 16

/** Bubble padding in dp. */
private const val BUBBLE_PADDING_DP = 12

/** Message spacing in dp. */
private const val MESSAGE_SPACING_DP = 8

/** Bottom input bar height in dp. */
private const val INPUT_BAR_PADDING_DP = 8

/** Max bubble width fraction of screen. */
private const val MAX_BUBBLE_WIDTH_FRACTION = 0.8f

/** Loading indicator size in dp. */
private const val LOADING_INDICATOR_DP = 24

/** Small spacing in dp. */
private const val SMALL_SPACING_DP = 4

/**
 * CLI-style chat interface for an agent.
 *
 * Shows a message list with user messages right-aligned and agent
 * responses left-aligned. Includes a text input bar at the bottom
 * and a settings icon in the top bar for navigating to agent editing.
 *
 * @param agentId Unique identifier of the agent.
 * @param onNavigateToEdit Callback to navigate to the agent edit screen.
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param chatViewModel The [ChatViewModel] for message state.
 * @param modifier Modifier applied to the root layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    agentId: String,
    onNavigateToEdit: (String) -> Unit,
    edgeMargin: Dp,
    chatViewModel: ChatViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val agentName by chatViewModel.agentName.collectAsStateWithLifecycle()
    val messages by chatViewModel.messages.collectAsStateWithLifecycle()
    val isLoading by chatViewModel.isLoading.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(agentId) {
        chatViewModel.loadAgent(agentId)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(agentName.ifBlank { "Chat" }) },
                actions = {
                    IconButton(
                        onClick = { onNavigateToEdit(agentId) },
                        modifier = Modifier.semantics {
                            contentDescription = "Edit agent settings"
                        },
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = null)
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = edgeMargin),
                verticalArrangement = Arrangement.spacedBy(MESSAGE_SPACING_DP.dp),
            ) {
                items(
                    items = messages,
                    key = { it.id },
                    contentType = { if (it.isFromUser) "user" else "agent" },
                ) { message ->
                    ChatBubble(message = message)
                }

                if (isLoading) {
                    item(key = "loading", contentType = "loading") {
                        TypingIndicator()
                    }
                }
            }

            ChatInputBar(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = {
                    chatViewModel.sendMessage(inputText)
                    inputText = ""
                },
                isLoading = isLoading,
                modifier = Modifier.padding(
                    horizontal = edgeMargin,
                    vertical = INPUT_BAR_PADDING_DP.dp,
                ),
            )
        }
    }
}

/**
 * Chat bubble displaying a single message.
 *
 * User messages are right-aligned with primary container color.
 * Agent messages are left-aligned with surface variant color.
 *
 * @param message The chat message to display.
 */
@Composable
private fun ChatBubble(message: ChatMessage) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (message.isFromUser) {
            Alignment.CenterEnd
        } else {
            Alignment.CenterStart
        },
    ) {
        Surface(
            shape = RoundedCornerShape(BUBBLE_CORNER_DP.dp),
            color = if (message.isFromUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(
                max = MAX_BUBBLE_WIDTH_FRACTION.dp * 400,
            ),
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isFromUser) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(BUBBLE_PADDING_DP.dp),
            )
        }
    }
}

/**
 * Typing indicator shown while waiting for the agent's response.
 */
@Composable
private fun TypingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = SMALL_SPACING_DP.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(LOADING_INDICATOR_DP.dp),
            strokeWidth = 2.dp,
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
 * Input bar with a text field and send button.
 *
 * @param value Current input text.
 * @param onValueChange Callback when text changes.
 * @param onSend Callback when the send button is tapped.
 * @param isLoading Whether a response is in progress (disables send).
 * @param modifier Modifier applied to the input bar.
 */
@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            enabled = value.isNotBlank() && !isLoading,
            modifier = Modifier.semantics {
                contentDescription = "Send message"
            },
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = if (value.isNotBlank() && !isLoading) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
