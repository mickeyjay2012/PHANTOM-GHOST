package com.phantom.ghost.audio

import android.content.Context
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/**
 * CMUdict -> ARPAbet -> LJSpeech/eSpeak symbols -> model-specific integer IDs.
 *
 * Unknown words use a deliberately conservative English spelling heuristic and
 * are surfaced to the UI as LOW_CONFIDENCE_PHONEME events.
 */
class KotlinPhonemizer(context: Context) : PiperPhonemizer {
    private val lexicon: Map<String, List<String>> = loadLexicon(context)
    private val bundledMapper = PhonemeMapper.fromAssets(context)

    override fun toPhonemeIds(
        text: String,
        phonemeIdMap: Map<String, LongArray>,
        onLowConfidence: (String) -> Unit
    ): LongArray {
        val modelMapper = PhonemeMapper.fromModelConfig(phonemeIdMap)
        bundledMapper.requireCompatible(phonemeIdMap)
        val output = ArrayList<Long>()
        val tokens = TOKEN.findAll(text).map { it.value }.toList()
        output += modelMapper.map("^").toList()
        var hasToken = false

        tokens.forEach { token ->
            if (token.firstOrNull()?.isLetter() == true) {
                if (hasToken) output += modelMapper.map(" ").toList()
                val word = normalizeWord(token)
                val arpabet = lexicon[word]
                val espeak = if (arpabet != null) {
                    ArpabetToEspeak.convert(arpabet)
                } else {
                    onLowConfidence(token)
                    RuleBasedEnglishG2p.convert(word)
                }
                output += modelMapper.map(espeak).toList()
                hasToken = true
            } else {
                val punctuation = when (token) {
                    ".", "!", "?", ",", ";", ":" -> token
                    "'" -> "'"
                    else -> ""
                }
                if (punctuation.isNotEmpty()) {
                    output += modelMapper.map(punctuation).toList()
                    hasToken = true
                }
            }
        }
        output += modelMapper.map("\$").toList()
        return output.toLongArray()
    }

    private fun normalizeWord(word: String): String =
        Normalizer.normalize(word.lowercase(Locale.US).replace('’', '\''), Normalizer.Form.NFKC)

    private fun loadLexicon(context: Context): Map<String, List<String>> {
        val root = JSONObject(context.assets.open("cmu_lexicon.json").bufferedReader().use { it.readText() })
        val entries = root.getJSONObject("entries")
        val overrides = root.optJSONObject("qa_overrides")
        return buildMap(entries.length() + (overrides?.length() ?: 0)) {
            fun appendEntries(source: JSONObject?) {
                if (source == null) return
                val keys = source.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val phones = source.getJSONArray(key)
                    put(key, List(phones.length()) { index -> phones.getString(index) })
                }
            }
            appendEntries(entries)
            appendEntries(overrides)
        }
    }

    private companion object {
        val TOKEN = Regex("[\\p{L}]+(?:['’][\\p{L}]+)?|[.,!?;:]")
    }
}

internal object ArpabetToEspeak {
    private val consonants = mapOf(
        "B" to "b", "CH" to "tʃ", "D" to "d", "DH" to "ð", "F" to "f",
        "G" to "ɡ", "HH" to "h", "JH" to "dʒ", "K" to "k", "L" to "l",
        "M" to "m", "N" to "n", "NG" to "ŋ", "P" to "p", "R" to "ɹ",
        "S" to "s", "SH" to "ʃ", "T" to "t", "TH" to "θ", "V" to "v",
        "W" to "w", "Y" to "j", "Z" to "z", "ZH" to "ʒ",
        "DX" to "ɾ", "EL" to "əl", "EM" to "əm", "EN" to "ən",
        "NX" to "ŋ", "Q" to "ʔ", "WH" to "w"
    )
    private val vowels = mapOf(
        "AA" to "ɑː", "AE" to "æ", "AO" to "ɔː", "AW" to "aʊ",
        "AY" to "aɪ", "EH" to "ɛ", "ER" to "ɚ", "EY" to "eɪ",
        "IH" to "ɪ", "IX" to "ɪ", "IY" to "iː", "OW" to "oʊ", "OY" to "ɔɪ",
        "UH" to "ʊ", "UX" to "ʊ", "UW" to "uː", "AX" to "ə"
    )

    fun convert(phones: List<String>): String = buildString {
        phones.forEach { raw ->
            val stress = raw.lastOrNull()?.digitToIntOrNull()
            val phone = if (stress != null) raw.dropLast(1) else raw
            val vowel = vowels[phone]
            if (vowel != null) {
                when (stress) {
                    1 -> append("ˈ")
                    2 -> append("ˌ")
                }
                append(vowel)
            } else if (phone == "AH") {
                when (stress) {
                    1, 2 -> append("ʌ")
                    else -> append("ə")
                }
            } else {
                append(consonants[phone] ?: error("Unsupported CMU ARPAbet symbol: $raw"))
            }
        }
    }
}

/**
 * A deliberately marked fallback, not a claim of dictionary-grade pronunciation.
 * It handles common English grapheme patterns and leaves an audit event per word.
 */
internal object RuleBasedEnglishG2p {
    private val voicedThWords = setOf("the", "this", "that", "these", "those", "they", "them", "there", "then", "though", "thus")
    private val digraphs = listOf(
        "tion" to "ʃən", "sion" to "ʒən", "igh" to "aɪ", "ch" to "tʃ",
        "sh" to "ʃ", "ph" to "f", "th" to "θ", "ng" to "ŋ", "ck" to "k",
        "qu" to "kw", "ee" to "iː", "ea" to "iː", "oo" to "uː",
        "ai" to "eɪ", "ay" to "eɪ", "ow" to "aʊ", "ou" to "aʊ",
        "oi" to "ɔɪ", "oy" to "ɔɪ"
    )
    private val singles = mapOf(
        'a' to "æ", 'b' to "b", 'c' to "k", 'd' to "d", 'e' to "ɛ",
        'f' to "f", 'g' to "ɡ", 'h' to "h", 'i' to "ɪ", 'j' to "dʒ",
        'k' to "k", 'l' to "l", 'm' to "m", 'n' to "n", 'o' to "ɒ",
        'p' to "p", 'q' to "k", 'r' to "ɹ", 's' to "s", 't' to "t",
        'u' to "ʌ", 'v' to "v", 'w' to "w", 'x' to "ks", 'y' to "j", 'z' to "z"
    )

    fun convert(word: String): String {
        if (word.isEmpty()) return "ə"
        val input = word.lowercase(Locale.US).replace("'", "")
        val out = StringBuilder()
        var i = 0
        while (i < input.length) {
            val match = digraphs.firstOrNull { (spelling, _) -> input.startsWith(spelling, i) }
            if (match != null) {
                val sound = if (match.first == "th" && i == 0 && input in voicedThWords) "ð" else match.second
                out.append(sound)
                i += match.first.length
                continue
            }
            val ch = input[i]
            val next = input.getOrNull(i + 1)
            val finalSilentE = ch == 'e' && i == input.lastIndex && input.length > 2
            if (!finalSilentE) {
                val sound = when {
                    ch == 'c' && next in listOf('e', 'i', 'y') -> "s"
                    ch == 'g' && next in listOf('e', 'i', 'y') -> "dʒ"
                    ch == 'a' && next == 'e' -> "eɪ"
                    else -> singles[ch]
                }
                if (sound != null) out.append(sound)
            }
            i++
        }
        return out.toString().ifBlank { "ə" }
    }
}
