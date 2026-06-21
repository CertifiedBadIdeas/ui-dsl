package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.editor.EditorViewModel
import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.Position
import ru.lazyhat.kraftui.foundation.modifier.TextAlignment
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy
import ru.lazyhat.kraftui.text.TextFlow
import ru.lazyhat.kraftui.text.TextLayout
import ru.lazyhat.kraftui.text.TextLayouter

class CachedPrimitiveScreenRuntimeExecutor(
    program: PrimitiveScreenProgram,
    optimization: PrimitiveOptimizationOptions = PrimitiveOptimizationOptions(),
    cacheLimits: PrimitiveRuntimeCacheLimits = PrimitiveRuntimeCacheLimits(),
) {
    private val optimized: PrimitiveOptimizationResult = program.optimizePrimitive(optimization)
    private val renderInstructions: List<IndexedPrimitiveRenderInstruction> =
        optimized.program.renderInstructions.mapIndexed { index, instruction ->
            IndexedPrimitiveRenderInstruction(index, instruction)
        }
    private val inputRegions: List<CompiledPrimitiveInputRegion> =
        optimized.program.inputInstructions.mapNotNull { instruction ->
            when (instruction) {
                is PrimitiveInputInstruction.ClickRegion ->
                    CompiledPrimitiveInputRegion(
                        path = instruction.path,
                        visible = instruction.visible,
                        origin = instruction.origin,
                        x = instruction.x,
                        y = instruction.y,
                        width = instruction.width,
                        height = instruction.height,
                        action = instruction.action,
                    )
            }
        }

    private val staticRenderCache = HashMap<Int, List<CachedPrimitiveRenderCommand>>()
    private val textLayoutCache = BoundedTextLayoutCache(cacheLimits.maxTextLayouts)

    val optimizationReport: PrimitiveOptimizationReport
        get() = optimized.report

    val staticRenderCacheSize: Int
        get() = staticRenderCache.size

    val textLayoutCacheSize: Int
        get() = textLayoutCache.size

    val compiledInputRegionCount: Int
        get() = inputRegions.size

    fun clearCaches() {
        staticRenderCache.clear()
        textLayoutCache.clear()
    }

    fun render(
        backend: RenderBackend,
        resolve: (PrimitiveValueExpression) -> Any?,
        textMetricsKey: Any,
    ) {
        for (instruction in renderInstructions) {
            val source = instruction.source
            val visible = source.visible?.resolveAs<Boolean>(resolve) ?: true
            if (!visible) continue
            val origin = source.origin?.resolveAs<Position>(resolve) ?: Position.Zero
            if (source.canUseStaticCommandCache) {
                val cached =
                    staticRenderCache.getOrPut(instruction.index) {
                        val recorder = StaticPrimitiveRenderCommandRecorder(backend)
                        source.op.render(
                            backend = recorder,
                            resolve = resolve,
                            textMetricsKey = textMetricsKey,
                            textLayoutCache = textLayoutCache,
                            ox = origin.x,
                            oy = origin.y,
                        )
                        recorder.commands
                }
                cached.forEach { it.replay(backend) }
            } else {
                source.op.render(
                    backend = backend,
                    resolve = resolve,
                    textMetricsKey = textMetricsKey,
                    textLayoutCache = textLayoutCache,
                    ox = origin.x,
                    oy = origin.y,
                )
            }
        }
    }

    fun mouseClicked(
        resolve: (PrimitiveValueExpression) -> Any?,
        x: Int,
        y: Int,
    ): PrimitiveClickResult {
        for (region in inputRegions) {
            val visible = region.visible?.resolveAs<Boolean>(resolve) ?: true
            if (!visible) continue
            val origin = region.origin?.resolveAs<Position>(resolve) ?: Position.Zero
            val left = region.x + origin.x
            val top = region.y + origin.y
            if (x >= left && y >= top && x < left + region.width && y < top + region.height) {
                return region.action?.resolve(resolve).toPrimitiveClickResult()
            }
        }
        return PrimitiveClickResult.Ignored
    }

    private val PrimitiveRenderInstruction.canUseStaticCommandCache: Boolean
        get() = visible == null && origin == null && op.canUseStaticCommandCache

    private val PrimitiveRenderOp.canUseStaticCommandCache: Boolean
        get() =
            when (this) {
                is PrimitiveRenderOp.FillRect -> color is PrimitiveValueExpression.Constant
                is PrimitiveRenderOp.DrawTerminalSurface -> snapshot is PrimitiveValueExpression.Constant
                is PrimitiveRenderOp.PushClip -> true
                PrimitiveRenderOp.PopClip -> true
                is PrimitiveRenderOp.DrawText,
                is PrimitiveRenderOp.DrawCodeEditor,
                -> false
            }
}

data class PrimitiveRuntimeCacheLimits(
    val maxTextLayouts: Int = 256,
) {
    init {
        require(maxTextLayouts > 0) {
            "maxTextLayouts must be positive"
        }
    }
}

private data class IndexedPrimitiveRenderInstruction(
    val index: Int,
    val source: PrimitiveRenderInstruction,
)

private data class CompiledPrimitiveInputRegion(
    val path: String,
    val visible: PrimitiveValueExpression?,
    val origin: PrimitiveValueExpression?,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val action: PrimitiveValueExpression?,
)

