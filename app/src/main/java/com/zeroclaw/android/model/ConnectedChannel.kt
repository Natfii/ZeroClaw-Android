/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * Input type for a channel configuration field, determining the
 * keyboard type and visual treatment in the form UI.
 */
enum class FieldInputType {
    /** Plain text input. */
    TEXT,

    /** Numeric input. */
    NUMBER,

    /** URL input with URL keyboard hints. */
    URL,

    /** Boolean toggle (switch). */
    BOOLEAN,

    /** Comma-separated list input. */
    LIST,

    /** Secret input with masked display and reveal toggle. */
    SECRET,
}

/**
 * Specification for a single configuration field within a [ChannelType].
 *
 * Each channel type declares its fields statically so the UI can render
 * a dynamic form without hard-coding per-channel layouts.
 *
 * @property key TOML key name matching the upstream Rust struct field.
 * @property label Human-readable label for the form field.
 * @property isRequired Whether the field must have a non-blank value to save.
 * @property isSecret Whether the value should be stored in encrypted preferences.
 * @property defaultValue Default value pre-filled in the form, empty if none.
 * @property inputType Determines keyboard type and visual treatment.
 */
data class ChannelFieldSpec(
    val key: String,
    val label: String,
    val isRequired: Boolean = false,
    val isSecret: Boolean = false,
    val defaultValue: String = "",
    val inputType: FieldInputType = FieldInputType.TEXT,
)

/**
 * Supported chat channel types matching upstream ZeroClaw channel configurations.
 *
 * Each entry declares its display name, TOML section key, and the list of
 * configuration fields required by the upstream Rust struct.
 *
 * @property displayName Human-readable name shown in the UI.
 * @property tomlKey Key used in the `[channels_config.<key>]` TOML section.
 * @property fields Ordered list of configuration field specifications.
 */
