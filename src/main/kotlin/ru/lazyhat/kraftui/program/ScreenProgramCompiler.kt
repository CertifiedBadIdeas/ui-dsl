package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.UiElement
import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.value
import ru.lazyhat.kraftui.foundation.TextureRegion
import ru.lazyhat.kraftui.foundation.TextureScaling
import ru.lazyhat.kraftui.foundation.TextureStyle
import ru.lazyhat.kraftui.foundation.Value
import ru.lazyhat.kraftui.foundation.andValues
import ru.lazyhat.kraftui.foundation.modifier.TextAlignment
import ru.lazyhat.kraftui.foundation.modifier.Position
import ru.lazyhat.kraftui.foundation.modifier.findBackground
import ru.lazyhat.kraftui.foundation.modifier.findDraggable
import ru.lazyhat.kraftui.foundation.modifier.findFocusable
import ru.lazyhat.kraftui.foundation.modifier.findHoverable
import ru.lazyhat.kraftui.foundation.modifier.findSize
import ru.lazyhat.kraftui.foundation.modifier.findTextAlignment
import ru.lazyhat.kraftui.foundation.modifier.findTextFlow
import ru.lazyhat.kraftui.foundation.modifier.findTextOverflow
import ru.lazyhat.kraftui.foundation.modifier.findTexture
import ru.lazyhat.kraftui.foundation.modifier.findTooltip
import ru.lazyhat.kraftui.foundation.modifier.findZIndex
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy
import ru.lazyhat.kraftui.layout.LayoutNode
import ru.lazyhat.kraftui.layout.UiLayoutResolver
import ru.lazyhat.kraftui.text.TextFlow
import ru.lazyhat.kraftui.text.TextLayouter

/**
 * Compiles a [UiElement] tree into a [ScreenProgram] with:
 *
 *  - a flat list of [RenderFrame]s, each with baked relative coordinates
 *    and optional dynamic `origin`/`visible` expressions;
 *  - a flat list of [HitRegion]s, sorted by descending z-index, with typed
 *    actions baked into click regions;
 *  - at most one focused node plus its `onKey` handler.
 *
 * The result is designed to be rebuilt only on structural changes (screen
 * resize, different content). Per-frame rendering and hit-testing avoid
 * allocations and map lookups entirely.
 */
