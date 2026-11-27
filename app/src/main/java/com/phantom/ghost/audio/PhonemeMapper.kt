package com.phantom.ghost.audio

import android.content.Context
import org.json.JSONObject

/**
 * Converts the eSpeak phoneme stream into the exact integer sequence expected by
 * the pinned LJSpeech ONNX model. The ONNX config remains the runtime authority.
 */
class PhonemeMapper(private val symbolIds: Map<String, LongArray>) {
    fun map(symbolStream: String): LongArray {
        val output = ArrayList<Long>(symbolStream.length)
        var offset = 0
        while (offset < symbolStream.length) {
            val codePoint = symbolStream.codePointAt(offset)
            val symbol = String(Character.toChars(codePoint))
            val ids = symbolIds[symbol]
                ?: throw IllegalArgumentException("LJSpeech phoneme map does not contain symbol '$symbol'.")
            ids.forEach(output::add)
            offset += Character.charCount(codePoint)
        }
        return output.toLongArray()
    }

    fun requireCompatible(modelMap: Map<String, LongArray>) {
        require(symbolIds.keys == modelMap.keys) {
            "Bundled LJSpeech symbol map does not match the selected model config."
        }
        symbolIds.forEach { (symbol, ids) ->
            require(ids.contentEquals(modelMap.getValue(symbol))) {
                "LJSpeech symbol ID mismatch for '$symbol'."
            }
        }
    }

    companion object {
        fun fromAssets(context: Context, assetName: String = "ljspeech_id_map.json"): PhonemeMapper {
            val root = JSONObject(context.assets.open(assetName).bufferedReader().use { it.readText() })
            val idsObject = root.getJSONObject("phoneme_id_map")
            val ids = buildMap {
                val keys = idsObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val values = idsObject.getJSONArray(key)
                    put(key, LongArray(values.length()) { index -> values.getLong(index) })
                }
            }
            require(ids.isNotEmpty()) { "The bundled LJSpeech phoneme map is empty." }
            return PhonemeMapper(ids)
        }

        fun fromModelConfig(modelMap: Map<String, LongArray>) = PhonemeMapper(modelMap)
    }
}
