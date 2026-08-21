package top.wkbin.taixu.runtime.pty

import org.junit.Assert.assertEquals
import org.junit.Test

class IncrementalUtf8DecoderTest {
    @Test
    fun preservesMultibyteCharacterAcrossChunks() {
        val decoder = IncrementalUtf8Decoder(maxChunkBytes = 8)
        val bytes = "太墟".toByteArray(Charsets.UTF_8)

        val first = decoder.decode(bytes.copyOfRange(0, 2), 2)
        val secondBytes = bytes.copyOfRange(2, bytes.size)
        val second = decoder.decode(secondBytes, secondBytes.size)

        assertEquals("", first)
        assertEquals("太墟", second + decoder.finish())
    }

    @Test
    fun replacesIncompleteSequenceAtEndOfInput() {
        val decoder = IncrementalUtf8Decoder(maxChunkBytes = 8)
        val bytes = "太".toByteArray(Charsets.UTF_8)

        assertEquals("", decoder.decode(bytes, 2))
        assertEquals("�", decoder.finish())
    }
}
