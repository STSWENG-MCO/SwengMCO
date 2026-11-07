package com.enil.logez.core.designsystem

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineMarkdownTest {
    private fun AnnotatedString.boldRanges() = spanStyles.filter { it.item.fontWeight == FontWeight.Bold }.map { text.substring(it.start, it.end) }
    private fun AnnotatedString.italicRanges() = spanStyles.filter { it.item.fontStyle == FontStyle.Italic }.map { text.substring(it.start, it.end) }

    @Test
    fun `bold and italic markers render as styles and disappear from the text`() {
        val out = InlineMarkdown.toAnnotatedString("Keep your **back flat** and *breathe out*.")
        assertEquals("Keep your back flat and breathe out.", out.text)
        assertEquals(listOf("back flat"), out.boldRanges())
        assertEquals(listOf("breathe out"), out.italicRanges())
    }

    @Test
    fun `underscore forms and triple markers work like the old editor's output`() {
        val out = InlineMarkdown.toAnnotatedString("__slow__ then _pause_ then ***drive***")
        assertEquals("slow then pause then drive", out.text)
        assertTrue(out.boldRanges().containsAll(listOf("slow", "drive")))
        assertTrue(out.italicRanges().containsAll(listOf("pause", "drive")))
    }

    /** "3 * 10 reps" must not italicise the rest of the step. */
    @Test
    fun `an unmatched marker stays literal`() {
        val out = InlineMarkdown.toAnnotatedString("Do 3 * 10 reps")
        assertEquals("Do 3 * 10 reps", out.text)
        assertTrue(out.spanStyles.isEmpty())
    }

    @Test
    fun `a backslash escape shows a literal asterisk`() {
        assertEquals("Grip *wide*", InlineMarkdown.toAnnotatedString("Grip \\*wide\\*").text)
    }

    /** Replaces RichTextStateRoundTripTest: storage is now exactly what is typed, newlines included. */
    @Test
    fun `multi-line text without markers passes through byte for byte`() {
        val raw = "Step 1: grip the bar.\nStep 2: brace and lift."
        assertEquals(raw, InlineMarkdown.toAnnotatedString(raw).text)
        assertEquals("", InlineMarkdown.toAnnotatedString("").text)
    }

    @Test
    fun `plain text passes through untouched`() {
        assertEquals("Stand tall.", InlineMarkdown.toAnnotatedString("Stand tall.").text)
    }

    @Test
    fun `toggle wraps a selection, and toggling again unwraps it`() {
        val wrapped = InlineMarkdown.toggle(TextFieldValue("lift slowly", TextRange(5, 11)), "**")
        assertEquals("lift **slowly**", wrapped.text)
        assertEquals(TextRange(7, 13), wrapped.selection)
        val unwrapped = InlineMarkdown.toggle(wrapped, "**")
        assertEquals("lift slowly", unwrapped.text)
        assertEquals(TextRange(5, 11), unwrapped.selection)
    }

    @Test
    fun `toggle with no selection inserts a pair and puts the cursor inside`() {
        val out = InlineMarkdown.toggle(TextFieldValue("go ", TextRange(3)), "*")
        assertEquals("go **", out.text)
        assertEquals(TextRange(4), out.selection)
    }

    @Test
    fun `stylesAt reports the style just before the cursor`() {
        val text = "a **bold** b"
        assertTrue(InlineMarkdown.stylesAt(text, 7).first)
        assertFalse(InlineMarkdown.stylesAt(text, 12).first)
    }

    @Test
    fun `the editor transformation keeps the raw text so the cursor maps one to one`() {
        val raw = "a **b** c"
        val transformed = InlineMarkdown.editorTransformation(androidx.compose.ui.graphics.Color.Gray)
            .filter(AnnotatedString(raw))
        assertEquals(raw, transformed.text.text)
        assertEquals(4, transformed.offsetMapping.originalToTransformed(4))
    }

    // --- 2026-09-25 review fixes ---

    @Test
    fun `a lone asterisk stays literal even when the step also has bold`() {
        val out = InlineMarkdown.toAnnotatedString("Do 3 * 10 reps **slow**")
        assertEquals("Do 3 * 10 reps slow", out.text)
        assertEquals(listOf("slow"), out.boldRanges())
        assertTrue(out.italicRanges().isEmpty())
    }

    @Test
    fun `italic on a bold word adds italic instead of stripping the bold`() {
        val bold = TextFieldValue("**bold**", TextRange(2, 6))
        val both = InlineMarkdown.toggleItalic(bold)
        assertEquals("**_bold_**", both.text)
        val rendered = InlineMarkdown.toAnnotatedString(both.text)
        assertEquals("bold", rendered.text)
        assertEquals(listOf("bold"), rendered.boldRanges())
        assertEquals(listOf("bold"), rendered.italicRanges())
    }

    @Test
    fun `italic toggles off text the old editor wrote with asterisks`() {
        val out = InlineMarkdown.toggleItalic(TextFieldValue("*soft*", TextRange(1, 5)))
        assertEquals("soft", out.text)
    }

    @Test
    fun `the caret just past a closing marker reads as outside the style`() {
        val text = "a **bold** b"
        assertTrue(InlineMarkdown.stylesAt(text, 8).first)
        assertFalse("just after the closing **", InlineMarkdown.stylesAt(text, 10).first)
    }

    @Test
    fun `the old editor's br filler lines are recognised`() {
        assertTrue(InlineMarkdown.isFillerLine("<br>"))
        assertTrue(InlineMarkdown.isFillerLine("  <BR> "))
        assertFalse(InlineMarkdown.isFillerLine("Brace <br> hard"))
    }
}
