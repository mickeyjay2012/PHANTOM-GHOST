package com.phantom.ghost.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ThoughtStream(lines: List<String>, modifier: Modifier = Modifier) {
    Column(modifier) {
        lines.takeLast(8).forEach { line ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                color = PhantomColors.PhantomCharcoal,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(line, modifier = Modifier.padding(12.dp), color = PhantomColors.SoftWhite, fontSize = 13.sp)
            }
        }
    }
}
