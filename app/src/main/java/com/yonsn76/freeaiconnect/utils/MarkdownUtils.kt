package com.yonsn76.freeaiconnect.utils

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import com.yonsn76.freeaiconnect.models.MarkdownBlock

object MarkdownUtils {

    private val CODE_BLOCK_PATTERN = Regex("```([a-zA-Z]*)\\r?\\n([\\s\\S]*?)```", RegexOption.MULTILINE)
    private val INLINE_CODE_PATTERN = Regex("`([^`]+)`")
    private val BOLD_PATTERN = Regex("\\*\\*(.+?)\\*\\*")
    private val ITALIC_PATTERN = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)")
    private const val INLINE_CODE_BG = 0xFFFBF3DB.toInt()
    private const val INLINE_CODE_TEXT = 0xFF383A42.toInt()

    fun parseBlocks(text: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val matches = CODE_BLOCK_PATTERN.findAll(text).toList()
        var lastEnd = 0

        for (match in matches) {
            val start = match.range.first
            if (start > lastEnd) {
                val textContent = text.substring(lastEnd, start).trimBlankLines()
                if (textContent.isNotEmpty()) {
                    blocks.add(MarkdownBlock.Text(textContent))
                }
            }
            val lang = match.groupValues[1].takeIf { it.isNotEmpty() } ?: "code"
            val code = match.groupValues[2].trimEnd()
            blocks.add(MarkdownBlock.Code(lang, code))
            lastEnd = match.range.last + 1
        }

        if (lastEnd < text.length) {
            val remaining = text.substring(lastEnd).trimBlankLines()
            if (remaining.isNotEmpty()) {
                blocks.add(MarkdownBlock.Text(remaining))
            }
        }

        return blocks
    }

    private fun String.trimBlankLines(): String {
        return this.trim().replace(Regex("\\n{3,}"), "\n\n")
    }

    fun cleanupText(text: String): String {
        return text
            .replace(Regex("(?m)^\\s*[-*_]{3,}\\s*$"), "")
            .replace(Regex("(?m)^\\s*#{1,6}\\s*$"), "")
            .replace(Regex("\\s*---+\\s*"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    fun toSpannable(text: String, context: Context): SpannableStringBuilder {
        var processed = text

        // Step 1: Protect inline code
        val inlineCodes = mutableListOf<String>()
        processed = INLINE_CODE_PATTERN.replace(processed) { m ->
            inlineCodes.add(m.groupValues[1])
            "\u0000INLINE_${inlineCodes.size - 1}\u0000"
        }

        // Step 2: Replace inline markdown markers with clean text.
        // Block structure (headings, paragraphs, lists) is handled by ChatAdapter.
        processed = cleanupText(processed)
        processed = BOLD_PATTERN.replace(processed) { it.groupValues[1] }
        processed = ITALIC_PATTERN.replace(processed) { it.groupValues[1] }

        // Step 3: Build spannable from clean text
        val ssb = SpannableStringBuilder(processed)

        // Step 4: Apply spans by finding clean text in ssb
        applySpansFromOriginal(ssb, text, BOLD_PATTERN) { start, end ->
            ssb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        applySpansFromOriginal(ssb, text, ITALIC_PATTERN) { start, end ->
            ssb.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // Step 5: Restore inline code with spans
        for (i in inlineCodes.indices) {
            val placeholder = "\u0000INLINE_${i}\u0000"
            val idx = ssb.indexOf(placeholder)
            if (idx >= 0) {
                ssb.replace(idx, idx + placeholder.length, inlineCodes[i])
                val end = idx + inlineCodes[i].length
                ssb.setSpan(TypefaceSpan("monospace"), idx, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ssb.setSpan(BackgroundColorSpan(INLINE_CODE_BG), idx, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                ssb.setSpan(ForegroundColorSpan(INLINE_CODE_TEXT), idx, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        return ssb
    }

    private fun applySpansFromOriginal(
        ssb: SpannableStringBuilder,
        original: String,
        pattern: Regex,
        applySpan: (Int, Int) -> Unit
    ) {
        val matches = pattern.findAll(original).toList()
        for (match in matches) {
            val inner = match.groupValues[1]
            val idx = ssb.indexOf(inner)
            if (idx >= 0) {
                applySpan(idx, idx + inner.length)
            }
        }
    }

    fun highlightCode(code: String, language: String, context: Context): SpannableStringBuilder {
        val ssb = SpannableStringBuilder(code)

        var offset = 0
        val lines = code.split("\n")
        for (line in lines) {
            val lineStart = offset

            // Comments
            """(//|#|--)\s*[^\n]*""".toRegex().findAll(line).forEach { match ->
                val start = lineStart + match.range.first
                val end = lineStart + match.range.last + 1
                if (ssb.getSpans(start, end, Any::class.java).isEmpty()) {
                    ssb.setSpan(
                        ForegroundColorSpan(0xFFB0A8A0.toInt()),
                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            // Strings
            """["'][^"']*["']""".toRegex().findAll(line).forEach { match ->
                val start = lineStart + match.range.first
                val end = lineStart + match.range.last + 1
                if (ssb.getSpans(start, end, Any::class.java).isEmpty()) {
                    ssb.setSpan(
                        ForegroundColorSpan(0xFF346538.toInt()),
                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            // Keywords
            val keywords = listOf(
                "def", "class", "import", "from", "return", "if", "else", "elif",
                "for", "while", "try", "except", "finally", "with", "as", "in",
                "not", "and", "or", "is", "lambda", "yield", "raise", "pass",
                "break", "continue", "async", "await", "const", "let", "var",
                "function", "new", "this", "super", "extends", "implements",
                "interface", "package", "private", "protected", "public", "static",
                "void", "int", "float", "double", "boolean", "string", "true",
                "false", "null", "nil", "None", "self", "fn", "pub", "struct",
                "enum", "match", "use", "mod", "mut", "ref", "type", "where",
                "func", "go", "defer", "chan", "select", "range", "print",
                "println", "console", "log", "echo"
            )
            """\\b(${keywords.joinToString("|")})\\b""".toRegex().findAll(line).forEach { match ->
                val start = lineStart + match.range.first
                val end = lineStart + match.range.last + 1
                if (ssb.getSpans(start, end, Any::class.java).isEmpty()) {
                    ssb.setSpan(
                        ForegroundColorSpan(0xFF9F2F2D.toInt()),
                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            // Numbers
            """\\b(\\d+\\.?\\d*)\\b""".toRegex().findAll(line).forEach { match ->
                val start = lineStart + match.range.first
                val end = lineStart + match.range.last + 1
                if (ssb.getSpans(start, end, Any::class.java).isEmpty()) {
                    ssb.setSpan(
                        ForegroundColorSpan(0xFF956400.toInt()),
                        start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            offset = lineStart + line.length + 1
        }

        return ssb
    }

    private fun SpannableStringBuilder.indexOf(str: String): Int {
        return this.toString().indexOf(str)
    }
}
