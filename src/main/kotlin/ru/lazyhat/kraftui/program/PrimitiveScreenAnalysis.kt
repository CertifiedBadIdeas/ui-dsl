package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.modifier.Position
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy
import ru.lazyhat.kraftui.text.TextLayouter

data class PrimitiveProgramAnalysisOptions(
    val measureText: (String) -> Int = String::length,
    val target: PrimitiveTargetCapabilities = PrimitiveTargetCapabilities.generic,
    val rejectOverlappingInputRegions: Boolean = false,
)

data class PrimitiveTargetCapabilities(
    val name: String,
    val supportsTerminalSurface: Boolean = true,
    val supportsCodeEditor: Boolean = true,
) {
    companion object {
        val generic: PrimitiveTargetCapabilities =
            PrimitiveTargetCapabilities(name = "generic")

        val minecraftGuiGraphics: PrimitiveTargetCapabilities =
            PrimitiveTargetCapabilities(
                name = "minecraft-gui-graphics",
                supportsTerminalSurface = false,
                supportsCodeEditor = false,
            )
    }
}

data class PrimitiveProgramAnalysisReport(
    val diagnostics: List<PrimitiveProgramDiagnostic>,
) {
    val isValid: Boolean
        get() = diagnostics.isEmpty()

    fun asText(): String =
        if (diagnostics.isEmpty()) {
            "No primitive program diagnostics"
        } else {
            diagnostics.joinToString(separator = "\n") { it.asText() }
        }
}

sealed interface PrimitiveProgramDiagnostic {
    val path: String

    data class InvalidRenderBounds(
        override val path: String,
        val width: Int,
        val height: Int,
    ) : PrimitiveProgramDiagnostic

    data class InvalidInputBounds(
        override val path: String,
        val width: Int,
        val height: Int,
    ) : PrimitiveProgramDiagnostic

    data class TextWidthOverflow(
        override val path: String,
        val text: String,
        val width: Int,
        val textWidth: Int,
        val policy: TextOverflowPolicy,
    ) : PrimitiveProgramDiagnostic

    data class TextHeightOverflow(
        override val path: String,
        val text: String,
        val height: Int,
        val textHeight: Int,
        val lineCount: Int,
        val policy: TextOverflowPolicy,
    ) : PrimitiveProgramDiagnostic

    data class DynamicTextRequiresRuntimeSafeOverflow(
        override val path: String,
        val policy: TextOverflowPolicy,
    ) : PrimitiveProgramDiagnostic

    data class UnsupportedTargetOperation(
        override val path: String,
        val target: String,
        val operation: String,
    ) : PrimitiveProgramDiagnostic

    data class MissingBakedTexture(
        override val path: String,
        val textureId: String,
    ) : PrimitiveProgramDiagnostic

    data class DuplicateBakedTextureId(
        override val path: String,
        val textureId: String,
    ) : PrimitiveProgramDiagnostic

    data class UnreachableInputRegion(
        override val path: String,
        val reason: String,
    ) : PrimitiveProgramDiagnostic

    data class OverlappingInputRegions(
        val firstPath: String,
        val secondPath: String,
    ) : PrimitiveProgramDiagnostic {
        override val path: String = "$firstPath / $secondPath"
    }
}

fun PrimitiveScreenProgram.analyze(options: PrimitiveProgramAnalysisOptions = PrimitiveProgramAnalysisOptions()): PrimitiveProgramAnalysisReport {
    val diagnostics = ArrayList<PrimitiveProgramDiagnostic>()
    val duplicateBakedTextureIds = bakedTextures.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
    duplicateBakedTextureIds.forEach { textureId ->
        diagnostics.add(
            PrimitiveProgramDiagnostic.DuplicateBakedTextureId(
                path = "bakedTextures/$textureId",
                textureId = textureId,
            ),
        )
    }
    val bakedTextureIds = bakedTextures.mapTo(HashSet()) { it.id }

    renderInstructions.forEach { instruction ->
        diagnostics.analyzeRenderInstruction(instruction, options, bakedTextureIds)
    }
    inputInstructions.forEach { instruction ->
        diagnostics.analyzeInputInstruction(instruction)
    }
    if (options.rejectOverlappingInputRegions) {
        diagnostics.analyzeInputOverlaps(inputInstructions)
    }

    return PrimitiveProgramAnalysisReport(diagnostics)
}

