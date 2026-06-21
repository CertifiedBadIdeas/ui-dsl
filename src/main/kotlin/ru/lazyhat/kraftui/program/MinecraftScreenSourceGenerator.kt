package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.Position
import ru.lazyhat.kraftui.foundation.modifier.TextAlignment
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy

fun PrimitiveScreenProgram.generateMinecraftScreenSource(
    packageName: String,
    className: String,
    stateType: String,
    actionType: String,
): PrimitiveScreenSource {
    require(packageName.isValidQualifiedIdentifier()) {
        "packageName must be a valid qualified Kotlin identifier"
    }
    require(className.isValidIdentifier()) {
        "className must be a valid Kotlin identifier"
    }
    require(stateType.isValidTypeName()) {
        "stateType must be a valid Kotlin type name"
    }
    require(actionType.isValidTypeName()) {
        "actionType must be a valid Kotlin type name"
    }
    rejectUnsupportedMinecraftOps()

    val source =
        buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import net.minecraft.client.Minecraft")
            appendLine("import net.minecraft.client.gui.Font")
            appendLine("import net.minecraft.client.gui.GuiGraphics")
            appendLine()
            appendLine("class $className {")
            appendLine("    private val clipStack = ArrayDeque<ClipRect>()")
            appendLine("    private val textLayoutCache =")
            appendLine("        object : LinkedHashMap<TextLayoutKey, List<TextLine>>(256, 0.75f, true) {")
            appendLine("            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TextLayoutKey, List<TextLine>>): Boolean =")
            appendLine("                size > 256")
            appendLine("        }")
            appendLine()
            appendLine("    fun render(graphics: GuiGraphics, state: $stateType) {")
            appendLine("        clipStack.clear()")
            renderInstructions.forEachIndexed { index, instruction ->
                appendMinecraftRenderInstruction(index, instruction)
            }
            appendLine("        if (clipStack.isNotEmpty()) {")
            appendLine("            graphics.disableScissor()")
            appendLine("            clipStack.clear()")
            appendLine("        }")
            appendLine("    }")
            appendLine()
            appendLine("    fun clearDynamicCaches() {")
            appendLine("        textLayoutCache.clear()")
            appendLine("    }")
            appendLine()
            appendLine("    fun mouseClicked(state: $stateType, x: Int, y: Int): $actionType? {")
            inputInstructions.forEachIndexed { index, instruction ->
                appendMinecraftInputInstruction(index, instruction)
            }
            appendLine("        return null")
            appendLine("    }")
            appendMinecraftHelpers()
            appendLine("}")
        }

    return PrimitiveScreenSource(
        packageName = packageName,
        className = className,
        source = source,
    )
}

private fun PrimitiveScreenProgram.rejectUnsupportedMinecraftOps() {
    val unsupported =
        analyze(
            options =
                PrimitiveProgramAnalysisOptions(
                    target = PrimitiveTargetCapabilities.minecraftGuiGraphics,
                ),
        ).diagnostics.filterIsInstance<PrimitiveProgramDiagnostic.UnsupportedTargetOperation>()
    require(unsupported.isEmpty()) {
        "Minecraft target cannot generate unsupported primitive operations:\n" +
            unsupported.joinToString(separator = "\n") {
                "${it.path}: ${it.operation}"
            }
        }
}

private fun StringBuilder.appendMinecraftRenderInstruction(
    index: Int,
    instruction: PrimitiveRenderInstruction,
) {
    val indent =
        if (instruction.visible != null) {
            appendLine("        if (${instruction.visible.kotlinExpression()}) {")
            "            "
        } else {
            "        "
        }
    if (instruction.origin != null) {
        appendLine("${indent}val origin$index = ${instruction.origin.kotlinExpression()}")
        appendLine("${indent}val ox$index = origin$index.x")
        appendLine("${indent}val oy$index = origin$index.y")
    }
    appendMinecraftRenderOp(index, instruction.origin != null, instruction.op, indent)
    if (instruction.visible != null) {
        appendLine("        }")
    }
}

