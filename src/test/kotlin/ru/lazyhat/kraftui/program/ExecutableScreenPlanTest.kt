package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.Modifier
import ru.lazyhat.kraftui.foundation.modifier.Position
import ru.lazyhat.kraftui.foundation.modifier.TextAlignment
import ru.lazyhat.kraftui.foundation.modifier.background
import ru.lazyhat.kraftui.foundation.modifier.offset
import ru.lazyhat.kraftui.foundation.modifier.size
import ru.lazyhat.kraftui.foundation.modifier.textAlign
import ru.lazyhat.kraftui.foundation.ui
import ru.lazyhat.kraftui.foundation.uiActions
import ru.lazyhat.kraftui.foundation.value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExecutableScreenPlanTest {
    private sealed interface TestAction {
        data object Base : TestAction
        data object Overlay : TestAction
    }

    @Test
    fun executablePlanFlattensRenderStepsWithFrameGuards() {
        var overlayPosition = Position(8, 9)
        var overlayVisible = true
        val program =
            ScreenProgramCompiler().compile(
                ui(Modifier.size(80, 40)) {
                    box(Modifier.size(10, 10).background(Color.Red))
                    overlay(
                        modifier = Modifier.size(20, 20),
                        anchor = value { overlayPosition },
                        visible = value { overlayVisible },
                    ) {
                        box(Modifier.size(20, 20).background(value { Color.Blue }))
                    }
                },
            )

        val plan = program.optimize().toExecutablePlan()

        assertEquals(2, plan.renderSteps.size)
        assertTrue(plan.renderSteps[0].effectiveDependencies.isStatic)
        assertTrue(plan.renderSteps[0].staticCacheable)
        assertEquals(UiDependencies(dynamicValue = true, dynamicVisibility = true, dynamicOrigin = true), plan.renderSteps[1].effectiveDependencies)
        assertFalse(plan.renderSteps[1].staticCacheable)
        assertEquals(Position(8, 9), plan.renderSteps[1].origin?.value)
        assertEquals(true, plan.renderSteps[1].visible?.value)

        overlayPosition = Position(12, 13)
        overlayVisible = false

        assertEquals(Position(12, 13), plan.renderSteps[1].origin?.value)
        assertEquals(false, plan.renderSteps[1].visible?.value)
    }

    @Test
    fun executablePlanFlattensHitStepsWithClipAndFrameGuards() {
        val program =
            ScreenProgramCompiler().compile(
                uiActions<TestAction>(Modifier.size(100, 100)) {
                    scrollArea(
                        modifier = Modifier.offset(10, 10).size(40, 30),
                        scrollX = value(0),
                        scrollY = value(0),
                    ) {
                        button(
                            modifier = Modifier.offset(0, 50).size(40, 20),
                            action = TestAction.Base,
                        ) {}
                    }
                },
            )

        val plan = program.optimize().toExecutablePlan()
        val step = plan.hitSteps.single()

        assertSame(program.hitRegions.single(), step.region)
        assertSame(program.frames[step.region.frameIndex], step.frame)
        assertEquals(10, step.clip?.x)
        assertEquals(UiDependencies(dynamicOrigin = true), step.effectiveDependencies)
    }

    @Test
    fun executableExecutorMatchesReferenceRenderAndClicks() {
        var status = "A"
        var overlayPosition = Position(30, 10)
        var overlayVisible = true
        val program =
            ScreenProgramCompiler().compile(
                uiActions<TestAction>(Modifier.size(120, 60)) {
                    box(Modifier.offset(2, 3).size(80, 20).background(Color.Red))
                    text(
                        modifier = Modifier.offset(2, 28).size(48, 9).textAlign(TextAlignment.End),
                        text = value { status },
                    )
                    button(
                        modifier = Modifier.offset(0, 0).size(20, 20),
                        action = TestAction.Base,
                    ) {}
                    overlay(
                        modifier = Modifier.size(20, 20),
                        anchor = value { overlayPosition },
                        visible = value { overlayVisible },
                    ) {
                        box(Modifier.size(20, 20).background(value { Color.Blue }))
                        button(
                            modifier = Modifier.size(20, 20),
                            action = TestAction.Overlay,
                        ) {}
                    }
                },
            )
        val reference = ScreenRuntimeExecutor(program)
        val executable = ExecutableScreenRuntimeExecutor(program.optimize().toExecutablePlan())

        assertEquals(recordRender(reference), recordRender(executable))
        assertEquals(reference.mouseClicked(5, 5), executable.mouseClicked(5, 5))
        assertEquals(reference.mouseClicked(35, 15), executable.mouseClicked(35, 15))

        status = "ABC"
        overlayPosition = Position(40, 12)
        overlayVisible = false

        assertEquals(recordRender(reference), recordRender(executable))
        assertEquals(reference.mouseClicked(35, 15), executable.mouseClicked(35, 15))
    }

    private fun recordRender(executor: ScreenRuntimeExecutor<*>): List<RenderCall> {
        val backend = RecordingBackend()
        executor.render(backend)
        return backend.calls
    }

    private fun recordRender(executor: ExecutableScreenRuntimeExecutor<*>): List<RenderCall> {
        val backend = RecordingBackend()
        executor.render(backend)
        return backend.calls
    }

    private sealed interface RenderCall {
        data class FillRect(
            val x: Int,
            val y: Int,
            val width: Int,
            val height: Int,
            val color: Color,
        ) : RenderCall

        data class DrawText(
            val x: Int,
            val y: Int,
            val text: String,
            val color: Color,
        ) : RenderCall

        data class PushClip(
            val x: Int,
            val y: Int,
            val width: Int,
            val height: Int,
        ) : RenderCall

        data object PopClip : RenderCall
    }

    private class RecordingBackend : RenderBackend {
        val calls = mutableListOf<RenderCall>()

        override fun fillRect(
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            color: Color,
        ) {
            calls += RenderCall.FillRect(x, y, width, height, color)
        }

        override fun drawText(
            x: Int,
            y: Int,
            text: String,
            color: Color,
        ) {
            calls += RenderCall.DrawText(x, y, text, color)
        }

        override fun drawTerminalSurface(
            x: Int,
            y: Int,
            snapshot: Any,
        ) {}

        override fun pushClip(
            x: Int,
            y: Int,
            width: Int,
            height: Int,
        ) {
            calls += RenderCall.PushClip(x, y, width, height)
        }

        override fun popClip() {
            calls += RenderCall.PopClip
        }

        override fun drawCodeEditor(
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            viewModel: ru.lazyhat.kraftui.editor.EditorViewModel,
            fontWidth: Int,
            fontHeight: Int,
        ) {}

        override fun measureText(text: String): Int = text.length * 6
    }
}
