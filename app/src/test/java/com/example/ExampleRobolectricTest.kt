package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.lyrics.LyricsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("My Music Player", appName)
    }

    @Test
    fun `test lyrics parser synchronized timestamps`() {
        val lrc = """
            [00:00.00]Line 1 starts
            [00:05.50]Line 2 starts
            [00:10.00]Line 3 starts
        """.trimIndent()

        val parsed = LyricsParser.parse(lrc)
        assertTrue(parsed.isSynchronized)
        assertEquals(3, parsed.lines.size)
        assertEquals(0L, parsed.lines[0].timestampMs)
        assertEquals(5500L, parsed.lines[1].timestampMs)
        assertEquals(10000L, parsed.lines[2].timestampMs)

        // At 2000ms -> Line 0
        assertEquals(0, LyricsParser.findActiveLineIndex(parsed, 2000L))
        // At 7000ms -> Line 1
        assertEquals(1, LyricsParser.findActiveLineIndex(parsed, 7000L))
        // At 15000ms -> Line 2
        assertEquals(2, LyricsParser.findActiveLineIndex(parsed, 15000L))
    }
}
