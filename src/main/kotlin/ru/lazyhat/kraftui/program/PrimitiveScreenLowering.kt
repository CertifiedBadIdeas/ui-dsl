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
                frame.ops.mapIndexed { opIndex, op ->
                    PrimitiveRenderInstruction(
                        path = "frame[$frameIndex].op[$opIndex]",
                        visible = frame.visible?.generatedExpression?.toPrimitiveValueExpression(),
                        origin = frame.origin?.generatedExpression?.toPrimitiveValueExpression(),
                        op = op.toPrimitiveRenderOp(),
                    )
                }
            },
        inputInstructions =
            hitRegions.map { region ->
                val frame = frames[region.frameIndex]
                PrimitiveInputInstruction.ClickRegion(
                    path = "hitRegion[${region.nodeId}]",
                    visible = frame.visible?.generatedExpression?.toPrimitiveValueExpression(),
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
    }

internal fun GeneratedProgramDiagnostic.asText(): String =
    when (this) {
        is GeneratedProgramDiagnostic.RuntimeOnlyValue -> "$path: runtime-only value"
        is GeneratedProgramDiagnostic.RuntimeOnlyOperation -> "$path: $reason"
    }
