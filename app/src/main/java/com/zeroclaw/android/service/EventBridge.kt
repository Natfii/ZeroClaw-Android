/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.service

import com.zeroclaw.android.data.repository.ActivityRepository
import com.zeroclaw.android.model.ActivityType
import com.zeroclaw.android.model.DaemonEvent
import com.zeroclaw.ffi.FfiEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Bridge between the Rust event callback interface and the Kotlin reactive layer.
 *
 * Implements [FfiEventListener] so it can be registered with the native daemon via
 * [com.zeroclaw.ffi.registerEventListener]. Incoming JSON event strings are parsed
 * into [DaemonEvent] instances and emitted on a [SharedFlow] for ViewModel consumption.
 * Each event is also persisted to the [ActivityRepository] for the dashboard feed.
 *
 * The internal [MutableSharedFlow] uses [BufferOverflow.DROP_OLDEST] with a capacity of
 * 64 events to avoid back-pressure blocking the native callback thread.
 *
 * @param activityRepository Repository for persisting events to the activity feed.
 * @param scope Coroutine scope for asynchronous emission and persistence.
 */
class EventBridge(
    private val activityRepository: ActivityRepository,
    private val scope: CoroutineScope,
) : FfiEventListener {
    private val _events =
        MutableSharedFlow<DaemonEvent>(
            extraBufferCapacity = BUFFER_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /** Observable stream of daemon events, parsed from the native callback JSON. */
    val events: SharedFlow<DaemonEvent> = _events.asSharedFlow()

    /**
     * Called by the native layer when a new event is produced.
     *
     * Parses the JSON string into a [DaemonEvent], emits it on [events],
     * and records it in the [ActivityRepository]. Malformed JSON is silently
     * dropped to avoid crashing the native callback thread.
     *
     * @param eventJson Raw JSON event string from the Rust daemon.
     */
    override fun onEvent(eventJson: String) {
        val event = parseEvent(eventJson) ?: return
        scope.launch {
            _events.emit(event)
            val (type, message) = event.toActivityRecord()
            activityRepository.record(type, message)
        }
    }

    /**
     * Registers this bridge as the active event listener with the native daemon.
     *
     * Only one listener may be registered at a time; calling this replaces any
     * previously registered listener.
     */
    fun register() {
        com.zeroclaw.ffi.registerEventListener(this)
    }

    /**
     * Removes this bridge as the active event listener from the native daemon.
     *
     * After calling this method, no further [onEvent] callbacks will be received.
     */
    fun unregister() {
        com.zeroclaw.ffi.unregisterEventListener()
    }

    /** Constants for [EventBridge]. */
    companion object {
        private const val BUFFER_CAPACITY = 64
    }
}

/**
 * Parses a raw JSON event string into a [DaemonEvent].
 *
 * Expected schema: `{"id":N,"timestamp_ms":N,"kind":"...","data":{...}}`.
 *
 * @param json Raw JSON string from the native callback.
 * @return Parsed [DaemonEvent], or `null` if the JSON is malformed.
 */
private fun parseEvent(json: String): DaemonEvent? =
    try {
        val obj = JSONObject(json)
        val dataObj = obj.optJSONObject("data") ?: JSONObject()
        val dataMap = mutableMapOf<String, String>()
        for (key in dataObj.keys()) {
            dataMap[key] = dataObj.optString(key, "")
        }
        DaemonEvent(
            id = obj.getLong("id"),
            timestampMs = obj.getLong("timestamp_ms"),
            kind = obj.getString("kind"),
            data = dataMap,
        )
    } catch (
        @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
    ) {
        null
    }

/**
 * Maps a [DaemonEvent] to an [ActivityType] and human-readable message for persistence.
 *
 * @receiver The daemon event to convert.
 * @return Pair of [ActivityType] and formatted message string.
 */
private fun DaemonEvent.toActivityRecord(): Pair<ActivityType, String> {
    val type =
        when (kind) {
            "error" -> ActivityType.DAEMON_ERROR
            else -> ActivityType.FFI_CALL
        }
    val message =
        when (kind) {
            "llm_request" -> "LLM Request: ${data["provider"]} / ${data["model"]}"
            "llm_response" -> "LLM Response: ${data["provider"]} (${data["duration_ms"]}ms)"
            "tool_call" -> "Tool: ${data["tool"]} (${data["duration_ms"]}ms)"
            "tool_call_start" -> "Tool Starting: ${data["tool"]}"
            "channel_message" -> "Channel: ${data["channel"]} (${data["direction"]})"
            "error" -> "Error: ${data["component"]} — ${data["message"]}"
            "heartbeat_tick" -> "Heartbeat"
            "turn_complete" -> "Turn Complete"
            "agent_start" -> "Agent Start: ${data["provider"]} / ${data["model"]}"
            "agent_end" -> "Agent End (${data["duration_ms"]}ms)"
            else -> "Event: $kind"
        }
    return type to message
}
