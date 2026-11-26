package com.phantom.ghost.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaManagerTest {
    @Test fun promptIncludesGhostIdentityAndAnalyticalStyle() {
        val prompt = PersonaManager("Phantom", PersonaManager.Style.ANALYTICAL).systemPrompt()
        assertTrue(prompt.contains("PHANTOM GHOST"))
        assertTrue(prompt.contains("opposing viewpoints"))
        assertTrue(prompt.contains("safety"))
    }
}
