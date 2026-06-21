package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.Modifier
import ru.lazyhat.kraftui.foundation.modifier.Position
import ru.lazyhat.kraftui.foundation.modifier.background
import ru.lazyhat.kraftui.foundation.modifier.offset
import ru.lazyhat.kraftui.foundation.modifier.size
import ru.lazyhat.kraftui.foundation.ui
import ru.lazyhat.kraftui.foundation.value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneratedRenderExecutorSourceTest {
    @Test
    fun generatedRenderSourceLowersExecutablePlanWithoutTreeTraversal() {
        var overlayPosition = Position(4, 6)
        val plan =
            ScreenProgramCompiler().compile(
                ui(Modifier.size(64, 32)) {
                    box(Modifier.offset(1, 2).size(10, 11).background(Color.Red))
                    text(
                        modifier = Modifier.offset(3, 4).size(20, 10),
                        color = Color.White,
                        text = value { "status" },
                    )
                    terminalSurface(
                        snapshot = value { "terminal" },
                        modifier = Modifier.offset(24, 4).size(16, 10),
                        onKey = { true },
                    )
                    canvas(modifier = Modifier.offset(42, 4).size(8, 8)) {
                        fillRect(1, 2, 3, 4, Color.Green)
                    }
                    overlay(
                        modifier = Modifier.size(20, 20),
                        anchor = value { overlayPosition },
                    ) {
                        box(Modifier.size(20, 20).background(value { Color.Blue }))
                    }
                },
            ).optimize()
                .toExecutablePlan()

        val generated =
            plan.generateRenderExecutorSource(
                packageName = "ru.lazyhat.generated",
                className = "GeneratedTestScreen",
            )

        assertEquals("ru.lazyhat.generated", generated.packageName)
        assertEquals("GeneratedTestScreen", generated.className)
        assertTrue("class GeneratedTestScreen" in generated.source)
        assertTrue("fun render(backend: RenderBackend)" in generated.source)
        assertTrue("plan.renderSteps[0]" in generated.source)
        assertTrue("backend.fillRect(1, 2, 10, 11, Color.Red)" in generated.source)
        assertTrue("TextLayouter(backend::measureText).layout(" in generated.source)
        assertTrue("backend.drawTerminalSurface(op2.x, op2.y, op2.snapshot.value)" in generated.source)
        assertTrue("canvasScope.bind(backend, op3.x, op3.y, op3.width, op3.height)" in generated.source)
        assertTrue("val origin4 = step4.origin?.value ?: Position.Zero" in generated.source)
        assertTrue("val op4 = step4.op as RenderOp.FillRect" in generated.source)
        assertTrue("backend.fillRect(op4.x + ox4, op4.y + oy4, op4.width, op4.height, op4.color.value)" in generated.source)
        assertFalse("unsupported render op" in generated.source)
        assertFalse("ScreenProgramCompiler" in generated.source)
        assertFalse("UiElement" in generated.source)

        overlayPosition = Position(7, 9)
        assertEquals(Position(7, 9), plan.renderSteps[4].origin?.value)
    }
}
