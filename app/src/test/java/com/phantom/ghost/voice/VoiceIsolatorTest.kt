package com.phantom.ghost.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceIsolatorTest {
    @Test fun identicalVectorsMatch() {
        val vector = FloatArray(256) { if (it == 0) 1f else 0f }
        assertEquals(VoiceIsolator.Decision.MATCH, VoiceIsolator().compare(vector, vector))
    }

    @Test fun differentVectorsReject() {
        val a = FloatArray(256) { if (it == 0) 1f else 0f }
        val b = FloatArray(256) { if (it == 1) 1f else 0f }
        assertEquals(VoiceIsolator.Decision.REJECT, VoiceIsolator().compare(a, b))
    }

    @Test fun invalidDimensionsAreRejected() {
        assertEquals(VoiceIsolator.Decision.INVALID_EMBEDDING, VoiceIsolator().compare(FloatArray(3), FloatArray(3)))
    }

    @Test fun zeroVectorHasNoSimilarity() {
        assertNull(VoiceIsolator().cosineSimilarity(FloatArray(256), FloatArray(256)))
    }
}
