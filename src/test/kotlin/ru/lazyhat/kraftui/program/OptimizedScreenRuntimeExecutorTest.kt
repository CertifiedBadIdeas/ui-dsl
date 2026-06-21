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

class OptimizedScreenRuntimeExecutorTest {
    private sealed interface TestAction {
        data object Base : TestAction
        data object Overlay : TestAction
    }

    @Test
    fun optimizedRenderMatchesReferenceRender() {
        var status = "A"
        var overlayPosition = Position(30, 10)
        var overlayVisible = true
        val program =
            ScreenProgramCompiler().compile(
                ui(Modifier.size(120, 60)) {
                    box(Modifier.offset(2, 3).size(80, 20).background(Color.Red))
                    text(
                        modifier = Modifier.offset(2, 28).size(48, 9).textAlign(TextAlignment.End),
                        text = value { status },
                    )
                    overlay(
                        modifier = Modifier.size(20, 20),
                        anchor = value { overlayPosition },
                        visible = value { overlayVisible },
                    ) {
                        box(Modifier.size(20, 20).background(value { Color.Blue }))
                        text("O", color = Color.White)
                    }
                },
            )
        val reference = ScreenRuntimeExecutor(program)
        val optimized = OptimizedScreenRuntimeExecutor(program.optimize())

        assertEquals(recordRender(reference), recordRender(optimized))

        status = "ABC"
        overlayPosition = Position(40, 12)
        overlayVisible = false

        assertEquals(recordRender(reference), recordRender(optimized))
    }

    @Test
    fun optimizedRenderCachesStaticCommandsWithoutChangingDrawOrder() {
        var dynamicColor = Color.Blue
        val program =
            ScreenProgramCompiler().compile(
                ui(Modifier.size(120, 30)) {
                    box(Modifier.offset(0, 0).size(10, 10).background(Color.Red))
                    box(Modifier.offset(10, 0).size(10, 10).background(value { dynamicColor }))
                    box(Modifier.offset(20, 0).size(10, 10).background(Color.White))
                },
            )
        val optimized = OptimizedScreenRuntimeExecutor(program.optimize())

        val first = RecordingBackend()
        optimized.render(first)
        assertEquals(2, optimized.staticRenderCacheSize)
        assertEquals(
            listOf<RenderCall>(
                RenderCall.FillRect(0, 0, 10, 10, Color.Red),
                RenderCall.FillRect(10, 0, 10, 10, Color.Blue),
                RenderCall.FillRect(20, 0, 10, 10, Color.White),
            ),
            first.calls,
        )

        dynamicColor = Color.Green
        val second = RecordingBackend()
        optimized.render(second)

        assertEquals(2, optimized.staticRenderCacheSize)
        assertEquals(
            listOf<RenderCall>(
                RenderCall.FillRect(0, 0, 10, 10, Color.Red),
                RenderCall.FillRect(10, 0, 10, 10, Color.Green),
                RenderCall.FillRect(20, 0, 10, 10, Color.White),
            ),
            second.calls,
        )
    }

    @Test
    fun optimizedMouseClicksMatchReferenceMouseClicks() {
        var overlayPosition = Position(50, 30)
        var overlayVisible = true
        val program =
            ScreenProgramCompiler().compile(
                uiActions<TestAction>(Modifier.size(100, 100)) {
                    button(
                        modifier = Modifier.offset(0, 0).size(20, 20),
                        action = TestAction.Base,
                    ) {}
                    overlay(
                        modifier = Modifier.size(20, 20),
                        anchor = value { overlayPosition },
                        visible = value { overlayVisible },
                    ) {
                        button(
                            modifier = Modifier.size(20, 20),
                            action = TestAction.Overlay,
                        ) {}
                    }
                },
            )
        val reference = ScreenRuntimeExecutor(program)
        val optimized = OptimizedScreenRuntimeExecutor(program.optimize())

        assertEquals(reference.mouseClicked(5, 5), optimized.mouseClicked(5, 5))
        assertEquals(reference.mouseClicked(55, 35), optimized.mouseClicked(55, 35))

        overlayPosition = Position(70, 40)
        overlayVisible = false

        assertEquals(reference.mouseClicked(55, 35), optimized.mouseClicked(55, 35))
        assertEquals(reference.mouseClicked(75, 45), optimized.mouseClicked(75, 45))
    }

    @Test
    fun optimizedMouseClicksMatchReferenceForClippedRegions() {
        val program =
            ScreenProgramCompiler().compile(
                uiActions<TestAction>(Modifier.size(100, 100)) {
                    scrollArea(
                        modifier = Modifier.offset(10, 10).size(40, 30),
                    ) {
                        button(
                            modifier = Modifier.offset(0, 50).size(40, 20),
                            action = TestAction.Base,
                        ) {}
                    }
                },
            )
        val reference = ScreenRuntimeExecutor(program)
        val optimized = OptimizedScreenRuntimeExecutor(program.optimize())

        assertEquals(reference.mouseClicked(15, 65), optimized.mouseClicked(15, 65))
        assertEquals(reference.mouseClicked(15, 15), optimized.mouseClicked(15, 15))
    }

    private fun recordRender(executor: ScreenRuntimeExecutor<*>): List<RenderCall> {
        val backend = RecordingBackend()
        executor.render(backend)
        return backend.calls
    }

    private fun recordRender(executor: OptimizedScreenRuntimeExecutor<*>): List<RenderCall> {
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

        data class DrawTerminalSurface(
            val x: Int,
            val y: Int,
            val snapshot: Any,
        ) : RenderCall

        data class DrawCodeEditor(
            val x: Int,
            val y: Int,
            val width: Int,
            val height: Int,
        ) : RenderCall
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
        ) {
            calls += RenderCall.DrawTerminalSurface(x, y, snapshot)
        }

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
        ) {
            calls += RenderCall.DrawCodeEditor(x, y, width, height)
        }

        override fun measureText(text: String): Int = text.length * 6
    }
}