class ScreenProgramCompiler(
    private val fontMetrics: FontMetrics? = null,
) {
    fun <Action> compile(
        root: UiElement<Action>,
        rootX: Int = 0,
        rootY: Int = 0,
        rootWidth: Int = 0,
        rootHeight: Int = 0,
    ): ScreenProgram<Action> {
        val frames = mutableListOf<MutableList<RenderOp>>()
        val descriptors = mutableListOf<FrameDescriptor>()
        val hitRegions = mutableListOf<HitRegion<Action>>()
        val hoverRegions = mutableListOf<HoverRegion>()
        val tooltipRegions = mutableListOf<TooltipRegion>()
        val focusNodes = mutableListOf<FocusNode>()
        val scrollRegions = mutableListOf<ScrollRegion>()
        val diagnostics = mutableListOf<ScreenProgramDiagnostic>()

        val rootSize = root.modifier.findSize()?.size
        val effectiveRootWidth = if (rootWidth > 0) rootWidth else rootSize?.width ?: 0
        val effectiveRootHeight = if (rootHeight > 0) rootHeight else rootSize?.height ?: 0

        val rootLayoutResult =
            UiLayoutResolver(effectiveRootWidth, effectiveRootHeight, fontMetrics)
                .resolveWithDiagnostics(root, rootNodeId = "root", rootX = rootX, rootY = rootY)
        val rootLayout = rootLayoutResult.nodes
        diagnostics += rootLayoutResult.diagnostics.map { ScreenProgramDiagnostic.LayoutViolation(it) }

        val rootOps = mutableListOf<RenderOp>()
        frames += rootOps
        descriptors += FrameDescriptor(origin = null, visible = null)
        lower(
            element = root,
            nodeId = "root",
            layout = rootLayout,
            ops = rootOps,
            hitRegions = hitRegions,
            hoverRegions = hoverRegions,
            tooltipRegions = tooltipRegions,
            frames = frames,
            descriptors = descriptors,
            focusNodes = focusNodes,
            scrollRegions = scrollRegions,
            diagnostics = diagnostics,
            frameIndex = 0,
        )

        return ScreenProgram(
            frames =
                frames.mapIndexed { index, ops ->
                    val descriptor = descriptors[index]
                    RenderFrame(origin = descriptor.origin, visible = descriptor.visible, ops = ops.toList())
                },
            hitRegions = hitRegions.sortedByDescending { it.zIndex },
            hoverRegions = hoverRegions.toList(),
            tooltipRegions = tooltipRegions.toList(),
            focusNodes = focusNodes.toList(),
            scrollRegions = scrollRegions.toList(),
            diagnostics = diagnostics.toList(),
        )
    }

    private fun <Action> lower(
        element: UiElement<Action>,
        nodeId: String,
        layout: Map<String, LayoutNode>,
        ops: MutableList<RenderOp>,
        hitRegions: MutableList<HitRegion<Action>>,
        hoverRegions: MutableList<HoverRegion>,
        tooltipRegions: MutableList<TooltipRegion>,
        frames: MutableList<MutableList<RenderOp>>,
        descriptors: MutableList<FrameDescriptor>,
        focusNodes: MutableList<FocusNode>,
        scrollRegions: MutableList<ScrollRegion>,
        diagnostics: MutableList<ScreenProgramDiagnostic>,
        frameIndex: Int,
        visible: Value<Boolean>? = null,
    ) {
        if (element is UiElement.Overlay) {
            lowerOverlay(
                element,
                nodeId,
                layout,
                hitRegions,
                hoverRegions,
                tooltipRegions,
                frames,
                descriptors,
                focusNodes,
                scrollRegions,
                diagnostics,
                visible,
            )
            return
        }

        val node = layout[nodeId] ?: return

        // Modifier-based focus claim works on any element (Box, Text, Canvas, ...).
        element.modifier.findFocusable()?.let { f ->
            focusNodes +=
                FocusNode(
                    nodeId = f.id,
                    frameIndex = frameIndex,
                    visible = visible,
                    x = node.x,
                    y = node.y,
                    width = node.width,
                    height = node.height,
                    tabOrder = f.tabOrder,
                    handler =
                        FocusHandler(
                            onKeyPressed = { key, _ -> f.onKeyPressed(key) },
                            onKeyReleased = f.onKeyReleased,
                            onCharTyped = f.onCharTyped,
                        ),
                )
        }
        element.modifier.findHoverable()?.let { hoverable ->
            hoverRegions +=
                HoverRegion(
                    nodeId = nodeId,
                    frameIndex = frameIndex,
                    visible = visible,
                    x = node.x,
                    y = node.y,
                    width = node.width,
                    height = node.height,
                    state = hoverable.state,
                )
        }
        element.modifier.findTooltip()?.let { tooltip ->
            tooltipRegions +=
                TooltipRegion(
                    nodeId = nodeId,
                    frameIndex = frameIndex,
                    visible = visible,
                    x = node.x,
                    y = node.y,
                    width = node.width,
                    height = node.height,
                    text = tooltip.text,
                )
        }
        when (element) {
            is UiElement.Box -> {
                element.modifier.findBackground()?.let { bg ->
                    ops += RenderOp.FillRect(node.x, node.y, node.width, node.height, bg.color)
                }
                element.modifier.findTexture()?.let { texture ->
                    ops += texture.texture.toRenderOps(nodeId, node.x, node.y, node.width, node.height)
                }
                val draggable = element.modifier.findDraggable()
                if (element.clickAction != null || draggable != null) {
                    hitRegions +=
                        HitRegion(
                            nodeId = nodeId,
                            frameIndex = frameIndex,
                            visible = visible,
                            x = node.x,
                            y = node.y,
                            width = node.width,
                            height = node.height,
                            zIndex = element.modifier.findZIndex()?.zIndex ?: 0,
                            action = element.clickAction,
                            onDragStart = draggable?.onDragStart,
                            onDrag = draggable?.onDrag,
                            onDragEnd = draggable?.onDragEnd,
                        )
                }
                element.children.forEachIndexed { index, child ->
                    lower(
                        child,
                        "$nodeId-$index",
                        layout,
                        ops,
                        hitRegions,
                        hoverRegions,
                        tooltipRegions,
                        frames,
                        descriptors,
                        focusNodes,
                        scrollRegions,
                        diagnostics,
                        frameIndex,
                        visible,
                    )
                }
            }

            is UiElement.Row -> {
                element.children.forEachIndexed { index, child ->
                    lower(
                        child,
                        "$nodeId-$index",
                        layout,
                        ops,
                        hitRegions,
                        hoverRegions,
                        tooltipRegions,
                        frames,
                        descriptors,
                        focusNodes,
                        scrollRegions,
                        diagnostics,
                        frameIndex,
                        visible,
                    )
                }
            }

            is UiElement.Column -> {
                element.children.forEachIndexed { index, child ->
                    lower(
                        child,
                        "$nodeId-$index",
                        layout,
                        ops,
                        hitRegions,
                        hoverRegions,
                        tooltipRegions,
                        frames,
                        descriptors,
                        focusNodes,
                        scrollRegions,
                        diagnostics,
                        frameIndex,
                        visible,
                    )
                }
            }

            is UiElement.Grid -> {
                element.cells.forEachIndexed { cellIndex, cell ->
                    cell.children.forEachIndexed { childIndex, child ->
                        lower(
                            child,
                            "$nodeId-$cellIndex-$childIndex",
                            layout,
                            ops,
                            hitRegions,
                            hoverRegions,
                            tooltipRegions,
                            frames,
                            descriptors,
                            focusNodes,
                            scrollRegions,
                            diagnostics,
                            frameIndex,
                            visible,
                        )
                    }
                }
            }

            is UiElement.Text -> {
                val overflow = element.modifier.findTextOverflow()?.policy ?: TextOverflowPolicy.FailInValidation
                val flowModifier = element.modifier.findTextFlow()
                val flow =
                    TextFlow(
                        wrap = flowModifier?.wrap ?: ru.lazyhat.kraftui.foundation.modifier.TextWrapPolicy.NoWrap,
                        maxLines = flowModifier?.maxLines,
                        lineHeight = flowModifier?.lineHeight ?: DEFAULT_TEXT_HEIGHT,
                    )
                val metrics = fontMetrics
                val text = metrics?.let { element.text.value }
                if (overflow == TextOverflowPolicy.FailInValidation && metrics != null && text != null) {
                    val textLayout =
                        TextLayouter(metrics::width).layout(
                            text = text,
                            width = node.width,
                            flow = flow,
                            overflow = overflow,
                        )
                    textLayout.lines.firstOrNull { it.width > node.width }?.let { line ->
                        diagnostics +=
                            ScreenProgramDiagnostic.TextWouldOverflow(
                                nodeId = nodeId,
                                text = line.text,
                                width = node.width,
                                textWidth = line.width,
                                policy = overflow,
                            )
                    }
                    if (textLayout.requiredHeight > node.height) {
                        diagnostics +=
                            ScreenProgramDiagnostic.TextHeightWouldOverflow(
                                nodeId = nodeId,
                                text = text,
                                height = node.height,
                                textHeight = textLayout.requiredHeight,
                                lineCount = textLayout.sourceLineCount,
                                policy = overflow,
                            )
                    }
                }
                ops +=
                    RenderOp.DrawText(
                        x = node.x,
                        y = node.y,
                        width = node.width,
                        height = node.height,
                        value = element.text,
                        color = element.color,
                        alignment = element.modifier.findTextAlignment()?.alignment ?: TextAlignment.Start,
                        overflow = overflow,
                        flow = flow,
                    )
            }

            is UiElement.Canvas -> {
                ops += RenderOp.DrawCanvas(node.x, node.y, node.width, node.height, element.onDraw)
            }

            is UiElement.TerminalSurface -> {
                ops += RenderOp.DrawTerminalSurface(node.x, node.y, node.width, node.height, element.snapshot)
                // Auto-claim focus only if the element doesn't already carry an explicit
                // `Modifier.focusable(...)` (which would have been collected above).
                if (element.modifier.findFocusable() == null) {
                    focusNodes +=
                        FocusNode(
                            nodeId = nodeId,
                            frameIndex = frameIndex,
                            visible = visible,
                            x = node.x,
                            y = node.y,
                            width = node.width,
                            height = node.height,
                            tabOrder = 0,
                            handler =
                                FocusHandler(
                                    onKeyPressed = { key, _ -> element.onKey(key) },
                                    onKeyReleased = element.onKeyReleased,
                                    onCharTyped = element.onCharTyped,
                                ),
                        )
                }
            }

            is UiElement.CodeEditor -> {
                lowerCodeEditor(
                    element,
                    nodeId,
                    node,
                    frameIndex,
                    ops,
                    hitRegions,
                    focusNodes,
                    scrollRegions,
                    visible,
                )
            }

            is UiElement.IfNode -> {
                val nestedVisible = andValues(visible, element.condition)
                ops += RenderOp.PushVisibility(element.condition)
                element.children.forEachIndexed { index, child ->
                    lower(
                        child,
                        "$nodeId-$index",
                        layout,
                        ops,
                        hitRegions,
                        hoverRegions,
                        tooltipRegions,
                        frames,
                        descriptors,
                        focusNodes,
                        scrollRegions,
                        diagnostics,
                        frameIndex,
                        nestedVisible,
                    )
                }
                ops += RenderOp.PopVisibility
            }

            is UiElement.Overlay -> {
                error("unreachable: Overlay handled before the layout-node guard")
            }

            is UiElement.ScrollArea -> {
                lowerScrollArea(
                    element,
                    nodeId,
                    node,
                    ops,
                    hitRegions,
                    hoverRegions,
                    tooltipRegions,
                    frames,
                    descriptors,
                    focusNodes,
                    scrollRegions,
                    diagnostics,
                    parentFrameIndex = frameIndex,
                    visible = visible,
                )
            }
        }
    }

    private fun <Action> lowerOverlay(
        element: UiElement.Overlay<Action>,
        nodeId: String,
        parentLayout: Map<String, LayoutNode>,
        hitRegions: MutableList<HitRegion<Action>>,
        hoverRegions: MutableList<HoverRegion>,
        tooltipRegions: MutableList<TooltipRegion>,
        frames: MutableList<MutableList<RenderOp>>,
        descriptors: MutableList<FrameDescriptor>,
        focusNodes: MutableList<FocusNode>,
        scrollRegions: MutableList<ScrollRegion>,
        diagnostics: MutableList<ScreenProgramDiagnostic>,
        visible: Value<Boolean>?,
    ) {
        val size = element.modifier.findSize()?.size
        val overlayWidth = size?.width ?: parentLayout["root"]?.width ?: 0
        val overlayHeight = size?.height ?: parentLayout["root"]?.height ?: 0

        val subLayoutResult =
            UiLayoutResolver(overlayWidth, overlayHeight, fontMetrics)
                .resolveWithDiagnostics(element, rootNodeId = nodeId, rootX = 0, rootY = 0)
        val subLayout = subLayoutResult.nodes
        diagnostics += subLayoutResult.diagnostics.map { ScreenProgramDiagnostic.LayoutViolation(it) }

        val subOps = mutableListOf<RenderOp>()
        val subFrameIndex = frames.size
        frames += subOps
        descriptors += FrameDescriptor(origin = element.anchor, visible = andValues(visible, element.visible))
        element.children.forEachIndexed { index, child ->
            lower(
                child,
                "$nodeId-$index",
                subLayout,
                subOps,
                hitRegions,
                hoverRegions,
                tooltipRegions,
                frames,
                descriptors,
                focusNodes,
                scrollRegions,
                diagnostics,
                subFrameIndex,
                visible = null,
            )
        }
    }

    private data class FrameDescriptor(
        val origin: Value<Position>?,
        val visible: Value<Boolean>?,
    )

    private fun <Action> lowerScrollArea(
        element: UiElement.ScrollArea<Action>,
        nodeId: String,
        outer: LayoutNode,
        parentOps: MutableList<RenderOp>,
        hitRegions: MutableList<HitRegion<Action>>,
        hoverRegions: MutableList<HoverRegion>,
        tooltipRegions: MutableList<TooltipRegion>,
        frames: MutableList<MutableList<RenderOp>>,
        descriptors: MutableList<FrameDescriptor>,
        focusNodes: MutableList<FocusNode>,
        scrollRegions: MutableList<ScrollRegion>,
        diagnostics: MutableList<ScreenProgramDiagnostic>,
        parentFrameIndex: Int,
        visible: Value<Boolean>?,
    ) {
        // Optional background fill on the viewport itself.
        element.modifier.findBackground()?.let { bg ->
            parentOps += RenderOp.FillRect(outer.x, outer.y, outer.width, outer.height, bg.color)
        }
        element.modifier.findTexture()?.let { texture ->
            parentOps += texture.texture.toRenderOps(nodeId, outer.x, outer.y, outer.width, outer.height)
        }

        // Push the viewport clip in the *parent* frame so the sub-frame's
        // translated draw calls are masked to the visible rectangle.
        parentOps += RenderOp.PushClip(outer.x, outer.y, outer.width, outer.height)

        // Resolve children's static layout in their own coordinate space (origin 0,0).
        val subLayoutResult =
            UiLayoutResolver(outer.width, outer.height, fontMetrics)
                .resolveWithDiagnostics(element, rootNodeId = nodeId, rootX = 0, rootY = 0)
        val subLayout = subLayoutResult.nodes
        diagnostics += subLayoutResult.diagnostics.map { ScreenProgramDiagnostic.LayoutViolation(it) }

        // Sub-frame whose dynamic origin is (outer.x - scrollX, outer.y - scrollY).
        val subOps = mutableListOf<RenderOp>()
        val subFrameIndex = frames.size
        frames += subOps
        val scrollX = element.scrollX
        val scrollY = element.scrollY
        val originX = outer.x
        val originY = outer.y
        val origin =
            ru.lazyhat.kraftui.foundation.value {
                Position(originX - scrollX.value, originY - scrollY.value)
            }
        descriptors += FrameDescriptor(origin = origin, visible = visible)

        // Hit-region clip stamped onto every region collected inside the sub-tree.
        val hitClip =
            HitClip(
                frameIndex = parentFrameIndex,
                x = outer.x,
                y = outer.y,
                width = outer.width,
                height = outer.height,
            )

        val hitRegionsBefore = hitRegions.size
        element.children.forEachIndexed { index, child ->
            lower(
                child,
                "$nodeId-$index",
                subLayout,
                subOps,
                hitRegions,
                hoverRegions,
                tooltipRegions,
                frames,
                descriptors,
                focusNodes,
                scrollRegions,
                diagnostics,
                subFrameIndex,
                visible = null,
            )
        }
        for (i in hitRegionsBefore until hitRegions.size) {
            hitRegions[i] = hitRegions[i].copy(clip = hitClip)
        }

        // Register the scroll handler for wheel events landing on the viewport.
        scrollRegions +=
            ScrollRegion(
                nodeId = nodeId,
                frameIndex = parentFrameIndex,
                visible = visible,
                x = outer.x,
                y = outer.y,
                width = outer.width,
                height = outer.height,
                onScroll = element.onScroll,
            )

        parentOps += RenderOp.PopClip
    }

    private fun <Action> lowerCodeEditor(
        element: UiElement.CodeEditor,
        nodeId: String,
        node: LayoutNode,
        frameIndex: Int,
        ops: MutableList<RenderOp>,
        hitRegions: MutableList<HitRegion<Action>>,
        focusNodes: MutableList<FocusNode>,
        scrollRegions: MutableList<ScrollRegion>,
        visible: Value<Boolean>?,
    ) {
        ops +=
            RenderOp.DrawCodeEditor(
                x = node.x,
                y = node.y,
                width = node.width,
                height = node.height,
                viewModel = element.viewModel,
                fontWidth = element.fontWidth,
                fontHeight = element.fontHeight,
            )

        val viewModel = element.viewModel
        val fontWidth = element.fontWidth
        val fontHeight = element.fontHeight
        val width = node.width
        val height = node.height

        focusNodes +=
            FocusNode(
                nodeId = nodeId,
                frameIndex = frameIndex,
                visible = visible,
                x = node.x,
                y = node.y,
                width = width,
                height = height,
                tabOrder = element.modifier.findFocusable()?.tabOrder ?: 0,
                handler =
                    FocusHandler(
                        onKeyPressed = { key, mods ->
                            viewModel.value.onKeyPressed(key, modifiers = mods, visibleLines = height / fontHeight)
                        },
                        onCharTyped = { ch ->
                            viewModel.value.onCharTyped(ch, visibleLines = height / fontHeight)
                        },
                    ),
            )

        // Hit region: a click translates pixel coordinates to (line, column),
        // forwards to the view-model and lets the executor focus this node.
        hitRegions +=
            HitRegion(
                nodeId = nodeId,
                frameIndex = frameIndex,
                visible = visible,
                x = node.x,
                y = node.y,
                width = width,
                height = height,
                zIndex = element.modifier.findZIndex()?.zIndex ?: 0,
                onClickAt = { localX, localY ->
                    val vm = viewModel.value
                    val gutter =
                        ru.lazyhat.kraftui.editor.CodeEditorMetrics
                            .gutterPixelWidth(
                                ru.lazyhat.kraftui.editor.CodeEditorMetrics
                                    .lineCount(vm.text),
                                fontWidth,
                            )
                    val line = vm.scrollLine + (localY / fontHeight)
                    val column = pixelToColumnAtLine(vm.text, line, (localX - gutter).coerceAtLeast(0), fontWidth)
                    vm.onMouseClickAt(line, column)
                },
            )

        scrollRegions +=
            ScrollRegion(
                nodeId = nodeId,
                frameIndex = frameIndex,
                visible = visible,
                x = node.x,
                y = node.y,
                width = width,
                height = height,
                onScroll = { delta ->
                    val lines =
                        when {
                            delta > 0.0 -> -1
                            delta < 0.0 -> 1
                            else -> 0
                        }
                    if (lines != 0) viewModel.value.onScroll(lines)
                    lines != 0
                },
            )
    }

    /**
     * Find the column on [line] of [text] whose rendered start-position is
     * closest to [localX] pixels from the line's left edge. Uses the
     * compiler's [fontMetrics] when available so variable-width MC fonts
     * map clicks accurately; falls back to a fixed-width division (using
     * [fontWidth]) if no metrics are available.
     */
    private fun pixelToColumnAtLine(
        text: String,
        line: Int,
        localX: Int,
        fontWidth: Int,
    ): Int {
        val lineText =
            text
                .split('\n')
                .getOrNull(line)
                .orEmpty()
        val metrics = fontMetrics ?: return localX / fontWidth.coerceAtLeast(1)
        if (lineText.isEmpty()) return 0
        // Walk character by character; pick the column where the click
        // sits past the *midpoint* of the glyph at column n-1.
        var prevWidth = 0
        for (col in 1..lineText.length) {
            val w = metrics.width(lineText.substring(0, col))
            val midpoint = (prevWidth + w) / 2
            if (localX < midpoint) return col - 1
            prevWidth = w
        }
        return lineText.length
    }

    private companion object {
        const val DEFAULT_TEXT_HEIGHT = 9
    }
}

