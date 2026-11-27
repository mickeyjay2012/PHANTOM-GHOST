package com.phantom.ghost.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaManagerTest {
    @Test fun standardPromptIsBalancedAndKeepsSafetyRequirements() {
        val prompt = PersonaManager("Phantom").getActiveSystemPrompt(PersonaMode.STANDARD)
        assertTrue(prompt.contains("balanced"))
        assertTrue(prompt.contains("safety"))
    }

    @Test fun unrestrictedPromptAddsStrategicPlannerWithoutDisablingSafety() {
        val prompt = PersonaManager("Phantom").getActiveSystemPrompt(PersonaMode.UNRESTRICTED)
        assertTrue(prompt.contains("STRATEGIC PLANNER"))
        assertTrue(prompt.contains("ghost_plan"))
        assertTrue(prompt.contains("Safety, privacy"))
        assertFalse(prompt.contains("not bound by typical"))
    }

    @Test fun fullContextIncludesModeAndUserInput() {
        val prompt = PersonaManager().buildFullContext(
            "Assess this idea", PersonaMode.UNRESTRICTED, "Previous discussion"
        )
        assertTrue(prompt.contains("Assess this idea"))
        assertTrue(prompt.contains("Previous discussion"))
    }
}
