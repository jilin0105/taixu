package top.wkbin.taixu.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnsiTerminalBufferTest {
    @Test
    fun preservesSgrStyleAcrossChunkBoundaries() {
        val buffer = AnsiTerminalBuffer(columns = 20, maxRows = 10)
        buffer.append("hello\u001B[3")
        val screen = buffer.append("1mred\u001B[0m!")

        val line = screen.first()
        assertEquals("hellored!", line.cells.joinToString("") { it.character.toString() })
        assertEquals(0xFFCC0000L, line.cells[5].foreground)
        assertEquals(null, line.cells.last().foreground)
    }

    @Test
    fun handlesCursorMovementAndEraseLine() {
        val buffer = AnsiTerminalBuffer(columns = 20, maxRows = 10)
        buffer.append("hello")
        val screen = buffer.append("\u001B[1G\u001B[Kx")

        assertEquals("x", screen.first().cells.joinToString("") { it.character.toString() })
    }

    @Test
    fun scrollbackIsBounded() {
        val buffer = AnsiTerminalBuffer(columns = 20, maxRows = 3)
        val screen = buffer.append("one\ntwo\nthree\nfour")

        assertTrue(screen.size <= 3)
        assertEquals("four", screen.last().cells.joinToString("") { it.character.toString() })
    }
}
