package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.editor.EditorViewModel
import ru.lazyhat.kraftui.foundation.CanvasScope
import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.HoverState
import ru.lazyhat.kraftui.foundation.Value
import ru.lazyhat.kraftui.foundation.modifier.Position
import ru.lazyhat.kraftui.foundation.modifier.TextAlignment
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy
import ru.lazyhat.kraftui.layout.LayoutAxis
import ru.lazyhat.kraftui.layout.LayoutDiagnostic
import ru.lazyhat.kraftui.text.TextFlow

/**
 * A compiled UI description ready for execution.
 *
 * The runtime contract is intentionally thin: render/hit-test walk a flat list
 * of [RenderFrame]s with baked, relative coordinates. Dynamic values enter the
 * pipeline only through [Value]s (e.g. frame origin, visibility,
 * op-level text/snapshot).
 *
 * This program is produced once per content by [ScreenProgramCompiler]. It is
 * expected to be recompiled only when the structural input changes (e.g. the
 * screen's root size).
 */
data class ScreenProgram<Action>(
    val frames: List<RenderFrame>,
    val hitRegions: List<HitRegion<Action>>,
    val hoverRegions: List<HoverRegion> = emptyList(),
    val tooltipRegions: List<TooltipRegion> = emptyList(),
    val focusNodes: List<FocusNode> = emptyList(),
    val scrollRegions: List<ScrollRegion> = emptyList(),
    val diagnostics: List<ScreenProgramDiagnostic> = emptyList(),
)

data class UiDependencies(
    val dynamicValue: Boolean = false,
    val dynamicVisibility: Boolean = false,
    val dynamicOrigin: Boolean = false,
    val dynamicInput: Boolean = false,
) {
    val isStatic: Boolean
        get() = !dynamicValue && !dynamicVisibility && !dynamicOrigin && !dynamicInput

    operator fun plus(other: UiDependencies): UiDependencies =
        UiDependencies(
            dynamicValue = dynamicValue || other.dynamicValue,
            dynamicVisibility = dynamicVisibility || other.dynamicVisibility,
            dynamicOrigin = dynamicOrigin || other.dynamicOrigin,
            dynamicInput = dynamicInput || other.dynamicInput,
        )

    companion object {
        val Static: UiDependencies = UiDependencies()
        val DynamicValue: UiDependencies = UiDependencies(dynamicValue = true)
        val DynamicVisibility: UiDependencies = UiDependencies(dynamicVisibility = true)
        val DynamicOrigin: UiDependencies = UiDependencies(dynamicOrigin = true)
        val DynamicInput: UiDependencies = UiDependencies(dynamicInput = true)
    }
}

val ScreenProgram<*>.dependencies: UiDependencies
    get() =
        frames.fold(UiDependencies.Static) { acc, frame -> acc + frame.dependencies } +
            hitRegions.fold(UiDependencies.Static) { acc, region -> acc + region.dependencies } +
            tooltipRegions.fold(UiDependencies.Static) { acc, region -> acc + region.dependencies }

fun ScreenProgram<*>.dependenciesFor(region: HitRegion<*>): UiDependencies =
    region.dependencies +
        frames[region.frameIndex].placementDependencies +
        region.clip.frameDependency(this)

sealed interface ScreenProgramDiagnostic {
    val nodeId: String

    data class LayoutViolation(
        val diagnostic: LayoutDiagnostic,
    ) : ScreenProgramDiagnostic {
        override val nodeId: String = diagnostic.nodeId
    }

    data class TextWouldOverflow(
        override val nodeId: String,
        val text: String,
        val width: Int,
        val textWidth: Int,
        val policy: TextOverflowPolicy,
    ) : ScreenProgramDiagnostic

    data class TextHeightWouldOverflow(
        override val nodeId: String,
        val text: String,
        val height: Int,
        val textHeight: Int,
        val lineCount: Int,
        val policy: TextOverflowPolicy,
    ) : ScreenProgramDiagnostic
}

