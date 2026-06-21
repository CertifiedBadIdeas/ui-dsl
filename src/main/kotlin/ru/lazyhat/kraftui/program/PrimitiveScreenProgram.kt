package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.modifier.TextAlignment
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy
import ru.lazyhat.kraftui.text.TextFlow

data class PrimitiveScreenProgram(
    val renderInstructions: List<PrimitiveRenderInstruction>,
    val inputInstructions: List<PrimitiveInputInstruction>,
)

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
    }