private fun StringBuilder.appendMinecraftRenderOp(
    index: Int,
    hasOrigin: Boolean,
    op: PrimitiveRenderOp,
    indent: String,
) {
    val ox = if (hasOrigin) " + ox$index" else ""
    val oy = if (hasOrigin) " + oy$index" else ""
    when (op) {
        is PrimitiveRenderOp.FillRect -> {
            appendLine(
                "${indent}graphics.fill(${op.x}$ox, ${op.y}$oy, ${op.x + op.width}$ox, ${op.y + op.height}$oy, ${op.color.minecraftColorExpression()})",
            )
        }
        is PrimitiveRenderOp.DrawText -> appendMinecraftDrawText(index, op, ox, oy, indent)
        is PrimitiveRenderOp.PushClip -> {
            appendLine("${indent}pushClip(graphics, ${op.x}$ox, ${op.y}$oy, ${op.width}, ${op.height})")
        }
        PrimitiveRenderOp.PopClip -> {
            appendLine("${indent}popClip(graphics)")
        }
        is PrimitiveRenderOp.DrawTerminalSurface,
        is PrimitiveRenderOp.DrawCodeEditor,
        -> error("unsupported Minecraft operation should have been rejected before generation")
    }
}

private fun StringBuilder.appendMinecraftDrawText(
    index: Int,
    op: PrimitiveRenderOp.DrawText,
    ox: String,
    oy: String,
    indent: String,
) {
    val flow = op.flow
    appendLine("${indent}val font$index = Minecraft.getInstance().font")
    appendLine(
        "${indent}drawText(graphics, font$index, ${op.text.kotlinExpression()}, ${op.x}$ox, ${op.y}$oy, ${op.width}, ${op.height}, ${op.color.minecraftColorExpression()}, ${op.alignment.kotlinExpression()}, ${op.overflow.kotlinExpression()}, ${flow.wrap.minecraftBooleanExpression()}, ${flow.maxLines.kotlinNullableInt()}, ${flow.lineHeight})",
    )
}

private fun StringBuilder.appendMinecraftInputInstruction(
    index: Int,
    instruction: PrimitiveInputInstruction,
) {
    when (instruction) {
        is PrimitiveInputInstruction.ClickRegion -> {
            instruction.visible?.let {
                appendLine("        if (${it.kotlinExpression()}) {")
            }
            val indent = if (instruction.visible != null) "            " else "        "
            if (instruction.origin != null) {
                appendLine("${indent}val origin$index = ${instruction.origin.kotlinExpression()}")
                appendLine("${indent}val ox$index = origin$index.x")
                appendLine("${indent}val oy$index = origin$index.y")
            }
            val ox = if (instruction.origin != null) " + ox$index" else ""
            val oy = if (instruction.origin != null) " + oy$index" else ""
            appendLine("${indent}if (x >= ${instruction.x}$ox && y >= ${instruction.y}$oy && x < ${instruction.x}$ox + ${instruction.width} && y < ${instruction.y}$oy + ${instruction.height}) {")
            if (instruction.action != null) {
                appendLine("${indent}    return ${instruction.action.kotlinExpression()}")
            } else {
                appendLine("${indent}    return null")
            }
            appendLine("${indent}}")
            if (instruction.visible != null) {
                appendLine("        }")
            }
        }
    }
}

