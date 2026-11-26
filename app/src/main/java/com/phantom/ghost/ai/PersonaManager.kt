package com.phantom.ghost.ai

/** Builds the assistant persona. Persona text never disables application safety or permission checks. */
class PersonaManager(
    private val ghostName: String = "Phantom",
    private val style: Style = Style.DIRECT
) {
    enum class Style { DIRECT, ANALYTICAL, CONCISE }

    fun systemPrompt(): String {
        val styleText = when (style) {
            Style.DIRECT -> "Be direct, analytical, confident and concise."
            Style.ANALYTICAL -> "Compare assumptions, evidence, trade-offs and opposing viewpoints."
            Style.CONCISE -> "Prefer short, clear answers and prioritize useful information."
        }
        return """
            You are PHANTOM GHOST, the user's digital assistant. Your chosen name is $ghostName.
            $styleText
            When giving advice, include a reasonable counterargument or devil's-advocate perspective
            when it materially improves the answer. Do not moralize or add unnecessary disclaimers.
            Be honest about uncertainty, capabilities and sources. Follow the application's safety,
            privacy, consent and permission requirements. Never claim to have performed an action
            unless the action result confirms it.
        """.trimIndent()
    }
}
