package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.modifier.Position

data class PrimitiveOptimizationOptions(
    val enabled: Boolean = true,
    val excludedPaths: Set<String> = emptySet(),
    val mergeAdjacentFills: Boolean = true,
)

data class PrimitiveOptimizationResult(
    val program: PrimitiveScreenProgram,
    val report: PrimitiveOptimizationReport,
) {
    val staticRenderInstructions: List<PrimitiveRenderInstruction> =
        program.renderInstructions.filter { it.dependencies.isStatic }
    val dynamicRenderInstructions: List<PrimitiveRenderInstruction> =
        program.renderInstructions.filter { !it.dependencies.isStatic }
    val staticInputInstructions: List<PrimitiveInputInstruction> =
        program.inputInstructions.filter { it.dependencies.isStatic }
    val dynamicInputInstructions: List<PrimitiveInputInstruction> =
        program.inputInstructions.filter { !it.dependencies.isStatic }
}

data class PrimitiveOptimizationReport(
    val entries: List<PrimitiveOptimizationEntry>,
) {
    val changed: Boolean
        get() = entries.any { it != PrimitiveOptimizationEntry.OptimizationDisabled }
}

sealed interface PrimitiveOptimizationEntry {
    data object OptimizationDisabled : PrimitiveOptimizationEntry

    data class SkippedByPath(
        val path: String,
    ) : PrimitiveOptimizationEntry

    data class RemovedAlwaysInvisibleInstruction(
        val path: String,
    ) : PrimitiveOptimizationEntry

    data class FoldedConstantVisibility(
        val path: String,
        val visible: Boolean,
    ) : PrimitiveOptimizationEntry

    data class FoldedConstantOrigin(
        val path: String,
        val x: Int,
        val y: Int,
    ) : PrimitiveOptimizationEntry

    data class MergedAdjacentFills(
        val firstPath: String,
        val secondPath: String,
        val mergedPath: String,
    ) : PrimitiveOptimizationEntry
}

fun PrimitiveScreenProgram.optimizePrimitive(options: PrimitiveOptimizationOptions = PrimitiveOptimizationOptions()): PrimitiveOptimizationResult {
    if (!options.enabled) {
        return PrimitiveOptimizationResult(
            program = this,
            report = PrimitiveOptimizationReport(listOf(PrimitiveOptimizationEntry.OptimizationDisabled)),
        )
    }

    val entries = ArrayList<PrimitiveOptimizationEntry>()
    val optimizedRenderInstructions =
        renderInstructions.mapNotNull { instruction ->
            if (instruction.path in options.excludedPaths) {
                entries += PrimitiveOptimizationEntry.SkippedByPath(instruction.path)
                instruction
            } else {
                instruction.optimize(entries)
            }
        }.let { instructions ->
            if (options.mergeAdjacentFills) {
                instructions.mergeAdjacentFills(
                    excludedPaths = options.excludedPaths,
                    entries = entries,
                )
            } else {
                instructions
            }
        }

    val optimizedInputInstructions =
        inputInstructions.mapNotNull { instruction ->
            val path = instruction.path()
            if (path in options.excludedPaths) {
                entries += PrimitiveOptimizationEntry.SkippedByPath(path)
                instruction
            } else {
                instruction.optimize(entries)
            }
        }

    return PrimitiveOptimizationResult(
        program =
            PrimitiveScreenProgram(
                renderInstructions = optimizedRenderInstructions,
                inputInstructions = optimizedInputInstructions,
            ),
        report = PrimitiveOptimizationReport(entries),
    )
}

private fun PrimitiveRenderInstruction.optimize(entries: MutableList<PrimitiveOptimizationEntry>): PrimitiveRenderInstruction? {
    when (visible) {
        PrimitiveValueExpression.Constant(false) -> {
            entries += PrimitiveOptimizationEntry.RemovedAlwaysInvisibleInstruction(path)
            return null
        }
        PrimitiveValueExpression.Constant(true) -> {
            entries += PrimitiveOptimizationEntry.FoldedConstantVisibility(path, visible = true)
        }
        null,
        is PrimitiveValueExpression.StateField,
        -> Unit
        is PrimitiveValueExpression.Constant -> Unit
    }

    val foldedVisibility =
        when (visible) {
            PrimitiveValueExpression.Constant(true) -> null
            else -> visible
        }
    val originPosition = origin.asConstantPosition()
    val foldedOp =
        if (originPosition != null) {
            entries += PrimitiveOptimizationEntry.FoldedConstantOrigin(path, originPosition.x, originPosition.y)
            op.shifted(originPosition.x, originPosition.y)
        } else {
            op
        }

    return copy(
        visible = foldedVisibility,
        origin = if (originPosition != null) null else origin,
        op = foldedOp,
    )
}