data class ScreenProgramDiagnosticReport(
    val diagnostics: List<ScreenProgramDiagnostic>,
) {
    fun asText(): String =
        if (diagnostics.isEmpty()) {
            "No UI diagnostics"
        } else {
            diagnostics.joinToString(separator = "\n") { it.asText() }
        }

    private fun ScreenProgramDiagnostic.asText(): String =
        when (this) {
            is ScreenProgramDiagnostic.LayoutViolation -> diagnostic.asText()
            is ScreenProgramDiagnostic.TextWouldOverflow ->
                "$nodeId: text overflow, text width $textWidth px, available $width px, policy $policy, text '$text'"
            is ScreenProgramDiagnostic.TextHeightWouldOverflow ->
                "$nodeId: text height overflow, text height $textHeight px across $lineCount lines, available $height px, policy $policy, text '$text'"
        }

    private fun LayoutDiagnostic.asText(): String =
        when (this) {
            is LayoutDiagnostic.ContainerOverflow -> {
                val axisName =
                    when (axis) {
                        LayoutAxis.Horizontal -> "horizontal"
                        LayoutAxis.Vertical -> "vertical"
                    }
                "$nodeId: $axisName overflow, required $required px, available $available px"
            }

            is LayoutDiagnostic.GridCellOutOfBounds ->
                "$nodeId: grid cell out of bounds, column $column span $columnSpan of $columnCount, row $row span $rowSpan of $rowCount"
        }
}

/**
 * A keyboard-focus target produced by a `Modifier.focusable(...)` or by an
 * element that auto-claims focus (e.g. [ru.lazyhat.kraftui.foundation.UiElement.TerminalSurface]).
 *
 *  - [nodeId] is stable across recompiles and is what the runtime stores
 *    to remember which node currently owns focus.
 *  - [tabOrder] drives Tab/Shift+Tab cycling: nodes are visited in
 *    ascending order; nodes with the same order are visited in
 *    compile-time order; nodes with negative order are skipped by
 *    cycling but can still acquire focus via mouse click.
 *  - [frameIndex] + bounds + [handler] follow the same baked relative
 *    coordinate scheme as [HitRegion].
 */
data class FocusNode(
    val nodeId: String,
    val frameIndex: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val tabOrder: Int,
    val handler: FocusHandler,
)

/**
 * Keyboard/char event handlers for a focused element. All three lambdas
 * return `true` if they consumed the event.
 */
data class FocusHandler(
    val onKeyPressed: (key: Int, modifiers: Int) -> Boolean = { _, _ -> false },
    val onKeyReleased: (Int) -> Boolean = { false },
    val onCharTyped: (Char) -> Boolean = { false },
)

/**
 * A group of [RenderOp]s that share an optional dynamic [origin] and
 * [visible] expression.
 *
 * When [origin] is `null`, ops are rendered at their baked absolute
 * coordinates. When non-null, the origin is evaluated once per render tick and
 * added to every op's baked relative coordinates.
 *
 * When [visible] is `null`, the frame is always drawn. Otherwise the
 * expression is evaluated once per render tick.
 */
data class RenderFrame(
    val origin: Value<Position>? = null,
    val visible: Value<Boolean>? = null,
    val ops: List<RenderOp>,
)

val RenderFrame.dependencies: UiDependencies
    get() =
        ops.fold(UiDependencies.Static) { acc, op -> acc + op.dependencies } +
            placementDependencies

val RenderFrame.placementDependencies: UiDependencies
    get() =
            origin.dynamicDependency(UiDependencies.DynamicOrigin) +
            visible.dynamicDependency(UiDependencies.DynamicVisibility)

/**
 * A drawing primitive bound to absolute pixels when [RenderFrame.origin] is
 * `null`, or to pixels relative to the frame origin otherwise.
 */
sealed interface RenderOp {
    data class FillRect(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val color: Value<Color>,
    ) : RenderOp

    data class DrawText(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val value: Value<String>,
        val color: Value<Color>,
        val alignment: TextAlignment,
        val overflow: TextOverflowPolicy = TextOverflowPolicy.FailInValidation,
        val flow: TextFlow = TextFlow(),
    ) : RenderOp

    data class DrawTerminalSurface(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val snapshot: Value<Any>,
    ) : RenderOp

    data class DrawCanvas(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val onDraw: CanvasScope.() -> Unit,
    ) : RenderOp

