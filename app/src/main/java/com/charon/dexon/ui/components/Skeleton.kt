package com.charon.dexon.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Below this, a load finishes before a placeholder could register, so nothing is shown at all. */
const val SKELETON_DELAY_MS = 200L

/**
 * Shows [content] only if it is still in the composition after [delayMs], fading it in.
 * Fast loads therefore never flash a placeholder; slow ones get a calm shimmer.
 */
@Composable
fun DelayedVisibility(delayMs: Long = SKELETON_DELAY_MS, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        visible = true
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(250)), exit = fadeOut()) {
        content()
    }
}

/** Sweeping highlight over a surface-variant block. The animation is read in the draw phase only. */
fun Modifier.shimmer(shape: Shape = RoundedCornerShape(6.dp)): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress = transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    clip(shape).drawBehind {
        val x = size.width * progress.value
        drawRect(
            Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(x - size.width * 0.6f, 0f),
                end = Offset(x, size.height),
            )
        )
    }
}

@Composable
fun SkeletonBlock(width: Dp? = null, height: Dp = 12.dp, shape: Shape = RoundedCornerShape(6.dp)) {
    val sized = if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()
    Box(sized.height(height).shimmer(shape))
}

/** Placeholder shaped like the home screen's app cards. */
@Composable
fun SkeletonAppList(count: Int = 9, contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp)) {
    Column(
        modifier = Modifier.padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) { i ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).shimmer(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // Vary widths so the placeholder reads as a list, not a grid of identical bars.
                    SkeletonBlock(width = (120 + (i * 37) % 90).dp, height = 14.dp)
                    Spacer(Modifier.height(6.dp))
                    SkeletonBlock(width = (160 + (i * 53) % 80).dp, height = 10.dp)
                    Spacer(Modifier.height(6.dp))
                    SkeletonBlock(width = 64.dp, height = 14.dp)
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(40.dp).shimmer(CircleShape))
            }
        }
    }
}

/** Placeholder for the detail screen's first cards. */
@Composable
fun SkeletonDetail() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).shimmer(CircleShape))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                SkeletonBlock(width = 160.dp, height = 16.dp)
                Spacer(Modifier.height(8.dp))
                SkeletonBlock(height = 10.dp)
                Spacer(Modifier.height(6.dp))
                SkeletonBlock(width = 200.dp, height = 10.dp)
            }
        }
        repeat(3) { SkeletonCard(lines = 4) }
    }
}

/** A card-sized placeholder with a title bar and [lines] text bars. */
@Composable
fun SkeletonCard(lines: Int = 3) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkeletonBlock(width = 140.dp, height = 14.dp)
        repeat(lines) { i -> SkeletonBlock(width = if (i == lines - 1) 180.dp else null, height = 10.dp) }
    }
}
