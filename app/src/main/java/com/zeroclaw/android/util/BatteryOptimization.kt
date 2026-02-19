/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Utilities for detecting and managing battery optimization exemptions.
 *
 * Provides methods to check if the app is exempt from Android's Doze and
 * App Standby restrictions, and to detect OEM-specific battery management
 * systems (Xiaomi MIUI, Samsung Device Care, Huawei Battery Manager,
 * OnePlus Battery Optimization, Oppo ColorOS, Vivo Funtouch/OriginOS,
 * and Realme RealmeUI) that aggressively terminate foreground services
 * beyond the stock Android behaviour.
 *
 * The [android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS]
 * permission must be declared in the manifest before calling
 * [requestExemption].
 */
object BatteryOptimization {
    /**
     * Returns whether the app is currently exempt from battery optimization.
     *
     * Safe to call from any thread.
     *
     * @param context Application or activity context.
     * @return True if the app is already exempt, false otherwise.
     */
    fun isExempt(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Creates an [Intent] that opens the system battery optimization
     * settings screen for this app.
     *
     * The caller is responsible for starting the activity with the
     * returned intent. Must be started from an [android.app.Activity]
     * context or with [Intent.FLAG_ACTIVITY_NEW_TASK].
     *
     * @param context Application context for reading the package name.
     * @return An intent targeting
     *   [Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS].
     */
    fun requestExemptionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Detects whether the device manufacturer uses an aggressive
     * battery management system that kills foreground services.
     *
     * Checks [Build.MANUFACTURER] against a known list of OEMs whose
     * custom battery managers go beyond Android's standard Doze/Standby
     * behaviour.
     *
     * @return The detected [OemBatteryType], or null if the device
     *   uses stock Android battery behaviour.
     */
    fun detectAggressiveOem(): OemBatteryType? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ->
                OemBatteryType.XIAOMI
            manufacturer.contains("samsung") ->
                OemBatteryType.SAMSUNG
            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                OemBatteryType.HUAWEI
            manufacturer.contains("oneplus") ->
                OemBatteryType.ONEPLUS
            manufacturer.contains("oppo") || manufacturer.contains("realme") ->
                OemBatteryType.OPPO
            manufacturer.contains("vivo") ->
                OemBatteryType.VIVO
            else -> null
        }
    }

    /**
     * Returns the dontkillmyapp.com URL with manufacturer-specific
     * instructions for disabling aggressive battery management.
     *
     * @param oemType The OEM type returned by [detectAggressiveOem].
     * @return A URL string pointing to the relevant guide.
     */
    fun getOemInstructionsUrl(oemType: OemBatteryType): String {
        val slug =
            when (oemType) {
                OemBatteryType.XIAOMI -> "xiaomi"
                OemBatteryType.SAMSUNG -> "samsung"
                OemBatteryType.HUAWEI -> "huawei"
                OemBatteryType.ONEPLUS -> "oneplus"
                OemBatteryType.OPPO -> "oppo"
                OemBatteryType.VIVO -> "vivo"
            }
        return "$BASE_URL/$slug"
    }

    /**
     * OEM manufacturers with aggressive battery management systems
     * that kill foreground services beyond stock Android behaviour.
     */
    enum class OemBatteryType {
        /** Xiaomi MIUI battery saver and Redmi variants. */
        XIAOMI,

        /** Samsung Device Care adaptive battery. */
        SAMSUNG,

        /** Huawei and Honor Battery Manager. */
        HUAWEI,

        /** OnePlus Battery Optimization. */
        ONEPLUS,

        /** Oppo ColorOS and Realme RealmeUI battery management. */
        OPPO,

        /** Vivo Funtouch/OriginOS battery management. */
        VIVO,
    }

    private const val BASE_URL = "https://dontkillmyapp.com"
}
