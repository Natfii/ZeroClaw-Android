/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component.setup

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.ui.theme.ZeroClawTheme
import com.zeroclaw.android.util.DeepLinkTarget
import com.zeroclaw.android.util.ExternalAppLauncher

/** Spacing between the leading icon and the button label text. */
private val IconTextSpacing = 8.dp

/**
 * An [OutlinedButton] that opens an external app or browser via [ExternalAppLauncher].
 *
 * Displays the [DeepLinkTarget.label] as button text with a leading open-in-new icon.
 * On click, delegates to [ExternalAppLauncher.launch] which handles URI scheme fallback
 * when the primary app is not installed.
 *
 * The button meets the minimum 48x48dp touch target requirement through the default
 * [OutlinedButton] sizing.
 *
 * @param target The deep-link destination defining the label and URI to open.
 * @param modifier Modifier applied to the [OutlinedButton].
 */
@Composable
fun DeepLinkButton(
    target: DeepLinkTarget,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    OutlinedButton(
        onClick = { ExternalAppLauncher.launch(context, target) },
        modifier =
            modifier.semantics {
                contentDescription = "Open ${target.label} in external app"
            },
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(IconTextSpacing))
        Text(text = target.label)
    }
}

@Preview(name = "Deep Link Button")
@Composable
private fun PreviewDeepLinkButton() {
    ZeroClawTheme {
        Surface {
            DeepLinkButton(target = ExternalAppLauncher.TELEGRAM_BOTFATHER)
        }
    }
}

@Preview(
    name = "Deep Link Button - Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PreviewDeepLinkButtonDark() {
    ZeroClawTheme {
        Surface {
            DeepLinkButton(target = ExternalAppLauncher.TELEGRAM_BOTFATHER)
        }
    }
}
