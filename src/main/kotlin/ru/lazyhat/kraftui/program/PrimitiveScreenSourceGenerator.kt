package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.GeneratedValueExpression
import ru.lazyhat.kraftui.foundation.modifier.Position
import ru.lazyhat.kraftui.foundation.modifier.TextAlignment
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy

data class PrimitiveScreenSource(
    val packageName: String,
    val className: String,
    val source: String,
)

fun PrimitiveScreenProgram.generatePrimitiveScreenSource(
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

    val source =
        buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import ru.lazyhat.kraftui.foundation.Color")
            appendLine("import ru.lazyhat.kraftui.foundation.modifier.Position")
            appendLine("import ru.lazyhat.kraftui.foundation.modifier.TextAlignment")
            appendLine("import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy")
            appendLine("import ru.lazyhat.kraftui.program.RenderBackend")
            appendLine("import ru.lazyhat.kraftui.text.TextFlow")
            appendLine("import ru.lazyhat.kraftui.text.TextLayouter")
            appendLine()
            appendLine("class $className {")
            appendLine("    fun render(target: RenderBackend, state: $stateType) {")
            renderInstructions.forEachIndexed { index, instruction ->
                appendRenderInstruction(index, instruction)
            }
            appendLine("    }")
            appendLine()
            appendLine("    fun mouseClicked(state: $stateType, x: Int, y: Int): $actionType? {")
            inputInstructions.forEachIndexed { index, instruction ->
                appendInputInstruction(index, instruction)
            }
            appendLine("        return null")
            appendLine("    }")
            appendLine("}")
        }

    return PrimitiveScreenSource(
        packageName = packageName,
        className = className,
        source = source,
    )
}

private fun StringBuilder.appendRenderInstruction(
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
    appendPrimitiveRenderOp(index, instruction.origin != null, instruction.op, indent)
    if (instruction.visible != null) {
        appendLine("        }")
    }
}

private fun StringBuilder.appendPrimitiveRenderOp(
    index: Int,
    hasOrigin: Boolean,
    op: PrimitiveRenderOp,
    indent: String,
) {
    val ox = if (hasOrigin) " + ox$index" else ""
    val oy = if (hasOrigin) " + oy$index" else ""
    when (op) {
        is PrimitiveRenderOp.FillRect -> {
            appendLine("${indent}target.fillRect(${op.x}$ox, ${op.y}$oy, ${op.width}, ${op.height}, ${op.color.kotlinExpression()})")
        }
        is PrimitiveRenderOp.DrawText -> appendDrawText(index, op, ox, oy, indent)
        is PrimitiveRenderOp.DrawTerminalSurface -> {
            appendLine("${indent}target.drawTerminalSurface(${op.x}$ox, ${op.y}$oy, ${op.snapshot.kotlinExpression()})")
        }
        is PrimitiveRenderOp.PushClip -> {
            appendLine("${indent}target.pushClip(${op.x}$ox, ${op.y}$oy, ${op.width}, ${op.height})")
        }
        PrimitiveRenderOp.PopClip -> {
            appendLine("${indent}target.popClip()")
        }
        is PrimitiveRenderOp.DrawCodeEditor -> {
            appendLine("${indent}target.drawCodeEditor(")
            appendLine("${indent}    ${op.x}$ox,")
            appendLine("${indent}    ${op.y}$oy,")
            appendLine("${indent}    ${op.width},")
            appendLine("${indent}    ${op.height},")
            appendLine("${indent}    ${op.viewModel.kotlinExpression()},")
            appendLine("${indent}    ${op.fontWidth},")
            appendLine("${indent}    ${op.fontHeight},")
            appendLine("${indent})")
        }
    }
}

