package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.Modifier
import ru.lazyhat.kraftui.foundation.modifier.background
import ru.lazyhat.kraftui.foundation.modifier.offset
import ru.lazyhat.kraftui.foundation.modifier.size
import ru.lazyhat.kraftui.foundation.stateValue
import ru.lazyhat.kraftui.foundation.uiActions
import ru.lazyhat.kraftui.foundation.value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratedProgramValidationTest {
    private data class ScreenState(
        val title: String,
        val action: TestAction?,
    )

    private sealed interface TestAction {
        data object Open : TestAction
    }

    @Test
    fun generatedValidationAcceptsConstantsAndStateFields() {
        var state = ScreenState(title = "Zone 0", action = TestAction.Open)
        val program =
            ScreenProgramCompiler().compile(
                uiActions<TestAction>(Modifier.size(80, 30)) {
                    button(
                        modifier = Modifier.offset(0, 0).size(50, 20).background(Color.Blue),
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

        assertTrue(program.validateGeneratedProgram().isValid)

        state = ScreenState(title = "Zone 1", action = null)
        val text = (program.frames.single().ops.filterIsInstance<RenderOp.DrawText>().single()).value
        assertEquals("Zone 1", text.value)
    }

    @Test
    fun generatedValidationRejectsRuntimeLambdasAndCanvasDrawCallbacks() {
        val program =
            ScreenProgramCompiler().compile(
                uiActions<TestAction>(Modifier.size(80, 30)) {
                    button(
                        modifier = Modifier.offset(0, 0).size(40, 20),
                        action = value { TestAction.Open },
                    ) {
                        text(
                            modifier = Modifier.size(40, 20),
                            color = Color.White,
                            text = value { "runtime" },
                        )
                    }
                    canvas(modifier = Modifier.offset(44, 0).size(8, 8)) {
                        fillRect(0, 0, 8, 8, Color.Red)
                    }
                },
            )

        val diagnostics = program.validateGeneratedProgram().diagnostics

        assertEquals(
            listOf(
                GeneratedProgramDiagnostic.RuntimeOnlyValue("frame[0].op[0].text"),
                GeneratedProgramDiagnostic.RuntimeOnlyOperation("frame[0].op[1]", "DrawCanvas uses a runtime drawing callback"),
                GeneratedProgramDiagnostic.RuntimeOnlyValue("hitRegion[root-0].action"),
            ),
            diagnostics,
        )
    }
}
