/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component.setup

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.data.channel.ChannelSetupSpec
import com.zeroclaw.android.data.channel.ChannelSetupStepSpec
import com.zeroclaw.android.data.channel.InstructionItem
import com.zeroclaw.android.data.channel.ValidatorType
import com.zeroclaw.android.data.validation.ValidationResult
import com.zeroclaw.android.model.ChannelFieldSpec
import com.zeroclaw.android.model.ChannelType
import com.zeroclaw.android.model.FieldInputType
import com.zeroclaw.android.ui.theme.ZeroClawTheme

/** Standard vertical spacing between major sections. */
private val SectionSpacing = 16.dp

/** Smaller vertical spacing between related elements. */
private val ElementSpacing = 8.dp

/** Vertical spacing between individual form fields. */
private val FieldSpacing = 12.dp

/** Spacing between the validate button icon and its label. */
private val ButtonIconSpacing = 4.dp

/**
 * Interactive per-channel sub-step wizard for guided channel configuration.
 *
 * Given a [ChannelSetupSpec], renders sub-steps sequentially with instructions,
 * deep links, configuration fields, and validation. Each sub-step is presented
 * in a scrollable column with navigation controls at the bottom.
 *
 * The composable is fully state-hoisted: all field values, navigation, and
 * validation state are managed by the parent.
 *
 * @param spec The channel setup specification defining the sub-steps to render.
 * @param currentSubStep Zero-based index of the currently visible sub-step.
 * @param fieldValues Map of field keys to their current string values.
 * @param validationResult Current state of the validation operation for this step.
 * @param onFieldChanged Callback when a field value changes, receiving the field
 *   key and new value.
 * @param onValidate Callback to trigger credential validation for the current step.
 * @param onNextSubStep Callback to advance to the next sub-step or finish the flow.
 * @param onPreviousSubStep Callback to return to the previous sub-step.
 * @param modifier Modifier applied to the root scrollable [Column].
 */
@Composable
fun ChannelSetupFlow(
    spec: ChannelSetupSpec,
    currentSubStep: Int,
    fieldValues: Map<String, String>,
    validationResult: ValidationResult = ValidationResult.Idle,
    onFieldChanged: (key: String, value: String) -> Unit,
    onValidate: () -> Unit = {},
    onNextSubStep: () -> Unit,
    onPreviousSubStep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = spec.steps[currentSubStep]
    val isFirstStep = currentSubStep == 0
    val isLastStep = currentSubStep == spec.steps.size - 1
    val requiredFieldsBlank =
        step.fields
            .filter { it.isRequired }
            .any { fieldValues[it.key].isNullOrBlank() }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = "Step ${currentSubStep + 1} of ${spec.steps.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.semantics {
                    contentDescription =
                        "Step ${currentSubStep + 1} of ${spec.steps.size}"
                },
        )

        Spacer(modifier = Modifier.height(ElementSpacing))

        Text(
            text = step.title,
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(modifier = Modifier.height(SectionSpacing))

        if (step.instructions.isNotEmpty()) {
            InstructionsList(
                items = step.instructions,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(SectionSpacing))
        }

        if (step.deepLink != null) {
            DeepLinkButton(
                target = step.deepLink,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(SectionSpacing))
        }

        step.fields.forEach { fieldSpec ->
            ChannelField(
                spec = fieldSpec,
                value = fieldValues[fieldSpec.key].orEmpty(),
                onValueChanged = { onFieldChanged(fieldSpec.key, it) },
            )
            Spacer(modifier = Modifier.height(FieldSpacing))
        }

        if (step.validatorType != null) {
            ValidateActionRow(
                validationResult = validationResult,
                onValidate = onValidate,
            )
            Spacer(modifier = Modifier.height(ElementSpacing))
            ValidationIndicator(
                result = validationResult,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(SectionSpacing))
        }

        Spacer(modifier = Modifier.weight(1f))

        NavigationRow(
            isFirstStep = isFirstStep,
            isLastStep = isLastStep,
            nextEnabled = !requiredFieldsBlank,
            onPrevious = onPreviousSubStep,
            onNext = onNextSubStep,
        )
    }
}

