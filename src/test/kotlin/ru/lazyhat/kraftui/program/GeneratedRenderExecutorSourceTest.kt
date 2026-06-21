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
        assertTrue("val origin1 = step1.origin?.value ?: Position.Zero" in generated.source)
        assertTrue("val op1 = step1.op as RenderOp.FillRect" in generated.source)
        assertTrue("backend.fillRect(op1.x + ox1, op1.y + oy1, op1.width, op1.height, op1.color.value)" in generated.source)
        assertFalse("ScreenProgramCompiler" in generated.source)
        assertFalse("UiElement" in generated.source)

        overlayPosition = Position(7, 9)
        assertEquals(Position(7, 9), plan.renderSteps[1].origin?.value)
    }
}
