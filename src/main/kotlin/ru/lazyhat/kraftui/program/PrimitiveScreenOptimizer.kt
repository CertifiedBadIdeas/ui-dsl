package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.Position

data class PrimitiveOptimizationOptions(
    val enabled: Boolean = true,
    val passes: Set<PrimitiveOptimizationPass> = PrimitiveOptimizationPass.default,
    val disabledRegions: Set<String> = emptySet(),
    val staticTextureBaking: PrimitiveStaticTextureBakingOptions = PrimitiveStaticTextureBakingOptions.Disabled,
) {
    fun enables(pass: PrimitiveOptimizationPass): Boolean = enabled && pass in passes
}

sealed interface PrimitiveStaticTextureBakingOptions {
    data object Disabled : PrimitiveStaticTextureBakingOptions

    data class Enabled(
        val minInstructionCount: Int = 8,
        val maxTexturePixels: Int = 256 * 256,
        val textureNamespace: String = "kraftui",
        val texturePathPrefix: String = "textures/gui/generated",
    ) : PrimitiveStaticTextureBakingOptions {
        init {
            require(minInstructionCount > 1) { "Static texture baking minInstructionCount must be greater than one" }
            require(maxTexturePixels > 0) { "Static texture baking maxTexturePixels must be positive" }
            require(textureNamespace.isNotBlank()) { "Static texture baking textureNamespace must not be blank" }
            require(texturePathPrefix.isNotBlank()) { "Static texture baking texturePathPrefix must not be blank" }
            require(!texturePathPrefix.startsWith('/')) { "Static texture baking texturePathPrefix must be relative" }
            require(!texturePathPrefix.endsWith('/')) { "Static texture baking texturePathPrefix must not end with '/'" }
        }
    }
}

enum class PrimitiveOptimizationPass {
    DeadBranchElimination,
    ConstantFolding,
    AdjacentFillMerging,
    VisibilityBlockGrouping,
    HitRegionPrecompute,
    TextLayoutCaching,
    StaticTextureBaking,
    ;

    companion object {
        val primitiveProgramPasses: Set<PrimitiveOptimizationPass> =
            setOf(
                DeadBranchElimination,
                ConstantFolding,
                AdjacentFillMerging,
            )

        val default: Set<PrimitiveOptimizationPass> =
            setOf(
                DeadBranchElimination,
                ConstantFolding,
                AdjacentFillMerging,
                VisibilityBlockGrouping,
                HitRegionPrecompute,
                TextLayoutCaching,
            )
    }
}

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
    val applied: List<PrimitiveAppliedOptimization> = emptyList(),
    val skipped: List<PrimitiveSkippedOptimization> = emptyList(),
    val warnings: List<PrimitiveOptimizationWarning> = emptyList(),
) {
    val entries: List<PrimitiveOptimizationEntry>
        get() = applied + skipped + warnings

    val changed: Boolean
        get() = applied.isNotEmpty()

    operator fun plus(other: PrimitiveOptimizationReport): PrimitiveOptimizationReport =
        PrimitiveOptimizationReport(
            applied = applied + other.applied,
            skipped = skipped + other.skipped,
            warnings = warnings + other.warnings,
        )
}

sealed interface PrimitiveOptimizationEntry

sealed interface PrimitiveAppliedOptimization : PrimitiveOptimizationEntry {
    data class RemovedAlwaysInvisibleInstruction(
        val path: String,
    ) : PrimitiveAppliedOptimization

    data class FoldedConstantVisibility(
        val path: String,
        val visible: Boolean,
    ) : PrimitiveAppliedOptimization

    data class FoldedConstantOrigin(
        val path: String,
        val x: Int,
        val y: Int,
    ) : PrimitiveAppliedOptimization

    data class MergedAdjacentFills(
        val firstPath: String,
        val secondPath: String,
        val mergedPath: String,
    ) : PrimitiveAppliedOptimization

    data class GroupedVisibilityBlock(
        val visibleExpression: String,
        val instructionCount: Int,
    ) : PrimitiveAppliedOptimization

    data class CachedTextLayout(
        val drawTextInstructionCount: Int,
    ) : PrimitiveAppliedOptimization

    data class PrecomputedHitRegions(
        val regionCount: Int,
    ) : PrimitiveAppliedOptimization

