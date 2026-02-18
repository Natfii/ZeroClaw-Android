/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * A structured event emitted by the Rust `events.rs` module and delivered over the FFI event
 * stream.
 *
 * Events are serialised by the daemon as JSON objects with the schema
 * `{"id":N,"timestamp_ms":N,"kind":"...","data":{...}}`. The bridge layer parses that JSON and
 * constructs instances of this class before handing them to ViewModels or repositories.
 *
 * @property id Monotonically increasing identifier assigned by the daemon at event creation time.
 * @property timestampMs Unix epoch timestamp in milliseconds at which the event was recorded.
 * @property kind Machine-readable event category string (e.g. "request_completed",
 *   "component_restarted"). New kinds may be introduced by upstream releases.
 * @property data Arbitrary key-value payload accompanying the event. All values are pre-coerced
 *   to strings by the bridge so that the UI layer never needs a JSON parser at render time.
 */
data class DaemonEvent(
    val id: Long,
    val timestampMs: Long,
    val kind: String,
    val data: Map<String, String>,
)
