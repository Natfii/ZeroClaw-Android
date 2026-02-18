// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.service

import com.zeroclaw.android.model.Skill
import com.zeroclaw.ffi.FfiException
import com.zeroclaw.ffi.FfiSkill
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bridge between the Android UI layer and the Rust skills FFI.
 *
 * Wraps the skills-related UniFFI-generated functions in coroutine-safe
 * suspend functions dispatched to [Dispatchers.IO].
 *
 * @param ioDispatcher Dispatcher for blocking FFI calls. Defaults to [Dispatchers.IO].
 */
class SkillsBridge(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Lists all skills loaded from the workspace's skills directory.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @return List of all [Skill] instances.
     * @throws FfiException if the native layer reports an error.
     */
    @Throws(FfiException::class)
    suspend fun listSkills(): List<Skill> =
        withContext(ioDispatcher) {
            com.zeroclaw.ffi
                .listSkills()
                .map { it.toModel() }
        }

    /**
     * Installs a skill from a URL or local path.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @param source URL or local filesystem path to the skill source.
     * @throws FfiException if the native layer reports an error.
     */
    @Throws(FfiException::class)
    suspend fun installSkill(source: String) {
        withContext(ioDispatcher) {
            com.zeroclaw.ffi.installSkill(source)
        }
    }

    /**
     * Removes an installed skill by name.
     *
     * Safe to call from the main thread; the underlying blocking FFI call is
     * dispatched to [ioDispatcher].
     *
     * @param name Name of the skill to remove.
     * @throws FfiException if the native layer reports an error.
     */
    @Throws(FfiException::class)
    suspend fun removeSkill(name: String) {
        withContext(ioDispatcher) {
            com.zeroclaw.ffi.removeSkill(name)
        }
    }
}

/**
 * Converts an FFI skill record to the domain model.
 *
 * @receiver FFI-generated [FfiSkill] record from the native layer.
 * @return Domain [Skill] model with identical field values.
 */
private fun FfiSkill.toModel(): Skill =
    Skill(
        name = name,
        description = description,
        version = version,
        author = author,
        tags = tags,
        toolCount = toolCount.toInt(),
        toolNames = toolNames,
    )
