package ru.lazyhat.kraftui.layout

import ru.lazyhat.kraftui.foundation.UiElement
import ru.lazyhat.kraftui.foundation.FixedGridTrack
import ru.lazyhat.kraftui.foundation.GridTrack
import ru.lazyhat.kraftui.foundation.WeightedGridTrack
import ru.lazyhat.kraftui.foundation.modifier.AxisSize
import ru.lazyhat.kraftui.foundation.modifier.Modifier
import ru.lazyhat.kraftui.foundation.modifier.Padding
import ru.lazyhat.kraftui.foundation.modifier.Position
import ru.lazyhat.kraftui.foundation.modifier.UiAlignment
import ru.lazyhat.kraftui.foundation.modifier.findAlignment
import ru.lazyhat.kraftui.foundation.modifier.findHeight
import ru.lazyhat.kraftui.foundation.modifier.findOffset
import ru.lazyhat.kraftui.foundation.modifier.findPadding
import ru.lazyhat.kraftui.foundation.modifier.findSize
import ru.lazyhat.kraftui.foundation.modifier.findTextFlow
import ru.lazyhat.kraftui.foundation.modifier.findWeight
import ru.lazyhat.kraftui.foundation.modifier.findWidth
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy
import ru.lazyhat.kraftui.foundation.modifier.TextWrapPolicy
import ru.lazyhat.kraftui.program.FontMetrics
import ru.lazyhat.kraftui.text.TextFlow
import ru.lazyhat.kraftui.text.TextLayouter

/**
 * A resolved rectangle for a single UI node in some frame's coordinate space.
 *
 * `nodeId` follows the same hierarchical path the compiler uses for lowering
 * (`"root"`, `"root-0"`, `"root-0-1"`, ...). For overlay subtrees, the root
 * node id is the path of the overlay itself.
 */