private fun TextureStyle.toRenderOps(
    nodeId: String,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
): List<RenderOp> =
    when (this) {
        is TextureStyle.Layered ->
            layers.flatMap { layer -> layer.toRenderOps(nodeId, x, y, width, height) }
        is TextureStyle.Resource ->
            error("$nodeId: raw texture resources need an explicit region before rendering")
        is TextureStyle.Region ->
            listOf(
                RenderOp.DrawTextureRegion(
                    x = x,
                    y = y,
                    width = width,
                    height = height,
                    region = region.toPrimitiveRegion(),
                    scaling = scaling.toPrimitiveScaling(),
                ),
            )
        is TextureStyle.Checkerboard -> checkerboardOps(x, y, width, height)
        is TextureStyle.BrassFrame -> brassFrameOps(x, y, width, height)
        is TextureStyle.NineSlice -> {
            require(width >= border.left + border.right) {
                "$nodeId: nine-slice target width $width is smaller than horizontal borders ${border.left + border.right}"
            }
            require(height >= border.top + border.bottom) {
                "$nodeId: nine-slice target height $height is smaller than vertical borders ${border.top + border.bottom}"
            }
            nineSliceOps(x, y, width, height)
        }
        is TextureStyle.SegmentedFrame -> {
            val horizontalBorder = topLeft.width + topRight.width
            val verticalBorder = topLeft.height + bottomLeft.height
            require(width >= horizontalBorder) {
                "$nodeId: segmented frame target width $width is smaller than horizontal borders $horizontalBorder"
            }
            require(height >= verticalBorder) {
                "$nodeId: segmented frame target height $height is smaller than vertical borders $verticalBorder"
            }
            segmentedFrameOps(x, y, width, height)
        }
    }

