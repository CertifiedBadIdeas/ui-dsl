package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.CanvasScope
import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.TickContext
import ru.lazyhat.kraftui.foundation.modifier.Position
import ru.lazyhat.kraftui.foundation.modifier.TextAlignment
import ru.lazyhat.kraftui.text.TextLayouter

/**
 * Runtime executor for [OptimizedScreenProgram].
 *
 * The current role of this executor is behavioral parity with
 * [ScreenRuntimeExecutor] while walking optimized render and hit lists. It is
 * the stable target for later cached layers and generated executors.
 */
class OptimizedScreenRuntimeExecutor<Action>(
    private val program: OptimizedScreenProgram<Action>,
) {
    private val source: ScreenProgram<Action> = program.source

    var focusedNodeId: String? = null
        private set

    val isFocused: Boolean
        get() = focusedNodeId != null

    var activeTooltip: String? = null
        private set

    private var tickCounter: Int = 0
    private var activeDragRegion: HitRegion<Action>? = null

    fun restoreFocus(nodeId: String?) {
        focusedNodeId =
            if (nodeId != null && source.focusNodes.any { it.nodeId == nodeId }) {
                nodeId
            } else {
                null
            }
    }

    fun firstFocusableNodeId(): String? =
        source.focusNodes
            .filter { it.tabOrder >= 0 }
            .minByOrNull { it.tabOrder }
            ?.nodeId

    fun updateMouse(
        mouseX: Int,
        mouseY: Int,
    ) {
        for (region in source.hoverRegions) {
            val frame = source.frames[region.frameIndex]
            if (frame.visible != null && !frame.visible.value) {
                region.state.isHovered = false
                continue
            }
            val origin = frame.origin?.value ?: Position.Zero
            val rx = region.x + origin.x
            val ry = region.y + origin.y
            region.state.isHovered =
                mouseX >= rx && mouseY >= ry && mouseX < rx + region.width && mouseY < ry + region.height
        }

        activeTooltip = null
        for (region in source.tooltipRegions) {
            val frame = source.frames[region.frameIndex]
            if (frame.visible != null && !frame.visible.value) continue
            val origin = frame.origin?.value ?: Position.Zero
            val rx = region.x + origin.x
            val ry = region.y + origin.y
            if (mouseX >= rx && mouseY >= ry && mouseX < rx + region.width && mouseY < ry + region.height) {
                activeTooltip = region.text.value
                break
            }
        }
    }

    fun render(backend: RenderBackend) {
        TickContext.current = ++tickCounter
        for (frame in program.frames) {
            val sourceFrame = frame.source
            if (sourceFrame.visible != null && !sourceFrame.visible.value) continue
            val origin = sourceFrame.origin?.value ?: Position.Zero
            val ox = origin.x
            val oy = origin.y
            for (optimizedOp in frame.renderOps) {
                renderOp(optimizedOp.source, ox, oy, backend)
            }
        }
    }

    private fun renderOp(
        op: RenderOp,
        ox: Int,
        oy: Int,
        backend: RenderBackend,
    ) {
        when (op) {
            is RenderOp.FillRect -> {
                backend.fillRect(op.x + ox, op.y + oy, op.width, op.height, op.color.value)
            }

            is RenderOp.DrawText -> {
                val visibleLineCount = (op.height / op.flow.lineHeight).coerceAtLeast(0)
                val effectiveMaxLines =
                    when {
                        visibleLineCount == 0 -> 0
                        op.flow.maxLines == null -> visibleLineCount
                        else -> minOf(op.flow.maxLines, visibleLineCount)
                    }
                val runtimeFlow = op.flow.copy(maxLines = effectiveMaxLines.coerceAtLeast(1))
                val textLayout =
                    TextLayouter(backend::measureText).layout(
                        text = op.value.value,
                        width = op.width,
                        flow = runtimeFlow,
                        overflow = op.overflow,
                    )

                backend.pushClip(op.x + ox, op.y + oy, op.width, op.height)
                textLayout.lines.forEachIndexed { index, line ->
                    val textX =
                        when (op.alignment) {
                            TextAlignment.Start -> op.x
                            TextAlignment.Center -> op.x + (op.width - line.width) / 2
                            TextAlignment.End -> op.x + op.width - line.width
                        }
                    backend.drawText(textX + ox, op.y + oy + index * op.flow.lineHeight, line.text, op.color.value)
                }
                backend.popClip()
            }

            is RenderOp.DrawTerminalSurface -> {
                backend.drawTerminalSurface(op.x + ox, op.y + oy, op.snapshot.value)
            }

            is RenderOp.DrawCanvas -> {
                canvasScope.bind(backend, op.x + ox, op.y + oy, op.width, op.height)
                op.onDraw.invoke(canvasScope)
            }

            is RenderOp.PushClip -> {
                backend.pushClip(op.x + ox, op.y + oy, op.width, op.height)
            }

            RenderOp.PopClip -> {
                backend.popClip()
            }

            is RenderOp.DrawCodeEditor -> {
                backend.drawCodeEditor(
                    op.x + ox,
                    op.y + oy,
                    op.width,
                    op.height,
                    op.viewModel.value,
                    op.fontWidth,
                    op.fontHeight,
                )
            }
        }
    }

    fun mouseClicked(
        x: Int,
        y: Int,
    ): UiInputResult<Action> {
        for (optimizedRegion in program.hitRegions) {
            val region = optimizedRegion.source
            val frame = source.frames[region.frameIndex]
            if (frame.visible != null && !frame.visible.value) continue
            val origin = frame.origin?.value ?: Position.Zero
            val rx = region.x + origin.x
            val ry = region.y + origin.y
            if (x >= rx && y >= ry && x < rx + region.width && y < ry + region.height) {
                val clip = region.clip
                if (clip != null) {
                    val clipFrame = source.frames[clip.frameIndex]
                    if (clipFrame.visible != null && !clipFrame.visible.value) continue
                    val clipOrigin = clipFrame.origin?.value ?: Position.Zero
                    val cx = clip.x + clipOrigin.x
                    val cy = clip.y + clipOrigin.y
                    if (x < cx || y < cy || x >= cx + clip.width || y >= cy + clip.height) continue
                }
                source.focusNodes
                    .firstOrNull { it.nodeId == region.nodeId }
                    ?.let { focusedNodeId = it.nodeId }
                region.onClickAt?.invoke(x - rx, y - ry)
                if (region.onDragStart != null || region.onDrag != null || region.onDragEnd != null) {
                    activeDragRegion = region
                    region.onDragStart?.invoke(x, y)
                }
                val action = region.action?.value
                return if (action != null) UiInputResult.Action(action) else UiInputResult.Consumed
            }
        }

        for (focus in source.focusNodes) {
            val frame = source.frames[focus.frameIndex]
            if (frame.visible != null && !frame.visible.value) continue
            val origin = frame.origin?.value ?: Position.Zero
            val fx = focus.x + origin.x
            val fy = focus.y + origin.y
            if (x >= fx && y >= fy && x < fx + focus.width && y < fy + focus.height) {
                focusedNodeId = focus.nodeId
                return UiInputResult.Consumed
            }
        }
        focusedNodeId = null
        return UiInputResult.Ignored
    }

    fun mouseDragged(
        x: Int,
        y: Int,
    ): Boolean {
        val region = activeDragRegion ?: return false
        region.onDrag?.invoke(x, y)
        return true
    }

    fun mouseReleased(
        x: Int,
        y: Int,
    ): Boolean {
        val region = activeDragRegion ?: return false
        region.onDragEnd?.invoke(x, y)
        activeDragRegion = null
        return true
    }

    fun mouseScrolled(
        x: Int,
        y: Int,
        deltaY: Double,
    ): Boolean {
        for (region in source.scrollRegions) {
            val frame = source.frames[region.frameIndex]
            if (frame.visible != null && !frame.visible.value) continue
            val origin = frame.origin?.value ?: Position.Zero
            val rx = region.x + origin.x
            val ry = region.y + origin.y
            if (x >= rx && y >= ry && x < rx + region.width && y < ry + region.height) {
                if (region.onScroll(deltaY)) return true
            }
        }
        return false
    }

    fun keyPressed(
        keyCode: Int,
        modifiers: Int = 0,
    ): Boolean {
        val handler = focusedHandler() ?: return false
        if (handler.onKeyPressed.invoke(keyCode, modifiers)) return true
        if (keyCode == KEY_TAB) {
            return cycleFocus(forward = (modifiers and MOD_SHIFT) == 0)
        }
        return false
    }

    fun keyReleased(keyCode: Int): Boolean {
        val handler = focusedHandler() ?: return false
        return handler.onKeyReleased.invoke(keyCode)
    }

    fun charTyped(ch: Char): Boolean {
        val handler = focusedHandler() ?: return false
        return handler.onCharTyped.invoke(ch)
    }

    private fun focusedHandler(): FocusHandler? {
        val id = focusedNodeId ?: return null
        return source.focusNodes.firstOrNull { it.nodeId == id }?.handler
    }

    private fun cycleFocus(forward: Boolean): Boolean {
        val tabbable =
            source.focusNodes
                .asSequence()
                .filter { it.tabOrder >= 0 }
                .filter {
                    val frame = source.frames[it.frameIndex]
                    frame.visible?.value ?: true
                }.toList()
                .let { list ->
                    val sorted = list.sortedBy { it.tabOrder }
                    if (forward) sorted else sorted.asReversed()
                }
        if (tabbable.isEmpty()) return false
        val currentIndex = tabbable.indexOfFirst { it.nodeId == focusedNodeId }
        val next = tabbable[(currentIndex + 1).mod(tabbable.size)]
        if (next.nodeId == focusedNodeId) return false
        focusedNodeId = next.nodeId
        return true
    }

    private val canvasScope = OffsetCanvasScope()

    private class OffsetCanvasScope : CanvasScope {
        private var backend: RenderBackend? = null
        private var originX: Int = 0
        private var originY: Int = 0
        override var width: Int = 0
            private set
        override var height: Int = 0
            private set

        fun bind(
            backend: RenderBackend,
            originX: Int,
            originY: Int,
            width: Int,
            height: Int,
        ) {
            this.backend = backend
            this.originX = originX
            this.originY = originY
            this.width = width
            this.height = height
        }

        override fun fillRect(
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            color: Color,
        ) {
            backend?.fillRect(originX + x, originY + y, width, height, color)
        }

        override fun measureText(text: String): Int = backend?.measureText(text) ?: 0
    }

    private companion object {
        const val KEY_TAB = 258
        const val MOD_SHIFT = 0x0001
    }
}
