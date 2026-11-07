package com.enil.logez.core.data.export

/**
 * RFC 4180 CSV. Every field is quoted unconditionally, matching the quoted header literal the
 * export schema pins, and because the alternative — quoting only when needed — is where escaping
 * bugs hide.
 *
 * Workout and exercise notes are free text and will contain commas and newlines. RFC 4180 allows a
 * literal newline inside a quoted field, so one is kept rather than mangled; a parser that splits
 * on newlines before honouring quotes will read those rows wrong, which is the parser's bug and
 * not something worth corrupting the data to avoid.
 */
object CsvWriter {
    /** RFC 4180 specifies CRLF, and Excel is stricter about it than most. */
    const val LINE_SEPARATOR = "\r\n"

    fun row(fields: List<String?>): String =
        fields.joinToString(",") { field -> quote(field.orEmpty()) } + LINE_SEPARATOR

    private fun quote(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
