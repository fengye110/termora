package app.termora

import app.termora.terminal.panel.convertWords
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 双击选词：按空白字符切分（所有非空白字符视为一个整体）。
 */
class ConvertWordsTest {

    @Test
    fun `path is selected as a whole`() {
        val text = "/root/zai-org/glm-5.3-flash-sm120"
        assertEquals(
            listOf(Triple(0, text, text.length)),
            convertWords(text)
        )
    }

    @Test
    fun `words split by whitespace`() {
        assertEquals(
            listOf(Triple(0, "Hello", 5), Triple(6, "World", 11)),
            convertWords("Hello World")
        )
    }

    @Test
    fun `chinese width compensation`() {
        // 你好(4列) + 空格(1列) + 世界(4列)
        assertEquals(
            listOf(Triple(0, "你好", 4), Triple(5, "世界", 9)),
            convertWords("你好 世界")
        )
    }

    @Test
    fun `mixed chinese and ascii width compensation`() {
        // a你好(5列) + 空格(1列) + b世界(5列)
        assertEquals(
            listOf(Triple(0, "a你好", 5), Triple(6, "b世界", 11)),
            convertWords("a你好 b世界")
        )
    }

    @Test
    fun `tab is whitespace`() {
        assertEquals(
            listOf(Triple(0, "foo", 3), Triple(4, "bar", 7), Triple(8, "baz", 11)),
            convertWords("foo\tbar baz")
        )
    }

    @Test
    fun `leading and trailing whitespace is ignored`() {
        assertEquals(
            listOf(Triple(1, "ab", 3)),
            convertWords(" ab ")
        )
    }
}
