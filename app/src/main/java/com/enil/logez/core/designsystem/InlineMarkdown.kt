package com.enil.logez.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * The inline Markdown subset exercise instructions are stored in: `**bold**` (or `__bold__`),
 * `*italic*` (or `_italic_`), `***both***`, and backslash escapes such as `\*`.
 *
 * Replaced the compose-rich-editor library on 2026-09-25 (Play-readiness audit). Every release of
 * that library, including the stable ones, pulled the whole app onto an alpha Material3 and a
 * Kotlin 2.4 standard library, ahead of this project's pinned Kotlin 2.3.0, for what is only bold
 * and italic in a How-to step. The storage format is unchanged, so instructions written with the
 * old editor render as before (the old editor's `<br>` filler lines are dropped by the callers).
 *
 * A marker only opens a style when a matching closer appears later. Marker characters are read in
 * runs: a single `*` never pairs with half of a `**`, so "3 * 10 reps **slow**" keeps its lone
 * asterisk literal instead of italicising the rest of the step.
 */
object InlineMarkdown {
    private enum class Marker(val token: String, val bold: Boolean) {
        BOLD_STARS("**", true), BOLD_UNDERSCORES("__", true), ITALIC_STAR("*", false), ITALIC_UNDERSCORE("_", false)
    }

    /** Bold and italic tokens the editor's toolbar writes. Italic uses `_` so it never merges with a `**`. */
    const val BOLD_TOKEN = "**"
    const val ITALIC_TOKEN = "_"

    /**
     * One scanned piece of the source: literal text, or a marker (hidden when rendered). A piece
     * carries the style in force *before* it; for a marker, [after] is the style it switches to.
     */
    private data class Piece(
        val start: Int,
        val end: Int,
        val text: String,
        val bold: Boolean,
        val italic: Boolean,
        val isMarker: Boolean,
        val after: Pair<Boolean, Boolean> = bold to italic,
    )

    private fun scan(source: String): List<Piece> {
        val pieces = mutableListOf<Piece>()
        var bold = false
        var italic = false
        var openBold: Marker? = null
        var openItalic: Marker? = null
        val literal = StringBuilder()
        var literalStart = 0
        var i = 0

        fun flush(at: Int) {
            if (literal.isNotEmpty()) pieces += Piece(literalStart, at, literal.toString(), bold, italic, isMarker = false)
            literal.clear()
            literalStart = at
        }

        while (i < source.length) {
            val c = source[i]
            if (c == '\\' && i + 1 < source.length && source[i + 1] in ESCAPABLE) {
                // "\*" is a literal asterisk: the backslash is treated like a marker (hidden when
                // rendered, kept in the stored text) and the next character is plain text.
                flush(i)
                pieces += Piece(i, i + 1, "\\", bold, italic, isMarker = true)
                literalStart = i + 1
                literal.append(source[i + 1])
                i += 2
                continue
            }
            val marker = Marker.entries.firstOrNull { source.startsWith(it.token, i) }
            if (marker != null) {
                val closes = if (marker.bold) openBold == marker else openItalic == marker
                val opens = !closes && (if (marker.bold) openBold == null else openItalic == null) &&
                    hasCloser(source, marker, i + marker.token.length)
                if (closes || opens) {
                    flush(i)
                    val before = bold to italic
                    if (marker.bold) {
                        bold = opens
                        openBold = if (opens) marker else null
                    } else {
                        italic = opens
                        openItalic = if (opens) marker else null
                    }
                    pieces += Piece(i, i + marker.token.length, marker.token, before.first, before.second, isMarker = true, after = bold to italic)
                    i += marker.token.length
                    literalStart = i
                    continue
                }
            }
            if (literal.isEmpty()) literalStart = i
            literal.append(c)
            i++
        }
        flush(source.length)
        return pieces
    }

    /**
     * Whether a closer for [marker] appears at or after [from], reading marker characters in runs
     * and skipping escaped ones. A run of one or three can close an italic, a run of two or three a
     * bold (three being `***`, both at once). The closer must not sit immediately at [from]: an
     * empty "****" is literal.
     */
    private fun hasCloser(source: String, marker: Marker, from: Int): Boolean {
        val c = marker.token[0]
        var j = from
        while (j < source.length) {
            if (source[j] == '\\') { j += 2; continue }
            if (source[j] != c) { j++; continue }
            var run = 0
            while (j + run < source.length && source[j + run] == c) run++
            val fits = if (marker.bold) run == 2 || run == 3 else run == 1 || run == 3
            if (fits && j > from) return true
            j += run
        }
        return false
    }

    private fun style(bold: Boolean, italic: Boolean) = SpanStyle(
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
    )

    /** For display (the How-to tab): markers removed, styles applied. */
    fun toAnnotatedString(source: String): AnnotatedString = buildAnnotatedString {
        scan(source).filterNot { it.isMarker }.forEach { piece ->
            if (piece.bold || piece.italic) {
                pushStyle(style(piece.bold, piece.italic))
                append(piece.text)
                pop()
            } else {
                append(piece.text)
            }
        }
    }

    /**
     * Whether text typed at [offset] would be bold / italic, for the editor's toggle buttons: the
     * style of the character before the caret, or, when that character belongs to a marker, the
     * style the marker switches to (so the caret just past a closing `**` reads as not bold).
     */
    fun stylesAt(source: String, offset: Int): Pair<Boolean, Boolean> {
        if (offset <= 0) return false to false
        val index = (offset - 1).coerceAtMost(source.length - 1)
        val piece = scan(source).firstOrNull { index >= it.start && index < it.end } ?: return false to false
        return if (piece.isMarker) piece.after else piece.bold to piece.italic
    }

    /**
     * For the editor: the raw text, markers left in place (so the cursor maps one to one and the
     * stored text is exactly what is typed), styled content, and markers dimmed to [markerColor].
     */
    fun editorTransformation(markerColor: Color): VisualTransformation = VisualTransformation { text ->
        val raw = text.text
        val styled = buildAnnotatedString {
            append(raw)
            scan(raw).forEach { piece ->
                if (piece.isMarker) {
                    addStyle(SpanStyle(color = markerColor), piece.start, piece.end)
                } else if (piece.bold || piece.italic) {
                    addStyle(style(piece.bold, piece.italic), piece.start, piece.end)
                }
            }
        }
        TransformedText(styled, OffsetMapping.Identity)
    }

    /** The Bold button. Unwraps `**x**` or `__x__`; wraps with `**`. */
    fun toggleBold(value: TextFieldValue): TextFieldValue = toggle(value, BOLD_TOKEN, listOf("**", "__"))

    /** The Italic button. Unwraps `_x_` or `*x*` (the old editor's form); wraps with `_`. */
    fun toggleItalic(value: TextFieldValue): TextFieldValue = toggle(value, ITALIC_TOKEN, listOf("_", "*"))

    /**
     * Wraps the selection in [token], or unwraps it when it is already wrapped in one of
     * [unwrapTokens]; with no selection, inserts an empty pair and puts the cursor between. A wrap
     * only counts when the characters just outside it are not more of the same marker, so the
     * single `*` of a `**` is never mistaken for an italic marker.
     */
    fun toggle(value: TextFieldValue, token: String, unwrapTokens: List<String> = listOf(token)): TextFieldValue {
        val text = value.text
        val start = value.selection.min
        val end = value.selection.max
        val wrapping = unwrapTokens.firstOrNull { isCleanWrap(text, start, end, it) }
        return when {
            wrapping != null -> TextFieldValue(
                text = text.removeRange(end, end + wrapping.length).removeRange(start - wrapping.length, start),
                selection = TextRange(start - wrapping.length, end - wrapping.length),
            )
            start == end -> TextFieldValue(
                text = text.substring(0, start) + token + token + text.substring(start),
                selection = TextRange(start + token.length),
            )
            else -> TextFieldValue(
                text = text.substring(0, start) + token + text.substring(start, end) + token + text.substring(end),
                selection = TextRange(start + token.length, end + token.length),
            )
        }
    }

    private fun isCleanWrap(text: String, start: Int, end: Int, token: String): Boolean {
        if (start == end || start < token.length || end + token.length > text.length) return false
        if (!text.startsWith(token, start - token.length) || !text.startsWith(token, end)) return false
        val c = token[0]
        val beforeOk = start - token.length - 1 < 0 || text[start - token.length - 1] != c
        val afterOk = end + token.length >= text.length || text[end + token.length] != c
        return beforeOk && afterOk
    }

    /** The old rich-text editor wrote `<br>` for extra blank paragraphs; it is filler, not a step. */
    fun isFillerLine(line: String): Boolean = line.trim().equals("<br>", ignoreCase = true)

    private val ESCAPABLE = setOf('*', '_', '\\')
}
