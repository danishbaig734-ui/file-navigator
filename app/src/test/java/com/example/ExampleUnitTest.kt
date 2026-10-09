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

  @Test
  fun testLogParser_startServiceAndBindServiceAreSuccess() {
    val startEntry = LogParser.parseLine("2026-10-08 17:10:14 — Termux started via startService")
    assertEquals(LogType.SUCCESS, startEntry.type)

    val bindEntry = LogParser.parseLine("2026-10-08 17:10:14 — Termux bound via bindService")
    assertEquals(LogType.SUCCESS, bindEntry.type)

    val doneEntry = LogParser.parseLine("2026-10-08 17:10:14 — === Done (all rules applied)")
    assertEquals(LogType.SUCCESS, doneEntry.type)

    val runStartedEntry = LogParser.parseLine("2026-10-08 17:10:14 — === Run started ===")
    assertEquals(LogType.SUCCESS, runStartedEntry.type)
  }

  @Test
  fun testLogParser_errorPrecedenceOverSuccess() {
    // Contains "Termux" and "intent failed" -> error should take precedence
    val failedEntry = LogParser.parseLine("2026-10-08 17:10:14 — Termux intent failed")
    assertEquals(LogType.ERROR, failedEntry.type)

    val blockedEntry = LogParser.parseLine("2026-10-08 17:10:14 — startService blocked: IllegalStateException")
    assertEquals(LogType.ERROR, blockedEntry.type)

    val secException = LogParser.parseLine("2026-10-08 17:10:14 — SecurityException: not permitted")
    assertEquals(LogType.ERROR, secException.type)

    val saveFailed = LogParser.parseLine("2026-10-08 17:10:14 — rules.conf save failed")
    assertEquals(LogType.ERROR, saveFailed.type)
  }

  @Test
  fun testLogParser_neutralCases() {
    val savedEntry = LogParser.parseLine("2026-10-08 17:10:14 — rules.conf saved")
    assertEquals(LogType.NEUTRAL, savedEntry.type)

    val collisionEntry = LogParser.parseLine("2026-10-08 17:10:14 — COLLISION: file already exists")
    assertEquals(LogType.NEUTRAL, collisionEntry.type)

    val removedEntry = LogParser.parseLine("2026-10-08 17:10:14 — REMOVED empty folder")
    assertEquals(LogType.NEUTRAL, removedEntry.type)

    val regrantedEntry = LogParser.parseLine("2026-10-08 17:10:14 — tree URI re-granted")
    assertEquals(LogType.NEUTRAL, regrantedEntry.type)

    val ignoreSaved = LogParser.parseLine("2026-10-08 17:10:14 — ignore list saved")
    assertEquals(LogType.NEUTRAL, ignoreSaved.type)
  }
}
