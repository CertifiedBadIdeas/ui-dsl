package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.GeneratedValueExpression

fun ScreenProgram<*>.toPrimitiveScreenProgram(): PrimitiveScreenProgram {
    val validation = validateGeneratedProgram()
    require(validation.isValid) {
        "Screen program contains runtime-only parts and cannot be lowered to primitive instructions:\n" +
            validation.diagnostics.joinToString(separator = "\n") { it.asText() }
    }

    return PrimitiveScreenProgram(
        renderInstructions =
            frames.flatMapIndexed { frameIndex, frame ->
                frame.toPrimitiveRenderInstructions(frameIndex)
            },
        inputInstructions =
            hitRegions.map { region ->
                val frame = frames[region.frameIndex]
                PrimitiveInputInstruction.ClickRegion(
                    path = "hitRegion[${region.nodeId}]",
                    visible =
                        combineVisibility(
                            frame.visible?.generatedExpression?.toPrimitiveValueExpression(),
                            region.visible?.generatedExpression?.toPrimitiveValueExpression(),
                        ),
                    origin = frame.origin?.generatedExpression?.toPrimitiveValueExpression(),
                    x = region.x,
                    y = region.y,
                    width = region.width,
                    height = region.height,
                    action = region.action?.generatedExpression?.toPrimitiveValueExpression(),
                )
            },
    )
}

private fun RenderFrame.toPrimitiveRenderInstructions(frameIndex: Int): List<PrimitiveRenderInstruction> {
    val frameVisible = visible?.generatedExpression?.toPrimitiveValueExpression()
    val visibilityStack = ArrayList<PrimitiveValueExpression>()
    val instructions = ArrayList<PrimitiveRenderInstruction>(ops.size)
    ops.forEachIndexed { opIndex, op ->
        when (op) {
            is RenderOp.PushVisibility -> {
                visibilityStack += op.condition.generatedExpression.requireGenerated().toPrimitiveValueExpression()
            }
            RenderOp.PopVisibility -> {
                visibilityStack.removeAt(visibilityStack.lastIndex)
            }
            else -> {
                instructions +=
                    PrimitiveRenderInstruction(
                        path = "frame[$frameIndex].op[$opIndex]",
                        visible = combineVisibility(frameVisible, combineVisibility(visibilityStack)),
                        origin = origin?.generatedExpression?.toPrimitiveValueExpression(),
                        op = op.toPrimitiveRenderOp(),
                    )
            }
        }
    }
    return instructions
}

private fun RenderOp.toPrimitiveRenderOp(): PrimitiveRenderOp =
    when (this) {
        is RenderOp.FillRect ->
            PrimitiveRenderOp.FillRect(
                x = x,
                y = y,
                width = width,
                height = height,
                color = color.generatedExpression.requireGenerated().toPrimitiveValueExpression(),
            )
        is RenderOp.DrawText ->
            PrimitiveRenderOp.DrawText(
                x = x,
                y = y,
                width = width,
                height = height,
                text = value.generatedExpression.requireGenerated().toPrimitiveValueExpression(),
                color = color.generatedExpression.requireGenerated().toPrimitiveValueExpression(),
                alignment = alignment,
                overflow = overflow,
                flow = flow,
            )
        is RenderOp.DrawTerminalSurface ->
            PrimitiveRenderOp.DrawTerminalSurface(
                x = x,
                y = y,
                width = width,
                height = height,
                snapshot = snapshot.generatedExpression.requireGenerated().toPrimitiveValueExpression(),
            )
        is RenderOp.DrawCanvas -> error("DrawCanvas should have been rejected by validateGeneratedProgram")
        is RenderOp.PushClip -> PrimitiveRenderOp.PushClip(x, y, width, height)
        RenderOp.PopClip -> PrimitiveRenderOp.PopClip
        is RenderOp.PushVisibility -> error("PushVisibility should have been consumed by primitive lowering")
        RenderOp.PopVisibility -> error("PopVisibility should have been consumed by primitive lowering")
        is RenderOp.DrawCodeEditor ->
            PrimitiveRenderOp.DrawCodeEditor(
                x = x,
                y = y,
                width = width,
                height = height,
                viewModel = viewModel.generatedExpression.requireGenerated().toPrimitiveValueExpression(),
                fontWidth = fontWidth,
                fontHeight = fontHeight,
            )
    }

private fun GeneratedValueExpression?.requireGenerated(): GeneratedValueExpression =
    requireNotNull(this) { "value should have been rejected by validateGeneratedProgram" }

private fun GeneratedValueExpression.toPrimitiveValueExpression(): PrimitiveValueExpression =
    when (this) {
        is GeneratedValueExpression.Constant -> PrimitiveValueExpression.Constant(value)
        is GeneratedValueExpression.StateField -> PrimitiveValueExpression.StateField(fieldName)
        is GeneratedValueExpression.And -> PrimitiveValueExpression.And(terms.map { it.toPrimitiveValueExpression() })
    }

private fun combineVisibility(expressions: List<PrimitiveValueExpression>): PrimitiveValueExpression? =
    when (expressions.size) {
        0 -> null
        1 -> expressions.single()
        else -> PrimitiveValueExpression.And(expressions)
    }

private fun combineVisibility(
    first: PrimitiveValueExpression?,
    second: PrimitiveValueExpression?,
): PrimitiveValueExpression? =
    combineVisibility(listOfNotNull(first, second).flatMap { it.flattenAndTerms() })

private fun PrimitiveValueExpression.flattenAndTerms(): List<PrimitiveValueExpression> =
    when (this) {
        is PrimitiveValueExpression.And -> terms.flatMap { it.flattenAndTerms() }
        else -> listOf(this)
    }

internal fun GeneratedProgramDiagnostic.asText(): String =
    when (this) {
        is GeneratedProgramDiagnostic.RuntimeOnlyValue -> "$path: runtime-only value"
        is GeneratedProgramDiagnostic.RuntimeOnlyOperation -> "$path: $reason"
    }