private data class PrimitiveTextLayoutKey(
    val textMetricsKey: Any,
    val text: String,
    val width: Int,
    val height: Int,
    val flow: TextFlow,
    val overflow: TextOverflowPolicy,
)

private class BoundedTextLayoutCache(
    private val maxSize: Int,
) : LinkedHashMap<PrimitiveTextLayoutKey, TextLayout>(maxSize, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<PrimitiveTextLayoutKey, TextLayout>): Boolean =
        size > maxSize
}

private fun PrimitiveRenderOp.render(
    backend: RenderBackend,
    resolve: (PrimitiveValueExpression) -> Any?,
    textMetricsKey: Any,
    textLayoutCache: BoundedTextLayoutCache,
    ox: Int,
    oy: Int,
) {
    when (this) {
        is PrimitiveRenderOp.FillRect -> {
            backend.fillRect(x + ox, y + oy, width, height, color.resolveAs(resolve))
        }
        is PrimitiveRenderOp.DrawText -> {
            val textValue = text.resolveAs<String>(resolve)
            val layout =
                textLayoutCache.getOrPut(
                    PrimitiveTextLayoutKey(
                        textMetricsKey = textMetricsKey,
                        text = textValue,
                        width = width,
                        height = height,
                        flow = flow.effectiveForHeight(height),
                        overflow = overflow,
                    ),
                ) { layoutText(backend, textValue) }

            backend.pushClip(x + ox, y + oy, width, height)
            layout.lines.forEachIndexed { index, line ->
                val textX =
                    when (alignment) {
                        TextAlignment.Start -> x
                        TextAlignment.Center -> x + (width - line.width) / 2
                        TextAlignment.End -> x + width - line.width
                    }
                backend.drawText(textX + ox, y + oy + index * flow.lineHeight, line.text, color.resolveAs(resolve))
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

private fun PrimitiveRenderOp.DrawText.layoutText(
    backend: RenderBackend,
    textValue: String,
): TextLayout =
    TextLayouter(backend::measureText).layout(
        text = textValue,
        width = width,
        flow = flow.effectiveForHeight(height),
        overflow = overflow,
    )

private fun TextFlow.effectiveForHeight(height: Int): TextFlow {
    val visibleLineCount = (height / lineHeight).coerceAtLeast(0)
    val effectiveMaxLines =
        when {
            visibleLineCount == 0 -> 0
            maxLines == null -> visibleLineCount
            else -> minOf(maxLines, visibleLineCount)
        }
    return copy(maxLines = effectiveMaxLines.coerceAtLeast(1))
}

private fun PrimitiveValueExpression.resolve(resolve: (PrimitiveValueExpression) -> Any?): Any? =
    resolve(this)

private inline fun <reified T> PrimitiveValueExpression.resolveAs(resolve: (PrimitiveValueExpression) -> Any?): T =
    requireNotNull(resolve(this) as? T) {
        "Primitive value expression $this did not resolve to ${T::class.simpleName}"
    }

private fun Any?.toPrimitiveClickResult(): PrimitiveClickResult =
    this?.let(PrimitiveClickResult::Action) ?: PrimitiveClickResult.Consumed

private sealed interface CachedPrimitiveRenderCommand {
    fun replay(backend: RenderBackend)

    data class FillRect(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val color: Color,
    ) : CachedPrimitiveRenderCommand {
        override fun replay(backend: RenderBackend) {
            backend.fillRect(x, y, width, height, color)
        }
    }

    data class DrawTerminalSurface(
        val x: Int,
        val y: Int,
        val snapshot: Any,
    ) : CachedPrimitiveRenderCommand {
        override fun replay(backend: RenderBackend) {
            backend.drawTerminalSurface(x, y, snapshot)
        }
    }

    data class PushClip(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    ) : CachedPrimitiveRenderCommand {
        override fun replay(backend: RenderBackend) {
            backend.pushClip(x, y, width, height)
        }
    }

    data object PopClip : CachedPrimitiveRenderCommand {
        override fun replay(backend: RenderBackend) {
            backend.popClip()
        }
    }
}

private class StaticPrimitiveRenderCommandRecorder(
    private val metricsBackend: RenderBackend,
) : RenderBackend {
    val commands = ArrayList<CachedPrimitiveRenderCommand>()

    override fun fillRect(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Color,
    ) {
        commands += CachedPrimitiveRenderCommand.FillRect(x, y, width, height, color)
    }

    override fun drawText(
        x: Int,
        y: Int,
        text: String,
        color: Color,
    ) {
        error("Static primitive text commands are cached through the text layout cache")
    }

    override fun drawTerminalSurface(
        x: Int,
        y: Int,
        snapshot: Any,
    ) {
        commands += CachedPrimitiveRenderCommand.DrawTerminalSurface(x, y, snapshot)
    }

    override fun pushClip(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        commands += CachedPrimitiveRenderCommand.PushClip(x, y, width, height)
    }

    override fun popClip() {
        commands += CachedPrimitiveRenderCommand.PopClip
    }

    override fun drawCodeEditor(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        viewModel: EditorViewModel,
        fontWidth: Int,
        fontHeight: Int,
    ) {
        error("Code editor commands are backend-owned and are not cached as static primitives")
    }

    override fun measureText(text: String): Int = metricsBackend.measureText(text)
}
