package com.phantom.ghost.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object PhantomColors {
    val PhantomBlack = Color(0xFF0A0A0A)
    val PhantomCharcoal = Color(0xFF121212)
    val GhostCyan = Color(0xFF00F0FF)
    val PhantomPurple = Color(0xFF9D00FF)
    val SoftWhite = Color(0xFFE0E0E0)
    val AlertRed = Color(0xFFFF4D5E)
}

object PhantomShapes {
    val Card = RoundedCornerShape(22.dp)
    val Message = RoundedCornerShape(14.dp)
    val Input = RoundedCornerShape(16.dp)
}

@Composable
fun PhantomDesignSystem(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = PhantomColors.GhostCyan,
            secondary = PhantomColors.PhantomPurple,
            background = PhantomColors.PhantomBlack,
            surface = PhantomColors.PhantomCharcoal,
            onBackground = PhantomColors.SoftWhite,
            onSurface = PhantomColors.SoftWhite,
            error = PhantomColors.AlertRed
        ),
        content = content
    )
}