/**
 * Renders a single channel configuration field based on its [ChannelFieldSpec].
 *
 * Maps [FieldInputType] to the appropriate keyboard type, visual transformation,
 * and widget:
 * - [FieldInputType.TEXT]: plain text [OutlinedTextField].
 * - [FieldInputType.NUMBER]: numeric keyboard [OutlinedTextField].
 * - [FieldInputType.URL]: URI keyboard [OutlinedTextField].
 * - [FieldInputType.SECRET]: password keyboard with masked input and a reveal toggle.
 * - [FieldInputType.BOOLEAN]: labelled [Switch] toggle.
 * - [FieldInputType.LIST]: plain text [OutlinedTextField] with supporting text.
 *
 * Required fields display a " *" suffix in their label. Secret fields (including
 * those with [ChannelFieldSpec.isSecret] regardless of input type) use
 * [PasswordVisualTransformation] with a visibility toggle [IconButton].
 *
 * @param spec The field specification describing label, type, and constraints.
 * @param value The current string value of the field.
 * @param onValueChanged Callback invoked when the user changes the field value.
 */
@Composable
private fun ChannelField(
    spec: ChannelFieldSpec,
    value: String,
    onValueChanged: (String) -> Unit,
) {
    val label = if (spec.isRequired) "${spec.label} *" else spec.label

    when (spec.inputType) {
        FieldInputType.BOOLEAN -> {
            BooleanField(
                label = label,
                checked = value.equals("true", ignoreCase = true),
                onCheckedChange = { onValueChanged(it.toString()) },
            )
        }
        FieldInputType.SECRET -> {
            SecretField(
                label = label,
                value = value,
                onValueChanged = onValueChanged,
            )
        }
        FieldInputType.NUMBER -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChanged,
                label = { Text(label) },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = label
                        },
            )
        }
        FieldInputType.URL -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChanged,
                label = { Text(label) },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = label
                        },
            )
        }
        FieldInputType.LIST -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChanged,
                label = { Text(label) },
                singleLine = true,
                supportingText = {
                    Text("Comma-separated values")
                },
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = label
                        },
            )
        }
        FieldInputType.TEXT -> {
            if (spec.isSecret) {
                SecretField(
                    label = label,
                    value = value,
                    onValueChanged = onValueChanged,
                )
            } else {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChanged,
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = label
                            },
                )
            }
        }
    }
}

/**
 * Secret text field with password masking and a visibility toggle.
 *
 * The field uses [PasswordVisualTransformation] by default and provides an
 * [IconButton] trailing icon to reveal or hide the value. The toggle state
 * is preserved across recompositions via [rememberSaveable].
 *
 * @param label The field label text.
 * @param value The current field value.
 * @param onValueChanged Callback invoked when the user changes the field value.
 */
@Composable
private fun SecretField(
    label: String,
    value: String,
    onValueChanged: (String) -> Unit,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        label = { Text(label) },
        singleLine = true,
        visualTransformation =
            if (revealed) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Password,
            ),
        trailingIcon = {
            IconButton(
                onClick = { revealed = !revealed },
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            if (revealed) {
                                "Hide $label"
                            } else {
                                "Show $label"
                            }
                    },
            ) {
                Icon(
                    imageVector =
                        if (revealed) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                    contentDescription = null,
                )
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = label
                },
    )
}

/**
 * Boolean toggle field rendered as a labelled [Switch].
 *
 * The label is displayed to the left and the switch to the right, filling the
 * available width. Meets the 48x48dp minimum touch target through the default
 * [Switch] sizing.
 *
 * @param label The display label for the toggle.
 * @param checked Whether the switch is currently on.
 * @param onCheckedChange Callback invoked when the switch state changes.
 */
@Composable
private fun BooleanField(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "$label, ${if (checked) "on" else "off"}"
                },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/**
 * Validate action button for the current sub-step.
 *
 * Displays a [FilledTonalButton] with a verification icon. The button is
 * disabled while validation is in progress to prevent duplicate requests.
 *
 * @param validationResult Current validation state, used to disable during loading.
 * @param onValidate Callback invoked when the validate button is clicked.
 */
