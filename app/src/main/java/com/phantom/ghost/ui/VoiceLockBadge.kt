package com.phantom.ghost.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VoiceLockBadge(active: Boolean, intruderDetected: Boolean = false) {
    val color = if (intruderDetected) PhantomColors.AlertRed else PhantomColors.GhostCyan
    Row(
        modifier = Modifier.background(PhantomColors.PhantomCharcoal, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = when {
                intruderDetected -> "VOICE CHECK: NO MATCH"
                active -> "VOICE CHECK: ACTIVE"
                else -> "VOICE CHECK: OFF"
            },
            color = color,
            fontSize = 10.sp
        )
    }
}
