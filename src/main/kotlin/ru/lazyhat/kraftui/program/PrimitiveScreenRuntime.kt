package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.Position
import ru.lazyhat.kraftui.foundation.modifier.TextAlignment
import ru.lazyhat.kraftui.text.TextLayouter

sealed interface PrimitiveClickResult {
    data object Ignored : PrimitiveClickResult

    data object Consumed : PrimitiveClickResult

    data class Action(
        val value: Any?,
    ) : PrimitiveClickResult
}

fun PrimitiveScreenProgram.render(
    backend: RenderBackend,
    resolve: (PrimitiveValueExpression) -> Any?,
) {
    renderInstructions.forEach { instruction ->
        val visible = instruction.visible?.resolveAs<Boolean>(resolve) ?: true
        if (!visible) return@forEach
        val origin = instruction.origin?.resolveAs<Position>(resolve) ?: Position.Zero
        instruction.op.render(backend, resolve, origin.x, origin.y)
    }
}

fun PrimitiveScreenProgram.mouseClicked(
    resolve: (PrimitiveValueExpression) -> Any?,
    x: Int,
    y: Int,
): PrimitiveClickResult {
    inputInstructions.forEach { instruction ->
        when (instruction) {
            is PrimitiveInputInstruction.ClickRegion -> {
                val visible = instruction.visible?.resolveAs<Boolean>(resolve) ?: true
                if (!visible) return@forEach
                val origin = instruction.origin?.resolveAs<Position>(resolve) ?: Position.Zero
                val left = instruction.x + origin.x
                val top = instruction.y + origin.y
                if (x >= left && y >= top && x < left + instruction.width && y < top + instruction.height) {
                    return instruction.action?.resolve(resolve)?.let(PrimitiveClickResult::Action)
                        ?: PrimitiveClickResult.Consumed
                }
            }
        }
    }
    return PrimitiveClickResult.Ignored
}

private fun PrimitiveRenderOp.render(
    backend: RenderBackend,
    resolve: (PrimitiveValueExpression) -> Any?,
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

private fun PrimitiveValueExpression.resolve(resolve: (PrimitiveValueExpression) -> Any?): Any? =
    resolve(this)

private inline fun <reified T> PrimitiveValueExpression.resolveAs(resolve: (PrimitiveValueExpression) -> Any?): T =
    requireNotNull(resolve(this) as? T) {
        "Primitive value expression $this did not resolve to ${T::class.simpleName}"
    }