private fun StringBuilder.appendDrawText(
    index: Int,
    op: PrimitiveRenderOp.DrawText,
    ox: String,
    oy: String,
    indent: String,
) {
    val flow = op.flow
    appendLine("${indent}val visibleLineCount$index = (${op.height} / ${flow.lineHeight}).coerceAtLeast(0)")
    appendLine("${indent}val effectiveMaxLines$index =")
    appendLine("${indent}    when {")
    appendLine("${indent}        visibleLineCount$index == 0 -> 0")
    appendLine("${indent}        ${flow.maxLines.kotlinNullableInt()} == null -> visibleLineCount$index")
    appendLine("${indent}        else -> minOf(${flow.maxLines.kotlinNullableInt()}, visibleLineCount$index)")
    appendLine("${indent}    }")
    appendLine("${indent}val runtimeFlow$index = ${flow.kotlinExpression()}.copy(maxLines = effectiveMaxLines$index.coerceAtLeast(1))")
    appendLine("${indent}val textLayout$index =")
    appendLine("${indent}    TextLayouter(target::measureText).layout(")
    appendLine("${indent}        text = ${op.text.kotlinExpression()},")
    appendLine("${indent}        width = ${op.width},")
    appendLine("${indent}        flow = runtimeFlow$index,")
    appendLine("${indent}        overflow = ${op.overflow.kotlinExpression()},")
    appendLine("${indent}    )")
    appendLine("${indent}target.pushClip(${op.x}$ox, ${op.y}$oy, ${op.width}, ${op.height})")
    appendLine("${indent}textLayout$index.lines.forEachIndexed { lineIndex$index, line$index ->")
    appendLine("${indent}    val textX$index =")
    appendLine("${indent}        when (${op.alignment.kotlinExpression()}) {")
    appendLine("${indent}            TextAlignment.Start -> ${op.x}")
    appendLine("${indent}            TextAlignment.Center -> ${op.x} + (${op.width} - line$index.width) / 2")
    appendLine("${indent}            TextAlignment.End -> ${op.x} + ${op.width} - line$index.width")
    appendLine("${indent}        }")
    appendLine("${indent}    target.drawText(textX$index$ox, ${op.y}$oy + lineIndex$index * ${flow.lineHeight}, line$index.text, ${op.color.kotlinExpression()})")
    appendLine("${indent}}")
    appendLine("${indent}target.popClip()")
}

private fun StringBuilder.appendInputInstruction(
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

private fun GeneratedValueExpression.kotlinExpression(): String =
    when (this) {
        is GeneratedValueExpression.Constant -> value.kotlinLiteral()
        is GeneratedValueExpression.StateField -> "state.$fieldName"
    }

private fun Any?.kotlinLiteral(): String =
    when (this) {
        null -> "null"
        is String -> "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        is Boolean -> toString()
        is Int -> toString()
        is UInt -> "${toString()}u"
        is Color -> kotlinLiteral()
        is Position -> "Position($x, $y)"
        else -> error("Cannot generate Kotlin literal for ${this::class.qualifiedName}")
    }

private fun Color.kotlinLiteral(): String =
    when (this) {
        Color.Transparent -> "Color.Transparent"
        Color.Black -> "Color.Black"
        Color.White -> "Color.White"
        Color.Red -> "Color.Red"
        Color.Green -> "Color.Green"
        Color.Blue -> "Color.Blue"
        else -> "Color.hex(0x${value.toLong().toString(16).uppercase()}u)"
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

private fun ru.lazyhat.kraftui.text.TextFlow.kotlinExpression(): String =
    "TextFlow(wrap = $wrap, maxLines = ${maxLines.kotlinNullableInt()}, lineHeight = $lineHeight)"

private fun Int?.kotlinNullableInt(): String = this?.toString() ?: "null"

private fun String.isValidQualifiedIdentifier(): Boolean =
    split('.').all { it.isValidIdentifier() }

private fun String.isValidTypeName(): Boolean =
    split('.').all { it.isValidIdentifier() }

private fun String.isValidIdentifier(): Boolean =
    isNotEmpty() &&
        first().let { it == '_' || it.isLetter() } &&
        drop(1).all { it == '_' || it.isLetterOrDigit() }
