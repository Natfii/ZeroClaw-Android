/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.model

/**
 * Commands emitted by the terminal REPL to trigger immediate data refresh
 * in other ViewModels after a mutating operation completes.
 *
 * When the REPL evaluates a command that changes server-side state (e.g.
 * adding a cron job or installing a skill), the [TerminalViewModel][com.zeroclaw.android.ui.screen.terminal.TerminalViewModel]
 * emits the corresponding variant so that the Dashboard and other screens
 * update without waiting for the next poll cycle.
 */
sealed interface RefreshCommand {
    /** Re-fetch cron jobs after add, remove, pause, or resume. */
    data object Cron : RefreshCommand

    /** Re-fetch cost summary after a send or send_vision call. */
    data object Cost : RefreshCommand

    /** Re-fetch health detail after a potentially state-changing operation. */
    data object Health : RefreshCommand
}