private fun TextureStyle.BrassFrame.brassFrameOps(
    targetX: Int,
    targetY: Int,
    targetWidth: Int,
    targetHeight: Int,
): List<RenderOp> {
    if (targetWidth <= 0 || targetHeight <= 0) return emptyList()
    val effectiveBorderWidth = borderWidth.coerceAtMost((minOf(targetWidth, targetHeight) + 1) / 2)
    return buildList {
        for (localY in 0 until targetHeight) {
            for (localX in 0 until targetWidth) {
                val edgeDistance =
                    minOf(
                        localX,
                        localY,
                        targetWidth - 1 - localX,
                        targetHeight - 1 - localY,
                    )
                if (edgeDistance >= effectiveBorderWidth) continue
                add(
                    RenderOp.FillRect(
                        x = targetX + localX,
                        y = targetY + localY,
                        width = 1,
                        height = 1,
                        color = value(brassPixelColor(localX, localY, targetWidth, targetHeight, edgeDistance, effectiveBorderWidth)),
                    ),
                )
            }
        }
        addBrassRivets(targetX, targetY, targetWidth, targetHeight, effectiveBorderWidth, base)
        if (ornament && targetWidth > effectiveBorderWidth * 2 + 6 && targetHeight > effectiveBorderWidth * 2 + 6) {
            addBrassOrnament(targetX, targetY, targetWidth, targetHeight, effectiveBorderWidth, base, ornamentSpacing)
        }
    }
}