    data class BakedStaticTexture(
        val textureId: String,
        val firstPath: String,
        val lastPath: String,
        val width: Int,
        val height: Int,
        val instructionCount: Int,
    ) : PrimitiveAppliedOptimization
}

sealed interface PrimitiveSkippedOptimization : PrimitiveOptimizationEntry {
    data object OptimizationDisabled : PrimitiveSkippedOptimization

    data class SkippedByRegion(
        val path: String,
    ) : PrimitiveSkippedOptimization

    data class PassDisabled(
        val pass: PrimitiveOptimizationPass,
    ) : PrimitiveSkippedOptimization
}

sealed interface PrimitiveOptimizationWarning : PrimitiveOptimizationEntry {
    data class UnsupportedPass(
        val pass: PrimitiveOptimizationPass,
        val targetId: String,
    ) : PrimitiveOptimizationWarning
}

fun PrimitiveScreenProgram.optimizePrimitive(options: PrimitiveOptimizationOptions = PrimitiveOptimizationOptions()): PrimitiveOptimizationResult {
    if (!options.enabled) {
        return PrimitiveOptimizationResult(
            program = this,
            report =
                PrimitiveOptimizationReport(
                    skipped = listOf(PrimitiveSkippedOptimization.OptimizationDisabled),
                ),
        )
    }

    val report = PrimitiveOptimizationReportBuilder(options)
    val optimizedRenderInstructionsBeforeBaking =
        renderInstructions.mapNotNull { instruction ->
            if (instruction.path in options.disabledRegions) {
                report.skipped += PrimitiveSkippedOptimization.SkippedByRegion(instruction.path)
                instruction
            } else {
                instruction.optimize(options, report)
            }
        }.let { instructions ->
            if (options.enables(PrimitiveOptimizationPass.AdjacentFillMerging)) {
                instructions.mergeAdjacentFills(
                    disabledRegions = options.disabledRegions,
                    report = report,
                )
            } else {
                instructions
            }
        }

    val bakedRender =
        if (options.enables(PrimitiveOptimizationPass.StaticTextureBaking)) {
            optimizedRenderInstructionsBeforeBaking.bakeStaticTextures(
                options = options.staticTextureBaking,
                disabledRegions = options.disabledRegions,
                firstTextureIndex = bakedTextures.size,
                report = report,
            )
        } else {
            StaticTextureBakeResult(optimizedRenderInstructionsBeforeBaking, emptyList())
        }

    val optimizedInputInstructions =
        inputInstructions.mapNotNull { instruction ->
            val path = instruction.path()
            if (path in options.disabledRegions) {
                report.skipped += PrimitiveSkippedOptimization.SkippedByRegion(path)
                instruction
            } else {
                instruction.optimize(options, report)
            }
        }

    report.recordDisabledPasses()
    return PrimitiveOptimizationResult(
        program =
            PrimitiveScreenProgram(
                renderInstructions = bakedRender.instructions,
                inputInstructions = optimizedInputInstructions,
                bakedTextures = bakedTextures + bakedRender.textures,
            ),
        report = report.build(),
    )
}

private class PrimitiveOptimizationReportBuilder(
    private val options: PrimitiveOptimizationOptions,
) {
    val applied: MutableList<PrimitiveAppliedOptimization> = ArrayList()
    val skipped: MutableList<PrimitiveSkippedOptimization> = ArrayList()
    val warnings: MutableList<PrimitiveOptimizationWarning> = ArrayList()

    fun recordDisabledPasses() {
        PrimitiveOptimizationPass.primitiveProgramPasses
            .filter { it !in options.passes }
            .forEach { pass ->
                skipped += PrimitiveSkippedOptimization.PassDisabled(pass)
            }
    }

    fun build(): PrimitiveOptimizationReport =
        PrimitiveOptimizationReport(
            applied = applied,
            skipped = skipped,
            warnings = warnings,
        )
}

