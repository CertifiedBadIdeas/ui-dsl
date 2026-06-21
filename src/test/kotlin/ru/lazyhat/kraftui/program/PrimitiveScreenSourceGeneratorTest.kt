package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.Modifier
import ru.lazyhat.kraftui.foundation.modifier.Position
import ru.lazyhat.kraftui.foundation.modifier.background
import ru.lazyhat.kraftui.foundation.modifier.offset
import ru.lazyhat.kraftui.foundation.modifier.size
import ru.lazyhat.kraftui.foundation.stateValue
import ru.lazyhat.kraftui.foundation.uiActions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrimitiveScreenSourceGeneratorTest {
    private data class ScreenState(
        val title: String,
        val action: TestAction?,
        val origin: Position,
        val visible: Boolean = true,
    )

    private sealed interface TestAction {
        data object Open : TestAction
    }

    @Test
    fun primitiveSourceReadsStateAndWritesDirectRenderCalls() {
        val state = ScreenState("Zone 0", TestAction.Open, Position(4, 5))
        val primitive =
            ScreenProgramCompiler().compile(
                uiActions<TestAction>(Modifier.size(80, 30)) {
                    box(Modifier.offset(1, 2).size(10, 10).background(Color.Red))
                    overlay(
                        modifier = Modifier.size(50, 20),
                        anchor = stateValue(ScreenState::origin) { state },
                    ) {
                        button(
                            modifier = Modifier.size(50, 20).background(Color.Blue),
                            action = stateValue(ScreenState::action) { state },
                        ) {
                            text(
                                modifier = Modifier.size(50, 20),
                                color = Color.White,
                                text = stateValue(ScreenState::title) { state },
                            )
                        }
                    }
                },
            ).toPrimitiveScreenProgram()

        val generated =
            primitive.generatePrimitiveScreenSource(
                packageName = "ru.lazyhat.generated",
                className = "GeneratedScreen",
                stateType = "ScreenState",
                actionType = "TestAction",
            )

        assertEquals("ru.lazyhat.generated", generated.packageName)
        assertEquals("GeneratedScreen", generated.className)
        assertTrue("class GeneratedScreen" in generated.source)
        assertTrue("import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy" in generated.source)
        assertTrue("fun render(target: RenderBackend, state: ScreenState)" in generated.source)
        assertTrue("target.fillRect(1, 2, 10, 10, Color.Red)" in generated.source)
        assertTrue("val origin1 = state.origin" in generated.source)
        assertTrue("target.fillRect(0 + ox1, 0 + oy1, 50, 20, Color.Blue)" in generated.source)
        assertTrue("text = state.title," in generated.source)
        assertTrue("fun mouseClicked(state: ScreenState, x: Int, y: Int): TestAction?" in generated.source)
        assertTrue("return state.action" in generated.source)
        assertFalse("PrimitiveScreenProgram" in generated.source)
        assertFalse("GeneratedValueExpression" in generated.source)
        assertFalse("RenderOp" in generated.source)
    }

    @Test
    fun primitiveSourceSkipsInvisibleInstructionWithoutReturningFromRender() {
        val state = ScreenState("Zone 0", TestAction.Open, Position.Zero, visible = false)
        val primitive =
            ScreenProgramCompiler().compile(
                uiActions<TestAction>(Modifier.size(80, 30)) {
                    If(stateValue(ScreenState::visible) { state }) {
                        box(Modifier.offset(1, 2).size(10, 10).background(Color.Red))
                    }
                    box(Modifier.offset(20, 2).size(10, 10).background(Color.Blue))
                },
            ).toPrimitiveScreenProgram()

        val generated =
            primitive.generatePrimitiveScreenSource(
                packageName = "ru.lazyhat.generated",
                className = "GeneratedScreen",
                stateType = "ScreenState",
                actionType = "TestAction",
            )

        assertTrue("if (state.visible) {" in generated.source)
        assertTrue("target.fillRect(20, 2, 10, 10, Color.Blue)" in generated.source)
        assertFalse("if (!state.visible) return" in generated.source)
    }
}