private fun TextureStyle.BrassFrame.brassPixelColor(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    edgeDistance: Int,
    borderWidth: Int,
): Color {
    val bevel =
        when {
            x == 0 || y == 0 -> 38
            x == width - 1 || y == height - 1 -> -48
            edgeDistance == borderWidth - 1 -> -26
            edgeDistance == 1 -> 14
            else -> 0
        }
    val grain = deterministicNoise(x, y, seed, noiseStrength)
    return base.shifted(bevel + grain)
}

private fun MutableList<RenderOp>.addBrassRivets(
    targetX: Int,
    targetY: Int,
    targetWidth: Int,
    targetHeight: Int,
    borderWidth: Int,
    base: Color,
) {
    val rivetSize = minOf(3, borderWidth).coerceAtLeast(1)
    val inset = 1.coerceAtMost(borderWidth - 1)
    val color = value(base.shifted(30))
    val shadow = value(base.shifted(-45))
    fun rivet(
        x: Int,
        y: Int,
    ) {
        add(RenderOp.FillRect(x, y, rivetSize, rivetSize, color))
        if (rivetSize > 1) {
            add(RenderOp.FillRect(x + rivetSize - 1, y + rivetSize - 1, 1, 1, shadow))
        }
    }
    rivet(targetX + inset, targetY + inset)
    rivet(targetX + targetWidth - inset - rivetSize, targetY + inset)
    rivet(targetX + inset, targetY + targetHeight - inset - rivetSize)
    rivet(targetX + targetWidth - inset - rivetSize, targetY + targetHeight - inset - rivetSize)
}