private fun PrimitiveRenderInstruction.optimize(
    options: PrimitiveOptimizationOptions,
    report: PrimitiveOptimizationReportBuilder,
): PrimitiveRenderInstruction? {
    when (visible) {
        PrimitiveValueExpression.Constant(false) -> {
            if (options.enables(PrimitiveOptimizationPass.DeadBranchElimination)) {
                report.applied += PrimitiveAppliedOptimization.RemovedAlwaysInvisibleInstruction(path)
                return null
            }
        }
        PrimitiveValueExpression.Constant(true) -> {
            if (options.enables(PrimitiveOptimizationPass.ConstantFolding)) {
                report.applied += PrimitiveAppliedOptimization.FoldedConstantVisibility(path, visible = true)
            }
        }
        null,
        is PrimitiveValueExpression.StateField,
        -> Unit
        is PrimitiveValueExpression.Constant -> Unit
    }

    val foldedVisibility =
        when {
            !options.enables(PrimitiveOptimizationPass.ConstantFolding) -> visible
            visible == PrimitiveValueExpression.Constant(true) -> null
            else -> visible
        }
    val originPosition = origin.asConstantPosition()
    val foldedOp =
        if (originPosition != null && options.enables(PrimitiveOptimizationPass.ConstantFolding)) {
            report.applied += PrimitiveAppliedOptimization.FoldedConstantOrigin(path, originPosition.x, originPosition.y)
            op.shifted(originPosition.x, originPosition.y)
        } else {
            op
        }

    return copy(
        visible = foldedVisibility,
        origin = if (originPosition != null && options.enables(PrimitiveOptimizationPass.ConstantFolding)) null else origin,
        op = foldedOp,
    )
}

private fun PrimitiveInputInstruction.optimize(
    options: PrimitiveOptimizationOptions,
    report: PrimitiveOptimizationReportBuilder,
): PrimitiveInputInstruction? =
    when (this) {
        is PrimitiveInputInstruction.ClickRegion -> optimize(options, report)
    }

private fun PrimitiveInputInstruction.path(): String =
    when (this) {
        is PrimitiveInputInstruction.ClickRegion -> path
    }

private fun PrimitiveInputInstruction.ClickRegion.optimize(
    options: PrimitiveOptimizationOptions,
    report: PrimitiveOptimizationReportBuilder,
): PrimitiveInputInstruction.ClickRegion? {
    when (visible) {
        PrimitiveValueExpression.Constant(false) -> {
            if (options.enables(PrimitiveOptimizationPass.DeadBranchElimination)) {
                report.applied += PrimitiveAppliedOptimization.RemovedAlwaysInvisibleInstruction(path)
                return null
            }
        }
        PrimitiveValueExpression.Constant(true) -> {
            if (options.enables(PrimitiveOptimizationPass.ConstantFolding)) {
                report.applied += PrimitiveAppliedOptimization.FoldedConstantVisibility(path, visible = true)
            }
        }
        null,
        is PrimitiveValueExpression.StateField,
        -> Unit
        is PrimitiveValueExpression.Constant -> Unit
    }

    val originPosition = origin.asConstantPosition()
    if (originPosition != null && options.enables(PrimitiveOptimizationPass.ConstantFolding)) {
        report.applied += PrimitiveAppliedOptimization.FoldedConstantOrigin(path, originPosition.x, originPosition.y)
    }
    val foldConstants = options.enables(PrimitiveOptimizationPass.ConstantFolding)

    return copy(
        visible =
            when {
                !foldConstants -> visible
                visible == PrimitiveValueExpression.Constant(true) -> null
                else -> visible
            },
        origin = if (originPosition != null && foldConstants) null else origin,
        x = x + if (foldConstants) originPosition?.x ?: 0 else 0,
        y = y + if (foldConstants) originPosition?.y ?: 0 else 0,
    )
}

