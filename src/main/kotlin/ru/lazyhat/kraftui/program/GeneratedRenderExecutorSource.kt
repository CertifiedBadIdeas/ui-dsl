package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Color

data class GeneratedRenderExecutorSource(
    val packageName: String,
    val className: String,
    val source: String,
)

fun ExecutableScreenPlan<*>.generateRenderExecutorSource(
    packageName: String,
    className: String,
): GeneratedRenderExecutorSource {
    require(packageName.isValidQualifiedIdentifier()) {
        "packageName must be a valid qualified Kotlin identifier"
    }
    require(className.isValidIdentifier()) {
        "className must be a valid Kotlin identifier"
    }

    val source =
        buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import ru.lazyhat.kraftui.foundation.CanvasScope")
            appendLine("import ru.lazyhat.kraftui.foundation.Color")
            appendLine("import ru.lazyhat.kraftui.foundation.modifier.Position")
            appendLine("import ru.lazyhat.kraftui.foundation.modifier.TextAlignment")
            appendLine("import ru.lazyhat.kraftui.program.ExecutableScreenPlan")
            appendLine("import ru.lazyhat.kraftui.program.RenderBackend")
            appendLine("import ru.lazyhat.kraftui.program.RenderOp")
            appendLine("import ru.lazyhat.kraftui.text.TextLayouter")
            appendLine()
            appendLine("class $className(")
            appendLine("    private val plan: ExecutableScreenPlan<*>,")
            appendLine(") {")
            appendLine("    private val canvasScope = OffsetCanvasScope()")
            appendLine()
            appendLine("    fun render(backend: RenderBackend) {")
            renderSteps.forEachIndexed { index, step ->
                appendRenderStep(index, step)
            }
            appendLine("    }")
            appendLine()
            appendOffsetCanvasScope()
            appendLine("}")
        }

    return GeneratedRenderExecutorSource(
        packageName = packageName,
        className = className,
        source = source,
    )
}

private fun StringBuilder.appendRenderStep(
    index: Int,
    step: ExecutableRenderStep,
) {
    appendLine("        val step$index = plan.renderSteps[$index]")
    if (step.visible != null) {
        appendLine("        if (step$index.visible?.value != false) {")
        appendRenderStepBody(index, step, indent = "            ")
        appendLine("        }")
    } else {
        appendRenderStepBody(index, step, indent = "        ")
    }
}

private fun StringBuilder.appendRenderStepBody(
    index: Int,
    step: ExecutableRenderStep,
    indent: String,
) {
    if (step.origin != null) {
        appendLine("${indent}val origin$index = step$index.origin?.value ?: Position.Zero")
        appendLine("${indent}val ox$index = origin$index.x")
        appendLine("${indent}val oy$index = origin$index.y")
    }

    when (val op = step.op) {
        is RenderOp.FillRect -> appendFillRect(index, step, op, indent)
        is RenderOp.DrawText -> appendDrawText(index, step, indent)
        is RenderOp.DrawTerminalSurface -> appendDrawTerminalSurface(index, step, indent)
        is RenderOp.DrawCanvas -> appendDrawCanvas(index, step, indent)
        is RenderOp.PushClip -> appendPushClip(index, step, indent)
        RenderOp.PopClip -> appendLine("${indent}backend.popClip()")
        is RenderOp.DrawCodeEditor -> appendDrawCodeEditor(index, step, indent)
    }
}

private fun StringBuilder.appendFillRect(
    index: Int,
    step: ExecutableRenderStep,
    op: RenderOp.FillRect,
    indent: String,
) {
    if (step.effectiveDependencies.isStatic && step.origin == null) {
        appendLine("${indent}backend.fillRect(${op.x}, ${op.y}, ${op.width}, ${op.height}, ${op.color.value.kotlinColorLiteral()})")
    } else {
        appendLine("${indent}val op$index = step$index.op as RenderOp.FillRect")
        val x = if (step.origin == null) "op$index.x" else "op$index.x + ox$index"
        val y = if (step.origin == null) "op$index.y" else "op$index.y + oy$index"
        appendLine("${indent}backend.fillRect($x, $y, op$index.width, op$index.height, op$index.color.value)")
    }
}

private fun StringBuilder.appendDrawText(
    index: Int,
    step: ExecutableRenderStep,
    indent: String,
) {
    val opName = "op$index"
    appendLine("${indent}val op$index = step$index.op as RenderOp.DrawText")
    appendLine("${indent}val visibleLineCount$index = (op$index.height / op$index.flow.lineHeight).coerceAtLeast(0)")
    appendLine("${indent}val effectiveMaxLines$index =")
    appendLine("${indent}    when {")
    appendLine("${indent}        visibleLineCount$index == 0 -> 0")
    appendLine("${indent}        op$index.flow.maxLines == null -> visibleLineCount$index")
    appendLine("${indent}        else -> minOf(op$index.flow.maxLines, visibleLineCount$index)")
    appendLine("${indent}    }")
    appendLine("${indent}val runtimeFlow$index = op$index.flow.copy(maxLines = effectiveMaxLines$index.coerceAtLeast(1))")
    appendLine("${indent}val textLayout$index =")
    appendLine("${indent}    TextLayouter(backend::measureText).layout(")
    appendLine("${indent}        text = op$index.value.value,")
    appendLine("${indent}        width = op$index.width,")
    appendLine("${indent}        flow = runtimeFlow$index,")
    appendLine("${indent}        overflow = op$index.overflow,")
    appendLine("${indent}    )")
    appendLine("${indent}backend.pushClip(${step.xExpr(index, opName)}, ${step.yExpr(index, opName)}, op$index.width, op$index.height)")
    appendLine("${indent}textLayout$index.lines.forEachIndexed { lineIndex$index, line$index ->")
    appendLine("${indent}    val textX$index =")
    appendLine("${indent}        when (op$index.alignment) {")
    appendLine("${indent}            TextAlignment.Start -> op$index.x")
    appendLine("${indent}            TextAlignment.Center -> op$index.x + (op$index.width - line$index.width) / 2")
    appendLine("${indent}            TextAlignment.End -> op$index.x + op$index.width - line$index.width")
    appendLine("${indent}        }")
    val offsetX = step.ifNeedsOrigin(index, " + ox$index")
    val offsetY = step.ifNeedsOrigin(index, " + oy$index")
    appendLine("${indent}    backend.drawText(textX$index$offsetX, op$index.y$offsetY + lineIndex$index * op$index.flow.lineHeight, line$index.text, op$index.color.value)")
    appendLine("${indent}}")
    appendLine("${indent}backend.popClip()")
}