private fun MutableList<RenderOp>.addBrassOrnament(
    targetX: Int,
    targetY: Int,
    targetWidth: Int,
    targetHeight: Int,
    borderWidth: Int,
    base: Color,
    spacing: Int,
) {
    val dark = value(base.shifted(-54))
    val light = value(base.shifted(44))
    val topY = targetY + (borderWidth / 2).coerceAtLeast(1)
    val bottomY = targetY + targetHeight - 1 - (borderWidth / 2).coerceAtLeast(1)
    val leftX = targetX + (borderWidth / 2).coerceAtLeast(1)
    val rightX = targetX + targetWidth - 1 - (borderWidth / 2).coerceAtLeast(1)

    for (x in targetX + borderWidth + 2 until targetX + targetWidth - borderWidth - 2) {
        val offset = triangularWave(x - targetX, spacing)
        if (offset == 0 || offset == 1) {
            add(RenderOp.FillRect(x, topY, 1, 1, dark))
            add(RenderOp.FillRect(x, bottomY, 1, 1, dark))
        } else if (offset == 2) {
            add(RenderOp.FillRect(x, topY + 1, 1, 1, light))
            add(RenderOp.FillRect(x, bottomY - 1, 1, 1, light))
        }
    }

    for (y in targetY + borderWidth + 2 until targetY + targetHeight - borderWidth - 2) {
        val offset = triangularWave(y - targetY, spacing)
        if (offset == 0 || offset == 1) {
            add(RenderOp.FillRect(leftX, y, 1, 1, dark))
            add(RenderOp.FillRect(rightX, y, 1, 1, dark))
        } else if (offset == 2) {
            add(RenderOp.FillRect(leftX + 1, y, 1, 1, light))
            add(RenderOp.FillRect(rightX - 1, y, 1, 1, light))
        }
    }
}