private fun List<PrimitiveRenderInstruction>.mergeAdjacentFills(
    disabledRegions: Set<String>,
    report: PrimitiveOptimizationReportBuilder,
): List<PrimitiveRenderInstruction> {
    val result = ArrayList<PrimitiveRenderInstruction>(size)
    var index = 0
    while (index < size) {
        val current = this[index]
        val next = getOrNull(index + 1)
        if (
            next != null &&
            current.path !in disabledRegions &&
            next.path !in disabledRegions &&
            current.canMergeWith(next)
        ) {
            val merged = current.mergeWith(next)
            report.applied +=
                PrimitiveAppliedOptimization.MergedAdjacentFills(
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

private data class StaticTextureBakeResult(
    val instructions: List<PrimitiveRenderInstruction>,
    val textures: List<PrimitiveBakedTexture>,
)

private data class StaticTextureBakeCandidate(
    val instruction: PrimitiveRenderInstruction,
    val fill: PrimitiveRenderOp.FillRect,
    val color: Color,
)

private fun List<PrimitiveRenderInstruction>.bakeStaticTextures(
    options: PrimitiveStaticTextureBakingOptions,
    disabledRegions: Set<String>,
    firstTextureIndex: Int,
    report: PrimitiveOptimizationReportBuilder,
): StaticTextureBakeResult {
    if (options !is PrimitiveStaticTextureBakingOptions.Enabled) {
        return StaticTextureBakeResult(this, emptyList())
    }

    val instructions = ArrayList<PrimitiveRenderInstruction>(size)
    val textures = ArrayList<PrimitiveBakedTexture>()
    var index = 0
    while (index < size) {
        val run = ArrayList<StaticTextureBakeCandidate>()
        var cursor = index
        while (cursor < size) {
            val candidate = this[cursor].staticTextureBakeCandidate(disabledRegions) ?: break
            run += candidate
            cursor++
        }

        val baked = run.tryBake(
            options = options,
            textureId = "baked_${firstTextureIndex + textures.size}",
            report = report,
        )
        if (baked == null) {
            instructions += this[index]
            index++
        } else {
            instructions += baked.instruction
            textures += baked.texture
            index += run.size
        }
    }

    return StaticTextureBakeResult(instructions, textures)
}

private data class BakedStaticRun(
    val instruction: PrimitiveRenderInstruction,
    val texture: PrimitiveBakedTexture,
)

private fun PrimitiveRenderInstruction.staticTextureBakeCandidate(disabledRegions: Set<String>): StaticTextureBakeCandidate? {
    if (path in disabledRegions || visible != null || origin != null) return null
    val fill = op as? PrimitiveRenderOp.FillRect ?: return null
    val color = fill.color.asConstantColor() ?: return null
    if (fill.width <= 0 || fill.height <= 0) return null
    return StaticTextureBakeCandidate(
        instruction = this,
        fill = fill,
        color = color,
    )
}

private fun List<StaticTextureBakeCandidate>.tryBake(
    options: PrimitiveStaticTextureBakingOptions.Enabled,
    textureId: String,
    report: PrimitiveOptimizationReportBuilder,
): BakedStaticRun? {
    if (size < options.minInstructionCount) return null
    val left = minOf { it.fill.x }
    val top = minOf { it.fill.y }
    val right = maxOf { it.fill.x + it.fill.width }
    val bottom = maxOf { it.fill.y + it.fill.height }
    val width = right - left
    val height = bottom - top
    val pixelCount = width.toLong() * height.toLong()
    if (width <= 0 || height <= 0 || pixelCount > options.maxTexturePixels) return null

    val pixels = IntArray(width * height)
    for (candidate in this) {
        val fill = candidate.fill
        val color = candidate.color.value.toInt()
        for (py in fill.y until fill.y + fill.height) {
            val row = (py - top) * width
            for (px in fill.x until fill.x + fill.width) {
                pixels[row + (px - left)] = color
            }
        }
    }

    val first = first().instruction
    val last = last().instruction
    report.applied +=
        PrimitiveAppliedOptimization.BakedStaticTexture(
            textureId = textureId,
            firstPath = first.path,
            lastPath = last.path,
            width = width,
            height = height,
            instructionCount = size,
        )

    return BakedStaticRun(
        instruction =
            PrimitiveRenderInstruction(
                path = "${first.path}..${last.path}",
                visible = null,
                origin = null,
                op =
                    PrimitiveRenderOp.DrawBakedTexture(
                        x = left,
                        y = top,
                        width = width,
                        height = height,
                        textureId = textureId,
                    ),
            ),
        texture =
            PrimitiveBakedTexture(
                id = textureId,
                width = width,
                height = height,
                argb = pixels,
            ),
    )
}

private fun PrimitiveValueExpression.asConstantColor(): Color? =
    (this as? PrimitiveValueExpression.Constant)?.value as? Color

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
        is PrimitiveRenderOp.DrawBakedTexture -> copy(x = x + dx, y = y + dy)
    }
