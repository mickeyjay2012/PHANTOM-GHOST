package com.phantom.ghost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.phantom.ghost.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PhantomGhostApp() }
    }
}

@Composable
private fun PhantomGhostApp() {
    var screen by remember { mutableStateOf(PhantomScreen.ASSISTANT) }
    var showAction by remember { mutableStateOf(false) }
    var showBanner by remember { mutableStateOf(true) }
    PhantomDesignSystem {
        Surface(Modifier.fillMaxSize(), color=PhantomColors.PhantomBlack) {
            Column(Modifier.fillMaxSize().background(PhantomColors.PhantomBlack)) {
                if (screen != PhantomScreen.ASSISTANT && showBanner) {
                    SmartNotificationBanner("PHANTOM GHOST is standing by.") { showBanner=false }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when(screen) {
                        PhantomScreen.ONBOARDING -> OnboardingScreen { screen=PhantomScreen.VOICE }
                        PhantomScreen.VOICE -> VoiceprintEnrollmentScreen { screen=PhantomScreen.ASSISTANT }
                        PhantomScreen.ASSISTANT -> MainAssistantScreen(
                            onConfirmAction={showAction=true},
                            onOpenMemory={screen=PhantomScreen.MEMORY},
                            onOpenModels={screen=PhantomScreen.MODELS}
                        )
                        PhantomScreen.MEMORY -> MemoryProfileScreen()
                        PhantomScreen.MODELS -> ModelManagerScreen()
                    }
                }
                if(screen != PhantomScreen.ASSISTANT) {
                    NavigationBar(containerColor=PhantomColors.PhantomCharcoal, tonalElevation=0.dp) {
                        listOf(
                            Triple(PhantomScreen.ONBOARDING,"SETUP","⌂"),
                            Triple(PhantomScreen.VOICE,"VOICE","◉"),
                            Triple(PhantomScreen.ASSISTANT,"GHOST","✧"),
                            Triple(PhantomScreen.MEMORY,"MEMORY","◇"),
                            Triple(PhantomScreen.MODELS,"SYSTEM","▦")
                        ).forEach { (target,label,icon) ->
                            NavigationBarItem(selected=screen==target,onClick={screen=target},
                                icon={Text(icon,color=if(screen==target) PhantomColors.GhostCyan else PhantomColors.Muted)},
                                label={Text(label,style=PhantomType.label,fontSize=8.sp)},
                                colors=NavigationBarItemDefaults.colors(
                                    selectedIconColor=PhantomColors.GhostCyan,selectedTextColor=PhantomColors.GhostCyan,
                                    indicatorColor=PhantomColors.GhostCyan.copy(alpha=.10f),
                                    unselectedTextColor=PhantomColors.Muted))
                        }
                    }
                }
            }
            if(showAction) ActionConfirmationDialog(
                "Open an app or navigate using an approved Android action.",
                onConfirm={showAction=false},onAbort={showAction=false}
            )
        }
    }
}
