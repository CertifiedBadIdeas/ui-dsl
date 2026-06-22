package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.modifier.TextAlignment
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy
import ru.lazyhat.kraftui.text.TextFlow

data class PrimitiveScreenProgram(
    val renderInstructions: List<PrimitiveRenderInstruction>,
    val inputInstructions: List<PrimitiveInputInstruction>,
    val bakedTextures: List<PrimitiveBakedTexture> = emptyList(),
)

data class PrimitiveBakedTexture(
    val id: String,
    val width: Int,
    val height: Int,
    val argb: IntArray,
) {
    init {
        require(id.isNotBlank()) { "Baked texture id must not be blank" }
        require(width > 0) { "Baked texture width must be positive" }
        require(height > 0) { "Baked texture height must be positive" }
        require(argb.size == width * height) {
            "Baked texture pixel count must be exactly width * height"
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is PrimitiveBakedTexture &&
            id == other.id &&
            width == other.width &&
            height == other.height &&
            argb.contentEquals(other.argb)

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + argb.contentHashCode()
        return result
    }
}

data class PrimitiveTextureRegion(
    val namespace: String,
    val path: String,
    val atlasWidth: Int,
    val atlasHeight: Int,
    val sourceX: Int,
    val sourceY: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
) {
    val resourceKey: String
        get() = "$namespace:$path"

    init {
        require(namespace.isNotBlank()) { "texture namespace must not be blank" }
        require(path.isNotBlank()) { "texture path must not be blank" }
        require(!path.startsWith('/')) { "texture path must be relative" }
        require(atlasWidth > 0) { "texture atlas width must be positive" }
        require(atlasHeight > 0) { "texture atlas height must be positive" }
        require(sourceX >= 0) { "texture sourceX must be non-negative" }
        require(sourceY >= 0) { "texture sourceY must be non-negative" }
        require(sourceWidth > 0) { "texture sourceWidth must be positive" }
        require(sourceHeight > 0) { "texture sourceHeight must be positive" }
        require(sourceX + sourceWidth <= atlasWidth) { "texture source region must fit atlas width" }
        require(sourceY + sourceHeight <= atlasHeight) { "texture source region must fit atlas height" }
    }
}

enum class PrimitiveTextureScaling {
    Stretch,
    Tile,
}

val PrimitiveScreenProgram.dependencies: PrimitiveProgramDependencies
    get() =
        renderInstructions.fold(PrimitiveProgramDependencies.Static) { acc, instruction ->
            acc + instruction.dependencies
        } +
            inputInstructions.fold(PrimitiveProgramDependencies.Static) { acc, instruction ->
                acc + instruction.dependencies
            }

data class PrimitiveProgramDependencies(
    val dynamicValue: Boolean = false,
    val dynamicVisibility: Boolean = false,
    val dynamicOrigin: Boolean = false,
    val dynamicInput: Boolean = false,
) {
    val isStatic: Boolean
        get() = !dynamicValue && !dynamicVisibility && !dynamicOrigin && !dynamicInput

    operator fun plus(other: PrimitiveProgramDependencies): PrimitiveProgramDependencies =
        PrimitiveProgramDependencies(
            dynamicValue = dynamicValue || other.dynamicValue,
            dynamicVisibility = dynamicVisibility || other.dynamicVisibility,
            dynamicOrigin = dynamicOrigin || other.dynamicOrigin,
            dynamicInput = dynamicInput || other.dynamicInput,
        )

    companion object {
        val Static: PrimitiveProgramDependencies = PrimitiveProgramDependencies()
        val DynamicValue: PrimitiveProgramDependencies = PrimitiveProgramDependencies(dynamicValue = true)
        val DynamicVisibility: PrimitiveProgramDependencies = PrimitiveProgramDependencies(dynamicVisibility = true)
        val DynamicOrigin: PrimitiveProgramDependencies = PrimitiveProgramDependencies(dynamicOrigin = true)
        val DynamicInput: PrimitiveProgramDependencies = PrimitiveProgramDependencies(dynamicInput = true)
    }
}

sealed interface PrimitiveValueExpression {
    data class Constant(
        val value: Any?,
    ) : PrimitiveValueExpression

    data class StateField(
        val fieldName: String,
    ) : PrimitiveValueExpression

    data class And(
        val terms: List<PrimitiveValueExpression>,
    ) : PrimitiveValueExpression

    data class Match(
        val subject: PrimitiveValueExpression,
        val cases: Map<Any?, PrimitiveValueExpression>,
        val default: PrimitiveValueExpression,
    ) : PrimitiveValueExpression
}

data class PrimitiveRenderInstruction(
    val path: String,
    val visible: PrimitiveValueExpression?,
    val origin: PrimitiveValueExpression?,
    val op: PrimitiveRenderOp,
)

val PrimitiveRenderInstruction.dependencies: PrimitiveProgramDependencies
    get() =
        visible.primitiveDependency(PrimitiveProgramDependencies.DynamicVisibility) +
            origin.primitiveDependency(PrimitiveProgramDependencies.DynamicOrigin) +
            op.dependencies

sealed interface PrimitiveRenderOp {
    data class FillRect(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val color: PrimitiveValueExpression,
    ) : PrimitiveRenderOp

    data class DrawText(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val text: PrimitiveValueExpression,
        val color: PrimitiveValueExpression,
        val alignment: TextAlignment = TextAlignment.Start,
        val overflow: TextOverflowPolicy = TextOverflowPolicy.FailInValidation,
        val flow: TextFlow = TextFlow(),
    ) : PrimitiveRenderOp

    data class DrawTerminalSurface(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val snapshot: PrimitiveValueExpression,
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
        val viewModel: PrimitiveValueExpression,
        val fontWidth: Int,
        val fontHeight: Int,
    ) : PrimitiveRenderOp

    data class DrawBakedTexture(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val textureId: String,
    ) : PrimitiveRenderOp

    data class DrawTextureRegion(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val region: PrimitiveTextureRegion,
        val scaling: PrimitiveTextureScaling = PrimitiveTextureScaling.Stretch,
    ) : PrimitiveRenderOp
}

val PrimitiveRenderOp.dependencies: PrimitiveProgramDependencies
    get() =
        when (this) {
            is PrimitiveRenderOp.FillRect -> color.primitiveDependency(PrimitiveProgramDependencies.DynamicValue)
            is PrimitiveRenderOp.DrawText ->
                text.primitiveDependency(PrimitiveProgramDependencies.DynamicValue) +
                    color.primitiveDependency(PrimitiveProgramDependencies.DynamicValue)
            is PrimitiveRenderOp.DrawTerminalSurface -> snapshot.primitiveDependency(PrimitiveProgramDependencies.DynamicValue)
            is PrimitiveRenderOp.PushClip -> PrimitiveProgramDependencies.Static
            PrimitiveRenderOp.PopClip -> PrimitiveProgramDependencies.Static
            is PrimitiveRenderOp.DrawCodeEditor -> viewModel.primitiveDependency(PrimitiveProgramDependencies.DynamicValue)
            is PrimitiveRenderOp.DrawBakedTexture -> PrimitiveProgramDependencies.Static
            is PrimitiveRenderOp.DrawTextureRegion -> PrimitiveProgramDependencies.Static
        }

sealed interface PrimitiveInputInstruction {
    data class ClickRegion(
        val path: String,
        val visible: PrimitiveValueExpression?,
        val origin: PrimitiveValueExpression?,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val action: PrimitiveValueExpression?,
    ) : PrimitiveInputInstruction
}

val PrimitiveInputInstruction.dependencies: PrimitiveProgramDependencies
    get() =
        when (this) {
            is PrimitiveInputInstruction.ClickRegion ->
                visible.primitiveDependency(PrimitiveProgramDependencies.DynamicVisibility) +
                    origin.primitiveDependency(PrimitiveProgramDependencies.DynamicOrigin) +
                    action.primitiveDependency(PrimitiveProgramDependencies.DynamicInput)
        }

private fun PrimitiveValueExpression?.primitiveDependency(kind: PrimitiveProgramDependencies): PrimitiveProgramDependencies =
    when (this) {
        null,
        is PrimitiveValueExpression.Constant,
        -> PrimitiveProgramDependencies.Static
        is PrimitiveValueExpression.StateField -> kind
        is PrimitiveValueExpression.And ->
            if (terms.all { it is PrimitiveValueExpression.Constant }) {
                PrimitiveProgramDependencies.Static
            } else {
                kind
            }
        is PrimitiveValueExpression.Match ->
            subject.primitiveDependency(kind) +
                cases.values.fold(PrimitiveProgramDependencies.Static) { acc, expression ->
                    acc + expression.primitiveDependency(kind)
                } +
                default.primitiveDependency(kind)
    }
