/*
 * Copyright 2026 ZeroClaw Community
 *
 * Licensed under the MIT License. See LICENSE in the project root.
 */

package com.zeroclaw.android.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.util.LocalPowerSaveMode

private const val TITLE_HEIGHT = 16
private const val SUBTITLE_HEIGHT = 12
private const val CORNER_RADIUS = 4
private const val LINE_SPACING = 8
private const val CARD_PADDING = 16
private const val LIST_ITEM_SPACING = 8
private const val TITLE_WIDTH_FRACTION = 0.6f
private const val SUBTITLE_WIDTH_FRACTION = 0.4f
private const val SHIMMER_ALPHA_MIN = 0.3f
private const val SHIMMER_ALPHA_MAX = 1.0f
private const val SHIMMER_DURATION_MS = 1200
private const val DEFAULT_SKELETON_COUNT = 5
private const val LOADING_DESCRIPTION = "Loading"

/**
 * Resolves the shimmer alpha for skeleton placeholders.
 *
 * In normal mode an [InfiniteTransition] oscillates between
 * [SHIMMER_ALPHA_MIN] and [SHIMMER_ALPHA_MAX] using a
 * [FastOutSlowInEasing] tween. When [LocalPowerSaveMode] is active
 * (system power saver or Samsung Battery Guardian), the animation
 * is skipped and a static alpha of [SHIMMER_ALPHA_MAX] is returned
 * to conserve battery.
 *
 * @return The current shimmer alpha value.
 */
@Composable
private fun shimmerAlpha(): Float {
    if (LocalPowerSaveMode.current) {
        return SHIMMER_ALPHA_MAX
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = SHIMMER_ALPHA_MIN,
        targetValue = SHIMMER_ALPHA_MAX,
        animationSpec =
            infiniteRepeatable(
                animation =
                    tween(
                        durationMillis = SHIMMER_DURATION_MS,
                        easing = FastOutSlowInEasing,
                    ),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "shimmerAlpha",
    )
    return alpha
}

/**
 * Skeleton placeholder shaped like a list item.
 *
 * Renders two horizontal bars representing a title and subtitle
 * line. In normal mode the bars shimmer via a GPU-optimized
 * [graphicsLayer] alpha animation. In power-save mode the bars
 * are rendered as static gray boxes.
 *
 * The component is marked with a `"Loading"` content description
 * for screen-reader accessibility.
 *
 * @param modifier Modifier applied to the root [Column].
 */
@Composable
fun SkeletonListItem(modifier: Modifier = Modifier) {
    val alpha = shimmerAlpha()
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    val shape = RoundedCornerShape(CORNER_RADIUS.dp)

    Column(
        modifier =
            modifier.semantics {
                contentDescription = LOADING_DESCRIPTION
            },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(TITLE_WIDTH_FRACTION)
                    .height(TITLE_HEIGHT.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .background(placeholderColor, shape),
        )
        Spacer(modifier = Modifier.height(LINE_SPACING.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(SUBTITLE_WIDTH_FRACTION)
                    .height(SUBTITLE_HEIGHT.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .background(placeholderColor, shape),
        )
    }
}

/**
 * Skeleton placeholder shaped like a card.
 *
 * Wraps [SkeletonListItem] inside a [Card] with interior padding,
 * providing a loading state that visually matches card-based layouts.
 * Inherits the shimmer behavior and accessibility semantics from
 * [SkeletonListItem].
 *
 * @param modifier Modifier applied to the outer [Card].
 */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        SkeletonListItem(
            modifier = Modifier.padding(CARD_PADDING.dp),
        )
    }
}

/**
 * Vertical list of [SkeletonListItem] placeholders.
 *
 * Displays [count] skeleton items separated by [LIST_ITEM_SPACING]
 * spacing, suitable as a full-screen loading state for list-based
 * screens. Each item inherits the shimmer animation and
 * accessibility semantics from [SkeletonListItem].
 *
 * @param count Number of skeleton items to display. Defaults to
 *   [DEFAULT_SKELETON_COUNT].
 * @param modifier Modifier applied to the root [Column].
 */
@Composable
fun SkeletonList(
    count: Int = DEFAULT_SKELETON_COUNT,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LIST_ITEM_SPACING.dp),
    ) {
        repeat(count) {
            SkeletonListItem()
        }
    }
}
