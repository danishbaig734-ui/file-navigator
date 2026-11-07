package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for LogParser and LogRun data model.
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testLogParser_singleRunSuccess() {
    val rawLog = """
      [2026-11-07 22:46:26]
      Moved file1.m4a -> audio
      Moved file2.m4a -> audio
      Done — 2 moved, 0 unmatched
      ---
    """.trimIndent()

    val runs = LogParser.parseLogLines(rawLog)
    assertEquals(1, runs.size)
    val run = runs[0]
    assertEquals("2026-11-07 22:46:26", run.timestamp)
    assertEquals(2, run.messages.size)
    assertEquals("Moved file1.m4a -> audio", run.messages[0])
    assertEquals("Moved file2.m4a -> audio", run.messages[1])
    assertEquals("Done — 2 moved, 0 unmatched", run.summary)
    assertEquals(LogType.SUCCESS, run.type)
  }

  @Test
  fun testLogParser_multiRunSeparatedByDashes() {
    val rawLog = """
      [2026-11-07 21:00:00]
      Moved song.mp3 -> audio
      Done — 1 moved, 0 unmatched
      ---
      [2026-11-07 22:00:00]
      Moved video.mp4 -> video
      Moved movie.mkv -> video
      Failed: error moving file
      ---
    """.trimIndent()

    val runs = LogParser.parseLogLines(rawLog)
    assertEquals(2, runs.size)

    assertEquals("2026-11-07 21:00:00", runs[0].timestamp)
    assertEquals(listOf("Moved song.mp3 -> audio"), runs[0].messages)
    assertEquals("Done — 1 moved, 0 unmatched", runs[0].summary)
    assertEquals(LogType.SUCCESS, runs[0].type)

    assertEquals("2026-11-07 22:00:00", runs[1].timestamp)
    assertEquals(listOf("Moved video.mp4 -> video", "Moved movie.mkv -> video"), runs[1].messages)
    assertEquals("Failed: error moving file", runs[1].summary)
    assertEquals(LogType.ERROR, runs[1].type)
  }

  @Test
  fun testLogParser_neutralType() {
    val rawLog = """
      [2026-11-07 23:00:00]
      Processing queue
      Paused by user
      ---
    """.trimIndent()

    val runs = LogParser.parseLogLines(rawLog)
    assertEquals(1, runs.size)
    assertEquals(LogType.NEUTRAL, runs[0].type)
    assertEquals("Paused by user", runs[0].summary)
  }

  @Test
  fun testLogParser_emptyOrBlank() {
    val runs = LogParser.parseLogLines("")
    assertTrue(runs.isEmpty())
  }

  @Test
  fun testLogParser_singleLineStatus() {
    val rawLog = "(tree URI invalid — re-grant folder access)"
    val runs = LogParser.parseLogLines(rawLog)
    assertEquals(1, runs.size)
    assertEquals("—", runs[0].timestamp)
    assertEquals("(tree URI invalid — re-grant folder access)", runs[0].summary)
    assertTrue(runs[0].messages.isEmpty())
  }
}
