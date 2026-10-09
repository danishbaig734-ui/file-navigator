package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testLogParser_withTimestampAndSuccess() {
    val line = "2026-10-08 17:10:14 — MOVED file.pdf -> Documents/"
    val entry = LogParser.parseLine(line)
    assertEquals("17:10:14", entry.timestamp)
    assertEquals("MOVED file.pdf -> Documents/", entry.message)
    assertEquals(LogType.SUCCESS, entry.type)
  }

  @Test
  fun testLogParser_withTimestampAndError() {
    val line = "2026-10-08 17:10:14 — FAILED: permission denied"
    val entry = LogParser.parseLine(line)
    assertEquals("17:10:14", entry.timestamp)
    assertEquals("FAILED: permission denied", entry.message)
    assertEquals(LogType.ERROR, entry.type)
  }

  @Test
  fun testLogParser_withTimestampAndNeutral() {
    val line = "2026-10-08 17:10:14 — Rules loaded"
    val entry = LogParser.parseLine(line)
    assertEquals("17:10:14", entry.timestamp)
    assertEquals("Rules loaded", entry.message)
    assertEquals(LogType.NEUTRAL, entry.type)
  }

  @Test
  fun testLogParser_unparseableTimestamp() {
    val line = "No events recorded yet."
    val entry = LogParser.parseLine(line)
    assertEquals("—", entry.timestamp)
    assertEquals("No events recorded yet.", entry.message)
    assertEquals(LogType.NEUTRAL, entry.type)
  }

  @Test
  fun testLogParser_emptyContent() {
    val entries = LogParser.parseContent("")
    assertEquals(1, entries.size)
    assertEquals("—", entries[0].timestamp)
    assertEquals("No events recorded yet.", entries[0].message)
    assertEquals(LogType.NEUTRAL, entries[0].type)
  }
}
