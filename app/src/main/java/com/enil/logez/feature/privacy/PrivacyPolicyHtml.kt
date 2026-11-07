package com.enil.logez.feature.privacy

/**
 * Renders [PrivacyPolicyDocument] as the standalone page hosted for Google Play and Health
 * Connect (`site/privacy/index.html`). Plain HTML with inline CSS: no scripts, no external fonts
 * or trackers, so the page itself collects nothing either.
 *
 * Output is deterministic, so PrivacyPolicyHtmlTest can compare it byte for byte with the
 * committed file.
 */
object PrivacyPolicyHtml {
    fun render(document: PrivacyPolicyDocument): String = buildString {
        appendLine("<!DOCTYPE html>")
        appendLine("<html lang=\"en\">")
        appendLine("<head>")
        appendLine("<meta charset=\"utf-8\">")
        appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        appendLine("<title>${escape(document.title)}</title>")
        appendLine("<style>")
        appendLine(CSS)
        appendLine("</style>")
        appendLine("</head>")
        appendLine("<body>")
        appendLine("<main>")
        appendLine("<h1>${escape(document.title)}</h1>")
        appendLine("<p class=\"effective\">Effective ${escape(document.effectiveDate)}</p>")
        document.sections.forEach { section ->
            appendLine("<h2>${escape(section.heading)}</h2>")
            section.blocks.forEach { block ->
                when (block) {
                    is PolicyBlock.Paragraph -> appendLine("<p>${linkify(block.text)}</p>")
                    is PolicyBlock.Bullets -> {
                        appendLine("<ul>")
                        block.items.forEach { appendLine("<li>${linkify(it)}</li>") }
                        appendLine("</ul>")
                    }
                }
            }
        }
        appendLine("</main>")
        appendLine("</body>")
        appendLine("</html>")
    }

    /** Escapes, then turns https URLs and email addresses into links. */
    internal fun linkify(text: String): String {
        val out = StringBuilder()
        var last = 0
        LINK.findAll(text).forEach { match ->
            out.append(escape(text.substring(last, match.range.first)))
            val value = match.value
            val href = if (value.startsWith("https://")) value else "mailto:$value"
            out.append("<a href=\"${escape(href)}\">${escape(value)}</a>")
            last = match.range.last + 1
        }
        out.append(escape(text.substring(last)))
        return out.toString()
    }

    internal fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /** A URL stops at whitespace; an email is the usual local@domain shape. Shared with the in-app screen. */
    internal val LINK = Regex("""https://[^\s]+[^\s.,;:)]|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")

    private val CSS = """
        :root { color-scheme: light dark; --bg: #ffffff; --fg: #15191c; --muted: #5b6770; --accent: #3d6b00; }
        @media (prefers-color-scheme: dark) { :root { --bg: #0a0d0f; --fg: #eef2ef; --muted: #9aa7a0; --accent: #b6ff3c; } }
        body { margin: 0; background: var(--bg); color: var(--fg); font: 16px/1.6 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
        main { max-width: 720px; margin: 0 auto; padding: 32px 16px 64px; }
        h1 { font-size: 1.8em; line-height: 1.2; margin: 0 0 4px; }
        h2 { font-size: 1.15em; margin: 32px 0 8px; }
        .effective { color: var(--muted); margin: 0 0 24px; }
        a { color: var(--accent); overflow-wrap: anywhere; }
        ul { padding-left: 1.25em; }
        li { margin: 4px 0; }
    """.trimIndent()
}