private fun TextureStyle.Checkerboard.checkerboardOps(
    targetX: Int,
    targetY: Int,
    targetWidth: Int,
    targetHeight: Int,
): List<RenderOp> =
    buildList {
        var y = 0
        while (y < targetHeight) {
            val cellHeight = minOf(cellSize, targetHeight - y)
            var x = 0
            while (x < targetWidth) {
                val cellWidth = minOf(cellSize, targetWidth - x)
                val color = if (((x / cellSize) + (y / cellSize)) % 2 == 0) first else second
                add(
                    RenderOp.FillRect(
                        x = targetX + x,
                        y = targetY + y,
                        width = cellWidth,
                        height = cellHeight,
                        color = value(color),
                    ),
                )
                x += cellWidth
            }
            y += cellHeight
        }
    }

private fun triangularWave(
    value: Int,
    period: Int,
): Int {
    val phase = positiveModulo(value, period)
    return if (phase < period / 2) phase else period - phase
}

private fun deterministicNoise(
    x: Int,
    y: Int,
    seed: Int,
    strength: Int,
): Int {
    if (strength == 0) return 0
    var value = x * 734287 + y * 912271 + seed * 438289
    value = value xor (value ushr 13)
    value *= 1274126177
    value = value xor (value ushr 16)
    return positiveModulo(value, strength * 2 + 1) - strength
}

private fun positiveModulo(
    value: Int,
    mod: Int,
): Int = ((value % mod) + mod) % mod

private fun Color.shifted(delta: Int): Color {
    val argb = value.toInt()
    val alpha = (argb ushr 24) and 0xFF
    val red = ((argb ushr 16) and 0xFF).shiftedChannel(delta)
    val green = ((argb ushr 8) and 0xFF).shiftedChannel(delta)
    val blue = (argb and 0xFF).shiftedChannel(delta)
    return Color.argb(alpha, red, green, blue)
}

private fun Int.shiftedChannel(delta: Int): Int = (this + delta).coerceIn(0, 255)

