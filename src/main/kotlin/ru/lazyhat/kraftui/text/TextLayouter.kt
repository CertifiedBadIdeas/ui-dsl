package ru.lazyhat.kraftui.text

import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy
import ru.lazyhat.kraftui.foundation.modifier.TextWrapPolicy

data class TextFlow(
    val wrap: TextWrapPolicy = TextWrapPolicy.NoWrap,
    val maxLines: Int? = null,
    val lineHeight: Int = 9,
)

data class TextLayoutLine(
    val text: String,
    val width: Int,
)

data class TextLayout(
    val lines: List<TextLayoutLine>,
    val lineHeight: Int,
    val sourceLineCount: Int,
    val truncatedByLineLimit: Boolean,
) {
    val requiredHeight: Int = sourceLineCount * lineHeight
}

class TextLayouter(
    private val measureText: (String) -> Int,
) {
    fun layout(
        text: String,
        width: Int,
        flow: TextFlow,
        overflow: TextOverflowPolicy,
    ): TextLayout {
        val sourceLines = buildSourceLines(text, width, flow.wrap)
        val lineLimit = flow.maxLines
        val visibleLines =
            if (lineLimit == null) {
                sourceLines
            } else {
                sourceLines.take(lineLimit)
            }
        val truncated = lineLimit != null && sourceLines.size > lineLimit
        val finalLines =
            if (overflow == TextOverflowPolicy.Ellipsize) {
                ellipsizeVisibleLines(visibleLines, width, truncated)
            } else {
                visibleLines
            }
        return TextLayout(
            lines = finalLines.map { TextLayoutLine(it, measureText(it)) },
            lineHeight = flow.lineHeight,
            sourceLineCount = sourceLines.size,
            truncatedByLineLimit = truncated,
        )
    }

    private fun buildSourceLines(
        text: String,
        width: Int,
        wrap: TextWrapPolicy,
    ): List<String> =
        text
            .split('\n')
            .flatMap { paragraph ->
                when (wrap) {
                    TextWrapPolicy.NoWrap -> listOf(paragraph)
                    TextWrapPolicy.WordWrap -> wrapParagraph(paragraph, width)
                }
            }
            .ifEmpty { listOf("") }

    private fun wrapParagraph(
        paragraph: String,
        width: Int,
    ): List<String> {
        if (paragraph.isEmpty()) return listOf("")
        if (width <= 0) return listOf(paragraph)

        val words = paragraph.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return listOf("")

        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && measureText(candidate) > width) {
                lines += current
                current = word
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) {
            lines += current
        }
        return lines
    }

    private fun ellipsizeVisibleLines(
        lines: List<String>,
        width: Int,
        truncatedByLineLimit: Boolean,
    ): List<String> {
        if (lines.isEmpty()) return lines
        return lines.mapIndexed { index, line ->
            val isLastVisibleLine = index == lines.lastIndex
            if (measureText(line) > width || (isLastVisibleLine && truncatedByLineLimit)) {
                line.ellipsize(width, forceMarker = isLastVisibleLine && truncatedByLineLimit)
            } else {
                line
            }
        }
    }

    private fun String.ellipsize(
        width: Int,
        forceMarker: Boolean,
    ): String {
        if (!forceMarker && measureText(this) <= width) return this
        if (width <= 0) return ""

        var marker = "..."
        while (marker.isNotEmpty() && measureText(marker) > width) {
            marker = marker.dropLast(1)
        }
        if (marker.isEmpty()) return ""

        var end = length
        while (end > 0 && measureText(take(end) + marker) > width) {
            end--
        }
        return take(end) + marker
    }
}
