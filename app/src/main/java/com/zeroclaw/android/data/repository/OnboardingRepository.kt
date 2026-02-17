/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository interface for tracking onboarding completion state.
 */
interface OnboardingRepository {
    /** Whether the user has completed the onboarding wizard. */
    val isCompleted: Flow<Boolean>

    /** Marks the onboarding wizard as completed. */
    suspend fun markComplete()

    /** Resets onboarding so the wizard is shown again. */
    suspend fun reset()
}

/** Extension property providing the singleton [DataStore] for onboarding. */
private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "onboarding",
)

/**
 * [OnboardingRepository] implementation backed by Jetpack DataStore Preferences.
 *
 * @param context Application context for DataStore initialization.
 */
class DataStoreOnboardingRepository(
    private val context: Context,
) : OnboardingRepository {
    override val isCompleted: Flow<Boolean> =
        context.onboardingDataStore.data.map { prefs ->
            prefs[KEY_COMPLETED] ?: false
        }

    override suspend fun markComplete() {
        context.onboardingDataStore.edit { prefs ->
            prefs[KEY_COMPLETED] = true
        }
    }

    override suspend fun reset() {
        context.onboardingDataStore.edit { prefs ->
            prefs[KEY_COMPLETED] = false
        }
    }

    /** DataStore preference keys for [DataStoreOnboardingRepository]. */
    companion object {
        /** Preference key for onboarding completion state. */
        val KEY_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }
}