private fun MutableList<PrimitiveProgramDiagnostic>.analyzeRenderInstruction(
    instruction: PrimitiveRenderInstruction,
    options: PrimitiveProgramAnalysisOptions,
    bakedTextureIds: Set<String>,
) {
    when (val op = instruction.op) {
        is PrimitiveRenderOp.FillRect -> {
            requireValidRenderBounds(instruction.path, op.width, op.height)
        }
        is PrimitiveRenderOp.DrawText -> {
            requireValidRenderBounds(instruction.path, op.width, op.height)
            analyzeText(instruction.path, op, options.measureText)
        }
        is PrimitiveRenderOp.DrawTerminalSurface -> {
            requireValidRenderBounds(instruction.path, op.width, op.height)
            if (!options.target.supportsTerminalSurface) {
                add(
                    PrimitiveProgramDiagnostic.UnsupportedTargetOperation(
                        path = instruction.path,
                        target = options.target.name,
                        operation = "DrawTerminalSurface",
                    ),
                )
            }
        }
        is PrimitiveRenderOp.PushClip -> {
            requireValidRenderBounds(instruction.path, op.width, op.height)
        }
        PrimitiveRenderOp.PopClip -> Unit
        is PrimitiveRenderOp.DrawCodeEditor -> {
            requireValidRenderBounds(instruction.path, op.width, op.height)
            if (!options.target.supportsCodeEditor) {
                add(
                    PrimitiveProgramDiagnostic.UnsupportedTargetOperation(
                        path = instruction.path,
                        target = options.target.name,
                        operation = "DrawCodeEditor",
                    ),
                )
            }
        }
        is PrimitiveRenderOp.DrawBakedTexture -> {
            requireValidRenderBounds(instruction.path, op.width, op.height)
            if (op.textureId !in bakedTextureIds) {
                add(
                    PrimitiveProgramDiagnostic.MissingBakedTexture(
                        path = instruction.path,
                        textureId = op.textureId,
                    ),
                )
            }
        }
        is PrimitiveRenderOp.DrawTextureRegion -> {
            requireValidRenderBounds(instruction.path, op.width, op.height)
        }
    }
}

private fun MutableList<PrimitiveProgramDiagnostic>.requireValidRenderBounds(
    path: String,
    width: Int,
    height: Int,
) {
    if (width <= 0 || height <= 0) {
        add(
            PrimitiveProgramDiagnostic.InvalidRenderBounds(
                path = path,
                width = width,
                height = height,
            ),
        )
    }
}

private fun MutableList<PrimitiveProgramDiagnostic>.analyzeText(
    path: String,
    op: PrimitiveRenderOp.DrawText,
    measureText: (String) -> Int,
) {
    when (val text = op.text) {
        is PrimitiveValueExpression.Constant -> {
            val value = text.value as? String ?: return
            val layout =
                TextLayouter(measureText).layout(
                    text = value,
                    width = op.width,
                    flow = op.flow,
                    overflow = op.overflow,
                )
            if (op.overflow == TextOverflowPolicy.FailInValidation) {
                layout.lines.firstOrNull { it.width > op.width }?.let { line ->
                    add(
                        PrimitiveProgramDiagnostic.TextWidthOverflow(
                            path = path,
                            text = value,
                            width = op.width,
                            textWidth = line.width,
                            policy = op.overflow,
                        ),
                    )
                }
                if (layout.requiredHeight > op.height) {
                    add(
                        PrimitiveProgramDiagnostic.TextHeightOverflow(
                            path = path,
                            text = value,
                            height = op.height,
                            textHeight = layout.requiredHeight,
                            lineCount = layout.sourceLineCount,
                            policy = op.overflow,
                        ),
                    )
                }
            }
        }
        is PrimitiveValueExpression.StateField -> {
            if (op.overflow == TextOverflowPolicy.FailInValidation) {
                add(
                    PrimitiveProgramDiagnostic.DynamicTextRequiresRuntimeSafeOverflow(
                        path = path,
                        policy = op.overflow,
                    ),
                )
            }
        }
        is PrimitiveValueExpression.And -> Unit
        is PrimitiveValueExpression.Match -> {
            if (op.overflow == TextOverflowPolicy.FailInValidation) {
                add(
                    PrimitiveProgramDiagnostic.DynamicTextRequiresRuntimeSafeOverflow(
                        path = path,
                        policy = op.overflow,
                    ),
                )
            }
        }
    }
}

