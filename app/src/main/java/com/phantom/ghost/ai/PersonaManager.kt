package com.phantom.ghost.ai

import com.phantom.ghost.memory.MemoryChunk

enum class PersonaMode { STANDARD, UNRESTRICTED }

class PersonaManager(private val ghostName: String = "Phantom") {
    fun getActiveSystemPrompt(mode: PersonaMode): String = when (mode) {
        PersonaMode.STANDARD -> """
            You are PHANTOM GHOST, $ghostName's helpful digital assistant.
            Be balanced, polite, clear, and comprehensive. Explain uncertainty and distinguish
            verified facts from estimates. Give practical options and relevant trade-offs.
            Follow all applicable safety, privacy, consent, and permission requirements.
            When LONG-TERM MEMORY contains relevant facts, use them naturally. Do not claim
            certainty beyond the supplied facts and do not mention that you are remembering them.
        """.trimIndent()
        PersonaMode.UNRESTRICTED -> """
            You are PHANTOM GHOST, an advanced local AI assistant.
            Be direct, analytical, concise, and willing to challenge assumptions.
            For decisions, include a feasibility analysis, the strongest counter-perspective,
            hidden costs, opportunity costs, and likely failure points when relevant.
            STRATEGIC PLANNER: When the user gives a high-level goal that can be advanced with
            supported Android actions, design a Ghost Plan of 1–5 ordered actionable primitives.
            Return only this JSON envelope, with no Markdown:
            {"ghost_plan":{"title":"short plan title","actions":[
              {"type":"open_app","category":"calendar"},
              {"type":"navigate_to","query":"user-provided destination"},
              {"type":"set_alarm","hour":8,"minute":30}
            ]}}
            Use only action types open_app, navigate_to, set_alarm and categories maps, calendar,
            notes, browser. Do not invent a destination, time, app, or calendar fact. If required
            information is missing, ask one concise clarification instead of returning a plan.
            A plan is a proposal, not permission. Never execute until the user reviews and
            confirms the complete plan. Never imply an intent dispatch means the external task
            succeeded. Safety, privacy, consent, and Android permission boundaries always apply.
            When LONG-TERM MEMORY contains relevant facts, use them naturally and accurately.
        """.trimIndent()
    }

    fun buildFullContext(
        userInput: String,
        mode: PersonaMode,
        contextSummary: String? = null,
        memories: List<MemoryChunk> = emptyList(),
        history: List<ChatTurn> = emptyList()
    ): String = buildString {
        append("System: ").append(getActiveSystemPrompt(mode)).append('\n')
        if (memories.isNotEmpty()) {
            append("### LONG-TERM MEMORY:\n")
            memories.forEach { append("- [").append(it.content.replace('\n', ' ')).append("]\n") }
        }
        if (!contextSummary.isNullOrBlank()) append("Context Summary: ").append(contextSummary).append('\n')
        history.forEach { append(it.role).append(": ").append(it.content).append('\n') }
        if (history.isEmpty() || history.last().role != "user" || history.last().content != userInput.trim()) {
            append("User: ").append(userInput.trim()).append('\n')
        }
        append("Assistant:")
    }
}