private fun StringBuilder.appendMinecraftHelpers() {
    appendLine()
    appendLine("    private fun drawText(")
    appendLine("        graphics: GuiGraphics,")
    appendLine("        font: Font,")
    appendLine("        text: String,")
    appendLine("        x: Int,")
    appendLine("        y: Int,")
    appendLine("        width: Int,")
    appendLine("        height: Int,")
    appendLine("        color: Int,")
    appendLine("        alignment: TextAlignment,")
    appendLine("        overflow: TextOverflowPolicy,")
    appendLine("        wrap: Boolean,")
    appendLine("        maxLines: Int?,")
    appendLine("        lineHeight: Int,")
    appendLine("    ) {")
    appendLine("        val visibleLineCount = (height / lineHeight).coerceAtLeast(0)")
    appendLine("        val effectiveMaxLines =")
    appendLine("            when {")
    appendLine("                visibleLineCount == 0 -> 0")
    appendLine("                maxLines == null -> visibleLineCount")
    appendLine("                else -> minOf(maxLines, visibleLineCount)")
    appendLine("            }")
    appendLine("        if (effectiveMaxLines <= 0) return")
    appendLine("        val key = TextLayoutKey(font, text, width, height, wrap, maxLines, lineHeight, overflow)")
    appendLine("        val finalLines =")
    appendLine("            textLayoutCache.getOrPut(key) {")
    appendLine("                buildFinalTextLines(font, text, width, wrap, overflow, effectiveMaxLines)")
    appendLine("            }")
    appendLine("        pushClip(graphics, x, y, width, height)")
    appendLine("        try {")
    appendLine("            finalLines.forEachIndexed { index, line ->")
    appendLine("                val textX =")
    appendLine("                    when (alignment) {")
    appendLine("                        TextAlignment.Start -> x")
    appendLine("                        TextAlignment.Center -> x + (width - line.width) / 2")
    appendLine("                        TextAlignment.End -> x + width - line.width")
    appendLine("                    }")
    appendLine("                graphics.drawString(font, line.text, textX, y + index * lineHeight, color, false)")
    appendLine("            }")
    appendLine("        } finally {")
    appendLine("            popClip(graphics)")
    appendLine("        }")
    appendLine("    }")
    appendLine()
    appendLine("    private fun buildFinalTextLines(")
    appendLine("        font: Font,")
    appendLine("        text: String,")
    appendLine("        width: Int,")
    appendLine("        wrap: Boolean,")
    appendLine("        overflow: TextOverflowPolicy,")
    appendLine("        effectiveMaxLines: Int,")
    appendLine("    ): List<TextLine> {")
    appendLine("        val sourceLines = buildTextLines(font, text, width, wrap)")
    appendLine("        val visibleLines = sourceLines.take(effectiveMaxLines)")
    appendLine("        val truncatedByLineLimit = sourceLines.size > visibleLines.size")
    appendLine("        val finalLines =")
    appendLine("            if (overflow == TextOverflowPolicy.Ellipsize) {")
    appendLine("                ellipsizeVisibleLines(font, visibleLines, width, truncatedByLineLimit)")
    appendLine("            } else {")
    appendLine("                visibleLines")
    appendLine("            }")
    appendLine("        return finalLines.map { TextLine(it, font.width(it)) }")
    appendLine("    }")
    appendLine()
    appendLine("    private fun buildTextLines(")
    appendLine("        font: Font,")
    appendLine("        text: String,")
    appendLine("        width: Int,")
    appendLine("        wrap: Boolean,")
    appendLine("    ): List<String> =")
    appendLine("        text")
    appendLine("            .split('\\n')")
    appendLine("            .flatMap { paragraph ->")
    appendLine("                if (wrap) wrapParagraph(font, paragraph, width) else listOf(paragraph)")
    appendLine("            }")
    appendLine("            .ifEmpty { listOf(\"\") }")
    appendLine()
    appendLine("    private fun wrapParagraph(")
    appendLine("        font: Font,")
    appendLine("        paragraph: String,")
    appendLine("        width: Int,")
    appendLine("    ): List<String> {")
    appendLine("        if (paragraph.isEmpty()) return listOf(\"\")")
    appendLine("        if (width <= 0) return listOf(paragraph)")
    appendLine("        val words = paragraph.split(Regex(\"\\\\s+\")).filter { it.isNotEmpty() }")
    appendLine("        if (words.isEmpty()) return listOf(\"\")")
    appendLine("        val lines = mutableListOf<String>()")
    appendLine("        var current = \"\"")
    appendLine("        for (word in words) {")
    appendLine("            val candidate = if (current.isEmpty()) word else \"\$current \$word\"")
    appendLine("            if (current.isNotEmpty() && font.width(candidate) > width) {")
    appendLine("                lines += current")
    appendLine("                current = word")
    appendLine("            } else {")
    appendLine("                current = candidate")
    appendLine("            }")
    appendLine("        }")
    appendLine("        if (current.isNotEmpty()) lines += current")
    appendLine("        return lines")
    appendLine("    }")
    appendLine()
    appendLine("    private fun ellipsizeVisibleLines(")
    appendLine("        font: Font,")
    appendLine("        lines: List<String>,")
    appendLine("        width: Int,")
    appendLine("        truncatedByLineLimit: Boolean,")
    appendLine("    ): List<String> {")
    appendLine("        if (lines.isEmpty()) return lines")
    appendLine("        return lines.mapIndexed { index, line ->")
    appendLine("            val isLastVisibleLine = index == lines.lastIndex")
    appendLine("            if (font.width(line) > width || (isLastVisibleLine && truncatedByLineLimit)) {")
    appendLine("                ellipsize(font, line, width, forceMarker = isLastVisibleLine && truncatedByLineLimit)")
    appendLine("            } else {")
    appendLine("                line")
    appendLine("            }")
    appendLine("        }")
    appendLine("    }")
    appendLine()
    appendLine("    private fun ellipsize(")
    appendLine("        font: Font,")
    appendLine("        text: String,")
    appendLine("        width: Int,")
    appendLine("        forceMarker: Boolean,")
    appendLine("    ): String {")
    appendLine("        if (!forceMarker && font.width(text) <= width) return text")
    appendLine("        if (width <= 0) return \"\"")
    appendLine("        var marker = \"...\"")
    appendLine("        while (marker.isNotEmpty() && font.width(marker) > width) {")
    appendLine("            marker = marker.dropLast(1)")
    appendLine("        }")
    appendLine("        if (marker.isEmpty()) return \"\"")
    appendLine("        var end = text.length")
    appendLine("        while (end > 0 && font.width(text.take(end) + marker) > width) {")
    appendLine("            end--")
    appendLine("        }")
    appendLine("        return text.take(end) + marker")
    appendLine("    }")
    appendLine()
    appendLine("    private fun pushClip(")
    appendLine("        graphics: GuiGraphics,")
    appendLine("        x: Int,")
    appendLine("        y: Int,")
    appendLine("        width: Int,")
    appendLine("        height: Int,")
    appendLine("    ) {")
    appendLine("        val requested = ClipRect(x, y, x + width, y + height)")
    appendLine("        val clipped = clipStack.lastOrNull()?.intersect(requested) ?: requested")
    appendLine("        clipStack.addLast(clipped)")
    appendLine("        graphics.enableScissor(clipped.left, clipped.top, clipped.right, clipped.bottom)")
    appendLine("    }")
    appendLine()
    appendLine("    private fun popClip(graphics: GuiGraphics) {")
    appendLine("        check(clipStack.isNotEmpty()) { \"Cannot pop an empty clip stack\" }")
    appendLine("        clipStack.removeLast()")
    appendLine("        val current = clipStack.lastOrNull()")
    appendLine("        if (current == null) {")
    appendLine("            graphics.disableScissor()")
    appendLine("        } else {")
    appendLine("            graphics.enableScissor(current.left, current.top, current.right, current.bottom)")
    appendLine("        }")
    appendLine("    }")
    appendLine()
    appendLine("    private data class ClipRect(")
    appendLine("        val left: Int,")
    appendLine("        val top: Int,")
    appendLine("        val right: Int,")
    appendLine("        val bottom: Int,")
    appendLine("    ) {")
    appendLine("        fun intersect(other: ClipRect): ClipRect =")
    appendLine("            ClipRect(")
    appendLine("                left = maxOf(left, other.left),")
    appendLine("                top = maxOf(top, other.top),")
    appendLine("                right = minOf(right, other.right),")
    appendLine("                bottom = minOf(bottom, other.bottom),")
    appendLine("            )")
    appendLine("    }")
    appendLine()
    appendLine("    private data class TextLine(")
    appendLine("        val text: String,")
    appendLine("        val width: Int,")
    appendLine("    )")
    appendLine()
    appendLine("    private data class TextLayoutKey(")
    appendLine("        val font: Font,")
    appendLine("        val text: String,")
    appendLine("        val width: Int,")
    appendLine("        val height: Int,")
    appendLine("        val wrap: Boolean,")
    appendLine("        val maxLines: Int?,")
    appendLine("        val lineHeight: Int,")
    appendLine("        val overflow: TextOverflowPolicy,")
    appendLine("    )")
    appendLine()
    appendLine("    private enum class TextAlignment {")
    appendLine("        Start,")
    appendLine("        Center,")
    appendLine("        End,")
    appendLine("    }")
    appendLine()
    appendLine("    private enum class TextOverflowPolicy {")
    appendLine("        FailInValidation,")
    appendLine("        Ellipsize,")
    appendLine("        Clip,")
    appendLine("    }")
}