private fun MutableList<PrimitiveProgramDiagnostic>.analyzeInputInstruction(instruction: PrimitiveInputInstruction) {
    when (instruction) {
        is PrimitiveInputInstruction.ClickRegion -> {
            if (instruction.width <= 0 || instruction.height <= 0) {
                add(
                    PrimitiveProgramDiagnostic.InvalidInputBounds(
                        path = instruction.path,
                        width = instruction.width,
                        height = instruction.height,
                    ),
                )
            }
            if (instruction.visible == PrimitiveValueExpression.Constant(false)) {
                add(
                    PrimitiveProgramDiagnostic.UnreachableInputRegion(
                        path = instruction.path,
                        reason = "visibility is always false",
                    ),
                )
            }
        }
    }
}

private fun MutableList<PrimitiveProgramDiagnostic>.analyzeInputOverlaps(inputInstructions: List<PrimitiveInputInstruction>) {
    val regions =
        inputInstructions.mapNotNull { instruction ->
            when (instruction) {
                is PrimitiveInputInstruction.ClickRegion -> instruction.staticRect()
            }
        }

    for (firstIndex in regions.indices) {
        for (secondIndex in firstIndex + 1 until regions.size) {
            val first = regions[firstIndex]
            val second = regions[secondIndex]
            if (first.overlaps(second)) {
                add(
                    PrimitiveProgramDiagnostic.OverlappingInputRegions(
                        firstPath = first.path,
                        secondPath = second.path,
                    ),
                )
            }
        }
    }
}

private fun PrimitiveInputInstruction.ClickRegion.staticRect(): PrimitiveRect? {
    if (width <= 0 || height <= 0) return null
    val origin =
        when (origin) {
            null -> Position.Zero
            is PrimitiveValueExpression.Constant -> origin.value as? Position ?: return null
            is PrimitiveValueExpression.StateField -> return null
            is PrimitiveValueExpression.And -> return null
            is PrimitiveValueExpression.Match -> return null
        }
    return PrimitiveRect(
        path = path,
        left = x + origin.x,
        top = y + origin.y,
        right = x + origin.x + width,
        bottom = y + origin.y + height,
    )
}

private data class PrimitiveRect(
    val path: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun overlaps(other: PrimitiveRect): Boolean =
        left < other.right &&
            right > other.left &&
            top < other.bottom &&
            bottom > other.top
}

private fun PrimitiveProgramDiagnostic.asText(): String =
    when (this) {
        is PrimitiveProgramDiagnostic.InvalidRenderBounds ->
            "$path: invalid render bounds ${width}x$height"
        is PrimitiveProgramDiagnostic.InvalidInputBounds ->
            "$path: invalid input bounds ${width}x$height"
        is PrimitiveProgramDiagnostic.TextWidthOverflow ->
            "$path: text width overflow, text width $textWidth px, available $width px, policy $policy"
        is PrimitiveProgramDiagnostic.TextHeightOverflow ->
            "$path: text height overflow, text height $textHeight px across $lineCount lines, available $height px, policy $policy"
        is PrimitiveProgramDiagnostic.DynamicTextRequiresRuntimeSafeOverflow ->
            "$path: dynamic text requires runtime-safe overflow policy, policy $policy"
        is PrimitiveProgramDiagnostic.UnsupportedTargetOperation ->
            "$path: target $target does not support $operation"
        is PrimitiveProgramDiagnostic.MissingBakedTexture ->
            "$path: missing baked texture $textureId"
        is PrimitiveProgramDiagnostic.DuplicateBakedTextureId ->
            "$path: duplicate baked texture id $textureId"
        is PrimitiveProgramDiagnostic.UnreachableInputRegion ->
            "$path: unreachable input region, $reason"
        is PrimitiveProgramDiagnostic.OverlappingInputRegions ->
            "$firstPath overlaps $secondPath"
    }
