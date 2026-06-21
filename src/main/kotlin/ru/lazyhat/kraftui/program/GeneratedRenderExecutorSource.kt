package ru.lazyhat.kraftui.program

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
            appendLine("import ru.lazyhat.kraftui.foundation.Color")
            appendLine("import ru.lazyhat.kraftui.foundation.modifier.Position")
            appendLine("import ru.lazyhat.kraftui.program.ExecutableScreenPlan")
            appendLine("import ru.lazyhat.kraftui.program.RenderBackend")
            appendLine("import ru.lazyhat.kraftui.program.RenderOp")
            appendLine()
            appendLine("class $className(")
            appendLine("    private val plan: ExecutableScreenPlan<*>,")
            appendLine(") {")
            appendLine("    fun render(backend: RenderBackend) {")
            renderSteps.forEachIndexed { index, step ->
                appendRenderStep(index, step)
            }
            appendLine("    }")
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
        is RenderOp.DrawText -> appendDynamicRenderOp(index, indent)
        is RenderOp.DrawTerminalSurface -> appendDynamicRenderOp(index, indent)
        is RenderOp.DrawCanvas -> appendDynamicRenderOp(index, indent)
        is RenderOp.PushClip -> appendDynamicRenderOp(index, indent)
        RenderOp.PopClip -> appendLine("${indent}backend.popClip()")
        is RenderOp.DrawCodeEditor -> appendDynamicRenderOp(index, indent)
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

private fun StringBuilder.appendDynamicRenderOp(
    index: Int,
    indent: String,
) {
    appendLine("${indent}when (val op$index = step$index.op) {")
    appendLine("${indent}    is RenderOp.FillRect -> backend.fillRect(op$index.x, op$index.y, op$index.width, op$index.height, op$index.color.value)")
    appendLine("${indent}    is RenderOp.DrawTerminalSurface -> backend.drawTerminalSurface(op$index.x, op$index.y, op$index.snapshot.value)")
    appendLine("${indent}    is RenderOp.PushClip -> backend.pushClip(op$index.x, op$index.y, op$index.width, op$index.height)")
    appendLine("${indent}    RenderOp.PopClip -> backend.popClip()")
    appendLine("${indent}    else -> error(\"Generated render source delegates unsupported render op $index to the checked executor\")")
    appendLine("${indent}}")
}

private fun ru.lazyhat.kraftui.foundation.Color.kotlinColorLiteral(): String =
    when (this) {
        ru.lazyhat.kraftui.foundation.Color.Black -> "Color.Black"
        ru.lazyhat.kraftui.foundation.Color.White -> "Color.White"
        ru.lazyhat.kraftui.foundation.Color.Red -> "Color.Red"
        ru.lazyhat.kraftui.foundation.Color.Green -> "Color.Green"
        ru.lazyhat.kraftui.foundation.Color.Blue -> "Color.Blue"
        else -> "Color.hex(0x${value.toString(16).uppercase()}u)"
    }

private fun String.isValidQualifiedIdentifier(): Boolean =
    split('.').all { it.isValidIdentifier() }

private fun String.isValidIdentifier(): Boolean =
    isNotEmpty() &&
        first().let { it == '_' || it.isLetter() } &&
        drop(1).all { it == '_' || it.isLetterOrDigit() }