private fun PrimitiveValueExpression.kotlinExpression(): String =
    when (this) {
        is PrimitiveValueExpression.Constant -> value.kotlinLiteral()
        is PrimitiveValueExpression.StateField -> "state.$fieldName"
    }

private fun PrimitiveValueExpression.minecraftColorExpression(): String =
    when (this) {
        is PrimitiveValueExpression.Constant -> value.minecraftColorLiteral()
        is PrimitiveValueExpression.StateField -> "state.$fieldName"
    }

private fun Any?.kotlinLiteral(): String =
    when (this) {
        null -> "null"
        is String -> "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        is Boolean -> toString()
        is Int -> toString()
        is UInt -> "${toString()}u"
        is Position -> "Position($x, $y)"
        else -> error("Cannot generate Kotlin literal for ${this::class.qualifiedName}")
    }

private fun Any?.minecraftColorLiteral(): String =
    when (this) {
        is Color -> "0x${value.toLong().toString(16).uppercase().padStart(8, '0')}.toInt()"
        is Int -> "0x${toUInt().toLong().toString(16).uppercase().padStart(8, '0')}.toInt()"
        is UInt -> "0x${toLong().toString(16).uppercase().padStart(8, '0')}.toInt()"
        else -> error("Cannot generate Minecraft color literal for ${this?.let { it::class.qualifiedName } ?: "null"}")
    }

