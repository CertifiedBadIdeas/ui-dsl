package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.Modifier
import ru.lazyhat.kraftui.foundation.modifier.Position
import ru.lazyhat.kraftui.foundation.modifier.background
import ru.lazyhat.kraftui.foundation.modifier.offset
import ru.lazyhat.kraftui.foundation.modifier.size
import ru.lazyhat.kraftui.foundation.stateValue
import ru.lazyhat.kraftui.foundation.uiActions
import ru.lazyhat.kraftui.foundation.value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PrimitiveScreenProgramTest {
    private data class ScreenState(
        val title: String,
        val action: TestAction?,
        val origin: Position = Position.Zero,
        val selected: Boolean = false,
        val enabled: Boolean = false,
    )

    private sealed interface TestAction {
        data object Open : TestAction
    }

    @Test
    fun primitiveProgramContainsRenderAndInputInstructionsWithoutRenderOps() {
        val state = ScreenState("Zone 0", TestAction.Open)
        val screenProgram =
            ScreenProgramCompiler().compile(
                uiActions<TestAction>(Modifier.size(80, 30)) {
                    button(
                        modifier = Modifier.offset(2, 3).size(50, 20).background(Color.Blue),
                        action = stateValue(ScreenState::action) { state },
                    ) {
                        text(
                            modifier = Modifier.size(50, 20),
                            color = Color.White,
                            text = stateValue(ScreenState::title) { state },
                        )
                    }
                },
            )

        val primitive = screenProgram.toPrimitiveScreenProgram()

        assertEquals(
            listOf(
                PrimitiveRenderInstruction(
                    path = "frame[0].op[0]",
                    visible = null,
                    origin = null,
                    op =
                        PrimitiveRenderOp.FillRect(
                            x = 2,
                            y = 3,
                            width = 50,
                            height = 20,
                            color = PrimitiveValueExpression.Constant(Color.Blue),
                        ),
                ),
                PrimitiveRenderInstruction(
                    path = "frame[0].op[1]",
                    visible = null,
                    origin = null,
                    op =
                        PrimitiveRenderOp.DrawText(
                            x = 2,
                            y = 3,
                            width = 50,
                            height = 20,
                            text = PrimitiveValueExpression.StateField("title"),
                            color = PrimitiveValueExpression.Constant(Color.White),
                        ),
                ),
            ),
            primitive.renderInstructions,
        )
        assertEquals(
            listOf(
                PrimitiveInputInstruction.ClickRegion(
                    path = "hitRegion[root-0]",
                    visible = null,
                    origin = null,
                    x = 2,
                    y = 3,
                    width = 50,
                    height = 20,
                    action = PrimitiveValueExpression.StateField("action"),
                ),
            ),
            primitive.inputInstructions,
        )
        assertEquals(
            PrimitiveProgramDependencies(
                dynamicValue = true,
                dynamicInput = true,
            ),
            primitive.dependencies,
        )
    }

    @Test
    fun primitiveProgramRejectsRuntimeOnlyValues() {
        val screenProgram =
            ScreenProgramCompiler().compile(
                uiActions<TestAction>(Modifier.size(80, 30)) {
                    button(
                        modifier = Modifier.size(50, 20),
                        action = value { TestAction.Open },
                    ) {
                        text(
                            modifier = Modifier.size(50, 20),
                            color = Color.White,
                            text = "Open",
                        )
                    }
                },
            )

        val failure =
            assertFailsWith<IllegalArgumentException> {
                screenProgram.toPrimitiveScreenProgram()
            }

        assertEquals(
            "Screen program contains runtime-only parts and cannot be lowered to primitive instructions:\n" +
                "hitRegion[root-0].action: runtime-only value",
            failure.message,
        )
    }

    @Test
    fun primitiveProgramRendersSameTraceAsExecutablePlan() {
        var state = ScreenState("Zone 0", TestAction.Open, origin = Position(4, 5))
        val screenProgram =
            ScreenProgramCompiler().compile(
                uiActions<TestAction>(Modifier.size(80, 30)) {
                    box(Modifier.offset(1, 2).size(10, 10).background(Color.Red))
                    overlay(
                        modifier = Modifier.size(50, 20),
                        anchor = stateValue(ScreenState::origin) { state },
                    ) {
                        text(
                            modifier = Modifier.size(50, 20),
                            color = Color.White,
                            text = stateValue(ScreenState::title) { state },
                        )
                    }
                },
            )
        val primitive = screenProgram.toPrimitiveScreenProgram()

        assertEquals(
            trace { ExecutableScreenRuntimeExecutor(screenProgram.optimize().toExecutablePlan()).render(it) },
            trace {
                primitive.render(
                    backend = it,
                    resolve = { expression ->
                        when (expression) {
                            is PrimitiveValueExpression.Constant -> expression.value
                            is PrimitiveValueExpression.StateField ->
                                when (expression.fieldName) {
                                    "title" -> state.title
                                    "origin" -> state.origin
                                    else -> error("Unexpected field ${expression.fieldName}")
                                }
                            is PrimitiveValueExpression.And -> expression.terms.all { it.resolveTestValue(state) as Boolean }
                        }
                    },
                )
            },
        )

        state = ScreenState("Zone 1", TestAction.Open, origin = Position(8, 9))

        assertEquals(
            trace { ExecutableScreenRuntimeExecutor(screenProgram.optimize().toExecutablePlan()).render(it) },
            trace {
                primitive.render(
                    backend = it,
                    resolve = { expression ->
                        when (expression) {
                            is PrimitiveValueExpression.Constant -> expression.value
                            is PrimitiveValueExpression.StateField ->
                                when (expression.fieldName) {
                                    "title" -> state.title
                                    "origin" -> state.origin
                                    else -> error("Unexpected field ${expression.fieldName}")
                                }
                            is PrimitiveValueExpression.And -> expression.terms.all { it.resolveTestValue(state) as Boolean }
                        }
                    },
                )
            },
        )
    }

    @Test
    fun conditionalRenderOpsKeepTreeOrder() {
        val state = ScreenState("Zone 0", TestAction.Open, selected = true)
        val screenProgram =
            ScreenProgramCompiler().compile(
                uiActions<TestAction>(Modifier.size(80, 30)) {
                    box(Modifier.size(50, 20).background(Color.Red))
                    If(stateValue(ScreenState::selected) { state }) {
                        box(Modifier.size(50, 20).background(Color.Blue))
                    }
                    text(
                        modifier = Modifier.size(50, 20),
                        color = Color.White,
                        text = "Label",
                    )
                },
            )

        val primitive = screenProgram.toPrimitiveScreenProgram()

        assertEquals(
            listOf(
                PrimitiveRenderOp.FillRect(
                    x = 0,
                    y = 0,
                    width = 50,
                    height = 20,
                    color = PrimitiveValueExpression.Constant(Color.Red),
                ),
                PrimitiveRenderOp.FillRect(
                    x = 0,
                    y = 0,
                    width = 50,
                    height = 20,
                    color = PrimitiveValueExpression.Constant(Color.Blue),
                ),
                PrimitiveRenderOp.DrawText(
                    x = 0,
                    y = 0,
                    width = 50,
                    height = 20,
                    text = PrimitiveValueExpression.Constant("Label"),
                    color = PrimitiveValueExpression.Constant(Color.White),
                ),
            ),
            primitive.renderInstructions.map { it.op },
        )
        assertEquals(PrimitiveValueExpression.StateField("selected"), primitive.renderInstructions[1].visible)
        assertEquals(null, primitive.renderInstructions[2].visible)
    }

    @Test
    fun nestedConditionalRenderOpsKeepBothVisibilityGuards() {
        val state = ScreenState("Zone 0", TestAction.Open, selected = true, enabled = true)
        val screenProgram =
            ScreenProgramCompiler().compile(
                uiActions<TestAction>(Modifier.size(80, 30)) {
                    If(stateValue(ScreenState::selected) { state }) {
                        If(stateValue(ScreenState::enabled) { state }) {
                            box(Modifier.size(50, 20).background(Color.Blue))
                        }
                    }
                },
            )

        val primitive = screenProgram.toPrimitiveScreenProgram()

        assertEquals(
            PrimitiveValueExpression.And(
                listOf(
                    PrimitiveValueExpression.StateField("selected"),
                    PrimitiveValueExpression.StateField("enabled"),
                ),
            ),
            primitive.renderInstructions.single().visible,
        )
    }

    private fun trace(render: (RenderTraceBackend) -> Unit): List<RenderTraceCall> {
        val backend = RenderTraceBackend()
        render(backend)
        return backend.calls
    }

    private fun PrimitiveValueExpression.resolveTestValue(state: ScreenState): Any? =
        when (this) {
            is PrimitiveValueExpression.Constant -> value
            is PrimitiveValueExpression.StateField ->
                when (fieldName) {
                    "title" -> state.title
                    "origin" -> state.origin
                    "selected" -> state.selected
                    "enabled" -> state.enabled
                    else -> error("Unexpected field $fieldName")
                }
            is PrimitiveValueExpression.And -> terms.all { it.resolveTestValue(state) as Boolean }
        }
}
