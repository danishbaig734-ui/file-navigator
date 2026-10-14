package com.example

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan

/**
 * Syntax highlighter for rules.conf files.
 * Highlights section headers as glowing badge pills, paths in dedicated path capsules,
 * keys in warm amber, and comma-separated extensions as individual rounded chip pills.
 */
object RulesSyntaxHighlighter {

    private val SECTION_REGEX = Regex("""(?m)^\s*(\[[^\]\n]+\])""")
    private val KEY_REGEX = Regex("""(?m)^\s*([a-zA-Z0-9_]+)\s*(=)""")
    private val PATH_REGEX = Regex("""(?:/[a-zA-Z0-9_\-\.]+)+""")
    private val EXT_LINE_REGEX = Regex("""(?m)^\s*ext\s*=\s*(.*)$""")
    private val EXT_TOKEN_REGEX = Regex("""[a-zA-Z0-9_\-]+""")

    fun highlight(
        text: CharSequence,
        isDark: Boolean,
        isReadMode: Boolean,
        density: Float = 2.0f
    ): Spannable {
        val spannable = if (text is Spannable) text else SpannableStringBuilder(text)

        // Clear existing syntax spans
        val existingPillSpans = spannable.getSpans(0, spannable.length, RoundedPillSpan::class.java)
        for (span in existingPillSpans) {
            spannable.removeSpan(span)
        }
        val existingColorSpans = spannable.getSpans(0, spannable.length, ForegroundColorSpan::class.java)
        for (span in existingColorSpans) {
            spannable.removeSpan(span)
        }
        val existingBgSpans = spannable.getSpans(0, spannable.length, BackgroundColorSpan::class.java)
        for (span in existingBgSpans) {
            spannable.removeSpan(span)
        }
        val existingStyleSpans = spannable.getSpans(0, spannable.length, StyleSpan::class.java)
        for (span in existingStyleSpans) {
            spannable.removeSpan(span)
        }

        // Color configurations
        val sectionBg = if (isDark) 0x3300E5FF.toInt() else 0xFFE1F5FE.toInt()
        val sectionBorder = if (isDark) 0xFF00E5FF.toInt() else 0xFF0288D1.toInt()
        val sectionText = if (isDark) 0xFF80D8FF.toInt() else 0xFF01579B.toInt()

        val pathBg = if (isDark) 0xFF14241B.toInt() else 0xFFE8F5E9.toInt()
        val pathBorder = if (isDark) 0xFF2E7D32.toInt() else 0xFF4CAF50.toInt()
        val pathText = if (isDark) 0xFFA5D6A7.toInt() else 0xFF1B5E20.toInt()

        val keyTextColor = if (isDark) 0xFFFFB74D.toInt() else 0xFFE65100.toInt()
        val equalSignColor = if (isDark) 0xFF888888.toInt() else 0xFF555555.toInt()

        val extBg = if (isDark) 0xFF2D1B38.toInt() else 0xFFF3E5F5.toInt()
        val extBorder = if (isDark) 0xFFAB47BC.toInt() else 0xFF9C27B0.toInt()
        val extText = if (isDark) 0xFFF48FB1.toInt() else 0xFF6A1B9A.toInt()

        val defaultTextColor = if (isDark) 0xFFCCCCCC.toInt() else 0xFF333333.toInt()
        spannable.setSpan(
            ForegroundColorSpan(defaultTextColor),
            0,
            spannable.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // 1. Highlight Keys: scan_dirs =, dest =, ext =
        for (match in KEY_REGEX.findAll(spannable)) {
            val keyGroup = match.groups[1]
            val eqGroup = match.groups[2]
            if (keyGroup != null) {
                spannable.setSpan(
                    ForegroundColorSpan(keyTextColor),
                    keyGroup.range.first,
                    keyGroup.range.last + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    keyGroup.range.first,
                    keyGroup.range.last + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (eqGroup != null) {
                spannable.setSpan(
                    ForegroundColorSpan(equalSignColor),
                    eqGroup.range.first,
                    eqGroup.range.last + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        // 2. Highlight Sections: [settings], [audio], etc.
        for (match in SECTION_REGEX.findAll(spannable)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (isReadMode) {
                spannable.setSpan(
                    RoundedPillSpan(
                        bgColor = sectionBg,
                        textColor = sectionText,
                        borderColor = sectionBorder,
                        cornerRadius = 12f * density,
                        paddingH = 12f * density,
                        paddingV = 4f * density,
                        isBold = true,
                        strokeWidth = 2f * density
                    ),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                spannable.setSpan(
                    BackgroundColorSpan(sectionBg),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    ForegroundColorSpan(sectionText),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        // 3. Highlight Paths: /sdcard/Download, /sdcard/Music, etc.
        for (match in PATH_REGEX.findAll(spannable)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (isReadMode) {
                spannable.setSpan(
                    RoundedPillSpan(
                        bgColor = pathBg,
                        textColor = pathText,
                        borderColor = pathBorder,
                        cornerRadius = 8f * density,
                        paddingH = 10f * density,
                        paddingV = 3f * density,
                        isBold = true,
                        strokeWidth = 1.5f * density
                    ),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                spannable.setSpan(
                    BackgroundColorSpan(pathBg),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    ForegroundColorSpan(pathText),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        // 4. Highlight Individual Extension Chips: pdf, doc, docx, etc. after ext =
        for (lineMatch in EXT_LINE_REGEX.findAll(spannable)) {
            val extValuesGroup = lineMatch.groups[1] ?: continue
            val extSubstr = extValuesGroup.value
            val baseOffset = extValuesGroup.range.first

            for (tokenMatch in EXT_TOKEN_REGEX.findAll(extSubstr)) {
                val tokenStart = baseOffset + tokenMatch.range.first
                val tokenEnd = baseOffset + tokenMatch.range.last + 1
                if (isReadMode) {
                    spannable.setSpan(
                        RoundedPillSpan(
                            bgColor = extBg,
                            textColor = extText,
                            borderColor = extBorder,
                            cornerRadius = 7f * density,
                            paddingH = 8f * density,
                            paddingV = 2.5f * density,
                            isBold = true,
                            strokeWidth = 1.5f * density
                        ),
                        tokenStart,
                        tokenEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                } else {
                    spannable.setSpan(
                        BackgroundColorSpan(extBg),
                        tokenStart,
                        tokenEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.setSpan(
                        ForegroundColorSpan(extText),
                        tokenStart,
                        tokenEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.setSpan(
                        StyleSpan(Typeface.BOLD),
                        tokenStart,
                        tokenEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }

        return spannable
    }
}