@Composable
private fun ValidateActionRow(
    validationResult: ValidationResult,
    onValidate: () -> Unit,
) {
    val isLoading = validationResult is ValidationResult.Loading

    FilledTonalButton(
        onClick = onValidate,
        enabled = !isLoading,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Validate credentials"
                },
    ) {
        Icon(
            imageVector = Icons.Filled.Verified,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(ButtonIconSpacing))
        Text(
            text = if (isLoading) "Validating\u2026" else "Validate",
        )
    }
}

/**
 * Navigation row with Back and Next/Done buttons.
 *
 * The Back button is hidden on the first sub-step. The Next button displays
 * "Done" on the last sub-step. The Next button is disabled when required
 * fields remain blank.
 *
 * @param isFirstStep Whether the current sub-step is the first in the flow.
 * @param isLastStep Whether the current sub-step is the last in the flow.
 * @param nextEnabled Whether the Next/Done button is interactive.
 * @param onPrevious Callback invoked when the Back button is clicked.
 * @param onNext Callback invoked when the Next/Done button is clicked.
 */
@Composable
private fun NavigationRow(
    isFirstStep: Boolean,
    isLastStep: Boolean,
    nextEnabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedButton(
            onClick = onPrevious,
            modifier =
                Modifier.semantics {
                    contentDescription =
                        if (isFirstStep) {
                            "Go back to channel selection"
                        } else {
                            "Go to previous step"
                        }
                },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(ButtonIconSpacing))
            Text(text = if (isFirstStep) "Back to Channels" else "Back")
        }

        Button(
            onClick = onNext,
            enabled = nextEnabled,
            modifier =
                Modifier.semantics {
                    contentDescription =
                        if (isLastStep) {
                            "Finish channel setup"
                        } else {
                            "Go to next step"
                        }
                },
        ) {
            Text(text = if (isLastStep) "Done" else "Next")
            Spacer(modifier = Modifier.width(ButtonIconSpacing))
            Icon(
                imageVector =
                    if (isLastStep) {
                        Icons.Filled.Done
                    } else {
                        Icons.AutoMirrored.Filled.ArrowForward
                    },
                contentDescription = null,
            )
        }
    }
}

@Preview(name = "Channel Setup - Step 1")
@Composable
private fun PreviewStep1() {
    ZeroClawTheme {
        Surface {
            ChannelSetupFlow(
                spec =
                    ChannelSetupSpec(
                        channelType = ChannelType.TELEGRAM,
                        steps =
                            listOf(
                                ChannelSetupStepSpec(
                                    title = "Create a Telegram Bot",
                                    instructions =
                                        listOf(
                                            InstructionItem.Text(
                                                "Create a new bot using BotFather.",
                                            ),
                                            InstructionItem.NumberedStep(
                                                1,
                                                "Open BotFather.",
                                            ),
                                            InstructionItem.NumberedStep(
                                                2,
                                                "Send /newbot.",
                                            ),
                                            InstructionItem.Warning(
                                                "Keep your bot token secret.",
                                            ),
                                        ),
                                    fields =
                                        listOf(
                                            ChannelFieldSpec(
                                                key = "bot_token",
                                                label = "Bot Token",
                                                isRequired = true,
                                                isSecret = true,
                                                inputType = FieldInputType.SECRET,
                                            ),
                                        ),
                                    validatorType = ValidatorType.TELEGRAM_BOT_TOKEN,
                                ),
                                ChannelSetupStepSpec(
                                    title = "Allow Your Account",
                                    instructions =
                                        listOf(
                                            InstructionItem.Text(
                                                "Restrict which users can interact.",
                                            ),
                                        ),
                                    fields =
                                        listOf(
                                            ChannelFieldSpec(
                                                key = "allowed_users",
                                                label = "Allowed Users",
                                                inputType = FieldInputType.LIST,
                                            ),
                                        ),
                                ),
                            ),
                    ),
                currentSubStep = 0,
                fieldValues = emptyMap(),
                onFieldChanged = { _, _ -> },
                onNextSubStep = {},
                onPreviousSubStep = {},
            )
        }
    }
}

