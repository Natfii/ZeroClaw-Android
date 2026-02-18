// Copyright 2026 ZeroClaw Community, MIT License

package com.zeroclaw.android.ui.screen.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeroclaw.android.model.Skill
import com.zeroclaw.android.ui.component.CategoryBadge
import com.zeroclaw.android.ui.component.EmptyState
import com.zeroclaw.android.ui.component.ErrorCard
import com.zeroclaw.android.ui.component.LoadingIndicator

/** Maximum number of tool name badges to show before truncating. */
private const val MAX_TOOL_BADGES = 3

/**
 * Content for the Skills tab inside the combined Plugins and Skills screen.
 *
 * Shows a search-filtered list of skills loaded from the workspace. Each
 * skill card displays metadata, tool count, and a remove button.
 *
 * @param skillsViewModel ViewModel providing skills state and actions.
 * @param modifier Modifier applied to the root layout.
 */
@Composable
fun SkillsTab(
    skillsViewModel: SkillsViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by skillsViewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by skillsViewModel.searchQuery.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { skillsViewModel.updateSearch(it) },
            label = { Text("Search skills") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uiState) {
            is SkillsUiState.Loading -> {
                LoadingIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            is SkillsUiState.Error -> {
                ErrorCard(
                    message = state.detail,
                    onRetry = { skillsViewModel.loadSkills() },
                )
            }
            is SkillsUiState.Content -> {
                val filtered =
                    if (searchQuery.isBlank()) {
                        state.data
                    } else {
                        state.data.filter { skill ->
                            skill.name.contains(searchQuery, ignoreCase = true) ||
                                skill.description.contains(searchQuery, ignoreCase = true)
                        }
                    }

                if (filtered.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.AutoFixHigh,
                        message =
                            if (searchQuery.isBlank()) {
                                "No skills installed. Use install to add skills from a URL."
                            } else {
                                "No skills match your search"
                            },
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = filtered,
                            key = { it.name },
                            contentType = { "skill" },
                        ) { skill ->
                            SkillCard(
                                skill = skill,
                                onRemove = { skillsViewModel.removeSkill(skill.name) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card displaying a single skill with metadata and action controls.
 *
 * Shows the skill name, description, version, author, tags, tool count,
 * and a delete button. Tool names are shown as badges up to [MAX_TOOL_BADGES].
 *
 * @param skill The skill to display.
 * @param onRemove Callback to remove this skill.
 * @param modifier Modifier applied to the card.
 */
@Composable
private fun SkillCard(
    skill: Skill,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        "${skill.name}: ${skill.description}, " +
                        "${skill.toolCount} tools, version ${skill.version}"
                },
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = skill.name,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = skill.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onRemove,
                    modifier =
                        Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = "Remove skill ${skill.name}" },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "v${skill.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                skill.author?.let { author ->
                    Text(
                        text = " \u2022 $author",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${skill.toolCount} tools",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (skill.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    skill.tags.forEach { tag ->
                        CategoryBadge(category = tag)
                    }
                }
            }

            if (skill.toolNames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                val displayNames = skill.toolNames.take(MAX_TOOL_BADGES)
                val remaining = skill.toolNames.size - displayNames.size
                Text(
                    text =
                        displayNames.joinToString(", ") +
                            if (remaining > 0) " +$remaining more" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
