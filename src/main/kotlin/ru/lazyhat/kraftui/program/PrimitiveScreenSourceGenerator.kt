package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.Position
import ru.lazyhat.kraftui.foundation.modifier.TextAlignment
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

data class PrimitiveScreenSource(
    val packageName: String,
    val className: String,
    val source: String,
    val assets: List<PrimitiveGeneratedAsset> = emptyList(),
)

data class PrimitiveGeneratedAsset(
    val path: String,
    val bytes: ByteArray,
) {
    init {
        require(path.isNotBlank()) { "Generated asset path must not be blank" }
        require(bytes.isNotEmpty()) { "Generated asset bytes must not be empty" }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is PrimitiveGeneratedAsset &&
            path == other.path &&
            bytes.contentEquals(other.bytes)

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

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
            bakedTextures.forEach { texture ->
                appendLine("    private val ${texture.id.kotlinIdentifier()}Pixels = ${texture.argb.kotlinIntArrayLiteral()}")
            }
            if (bakedTextures.isNotEmpty()) {
                appendLine()
            }
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
        is PrimitiveRenderOp.DrawBakedTexture -> {
            appendLine(
                "${indent}target.drawBakedTexture(${op.x}$ox, ${op.y}$oy, ${op.width}, ${op.height}, ${op.textureId.kotlinIdentifier()}Pixels, ${op.width}, ${op.height})",
            )
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

private fun PrimitiveValueExpression.kotlinExpression(): String =
    when (this) {
        is PrimitiveValueExpression.Constant -> value.kotlinLiteral()
        is PrimitiveValueExpression.StateField -> "state.$fieldName"
        is PrimitiveValueExpression.And -> terms.joinToString(separator = " && ") { "(${it.kotlinExpression()})" }
        is PrimitiveValueExpression.Match ->
            booleanMatchExpression(
                trueExpression = { it.kotlinExpression() },
                falseExpression = { it.kotlinExpression() },
            )
                ?: cases.entries.joinToString(
                    prefix = "when (${subject.kotlinExpression()}) { ",
                    postfix = " else -> ${default.kotlinExpression()} }",
                    separator = "; ",
                ) { (key, value) -> "${key.kotlinLiteral()} -> ${value.kotlinExpression()}" }
    }

private fun PrimitiveValueExpression.Match.booleanMatchExpression(
    trueExpression: (PrimitiveValueExpression) -> String,
    falseExpression: (PrimitiveValueExpression) -> String,
): String? {
    val trueCase = cases[true] ?: return null
    val falseCase = cases[false] ?: return null
    if (cases.keys.any { it !is Boolean }) return null
    return "if (${subject.kotlinExpression()}) { ${trueExpression(trueCase)} } else { ${falseExpression(falseCase)} }"
}

private fun Any?.kotlinLiteral(): String =
    when (this) {
        null -> "null"
        is String -> "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        is Boolean -> toString()
        is Int -> toString()
        is UInt -> "${toString()}u"
        is Enum<*> -> "${this::class.qualifiedName!!.replace('$', '.')}.${name}"
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

internal fun IntArray.kotlinIntArrayLiteral(): String =
    joinToString(
        separator = ", ",
        prefix = "intArrayOf(",
        postfix = ")",
    ) { "0x${it.toUInt().toString(16).uppercase().padStart(8, '0')}.toInt()" }

internal fun PrimitiveBakedTexture.toPngBytes(): ByteArray {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, width, height, argb, 0, width)
    val output = ByteArrayOutputStream()
    check(ImageIO.write(image, "png", output)) {
        "No PNG writer is available"
    }
    return output.toByteArray()
}

internal fun PrimitiveBakedTexture.assetPath(options: PrimitiveStaticTextureBakingOptions.Enabled): String =
    "assets/${options.textureNamespace}/${options.texturePathPrefix}/$id.png"

internal fun String.kotlinIdentifier(): String {
    require(isNotEmpty()) { "Identifier source must not be empty" }
    return buildString {
        this@kotlinIdentifier.forEachIndexed { index, char ->
            val valid =
                if (index == 0) {
                    char == '_' || char.isLetter()
                } else {
                    char == '_' || char.isLetterOrDigit()
                }
            append(if (valid) char else '_')
        }
    }
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
