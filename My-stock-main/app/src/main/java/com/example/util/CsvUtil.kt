package com.example.util

/** Minimal RFC-4180 style CSV reader/writer (quotes, commas and line breaks inside fields). */
object CsvUtil {

    fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    fun row(vararg values: Any?): String =
        values.joinToString(",") { escape(it?.toString() ?: "") } + "\n"

    fun parse(input: String): List<List<String>> {
        val text = input.removePrefix("\uFEFF")
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        fun endField() { row.add(field.toString()); field.setLength(0) }
        fun endRow() {
            endField()
            if (!(row.size == 1 && row[0].isBlank())) rows.add(row)
            row = mutableListOf()
        }

        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ }
                    else inQuotes = false
                } else field.append(c)
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> endField()
                    '\r' -> { if (i + 1 < text.length && text[i + 1] == '\n') i++; endRow() }
                    '\n' -> endRow()
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    /** Maps lower-cased header names (spaces/underscores removed) to column index. */
    fun headerIndex(header: List<String>): Map<String, Int> =
        header.mapIndexed { idx, h -> h.trim().lowercase().replace(" ", "").replace("_", "") to idx }.toMap()
}