@Suppress("MagicNumber")
enum class ChannelType(
    val displayName: String,
    val tomlKey: String,
    val fields: List<ChannelFieldSpec>,
) {
    /** Telegram Bot API channel. */
    TELEGRAM(
        displayName = "Telegram",
        tomlKey = "telegram",
        fields = listOf(
            ChannelFieldSpec(
                "bot_token", "Bot Token",
                isRequired = true,
                isSecret = true,
                inputType = FieldInputType.SECRET,
            ),
            ChannelFieldSpec(
                "allowed_users", "Allowed Users",
                inputType = FieldInputType.LIST,
            ),
        ),
    ),

    /** Discord Bot channel. */
    DISCORD(
        displayName = "Discord",
        tomlKey = "discord",
        fields = listOf(
            ChannelFieldSpec(
                "bot_token", "Bot Token",
                isRequired = true,
                isSecret = true,
                inputType = FieldInputType.SECRET,
            ),
            ChannelFieldSpec("guild_id", "Guild ID"),
            ChannelFieldSpec(
                "allowed_users", "Allowed Users",
                inputType = FieldInputType.LIST,
            ),
            ChannelFieldSpec(
                "listen_to_bots", "Listen to Bots",
                defaultValue = "false",
                inputType = FieldInputType.BOOLEAN,
            ),
        ),
    ),

    /** Slack Bot channel. */
    SLACK(
        displayName = "Slack",
        tomlKey = "slack",
        fields = listOf(
            ChannelFieldSpec(
                "bot_token", "Bot Token",
                isRequired = true,
                isSecret = true,
                inputType = FieldInputType.SECRET,
            ),
            ChannelFieldSpec(
                "app_token", "App Token",
                isSecret = true,
                inputType = FieldInputType.SECRET,
            ),
            ChannelFieldSpec("channel_id", "Channel ID"),
            ChannelFieldSpec(
                "allowed_users", "Allowed Users",
                inputType = FieldInputType.LIST,
            ),
        ),
    ),

    /** WhatsApp Business API channel. */
    WHATSAPP(
        displayName = "WhatsApp",
        tomlKey = "whatsapp",
        fields = listOf(
            ChannelFieldSpec(
                "access_token", "Access Token",
                isRequired = true,
                isSecret = true,
                inputType = FieldInputType.SECRET,
            ),
            ChannelFieldSpec(
                "phone_number_id", "Phone Number ID",
                isRequired = true,
            ),
            ChannelFieldSpec(
                "verify_token", "Verify Token",
                isRequired = true,
                isSecret = true,
                inputType = FieldInputType.SECRET,
            ),
            ChannelFieldSpec(
                "app_secret", "App Secret",
                isSecret = true,
                inputType = FieldInputType.SECRET,
            ),
            ChannelFieldSpec(
                "allowed_numbers", "Allowed Numbers",
                inputType = FieldInputType.LIST,
            ),
        ),
    ),

    /** Matrix protocol channel. */
    MATRIX(
        displayName = "Matrix",
        tomlKey = "matrix",
        fields = listOf(
            ChannelFieldSpec(
                "homeserver", "Homeserver URL",
                isRequired = true,
                inputType = FieldInputType.URL,
            ),
            ChannelFieldSpec(
                "access_token", "Access Token",
                isRequired = true,
                isSecret = true,
                inputType = FieldInputType.SECRET,
            ),
            ChannelFieldSpec("room_id", "Room ID", isRequired = true),
            ChannelFieldSpec(
                "allowed_users", "Allowed Users",
                inputType = FieldInputType.LIST,
            ),
        ),
    ),

    /** Email (IMAP/SMTP) channel. */
    EMAIL(
        displayName = "Email",
        tomlKey = "email",
        fields = listOf(
            ChannelFieldSpec(
                "imap_host", "IMAP Host",
                isRequired = true,
            ),
            ChannelFieldSpec(
                "imap_port", "IMAP Port",
                defaultValue = "993",
                inputType = FieldInputType.NUMBER,
            ),
            ChannelFieldSpec(
                "imap_folder", "IMAP Folder",
                defaultValue = "INBOX",
            ),
            ChannelFieldSpec(
                "smtp_host", "SMTP Host",
                isRequired = true,
            ),
            ChannelFieldSpec(
                "smtp_port", "SMTP Port",
                defaultValue = "587",
                inputType = FieldInputType.NUMBER,
            ),
            ChannelFieldSpec(
                "smtp_tls", "SMTP TLS",
                defaultValue = "true",
                inputType = FieldInputType.BOOLEAN,
            ),
            ChannelFieldSpec(
                "username", "Username",
                isRequired = true,
            ),
            ChannelFieldSpec(
                "password", "Password",
                isRequired = true,
                isSecret = true,
                inputType = FieldInputType.SECRET,
            ),
            ChannelFieldSpec(
                "from_address", "From Address",
                isRequired = true,
            ),
            ChannelFieldSpec(
                "poll_interval_secs",
                "Poll Interval (seconds)",
                defaultValue = "60",
                inputType = FieldInputType.NUMBER,
            ),
            ChannelFieldSpec(
                "allowed_senders", "Allowed Senders",
                inputType = FieldInputType.LIST,
            ),
        ),
    ),

    /** IRC channel. */
    IRC(
        displayName = "IRC",
        tomlKey = "irc",
        fields = listOf(
            ChannelFieldSpec(
                "server", "Server",
                isRequired = true,
            ),
            ChannelFieldSpec(
                "port", "Port",
                defaultValue = "6697",
                inputType = FieldInputType.NUMBER,
            ),
            ChannelFieldSpec(
                "nickname", "Nickname",
                isRequired = true,
            ),
            ChannelFieldSpec("username", "Username"),
            ChannelFieldSpec(
                "channels", "Channels",
                inputType = FieldInputType.LIST,
            ),
            ChannelFieldSpec(
                "allowed_users", "Allowed Users",
                inputType = FieldInputType.LIST,
            ),
            ChannelFieldSpec(
                "server_password", "Server Password",
                isSecret = true,
                inputType = FieldInputType.SECRET,
            ),
            ChannelFieldSpec(
                "nickserv_password",
                "NickServ Password",
                isSecret = true,
                inputType = FieldInputType.SECRET,
            ),
            ChannelFieldSpec(
                "sasl_password", "SASL Password",
                isSecret = true,
                inputType = FieldInputType.SECRET,
            ),
            ChannelFieldSpec(
                "verify_tls", "Verify TLS",
                defaultValue = "true",
                inputType = FieldInputType.BOOLEAN,
            ),
        ),
    ),

    /** Lark/Feishu channel. */
    LARK(
        displayName = "Lark",
        tomlKey = "lark",
        fields = listOf(
            ChannelFieldSpec(
                "app_id", "App ID",
                isRequired = true,
            ),
            ChannelFieldSpec(
                "app_secret", "App Secret",
                isRequired = true,
                isSecret = true,
                inputType = FieldInputType.SECRET,
            ),
            ChannelFieldSpec(
                "encrypt_key", "Encrypt Key",
                isSecret = true,
                inputType = FieldInputType.SECRET,
            ),
            ChannelFieldSpec(
                "verification_token",
                "Verification Token",
                isSecret = true,
                inputType = FieldInputType.SECRET,
            ),
            ChannelFieldSpec(
                "allowed_users", "Allowed Users",
                inputType = FieldInputType.LIST,
            ),
            ChannelFieldSpec(
                "use_feishu", "Use Feishu Endpoint",
                defaultValue = "false",
                inputType = FieldInputType.BOOLEAN,
            ),
        ),
    ),

    /** Webhook channel. */
    WEBHOOK(
        displayName = "Webhook",
        tomlKey = "webhook",
        fields = listOf(
            ChannelFieldSpec(
                "port", "Port",
                isRequired = true,
                inputType = FieldInputType.NUMBER,
            ),
            ChannelFieldSpec(
                "secret", "Secret",
                isSecret = true,
                inputType = FieldInputType.SECRET,
            ),
        ),
    ),
}

/**
 * A configured chat channel instance.
 *
 * Non-secret configuration values are stored in Room via [configValues].
 * Secret values (bot tokens, passwords) are stored separately in
 * EncryptedSharedPreferences and retrieved on demand.
 *
 * @property id Unique identifier (UUID string).
 * @property type The channel platform type.
 * @property isEnabled Whether the channel is active for daemon communication.
 * @property configValues Non-secret configuration key-value pairs.
 * @property createdAt Epoch milliseconds when the channel was configured.
 */
data class ConnectedChannel(
    val id: String,
    val type: ChannelType,
    val isEnabled: Boolean = true,
    val configValues: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
)
