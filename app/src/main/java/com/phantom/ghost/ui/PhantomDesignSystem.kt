package com.phantom.ghost.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PhantomColors {
    val PhantomBlack = Color(0xFF0A0A0A)
    val PhantomCharcoal = Color(0xFF121212)
    val GhostCyan = Color(0xFF00F0FF)
    val PhantomPurple = Color(0xFF9D00FF)
    val GhostWhite = Color(0xFFE0E0E0)
    val SoftWhite = GhostWhite
    val AlertRed = Color(0xFFFF4D5E)
    val Muted = Color(0xFF8B929D)
    val Glass = Color.White.copy(alpha = 0.07f)
}

object PhantomShapes {
    val Card = RoundedCornerShape(16.dp)
    val Message = RoundedCornerShape(14.dp)
    val Input = RoundedCornerShape(24.dp)
}

object PhantomType {
    // Uses system fallbacks; bundled Inter/JetBrains Mono fonts can be added without changing call sites.
    val PhantomMono = FontFamily.Monospace
    val PhantomSans = FontFamily.SansSerif
    val label = TextStyle(fontFamily = PhantomMono, fontSize = 10.sp, letterSpacing = 1.2.sp)
}

fun Modifier.glassCard() = this
    .shadow(8.dp, PhantomShapes.Card, clip = false)
    .background(Color.White.copy(alpha = 0.10f), PhantomShapes.Card)
    .border(1.dp, PhantomColors.GhostCyan.copy(alpha = 0.30f), PhantomShapes.Card)

fun Modifier.glowEffect(color: Color = PhantomColors.GhostCyan, radius: androidx.compose.ui.unit.Dp = 14.dp) =
    this.shadow(radius, RoundedCornerShape(50), ambientColor = color.copy(alpha = 0.55f), spotColor = color.copy(alpha = 0.75f))

@Composable
fun PhantomDesignSystem(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = PhantomColors.GhostCyan,
            secondary = PhantomColors.PhantomPurple,
            background = PhantomColors.PhantomBlack,
            surface = PhantomColors.PhantomCharcoal,
            onBackground = PhantomColors.GhostWhite,
            onSurface = PhantomColors.GhostWhite,
            error = PhantomColors.AlertRed
        ),
        typography = MaterialTheme.typography.copy(
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = PhantomType.PhantomSans),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = PhantomType.PhantomSans),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = PhantomType.PhantomSans)
        ),
        content = content
    )
}