private fun TextureStyle.SegmentedFrame.segmentedFrameOps(
    targetX: Int,
    targetY: Int,
    targetWidth: Int,
    targetHeight: Int,
): List<RenderOp> {
    val leftWidth = topLeft.width
    val rightWidth = topRight.width
    val topHeight = topLeft.height
    val bottomHeight = bottomLeft.height
    val centerTargetWidth = targetWidth - leftWidth - rightWidth
    val centerTargetHeight = targetHeight - topHeight - bottomHeight
    return buildList {
        addSegment(targetX, targetY, leftWidth, topHeight, topLeft, TextureScaling.Stretch)
        addSegment(targetX + leftWidth, targetY, centerTargetWidth, topHeight, top, edgeScaling)
        addSegment(targetX + targetWidth - rightWidth, targetY, rightWidth, topHeight, topRight, TextureScaling.Stretch)

        addSegment(targetX, targetY + topHeight, leftWidth, centerTargetHeight, left, edgeScaling)
        center?.let {
            addSegment(targetX + leftWidth, targetY + topHeight, centerTargetWidth, centerTargetHeight, it.region, it.scaling)
        }
        addSegment(targetX + targetWidth - rightWidth, targetY + topHeight, rightWidth, centerTargetHeight, right, edgeScaling)

        addSegment(targetX, targetY + targetHeight - bottomHeight, leftWidth, bottomHeight, bottomLeft, TextureScaling.Stretch)
        addSegment(targetX + leftWidth, targetY + targetHeight - bottomHeight, centerTargetWidth, bottomHeight, bottom, edgeScaling)
        addSegment(targetX + targetWidth - rightWidth, targetY + targetHeight - bottomHeight, rightWidth, bottomHeight, bottomRight, TextureScaling.Stretch)
    }
}

private fun TextureStyle.NineSlice.nineSliceOps(
    targetX: Int,
    targetY: Int,
    targetWidth: Int,
    targetHeight: Int,
): List<RenderOp> {
    val left = border.left
    val top = border.top
    val right = border.right
    val bottom = border.bottom
    val centerSourceWidth = region.width - left - right
    val centerSourceHeight = region.height - top - bottom
    val centerTargetWidth = targetWidth - left - right
    val centerTargetHeight = targetHeight - top - bottom
    return buildList {
        addSegment(targetX, targetY, left, top, region.subRegion(0, 0, left, top), TextureScaling.Stretch)
        addSegment(targetX + left, targetY, centerTargetWidth, top, region.subRegion(left, 0, centerSourceWidth, top), edgeScaling)
        addSegment(targetX + targetWidth - right, targetY, right, top, region.subRegion(region.width - right, 0, right, top), TextureScaling.Stretch)

        addSegment(targetX, targetY + top, left, centerTargetHeight, region.subRegion(0, top, left, centerSourceHeight), edgeScaling)
        addSegment(targetX + left, targetY + top, centerTargetWidth, centerTargetHeight, region.subRegion(left, top, centerSourceWidth, centerSourceHeight), centerScaling)
        addSegment(targetX + targetWidth - right, targetY + top, right, centerTargetHeight, region.subRegion(region.width - right, top, right, centerSourceHeight), edgeScaling)

        addSegment(targetX, targetY + targetHeight - bottom, left, bottom, region.subRegion(0, region.height - bottom, left, bottom), TextureScaling.Stretch)
        addSegment(targetX + left, targetY + targetHeight - bottom, centerTargetWidth, bottom, region.subRegion(left, region.height - bottom, centerSourceWidth, bottom), edgeScaling)
        addSegment(targetX + targetWidth - right, targetY + targetHeight - bottom, right, bottom, region.subRegion(region.width - right, region.height - bottom, right, bottom), TextureScaling.Stretch)
    }
}

private fun MutableList<RenderOp>.addSegment(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    region: TextureRegion?,
    scaling: TextureScaling,
) {
    if (width == 0 || height == 0 || region == null) return
    add(
        RenderOp.DrawTextureRegion(
            x = x,
            y = y,
            width = width,
            height = height,
            region = region.toPrimitiveRegion(),
            scaling = scaling.toPrimitiveScaling(),
        ),
    )
}

private fun TextureRegion.subRegion(
    offsetX: Int,
    offsetY: Int,
    width: Int,
    height: Int,
): TextureRegion? =
    if (width == 0 || height == 0) {
        null
    } else {
        TextureRegion(
            atlas = atlas,
            x = x + offsetX,
            y = y + offsetY,
            width = width,
            height = height,
        )
    }

private fun TextureRegion.toPrimitiveRegion(): PrimitiveTextureRegion =
    PrimitiveTextureRegion(
        namespace = atlas.namespace,
        path = atlas.path,
        atlasWidth = atlas.width,
        atlasHeight = atlas.height,
        sourceX = x,
        sourceY = y,
        sourceWidth = width,
        sourceHeight = height,
    )

private fun TextureScaling.toPrimitiveScaling(): PrimitiveTextureScaling =
    when (this) {
        TextureScaling.Stretch -> PrimitiveTextureScaling.Stretch
        TextureScaling.Tile -> PrimitiveTextureScaling.Tile
    }
