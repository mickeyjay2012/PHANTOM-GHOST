package com.phantom.ghost.ai

/** Explicit read-only allowlist; consequential actions require a separate consent flow. */
object ToolPolicy {
    val allowedReadOnlyTools: Set<String> = setOf("calculator", "local_memory_search")
    fun isAllowedReadOnlyTool(name: String): Boolean = name in allowedReadOnlyTools
}
