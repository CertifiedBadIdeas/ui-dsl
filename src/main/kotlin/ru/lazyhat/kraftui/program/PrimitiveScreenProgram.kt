package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.GeneratedValueExpression
import ru.lazyhat.kraftui.foundation.modifier.Position
import ru.lazyhat.kraftui.foundation.modifier.TextAlignment
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy
import ru.lazyhat.kraftui.text.TextFlow
import ru.lazyhat.kraftui.text.TextLayouter

data class PrimitiveScreenProgram(
    val renderInstructions: List<PrimitiveRenderInstruction>,
    val inputInstructions: List<PrimitiveInputInstruction>,
)

data class PrimitiveRenderInstruction(
    val path: String,
    val visible: GeneratedValueExpression?,
    val origin: GeneratedValueExpression?,
    val op: PrimitiveRenderOp,
)

sealed interface PrimitiveRenderOp {
    data class FillRect(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val color: GeneratedValueExpression,
    ) : PrimitiveRenderOp

    data class DrawText(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val text: GeneratedValueExpression,
        val color: GeneratedValueExpression,
        val alignment: TextAlignment = TextAlignment.Start,
        val overflow: TextOverflowPolicy = TextOverflowPolicy.FailInValidation,
        val flow: TextFlow = TextFlow(),
    ) : PrimitiveRenderOp

    data class DrawTerminalSurface(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val snapshot: GeneratedValueExpression,
    ) : PrimitiveRenderOp

    data class PushClip(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    ) : PrimitiveRenderOp

    data object PopClip : PrimitiveRenderOp

    data class DrawCodeEditor(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val viewModel: GeneratedValueExpression,
        val fontWidth: Int,
        val fontHeight: Int,
    ) : PrimitiveRenderOp
}

sealed interface PrimitiveInputInstruction {
    data class ClickRegion(
        val path: String,
        val visible: GeneratedValueExpression?,
        val origin: GeneratedValueExpression?,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val action: GeneratedValueExpression?,
    ) : PrimitiveInputInstruction
}

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
                        visible = frame.visible?.generatedExpression,
                        origin = frame.origin?.generatedExpression,
                        op = op.toPrimitiveRenderOp(),
                    )
                }
            },
        inputInstructions =
            hitRegions.map { region ->
                val frame = frames[region.frameIndex]
                PrimitiveInputInstruction.ClickRegion(
                    path = "hitRegion[${region.nodeId}]",
                    visible = frame.visible?.generatedExpression,
                    origin = frame.origin?.generatedExpression,
                    x = region.x,
                    y = region.y,
                    width = region.width,
                    height = region.height,
                    action = region.action?.generatedExpression,
                )
            },
    )
}

fun PrimitiveScreenProgram.render(
    backend: RenderBackend,
    resolve: (GeneratedValueExpression) -> Any?,
) {
    renderInstructions.forEach { instruction ->
        val visible = instruction.visible?.resolveAs<Boolean>(resolve) ?: true
        if (!visible) return@forEach
        val origin = instruction.origin?.resolveAs<Position>(resolve) ?: Position.Zero
        instruction.op.render(backend, resolve, origin.x, origin.y)
    }
}

private fun RenderOp.toPrimitiveRenderOp(): PrimitiveRenderOp =
    when (this) {
        is RenderOp.FillRect ->
            PrimitiveRenderOp.FillRect(
                x = x,
                y = y,
                width = width,
                height = height,
                color = color.generatedExpression.requireGenerated(),
            )
        is RenderOp.DrawText ->
            PrimitiveRenderOp.DrawText(
                x = x,
                y = y,
                width = width,
                height = height,
                text = value.generatedExpression.requireGenerated(),
                color = color.generatedExpression.requireGenerated(),
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
                snapshot = snapshot.generatedExpression.requireGenerated(),
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
                viewModel = viewModel.generatedExpression.requireGenerated(),
                fontWidth = fontWidth,
                fontHeight = fontHeight,
            )
    }

private fun PrimitiveRenderOp.render(
    backend: RenderBackend,
    resolve: (GeneratedValueExpression) -> Any?,
    ox: Int,
    oy: Int,
) {
    when (this) {
        is PrimitiveRenderOp.FillRect -> {
            backend.fillRect(x + ox, y + oy, width, height, color.resolveAs<Color>(resolve))
        }
        is PrimitiveRenderOp.DrawText -> {
            val visibleLineCount = (height / flow.lineHeight).coerceAtLeast(0)
            val effectiveMaxLines =
                when {
                    visibleLineCount == 0 -> 0
                    flow.maxLines == null -> visibleLineCount
                    else -> minOf(flow.maxLines, visibleLineCount)
                }
            val runtimeFlow = flow.copy(maxLines = effectiveMaxLines.coerceAtLeast(1))
            val textLayout =
                TextLayouter(backend::measureText).layout(
                    text = text.resolveAs<String>(resolve),
                    width = width,
                    flow = runtimeFlow,
                    overflow = overflow,
                )

            backend.pushClip(x + ox, y + oy, width, height)
            textLayout.lines.forEachIndexed { index, line ->
                val textX =
                    when (alignment) {
                        TextAlignment.Start -> x
                        TextAlignment.Center -> x + (width - line.width) / 2
                        TextAlignment.End -> x + width - line.width
                    }
                backend.drawText(textX + ox, y + oy + index * flow.lineHeight, line.text, color.resolveAs<Color>(resolve))
            }
            backend.popClip()
        }
        is PrimitiveRenderOp.DrawTerminalSurface -> {
            backend.drawTerminalSurface(x + ox, y + oy, snapshot.resolveAs<Any>(resolve))
        }
        is PrimitiveRenderOp.PushClip -> {
            backend.pushClip(x + ox, y + oy, width, height)
        }
        PrimitiveRenderOp.PopClip -> {
            backend.popClip()
        }
        is PrimitiveRenderOp.DrawCodeEditor -> {
            backend.drawCodeEditor(
                x + ox,
                y + oy,
                width,
                height,
                viewModel.resolveAs(resolve),
                fontWidth,
                fontHeight,
            )
        }
    }
}

private fun GeneratedValueExpression.resolve(resolve: (GeneratedValueExpression) -> Any?): Any? =
    resolve(this)

private inline fun <reified T> GeneratedValueExpression.resolveAs(resolve: (GeneratedValueExpression) -> Any?): T =
    requireNotNull(resolve(this) as? T) {
        "Generated value expression $this did not resolve to ${T::class.simpleName}"
    }

internal fun GeneratedProgramDiagnostic.asText(): String =
    when (this) {
        is GeneratedProgramDiagnostic.RuntimeOnlyValue -> "$path: runtime-only value"
        is GeneratedProgramDiagnostic.RuntimeOnlyOperation -> "$path: $reason"
    }

private fun GeneratedValueExpression?.requireGenerated(): GeneratedValueExpression =
    requireNotNull(this) { "value should have been rejected by validateGeneratedProgram" }