    /**
     * Pushes a rectangular clip region onto the backend's clip stack.
     * Subsequent draw ops within the same frame are restricted to the
     * intersection of all currently pushed clips. Coordinates follow the
     * same baked/frame-relative scheme as other ops.
     */
    data class PushClip(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    ) : RenderOp

    /** Pops the most recently pushed clip region. */
    object PopClip : RenderOp

    /**
     * Draws a complete code editor. The backend reads everything it needs
     * (text, cursor, highlights, diagnostics, scroll) from [viewModel],
     * which is evaluated once per render tick.
     *
     * [fontWidth]/[fontHeight] describe a monospace glyph in pixels.
     */
    data class DrawCodeEditor(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val viewModel: Value<EditorViewModel>,
        val fontWidth: Int,
        val fontHeight: Int,
    ) : RenderOp
}

val RenderOp.dependencies: UiDependencies
    get() =
        when (this) {
            is RenderOp.FillRect -> color.dynamicDependency(UiDependencies.DynamicValue)
            is RenderOp.DrawText ->
                value.dynamicDependency(UiDependencies.DynamicValue) +
                    color.dynamicDependency(UiDependencies.DynamicValue)
            is RenderOp.DrawTerminalSurface -> snapshot.dynamicDependency(UiDependencies.DynamicValue)
            is RenderOp.DrawCanvas -> UiDependencies.DynamicValue
            is RenderOp.PushClip -> UiDependencies.Static
            RenderOp.PopClip -> UiDependencies.Static
            is RenderOp.DrawCodeEditor -> viewModel.dynamicDependency(UiDependencies.DynamicValue)
        }

/**
 * A clickable area.
 *
 * [frameIndex] refers into [ScreenProgram.frames]; the region inherits its
 * frame's origin/visibility so popups/overlays remain clickable wherever they
 * are drawn.
 *
 * Coordinates are baked relative to the parent frame's origin.
 */
data class HitRegion<out Action>(
    val nodeId: String,
    val frameIndex: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val zIndex: Int,
    val action: Value<Action?>? = null,
    val onClickAt: ((x: Int, y: Int) -> Unit)? = null,
    val clip: HitClip? = null,
    val onDragStart: ((x: Int, y: Int) -> Unit)? = null,
    val onDrag: ((x: Int, y: Int) -> Unit)? = null,
    val onDragEnd: ((x: Int, y: Int) -> Unit)? = null,
)

val HitRegion<*>.dependencies: UiDependencies
    get() = action.dynamicDependency(UiDependencies.DynamicInput)

/**
 * A rectangular bound, expressed in the coordinate space of the frame
 * referenced by [frameIndex] (i.e. the bounds of a parent ScrollArea), used
 * to mask hit-tests so that clicks landing on parts of a region clipped out
 * of the visible viewport are ignored.
 */
data class HitClip(
    val frameIndex: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/**
 * A rectangle whose [HoverState] flag is updated each render tick based on
 * mouse position. Coordinates follow the same baked/frame-relative scheme
 * as [HitRegion].
 */
data class HoverRegion(
    val nodeId: String,
    val frameIndex: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val state: HoverState,
)

/**
 * A rectangle whose owning element's tooltip should be shown while the
 * mouse hovers over it. [text] is evaluated lazily once per render tick
 * (only for the region that actually contains the cursor).
 */
data class TooltipRegion(
    val nodeId: String,
    val frameIndex: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val text: Value<String>,
)

val TooltipRegion.dependencies: UiDependencies
    get() = text.dynamicDependency(UiDependencies.DynamicValue)

private fun Value<*>?.dynamicDependency(kind: UiDependencies): UiDependencies =
    if (this == null || isStatic) UiDependencies.Static else kind

private fun HitClip?.frameDependency(program: ScreenProgram<*>): UiDependencies =
    if (this == null) {
        UiDependencies.Static
    } else {
        program.frames[frameIndex].placementDependencies
    }

/**
 * A rectangle that absorbs mouse-wheel events. Coordinates follow the same
 * baked/frame-relative scheme as other regions; [onScroll] receives the
 * vertical wheel delta and returns `true` if the event was consumed.
 */
data class ScrollRegion(
    val nodeId: String,
    val frameIndex: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val onScroll: (Double) -> Boolean,
)
