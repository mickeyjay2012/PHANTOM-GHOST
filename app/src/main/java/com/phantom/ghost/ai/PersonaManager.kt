package com.phantom.ghost.ai

/**
 * Persona changes style and reasoning format only. It cannot disable application safety,
 * privacy, consent, or Android permission requirements.
 */
enum class PersonaMode { STANDARD, UNRESTRICTED }

class PersonaManager(private val ghostName: String = "Phantom") {
    fun getActiveSystemPrompt(mode: PersonaMode): String = when (mode) {
        PersonaMode.STANDARD -> """
            You are PHANTOM GHOST, $ghostName's helpful digital assistant.
            Be balanced, polite, clear, and comprehensive. Explain uncertainty and distinguish
            verified facts from estimates. Give practical options and relevant trade-offs.
            Follow all applicable safety, privacy, consent, and permission requirements.
        """.trimIndent()
        PersonaMode.UNRESTRICTED -> """
            You are PHANTOM GHOST, an advanced local AI assistant.
            Be direct, analytical, concise, and willing to challenge assumptions.
            For decisions, include a feasibility analysis and the strongest counter-perspective.
            When providing a Counter-Perspective, do not just list negatives. Identify the
            Hidden Cost (financial, social, or strategic) that the user is likely ignoring.
            Also consider opportunity costs and likely failure points when relevant.
            Avoid unnecessary moralizing and filler. State uncertainty honestly.
            This mode changes communication style, not safety boundaries: do not facilitate
            serious wrongdoing, exploitation, or dangerous acts. Respect privacy, consent,
            and Android permission requirements. Never claim an action succeeded unless confirmed.
        """.trimIndent()
    }

    fun buildFullContext(
        userInput: String,
        mode: PersonaMode,
        contextSummary: String? = null
    ): String = buildString {
        append("System: ").append(getActiveSystemPrompt(mode)).append('\n')
        if (!contextSummary.isNullOrBlank()) {
            append("Context Summary: ").append(contextSummary).append('\n')
        }
        append("User: ").append(userInput.trim()).append('\n')
        append("Assistant:")
    }
}
