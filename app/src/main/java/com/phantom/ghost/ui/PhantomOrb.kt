package com.phantom.ghost.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PhantomOrb(modifier: Modifier = Modifier, loading: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "phantom-orb")
    val pulse = if (loading) transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "orb-pulse"
    ).value else 1f
    Box(
        modifier = modifier.size(92.dp).scale(pulse).alpha(if (loading) 0.9f else 1f)
            .background(PhantomColors.PhantomCharcoal, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("◉", color = PhantomColors.GhostCyan, fontSize = 52.sp)
    }
}
