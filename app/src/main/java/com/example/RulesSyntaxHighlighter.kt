package com.example

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan

class RulesColorSpan(color: Int) : ForegroundColorSpan(color), RulesSyntaxSpan
class RulesBgSpan(color: Int) : BackgroundColorSpan(color), RulesSyntaxSpan
class RulesStyleSpan(style: Int) : StyleSpan(style), RulesSyntaxSpan

/**
 * High-performance syntax highlighter for rules.conf files.
 * Aligned strictly with the app's minimalist monochrome and high-contrast aesthetic.
 * Optimized with single-pass span recycling, fast String regex searching, and zero-allocation text scanning.
 */
object RulesSyntaxHighlighter {

    val SECTION_REGEX = Regex("""(?m)^\s*(\[[^\]\n]+\])""")
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
        val textLength = spannable.length
        if (textLength == 0) return spannable

        // 1. Fast single-pass removal of existing syntax spans (leaves IME, selection, and system spans untouched)
        val existingSpans = spannable.getSpans(0, textLength, RulesSyntaxSpan::class.java)
        for (span in existingSpans) {
            spannable.removeSpan(span)
        }

        // Clean monochrome theme palette
        val defaultTextColor = if (isDark) 0xFFE0E0E0.toInt() else 0xFF222222.toInt()

        val sectionBg = if (isDark) 0xFF282828.toInt() else 0xFFE5E5E5.toInt()
        val sectionBorder = if (isDark) 0xFF404040.toInt() else 0xFFD0D0D0.toInt()
        val sectionText = if (isDark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

        val pathBg = if (isDark) 0xFF202224.toInt() else 0xFFEEEEEE.toInt()
        val pathBorder = if (isDark) 0xFF34383C.toInt() else 0xFFD8D8D8.toInt()
        val pathText = if (isDark) 0xFFD0D7DE.toInt() else 0xFF1A1A1A.toInt()

        val keyTextColor = if (isDark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val equalSignColor = if (isDark) 0xFF888888.toInt() else 0xFF666666.toInt()

        val extBg = if (isDark) 0xFF262626.toInt() else 0xFFEAEAEA.toInt()
        val extBorder = if (isDark) 0xFF3C3C3C.toInt() else 0xFFD4D4D4.toInt()
        val extText = if (isDark) 0xFFE6E6E6.toInt() else 0xFF222222.toInt()

        // Default base text color
        spannable.setSpan(
            RulesColorSpan(defaultTextColor),
            0,
            textLength,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Convert to string once for fast regex matching
        val textStr = spannable.toString()

        // 2. Highlight Keys: scan_dirs =, dest =, ext =
        for (match in KEY_REGEX.findAll(textStr)) {
            val keyGroup = match.groups[1]
            val eqGroup = match.groups[2]
            if (keyGroup != null) {
                val start = keyGroup.range.first
                val end = keyGroup.range.last + 1
                spannable.setSpan(
                    RulesColorSpan(keyTextColor),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    RulesStyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (eqGroup != null) {
                spannable.setSpan(
                    RulesColorSpan(equalSignColor),
                    eqGroup.range.first,
                    eqGroup.range.last + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        // 3. Highlight Sections: [settings], [audio], etc.
        for (match in SECTION_REGEX.findAll(textStr)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (isReadMode) {
                spannable.setSpan(
                    RoundedPillSpan(
                        bgColor = sectionBg,
                        textColor = sectionText,
                        borderColor = sectionBorder,
                        cornerRadius = 8f * density,
                        paddingH = 10f * density,
                        paddingV = 3f * density,
                        isBold = true,
                        strokeWidth = 1f * density
                    ),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                // In Edit Mode, use fast RulesColorSpan + bold to avoid background rect redraw during typing
                spannable.setSpan(
                    RulesColorSpan(sectionText),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    RulesStyleSpan(Typeface.BOLD),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        // 4. Highlight Paths: /sdcard/Download, /sdcard/Music, etc.
        for (match in PATH_REGEX.findAll(textStr)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (isReadMode) {
                spannable.setSpan(
                    RoundedPillSpan(
                        bgColor = pathBg,
                        textColor = pathText,
                        borderColor = pathBorder,
                        cornerRadius = 6f * density,
                        paddingH = 8f * density,
                        paddingV = 2.5f * density,
                        isBold = false,
                        strokeWidth = 1f * density
                    ),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                spannable.setSpan(
                    RulesColorSpan(pathText),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        // 5. Highlight Individual Extension Chips: pdf, doc, docx, etc. after ext =
        for (lineMatch in EXT_LINE_REGEX.findAll(textStr)) {
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
                            cornerRadius = 5f * density,
                            paddingH = 6f * density,
                            paddingV = 2f * density,
                            isBold = true,
                            strokeWidth = 1f * density
                        ),
                        tokenStart,
                        tokenEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                } else {
                    spannable.setSpan(
                        RulesColorSpan(extText),
                        tokenStart,
                        tokenEnd,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.setSpan(
                        RulesStyleSpan(Typeface.BOLD),
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
