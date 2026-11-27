package com.phantom.ghost.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KotlinPhonemizerTest {
    @Test
    fun algorithmUsesLjspeechCompatibleSymbols() {
        val result = ArpabetToEspeak.convert(
            listOf("AE1", "L", "G", "ER0", "IH2", "DH", "AH0", "M")
        )
        assertEquals("ˈælɡɚˌɪðəm", result)
    }

    @Test
    fun phantomUsesLexicalStressAndConsonants() {
        val result = ArpabetToEspeak.convert(listOf("F", "AE1", "N", "T", "AH0", "M"))
        assertEquals("fˈæntəm", result)
    }

    @Test
    fun decentralizedPreservesSecondaryStress() {
        val result = ArpabetToEspeak.convert(
            listOf("D", "IY0", "S", "EH1", "N", "T", "R", "AH0", "L", "AY2", "Z", "D")
        )
        assertEquals("diːsˈɛntɹəlˌaɪzd", result)
    }

    @Test
    fun unknownWordFallbackProducesVisiblePhonemeMaterial() {
        assertFalse(RuleBasedEnglishG2p.convert("kubernetes").isBlank())
    }

    @Test
    fun mapperExpandsEachUnicodeSymbolThroughModelIds() {
        val mapper = PhonemeMapper(
            mapOf("ˈ" to longArrayOf(120), "æ" to longArrayOf(39), "ɡ" to longArrayOf(66))
        )
        assertEquals(listOf(120L, 39L, 66L), mapper.map("ˈæɡ").toList())
    }
}