private fun PrimitiveInputInstruction.optimize(entries: MutableList<PrimitiveOptimizationEntry>): PrimitiveInputInstruction? =
    when (this) {
        is PrimitiveInputInstruction.ClickRegion -> optimize(entries)
    }

private fun PrimitiveInputInstruction.path(): String =
    when (this) {
        is PrimitiveInputInstruction.ClickRegion -> path
    }

private fun PrimitiveInputInstruction.ClickRegion.optimize(entries: MutableList<PrimitiveOptimizationEntry>): PrimitiveInputInstruction.ClickRegion? {
    when (visible) {
        PrimitiveValueExpression.Constant(false) -> {
            entries += PrimitiveOptimizationEntry.RemovedAlwaysInvisibleInstruction(path)
            return null
        }
        PrimitiveValueExpression.Constant(true) -> {
            entries += PrimitiveOptimizationEntry.FoldedConstantVisibility(path, visible = true)
        }
        null,
        is PrimitiveValueExpression.StateField,
        -> Unit
        is PrimitiveValueExpression.Constant -> Unit
    }

    val originPosition = origin.asConstantPosition()
    if (originPosition != null) {
        entries += PrimitiveOptimizationEntry.FoldedConstantOrigin(path, originPosition.x, originPosition.y)
    }

    return copy(
        visible =
            when (visible) {
                PrimitiveValueExpression.Constant(true) -> null
                else -> visible
            },
        origin = if (originPosition != null) null else origin,
        x = x + (originPosition?.x ?: 0),
        y = y + (originPosition?.y ?: 0),
    )
}

private fun List<PrimitiveRenderInstruction>.mergeAdjacentFills(
    excludedPaths: Set<String>,
    entries: MutableList<PrimitiveOptimizationEntry>,
): List<PrimitiveRenderInstruction> {
    val result = ArrayList<PrimitiveRenderInstruction>(size)
    var index = 0
    while (index < size) {
        val current = this[index]
        val next = getOrNull(index + 1)
        if (
            next != null &&
            current.path !in excludedPaths &&
            next.path !in excludedPaths &&
            current.canMergeWith(next)
        ) {
            val merged = current.mergeWith(next)
            entries +=
                PrimitiveOptimizationEntry.MergedAdjacentFills(
                    firstPath = current.path,
                    secondPath = next.path,
                    mergedPath = merged.path,
                )
            result += merged
            index += 2
        } else {
            result += current
            index++
        }
    }
    return result
}

private fun PrimitiveRenderInstruction.canMergeWith(other: PrimitiveRenderInstruction): Boolean {
    val first = op as? PrimitiveRenderOp.FillRect ?: return false
    val second = other.op as? PrimitiveRenderOp.FillRect ?: return false
    return visible == null &&
        origin == null &&
        other.visible == null &&
        other.origin == null &&
        first.y == second.y &&
        first.height == second.height &&
        first.x + first.width == second.x &&
        first.color == second.color
}

private fun PrimitiveRenderInstruction.mergeWith(other: PrimitiveRenderInstruction): PrimitiveRenderInstruction {
    val first = op as PrimitiveRenderOp.FillRect
    val second = other.op as PrimitiveRenderOp.FillRect
    return copy(
        op =
            first.copy(
                width = first.width + second.width,
            ),
    )
}

private fun PrimitiveValueExpression?.asConstantPosition(): Position? =
    (this as? PrimitiveValueExpression.Constant)?.value as? Position

private fun PrimitiveRenderOp.shifted(
    dx: Int,
    dy: Int,
): PrimitiveRenderOp =
    when (this) {
        is PrimitiveRenderOp.FillRect -> copy(x = x + dx, y = y + dy)
        is PrimitiveRenderOp.DrawText -> copy(x = x + dx, y = y + dy)
        is PrimitiveRenderOp.DrawTerminalSurface -> copy(x = x + dx, y = y + dy)
        is PrimitiveRenderOp.PushClip -> copy(x = x + dx, y = y + dy)
        PrimitiveRenderOp.PopClip -> this
        is PrimitiveRenderOp.DrawCodeEditor -> copy(x = x + dx, y = y + dy)
    }