private fun StringBuilder.appendDrawTerminalSurface(
    index: Int,
    step: ExecutableRenderStep,
    indent: String,
) {
    val opName = "op$index"
    appendLine("${indent}val op$index = step$index.op as RenderOp.DrawTerminalSurface")
    appendLine("${indent}backend.drawTerminalSurface(${step.xExpr(index, opName)}, ${step.yExpr(index, opName)}, op$index.snapshot.value)")
}

private fun StringBuilder.appendDrawCanvas(
    index: Int,
    step: ExecutableRenderStep,
    indent: String,
) {
    val opName = "op$index"
    appendLine("${indent}val op$index = step$index.op as RenderOp.DrawCanvas")
    appendLine("${indent}canvasScope.bind(backend, ${step.xExpr(index, opName)}, ${step.yExpr(index, opName)}, op$index.width, op$index.height)")
    appendLine("${indent}op$index.onDraw.invoke(canvasScope)")
}

private fun StringBuilder.appendPushClip(
    index: Int,
    step: ExecutableRenderStep,
    indent: String,
) {
    val opName = "op$index"
    appendLine("${indent}val op$index = step$index.op as RenderOp.PushClip")
    appendLine("${indent}backend.pushClip(${step.xExpr(index, opName)}, ${step.yExpr(index, opName)}, op$index.width, op$index.height)")
}

private fun StringBuilder.appendDrawCodeEditor(
    index: Int,
    step: ExecutableRenderStep,
    indent: String,
) {
    val opName = "op$index"
    appendLine("${indent}val op$index = step$index.op as RenderOp.DrawCodeEditor")
    appendLine("${indent}backend.drawCodeEditor(")
    appendLine("${indent}    ${step.xExpr(index, opName)},")
    appendLine("${indent}    ${step.yExpr(index, opName)},")
    appendLine("${indent}    op$index.width,")
    appendLine("${indent}    op$index.height,")
    appendLine("${indent}    op$index.viewModel.value,")
    appendLine("${indent}    op$index.fontWidth,")
    appendLine("${indent}    op$index.fontHeight,")
    appendLine("${indent})")
}

private fun StringBuilder.appendOffsetCanvasScope() {
    appendLine("    private class OffsetCanvasScope : CanvasScope {")
    appendLine("        private var backend: RenderBackend? = null")
    appendLine("        private var originX: Int = 0")
    appendLine("        private var originY: Int = 0")
    appendLine("        override var width: Int = 0")
    appendLine("            private set")
    appendLine("        override var height: Int = 0")
    appendLine("            private set")
    appendLine()
    appendLine("        fun bind(")
    appendLine("            backend: RenderBackend,")
    appendLine("            originX: Int,")
    appendLine("            originY: Int,")
    appendLine("            width: Int,")
    appendLine("            height: Int,")
    appendLine("        ) {")
    appendLine("            this.backend = backend")
    appendLine("            this.originX = originX")
    appendLine("            this.originY = originY")
    appendLine("            this.width = width")
    appendLine("            this.height = height")
    appendLine("        }")
    appendLine()
    appendLine("        override fun fillRect(")
    appendLine("            x: Int,")
    appendLine("            y: Int,")
    appendLine("            width: Int,")
    appendLine("            height: Int,")
    appendLine("            color: Color,")
    appendLine("        ) {")
    appendLine("            backend?.fillRect(originX + x, originY + y, width, height, color)")
    appendLine("        }")
    appendLine()
    appendLine("        override fun measureText(text: String): Int = backend?.measureText(text) ?: 0")
    appendLine("    }")
}

private fun ExecutableRenderStep.hasOrigin(): Boolean = origin != null

private fun ExecutableRenderStep.ifNeedsOrigin(
    index: Int,
    value: String,
): String = if (hasOrigin()) value else ""

private fun ExecutableRenderStep.xExpr(
    index: Int,
    op: String,
): String = "$op.x${ifNeedsOrigin(index, " + ox$index")}"

private fun ExecutableRenderStep.yExpr(
    index: Int,
    op: String,
): String = "$op.y${ifNeedsOrigin(index, " + oy$index")}"

private fun Color.kotlinColorLiteral(): String =
    when (this) {
        Color.Black -> "Color.Black"
        Color.White -> "Color.White"
        Color.Red -> "Color.Red"
        Color.Green -> "Color.Green"
        Color.Blue -> "Color.Blue"
        else -> "Color.hex(0x${value.toLong().toString(16).uppercase()}u)"
    }

private fun String.isValidQualifiedIdentifier(): Boolean =
    split('.').all { it.isValidIdentifier() }

private fun String.isValidIdentifier(): Boolean =
    isNotEmpty() &&
        first().let { it == '_' || it.isLetter() } &&
        drop(1).all { it == '_' || it.isLetterOrDigit() }