private fun TextAlignment.kotlinExpression(): String =
    when (this) {
        TextAlignment.Start -> "TextAlignment.Start"
        TextAlignment.Center -> "TextAlignment.Center"
        TextAlignment.End -> "TextAlignment.End"
    }

private fun TextOverflowPolicy.kotlinExpression(): String =
    when (this) {
        TextOverflowPolicy.FailInValidation -> "TextOverflowPolicy.FailInValidation"
        TextOverflowPolicy.Ellipsize -> "TextOverflowPolicy.Ellipsize"
        TextOverflowPolicy.Clip -> "TextOverflowPolicy.Clip"
    }

private fun ru.lazyhat.kraftui.foundation.modifier.TextWrapPolicy.minecraftBooleanExpression(): String =
    when (this) {
        ru.lazyhat.kraftui.foundation.modifier.TextWrapPolicy.NoWrap -> "false"
        ru.lazyhat.kraftui.foundation.modifier.TextWrapPolicy.WordWrap -> "true"
    }

private fun Int?.kotlinNullableInt(): String = this?.toString() ?: "null"

private fun String.isValidQualifiedIdentifier(): Boolean =
    split('.').all { it.isValidIdentifier() }

private fun String.isValidTypeName(): Boolean =
    split('.').all { it.isValidIdentifier() }

private fun String.isValidIdentifier(): Boolean =
    isNotEmpty() &&
        first().let { it == '_' || it.isLetter() } &&
        drop(1).all { it == '_' || it.isLetterOrDigit() }
