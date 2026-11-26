package to.eyed.inferno.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownChunkerTest {
    @Test
    fun `paragraphs split on blank lines`() {
        val chunks = MarkdownChunker.chunks("one\n\ntwo\n\n\nthree")
        assertEquals(listOf("one", "two", "three"), chunks)
    }

    @Test
    fun `unterminated fence stays in the tail chunk while streaming`() {
        val chunks = MarkdownChunker.chunks("intro\n\n```kotlin\nval a = 1\n\nval b = 2")
        assertEquals(2, chunks.size)
        assertEquals("```kotlin\nval a = 1\n\nval b = 2", chunks[1])
    }

    @Test
    fun `terminated fence is one chunk and later text is another`() {
        val chunks = MarkdownChunker.chunks("```\na\n\nb\n```\n\nafter")
        assertEquals(listOf("```\na\n\nb\n```", "after"), chunks)
    }

    @Test
    fun `inline code at line start is not a fence opener`() {
        val chunks = MarkdownChunker.chunks("```foo``` does X\n\nnext\n\nlast")
        assertEquals(listOf("```foo``` does X", "next", "last"), chunks)
    }

    @Test
    fun `longer backtick fence contains a shorter one`() {
        val chunks = MarkdownChunker.chunks("````md\n```py\nx\n\ny\n```\n````\n\nafter")
        assertEquals(listOf("````md\n```py\nx\n\ny\n```\n````", "after"), chunks)
    }

    @Test
    fun `tilde fence is not closed by backticks`() {
        val chunks = MarkdownChunker.chunks("~~~\n```\na\n\nb\n~~~\n\nafter")
        assertEquals(listOf("~~~\n```\na\n\nb\n~~~", "after"), chunks)
    }

    @Test
    fun `closer with an info string is content`() {
        val chunks = MarkdownChunker.chunks("```\ncode\n```extra\n\nstill code\n```\n\nafter")
        assertEquals(listOf("```\ncode\n```extra\n\nstill code\n```", "after"), chunks)
    }

    @Test
    fun `list runs separated by blank lines stay together`() {
        val chunks = MarkdownChunker.chunks("1. first\n\n2. second\n\n- bullet\n\nplain")
        assertEquals(2, chunks.size)
        assertEquals("1. first\n\n2. second\n\n- bullet", chunks[0])
        assertEquals("plain", chunks[1])
    }

    @Test
    fun `earlier chunks are stable as the tail grows`() {
        val a = MarkdownChunker.chunks("# Title\n\nSome text that is")
        val b = MarkdownChunker.chunks("# Title\n\nSome text that is growing\n\nNew para")
        assertEquals(a[0], b[0])
        assertTrue(b.size > a.size)
    }

    @Test
    fun `blank input yields no chunks`() {
        assertTrue(MarkdownChunker.chunks("").isEmpty())
        assertTrue(MarkdownChunker.chunks("\n\n  \n").isEmpty())
    }

    @Test
    fun `open fence is split out for live syntax highlighted rendering`() {
        val fence = 96.toChar().toString().repeat(3)
        val source = "intro\n\n" + fence + "html\n<div>hello</div>"
        val (prefix, code) = MarkdownChunker.splitOpenFence(source)
        assertEquals("intro", prefix)
        assertEquals("html", code?.language)
        assertEquals("<div>hello</div>", code?.code)
    }

    @Test
    fun `closed fence remains in markdown chunks`() {
        val fence = 96.toChar().toString().repeat(3)
        val source = "intro\n\n" + fence + "html\n<div/>\n" + fence
        val (prefix, code) = MarkdownChunker.splitOpenFence(source)
        assertEquals(source, prefix)
        assertEquals(null, code)
    }

    @Test
    fun `math delimiters and common latex identifiers render as unicode`() {
        assertEquals("Result: (1)⁄(2) + α²", MarkdownMath.format("""Result: $\frac{1}{2} + \alpha^2$"""))
    }

    @Test
    fun `math formatter leaves fenced code untouched`() {
        val fence = 96.toChar().toString().repeat(3)
        val source = fence + "tex\n$\\alpha$\n" + fence
        assertEquals(source, MarkdownMath.format(source))
    }

    @Test
    fun `duration formatting`() {
        assertEquals("8.1 s", formatDuration(8_100))
        assertEquals("1 min 8 s", formatDuration(68_000))
        assertEquals("0.3 s", formatDuration(300))
    }
}
