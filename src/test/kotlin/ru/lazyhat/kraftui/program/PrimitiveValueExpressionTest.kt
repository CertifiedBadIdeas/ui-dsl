package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.matchValue
import ru.lazyhat.kraftui.foundation.stateValue
import ru.lazyhat.kraftui.foundation.value
import ru.lazyhat.kraftui.foundation.modifier.Modifier
import ru.lazyhat.kraftui.foundation.modifier.background
import ru.lazyhat.kraftui.foundation.modifier.size
import ru.lazyhat.kraftui.foundation.uiActions
import kotlin.test.Test
import kotlin.test.assertTrue

class PrimitiveValueExpressionTest {
    enum class VisualState {
        Normal,
        Selected,
    }

    data class ScreenState(
        val visualState: VisualState,
    )

    @Test
    fun matchValueLowersToPrimitiveMatchExpression() {
        var state = ScreenState(VisualState.Selected)
        val root =
            uiActions<Unit>(Modifier.size(10, 10)) {
                box(
                    Modifier
                        .size(10, 10)
                        .background(
                            matchValue(
                                subject = stateValue(ScreenState::visualState) { state },
                                cases =
                                    mapOf(
                                        VisualState.Normal to value(Color.Black),
                                        VisualState.Selected to value(Color.Green),
                                    ),
                                default = value(Color.Red),
                            ),
                        ),
                )
            }

        val primitive = ScreenProgramCompiler().compile(root).toPrimitiveScreenProgram()

        assertTrue(
            primitive.renderInstructions.any {
                (it.op as? PrimitiveRenderOp.FillRect)?.color is PrimitiveValueExpression.Match
            },
        )
    }

    @Test
    fun minecraftGeneratorEmitsWhenForMatchColorExpression() {
        val program =
            PrimitiveScreenProgram(
                renderInstructions =
                    listOf(
                        PrimitiveRenderInstruction(
                            path = "box",
                            visible = null,
                            origin = null,
                            op =
                                PrimitiveRenderOp.FillRect(
                                    x = 0,
                                    y = 0,
                                    width = 10,
                                    height = 10,
                                    color =
                                        PrimitiveValueExpression.Match(
                                            subject = PrimitiveValueExpression.StateField("visualState"),
                                            cases =
                                                mapOf(
                                                    VisualState.Normal to PrimitiveValueExpression.Constant(Color.Black),
                                                    VisualState.Selected to PrimitiveValueExpression.Constant(Color.Green),
                                                ),
                                            default = PrimitiveValueExpression.Constant(Color.Red),
                                        ),
                                ),
                        ),
                    ),
                inputInstructions = emptyList(),
            )

        val generated =
            program.generateMinecraftScreenSource(
                packageName = "test",
                className = "Generated",
                stateType = "State",
                actionType = "Action",
            )

        assertTrue("when (state.visualState)" in generated.source)
        assertTrue("VisualState.Selected -> 0xFF00FF00.toInt()" in generated.source)
    }
}