@Preview(name = "Channel Setup - Step 2 with Values")
@Composable
private fun PreviewStep2() {
    ZeroClawTheme {
        Surface {
            ChannelSetupFlow(
                spec =
                    ChannelSetupSpec(
                        channelType = ChannelType.TELEGRAM,
                        steps =
                            listOf(
                                ChannelSetupStepSpec(
                                    title = "Create a Telegram Bot",
                                    instructions = emptyList(),
                                    fields =
                                        listOf(
                                            ChannelFieldSpec(
                                                key = "bot_token",
                                                label = "Bot Token",
                                                isRequired = true,
                                                isSecret = true,
                                                inputType = FieldInputType.SECRET,
                                            ),
                                        ),
                                ),
                                ChannelSetupStepSpec(
                                    title = "Allow Your Account",
                                    instructions =
                                        listOf(
                                            InstructionItem.Text(
                                                "Restrict which users can interact.",
                                            ),
                                        ),
                                    fields =
                                        listOf(
                                            ChannelFieldSpec(
                                                key = "allowed_users",
                                                label = "Allowed Users",
                                                inputType = FieldInputType.LIST,
                                            ),
                                        ),
                                ),
                            ),
                    ),
                currentSubStep = 1,
                fieldValues =
                    mapOf(
                        "bot_token" to "123456:ABC-DEF",
                        "allowed_users" to "12345",
                    ),
                onFieldChanged = { _, _ -> },
                onNextSubStep = {},
                onPreviousSubStep = {},
            )
        }
    }
}

@Preview(name = "Channel Setup - Validation Success")
@Composable
private fun PreviewValidationSuccess() {
    ZeroClawTheme {
        Surface {
            ChannelSetupFlow(
                spec =
                    ChannelSetupSpec(
                        channelType = ChannelType.DISCORD,
                        steps =
                            listOf(
                                ChannelSetupStepSpec(
                                    title = "Create a Discord Bot",
                                    instructions =
                                        listOf(
                                            InstructionItem.Text(
                                                "Create a new Discord application.",
                                            ),
                                        ),
                                    fields =
                                        listOf(
                                            ChannelFieldSpec(
                                                key = "bot_token",
                                                label = "Bot Token",
                                                isRequired = true,
                                                isSecret = true,
                                                inputType = FieldInputType.SECRET,
                                            ),
                                        ),
                                    validatorType = ValidatorType.DISCORD_BOT_TOKEN,
                                ),
                            ),
                    ),
                currentSubStep = 0,
                fieldValues = mapOf("bot_token" to "discord-token"),
                validationResult =
                    ValidationResult.Success(
                        details = "Bot: ZeroClaw#1234",
                    ),
                onFieldChanged = { _, _ -> },
                onNextSubStep = {},
                onPreviousSubStep = {},
            )
        }
    }
}

@Preview(
    name = "Channel Setup - Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PreviewDark() {
    ZeroClawTheme {
        Surface {
            ChannelSetupFlow(
                spec =
                    ChannelSetupSpec(
                        channelType = ChannelType.TELEGRAM,
                        steps =
                            listOf(
                                ChannelSetupStepSpec(
                                    title = "Create a Telegram Bot",
                                    instructions =
                                        listOf(
                                            InstructionItem.Text(
                                                "Create a new bot using BotFather.",
                                            ),
                                            InstructionItem.Warning(
                                                "Keep your bot token secret.",
                                            ),
                                        ),
                                    fields =
                                        listOf(
                                            ChannelFieldSpec(
                                                key = "bot_token",
                                                label = "Bot Token",
                                                isRequired = true,
                                                isSecret = true,
                                                inputType = FieldInputType.SECRET,
                                            ),
                                        ),
                                    validatorType = ValidatorType.TELEGRAM_BOT_TOKEN,
                                ),
                                ChannelSetupStepSpec(
                                    title = "Allow Your Account",
                                    instructions = emptyList(),
                                    fields =
                                        listOf(
                                            ChannelFieldSpec(
                                                key = "allowed_users",
                                                label = "Allowed Users",
                                                inputType = FieldInputType.LIST,
                                            ),
                                        ),
                                ),
                            ),
                    ),
                currentSubStep = 0,
                fieldValues = mapOf("bot_token" to "test-token"),
                validationResult = ValidationResult.Loading,
                onFieldChanged = { _, _ -> },
                onNextSubStep = {},
                onPreviousSubStep = {},
            )
        }
    }
}
