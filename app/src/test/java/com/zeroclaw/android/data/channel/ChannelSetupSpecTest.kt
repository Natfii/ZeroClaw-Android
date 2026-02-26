/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.channel

import com.zeroclaw.android.model.ChannelType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ChannelSetupSpec")
class ChannelSetupSpecTest {
    @Test
    @DisplayName("every active channel type has a setup spec")
    @Suppress("DEPRECATION")
    fun `every active channel type has a setup spec`() {
        ChannelType.entries
            .filter { it != ChannelType.WEBHOOK }
            .forEach { type ->
                assertNotNull(
                    ChannelSetupSpecs.forType(type),
                    "Missing setup spec for ${type.name}",
                )
            }
    }

    @Test
    @DisplayName("telegram spec has 3 sub-steps")
    fun `telegram spec has 3 sub-steps`() {
        val spec = ChannelSetupSpecs.forType(ChannelType.TELEGRAM)
        assertNotNull(spec)
        assertEquals(3, spec!!.steps.size)
    }

    @Test
    @DisplayName("telegram step 1 has bot_token field")
    fun `telegram step 1 has bot_token field`() {
        val step = ChannelSetupSpecs.forType(ChannelType.TELEGRAM)!!.steps[0]
        assertTrue(
            step.fields.any { it.key == "bot_token" },
            "Step 1 should contain bot_token field",
        )
    }

    @Test
    @DisplayName("telegram step 1 has TELEGRAM_BOT_TOKEN validator")
    fun `telegram step 1 has TELEGRAM_BOT_TOKEN validator`() {
        val step = ChannelSetupSpecs.forType(ChannelType.TELEGRAM)!!.steps[0]
        assertEquals(ValidatorType.TELEGRAM_BOT_TOKEN, step.validatorType)
    }

    @Test
    @DisplayName("telegram step 2 has allowed_users field")
    fun `telegram step 2 has allowed_users field`() {
        val step = ChannelSetupSpecs.forType(ChannelType.TELEGRAM)!!.steps[1]
        assertTrue(
            step.fields.any { it.key == "allowed_users" },
            "Step 2 should contain allowed_users field",
        )
    }

    @Test
    @DisplayName("telegram step 3 is optional")
    fun `telegram step 3 is optional`() {
        val step = ChannelSetupSpecs.forType(ChannelType.TELEGRAM)!!.steps[2]
        assertTrue(step.optional, "Step 3 should be optional")
    }

    @Test
    @DisplayName("discord spec has 3 sub-steps")
    fun `discord spec has 3 sub-steps`() {
        val spec = ChannelSetupSpecs.forType(ChannelType.DISCORD)
        assertNotNull(spec)
        assertEquals(3, spec!!.steps.size)
    }

    @Test
    @DisplayName("slack spec has 3 sub-steps")
    fun `slack spec has 3 sub-steps`() {
        val spec = ChannelSetupSpecs.forType(ChannelType.SLACK)
        assertNotNull(spec)
        assertEquals(3, spec!!.steps.size)
    }

    @Test
    @DisplayName("matrix spec has 2 sub-steps")
    fun `matrix spec has 2 sub-steps`() {
        val spec = ChannelSetupSpecs.forType(ChannelType.MATRIX)
        assertNotNull(spec)
        assertEquals(2, spec!!.steps.size)
    }

    @Test
    @DisplayName("webhook returns null spec")
    @Suppress("DEPRECATION")
    fun `webhook returns null spec`() {
        assertNull(ChannelSetupSpecs.forType(ChannelType.WEBHOOK))
    }

    @Test
    @DisplayName("all spec field keys exist in channel type fields")
    @Suppress("DEPRECATION")
    fun `all spec field keys exist in channel type fields`() {
        ChannelType.entries
            .filter { it != ChannelType.WEBHOOK }
            .forEach { type ->
                val spec =
                    ChannelSetupSpecs.forType(type)
                        ?: return@forEach
                val validKeys = type.fields.map { it.key }.toSet()
                spec.steps.forEach { step ->
                    step.fields.forEach { field ->
                        assertTrue(
                            field.key in validKeys,
                            "Field '${field.key}' in ${type.name} spec " +
                                "step '${step.title}' does not exist " +
                                "in ChannelType.${type.name}.fields " +
                                "(valid keys: $validKeys)",
                        )
                    }
                }
            }
    }
}