data class LayoutNode(
    val nodeId: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

data class LayoutResult(
    val nodes: Map<String, LayoutNode>,
    val diagnostics: List<LayoutDiagnostic>,
)

enum class LayoutAxis {
    Horizontal,
    Vertical,
}

sealed interface LayoutDiagnostic {
    val nodeId: String

    data class ContainerOverflow(
        override val nodeId: String,
        val axis: LayoutAxis,
        val available: Int,
        val required: Int,
    ) : LayoutDiagnostic

    data class GridCellOutOfBounds(
        override val nodeId: String,
        val column: Int,
        val row: Int,
        val columnSpan: Int,
        val rowSpan: Int,
        val columnCount: Int,
        val rowCount: Int,
    ) : LayoutDiagnostic
}

/**
 * Resolves a subtree into a flat map of `nodeId -> LayoutNode`.
 *
 * The resolver intentionally does not know about frames, render ops, focus,
 * or dynamic values — it only computes static bounds. Overlay children are
 * deliberately skipped here (they live in their own frame) and are laid out
 * by running a fresh resolver from the compiler.
 */
class UiLayoutResolver(
    private val rootWidth: Int,
    private val rootHeight: Int,
    private val fontMetrics: FontMetrics? = null,
) {
    fun resolve(
        root: UiElement,
        rootNodeId: String = "root",
        rootX: Int = 0,
        rootY: Int = 0,
    ): Map<String, LayoutNode> =
        resolveWithDiagnostics(root, rootNodeId, rootX, rootY).nodes

    fun resolveWithDiagnostics(
        root: UiElement,
        rootNodeId: String = "root",
        rootX: Int = 0,
        rootY: Int = 0,
    ): LayoutResult {
        val resolved = linkedMapOf<String, LayoutNode>()
        val diagnostics = mutableListOf<LayoutDiagnostic>()
        resolveAsFrameRoot(root, rootNodeId, rootX, rootY, rootWidth, rootHeight, resolved, diagnostics)
        return LayoutResult(resolved, diagnostics)
    }

    private fun resolveAsFrameRoot(
        element: UiElement,
        nodeId: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        resolved: MutableMap<String, LayoutNode>,
        diagnostics: MutableList<LayoutDiagnostic>,
    ) {
        resolved[nodeId] = LayoutNode(nodeId, x, y, width, height)
        when (element) {
            is UiElement.Box -> {
                resolveBoxChildren(element.children, nodeId, x, y, width, height, element.modifier, resolved, diagnostics)
            }

            is UiElement.Overlay -> {
                resolveBoxChildren(element.children, nodeId, x, y, width, height, element.modifier, resolved, diagnostics)
            }

            is UiElement.Row -> {
                resolveRowChildren(
                    element.children,
                    nodeId,
                    x,
                    y,
                    width,
                    height,
                    element.modifier,
                    element.gap,
                    element.verticalAlignment,
                    resolved,
                    diagnostics,
                )
            }

            is UiElement.Column -> {
                resolveColumnChildren(
                    element.children,
                    nodeId,
                    x,
                    y,
                    width,
                    height,
                    element.modifier,
                    element.gap,
                    element.horizontalAlignment,
                    resolved,
                    diagnostics,
                )
            }

            is UiElement.Grid -> {
                resolveGridCells(
                    element,
                    nodeId,
                    x,
                    y,
                    width,
                    height,
                    resolved,
                    diagnostics,
                )
            }

            is UiElement.IfNode -> {
                element.children.forEachIndexed { index, child ->
                    resolveNode(child, "$nodeId-$index", x, y, width, height, resolved, diagnostics)
                }
            }

            is UiElement.Text, is UiElement.TerminalSurface, is UiElement.Canvas, is UiElement.CodeEditor -> {
                Unit
            }

            is UiElement.ScrollArea -> {
                // Children live in a sub-frame and are resolved separately by the compiler.
                resolveBoxChildren(element.children, nodeId, x, y, width, height, element.modifier, resolved, diagnostics)
            }
        }
    }

    private fun resolveNode(
        element: UiElement,
        nodeId: String,
        parentX: Int,
        parentY: Int,
        parentWidth: Int,
        parentHeight: Int,
        resolved: MutableMap<String, LayoutNode>,
        diagnostics: MutableList<LayoutDiagnostic>,
        forcedWidth: Int? = null,
        forcedHeight: Int? = null,
    ) {
        // Overlays are rendered in their own frame; they contribute nothing to
        // the parent frame's layout and must not occupy any slot in Row/Column
        // flow. Callers filter them out before computing positions.
        if (element is UiElement.Overlay) return

        val width = forcedWidth ?: explicitOrIntrinsicWidth(element, parentWidth)
        val height = forcedHeight ?: explicitOrIntrinsicHeight(element, parentHeight, width)

        val alignment = element.modifier.findAlignment()?.alignment
        val position = element.modifier.findOffset()?.position ?: Position.Zero

        val alignedX = alignX(parentX, parentWidth, width, alignment)
        val alignedY = alignY(parentY, parentHeight, height, alignment)

        val x = alignedX + position.x
        val y = alignedY + position.y

        resolved[nodeId] = LayoutNode(nodeId, x, y, width, height)

        when (element) {
            is UiElement.Box -> {
                resolveBoxChildren(element.children, nodeId, x, y, width, height, element.modifier, resolved, diagnostics)
            }

            is UiElement.Row -> {
                resolveRowChildren(
                    element.children,
                    nodeId,
                    x,
                    y,
                    width,
                    height,
                    element.modifier,
                    element.gap,
                    element.verticalAlignment,
                    resolved,
                    diagnostics,
                )
            }

            is UiElement.Column -> {
                resolveColumnChildren(
                    element.children,
                    nodeId,
                    x,
                    y,
                    width,
                    height,
                    element.modifier,
                    element.gap,
                    element.horizontalAlignment,
                    resolved,
                    diagnostics,
                )
            }

            is UiElement.Grid -> {
                resolveGridCells(
                    element,
                    nodeId,
                    x,
                    y,
                    width,
                    height,
                    resolved,
                    diagnostics,
                )
            }

            is UiElement.IfNode -> {
                element.children.forEachIndexed { index, child ->
                    resolveNode(child, "$nodeId-$index", x, y, width, height, resolved, diagnostics)
                }
            }

            is UiElement.Text, is UiElement.TerminalSurface, is UiElement.Canvas, is UiElement.Overlay, is UiElement.CodeEditor -> {
                Unit
            }

            is UiElement.ScrollArea -> {
                // ScrollArea owns its own frame; children are laid out by a sub-resolver.
                Unit
            }
        }
    }

    private fun resolveBoxChildren(
        children: List<UiElement>,
        nodeId: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        modifier: Modifier,
        resolved: MutableMap<String, LayoutNode>,
        diagnostics: MutableList<LayoutDiagnostic>,
    ) {
        val padding = modifier.findPadding()?.padding ?: Padding.Zero

        val contentX = x + padding.left
        val contentY = y + padding.top
        val contentWidth = width - padding.left - padding.right
        val contentHeight = height - padding.top - padding.bottom

        children.forEachIndexed { index, child ->
            resolveNode(child, "$nodeId-$index", contentX, contentY, contentWidth, contentHeight, resolved, diagnostics)
        }
    }

    private fun resolveRowChildren(
        children: List<UiElement>,
        nodeId: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        modifier: Modifier,
        gap: Int,
        verticalAlignment: UiAlignment?,
        resolved: MutableMap<String, LayoutNode>,
        diagnostics: MutableList<LayoutDiagnostic>,
    ) {
        val padding = modifier.findPadding()?.padding ?: Padding.Zero

        val contentX = x + padding.left
        val contentY = y + padding.top
        val contentWidth = width - padding.left - padding.right
        val contentHeight = height - padding.top - padding.bottom

        val flow = children.filterNot { it is UiElement.Overlay }
        val gapWidth = gap * (flow.size - 1).coerceAtLeast(0)
        val fixedWidth =
            flow
                .filter { it.modifier.findWeight() == null }
                .sumOf { primaryWidthForRow(it, contentWidth) } + gapWidth
        if (fixedWidth > contentWidth) {
            diagnostics +=
                LayoutDiagnostic.ContainerOverflow(
                    nodeId = nodeId,
                    axis = LayoutAxis.Horizontal,
                    available = contentWidth,
                    required = fixedWidth,
                )
        }
        val totalWeight = flow.sumOf { (it.modifier.findWeight()?.weight ?: 0f).toDouble() }.toFloat()

        val remainingWidth = (contentWidth - fixedWidth).coerceAtLeast(0)
        var cursorX = contentX
        var assignedWeightedWidth = 0
        val weightedChildren = flow.count { it.modifier.findWeight() != null }
        var weightedIndex = 0

        children.forEachIndexed { index, child ->
            if (child is UiElement.Overlay) return@forEachIndexed
            val childWeight = child.modifier.findWeight()?.weight
            val childWidth =
                if (childWeight != null && totalWeight > 0f) {
                    weightedIndex += 1
                    if (weightedIndex == weightedChildren) {
                        remainingWidth - assignedWeightedWidth
                    } else {
                        ((remainingWidth * (childWeight / totalWeight))).toInt().also {
                            assignedWeightedWidth += it
                        }
                    }
                } else {
                    primaryWidthForRow(child, contentWidth)
                }
            val alignment = child.modifier.findAlignment()?.alignment ?: verticalAlignment
            val childHeight =
                when (alignment) {
                    UiAlignment.Stretch -> contentHeight
                    else -> explicitOrIntrinsicHeight(child, contentHeight, childWidth)
                }

            resolveNode(
                child,
                "$nodeId-$index",
                cursorX,
                alignY(contentY, contentHeight, childHeight, alignment),
                childWidth,
                childHeight,
                resolved,
                diagnostics,
                forcedWidth = childWidth,
                forcedHeight = childHeight,
            )
            cursorX += childWidth + gap
        }
    }

    private fun resolveColumnChildren(
        children: List<UiElement>,
        nodeId: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        modifier: Modifier,
        gap: Int,
        horizontalAlignment: UiAlignment?,
        resolved: MutableMap<String, LayoutNode>,
        diagnostics: MutableList<LayoutDiagnostic>,
    ) {
        val padding = modifier.findPadding()?.padding ?: Padding.Zero

        val contentX = x + padding.left
        val contentY = y + padding.top
        val contentWidth = width - padding.left - padding.right
        val contentHeight = height - padding.top - padding.bottom

        val flow = children.filterNot { it is UiElement.Overlay }
        val gapHeight = gap * (flow.size - 1).coerceAtLeast(0)
        val fixedHeight =
            flow
                .filter { it.modifier.findWeight() == null }
                .sumOf { primaryHeightForColumn(it, contentHeight) } + gapHeight
        if (fixedHeight > contentHeight) {
            diagnostics +=
                LayoutDiagnostic.ContainerOverflow(
                    nodeId = nodeId,
                    axis = LayoutAxis.Vertical,
                    available = contentHeight,
                    required = fixedHeight,
                )
        }
        val totalWeight = flow.sumOf { (it.modifier.findWeight()?.weight ?: 0f).toDouble() }.toFloat()

        val remainingHeight = (contentHeight - fixedHeight).coerceAtLeast(0)
        var cursorY = contentY
        var assignedWeightedHeight = 0
        val weightedChildren = flow.count { it.modifier.findWeight() != null }
        var weightedIndex = 0

        children.forEachIndexed { index, child ->
            if (child is UiElement.Overlay) return@forEachIndexed
            val childWeight = child.modifier.findWeight()?.weight
            val childHeight =
                if (childWeight != null && totalWeight > 0f) {
                    weightedIndex += 1
                    if (weightedIndex == weightedChildren) {
                        remainingHeight - assignedWeightedHeight
                    } else {
                        ((remainingHeight * (childWeight / totalWeight))).toInt().also {
                            assignedWeightedHeight += it
                        }
                    }
                } else {
                    primaryHeightForColumn(child, contentHeight)
                }
            val alignment = child.modifier.findAlignment()?.alignment ?: horizontalAlignment
            val childWidth =
                when (alignment) {
                    UiAlignment.Stretch -> contentWidth
                    else -> explicitOrIntrinsicWidth(child, contentWidth)
                }

            resolveNode(
                child,
                "$nodeId-$index",
                alignX(contentX, contentWidth, childWidth, alignment),
                cursorY,
                childWidth,
                childHeight,
                resolved,
                diagnostics,
                forcedWidth = childWidth,
                forcedHeight = childHeight,
            )
            cursorY += childHeight + gap
        }
    }

    private fun resolveGridCells(
        element: UiElement.Grid,
        nodeId: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        resolved: MutableMap<String, LayoutNode>,
        diagnostics: MutableList<LayoutDiagnostic>,
    ) {
        val padding = element.modifier.findPadding()?.padding ?: Padding.Zero

        val contentX = x + padding.left
        val contentY = y + padding.top
        val contentWidth = width - padding.left - padding.right
        val contentHeight = height - padding.top - padding.bottom

        val columnWidths =
            resolveTracks(
                nodeId = nodeId,
                axis = LayoutAxis.Horizontal,
                tracks = element.columns,
                available = contentWidth,
                gap = element.columnGap,
                diagnostics = diagnostics,
            )
        val rowHeights =
            resolveTracks(
                nodeId = nodeId,
                axis = LayoutAxis.Vertical,
                tracks = element.rows,
                available = contentHeight,
                gap = element.rowGap,
                diagnostics = diagnostics,
            )

        val columnOffsets = offsets(columnWidths, element.columnGap)
        val rowOffsets = offsets(rowHeights, element.rowGap)

        element.cells.forEachIndexed { cellIndex, cell ->
            val cellNodeId = "$nodeId-$cellIndex"
            if (cell.column + cell.columnSpan > element.columns.size || cell.row + cell.rowSpan > element.rows.size) {
                diagnostics +=
                    LayoutDiagnostic.GridCellOutOfBounds(
                        nodeId = cellNodeId,
                        column = cell.column,
                        row = cell.row,
                        columnSpan = cell.columnSpan,
                        rowSpan = cell.rowSpan,
                        columnCount = element.columns.size,
                        rowCount = element.rows.size,
                    )
                return@forEachIndexed
            }

            val cellX = contentX + columnOffsets[cell.column]
            val cellY = contentY + rowOffsets[cell.row]
            val cellWidth = spanSize(columnWidths, element.columnGap, cell.column, cell.columnSpan)
            val cellHeight = spanSize(rowHeights, element.rowGap, cell.row, cell.rowSpan)

            cell.children.forEachIndexed { childIndex, child ->
                resolveNode(
                    child,
                    "$cellNodeId-$childIndex",
                    cellX,
                    cellY,
                    cellWidth,
                    cellHeight,
                    resolved,
                    diagnostics,
                )
            }
        }
    }

    private fun resolveTracks(
        nodeId: String,
        axis: LayoutAxis,
        tracks: List<GridTrack>,
        available: Int,
        gap: Int,
        diagnostics: MutableList<LayoutDiagnostic>,
    ): List<Int> {
        val gapSize = gap * (tracks.size - 1).coerceAtLeast(0)
        val fixedSize =
            tracks.sumOf {
                when (it) {
                    is FixedGridTrack -> it.pixels
                    is WeightedGridTrack -> 0
                }
            } + gapSize
        if (fixedSize > available) {
            diagnostics +=
                LayoutDiagnostic.ContainerOverflow(
                    nodeId = nodeId,
                    axis = axis,
                    available = available,
                    required = fixedSize,
                )
        }

        val remaining = (available - fixedSize).coerceAtLeast(0)
        val totalWeight =
            tracks.sumOf {
                when (it) {
                    is FixedGridTrack -> 0.0
                    is WeightedGridTrack -> it.weight.toDouble()
                }
            }.toFloat()
        var assignedWeightedSize = 0
        val weightedTracks = tracks.count { it is WeightedGridTrack }
        var weightedIndex = 0

        return tracks.map { track ->
            when (track) {
                is FixedGridTrack -> track.pixels
                is WeightedGridTrack -> {
                    weightedIndex += 1
                    if (weightedIndex == weightedTracks) {
                        remaining - assignedWeightedSize
                    } else {
                        ((remaining * (track.weight / totalWeight))).toInt().also {
                            assignedWeightedSize += it
                        }
                    }
                }
            }
        }
    }

    private fun offsets(
        sizes: List<Int>,
        gap: Int,
    ): List<Int> {
        var cursor = 0
        return sizes.map { size ->
            val current = cursor
            cursor += size + gap
            current
        }
    }

    private fun spanSize(
        sizes: List<Int>,
        gap: Int,
        start: Int,
        span: Int,
    ): Int =
        sizes.drop(start).take(span).sum() + gap * (span - 1).coerceAtLeast(0)

    private fun alignX(
        parentX: Int,
        parentWidth: Int,
        width: Int,
        alignment: UiAlignment?,
    ): Int =
        when (alignment) {
            UiAlignment.Center -> parentX + (parentWidth - width) / 2
            UiAlignment.End -> parentX + parentWidth - width
            else -> parentX
        }

    private fun alignY(
        parentY: Int,
        parentHeight: Int,
        height: Int,
        alignment: UiAlignment?,
    ): Int =
        when (alignment) {
            UiAlignment.Center -> parentY + (parentHeight - height) / 2
            UiAlignment.End -> parentY + parentHeight - height
            else -> parentY
        }

    private fun explicitOrIntrinsicWidth(
        element: UiElement,
        fallbackWidth: Int,
    ): Int =
        explicitWidth(element, fallbackWidth) ?: when (element) {
            is UiElement.Text -> fontMetrics?.width(element.text.value) ?: fallbackWidth
            else -> fallbackWidth
        }

    private fun explicitOrIntrinsicHeight(
        element: UiElement,
        fallbackHeight: Int,
        width: Int = fallbackHeight,
    ): Int =
        explicitHeight(element, fallbackHeight) ?: when (element) {
            is UiElement.Text -> intrinsicTextHeight(element, width)
            else -> fallbackHeight
        }

    private fun primaryWidthForRow(
        element: UiElement,
        parentWidth: Int,
    ): Int =
        explicitWidth(element, parentWidth) ?: when (element) {
            is UiElement.Text -> fontMetrics?.width(element.text.value) ?: 0
            else -> 0
        }

    private fun primaryHeightForColumn(
        element: UiElement,
        parentHeight: Int,
    ): Int =
        explicitHeight(element, parentHeight) ?: when (element) {
            is UiElement.Text -> intrinsicTextHeight(element, explicitWidth(element, parentHeight) ?: parentHeight)
            else -> 0
        }

    private fun intrinsicTextHeight(
        element: UiElement.Text,
        width: Int,
    ): Int {
        val flowModifier = element.modifier.findTextFlow()
        val flow =
            TextFlow(
                wrap = flowModifier?.wrap ?: TextWrapPolicy.NoWrap,
                maxLines = flowModifier?.maxLines,
                lineHeight = flowModifier?.lineHeight ?: DEFAULT_TEXT_HEIGHT,
            )
        val metrics = fontMetrics ?: return flow.lineHeight
        val layout =
            TextLayouter(metrics::width).layout(
                text = element.text.value,
                width = width,
                flow = flow,
                overflow = TextOverflowPolicy.FailInValidation,
            )
        return layout.lines.size * flow.lineHeight
    }

    private fun explicitWidth(
        element: UiElement,
        fallbackWidth: Int,
    ): Int? =
        when (val width = element.modifier.findWidth()?.width) {
            is AxisSize.Fixed -> width.pixels
            AxisSize.Fill -> fallbackWidth
            null -> element.modifier.findSize()?.size?.width
        }

    private fun explicitHeight(
        element: UiElement,
        fallbackHeight: Int,
    ): Int? =
        when (val height = element.modifier.findHeight()?.height) {
            is AxisSize.Fixed -> height.pixels
            AxisSize.Fill -> fallbackHeight
            null -> element.modifier.findSize()?.size?.height
        }

    private companion object {
        const val DEFAULT_TEXT_HEIGHT = 9
    }
}
