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
import ru.lazyhat.kraftui.foundation.value
import kotlin.test.Test
import kotlin.test.assertEquals

class RenderExecutorTraceEquivalenceTest {
    @Test
    fun renderTraceMatchesForRuntimeOptimizedAndExecutableExecutors() {
        var label = "ready"
        var overlayPosition = Position(4, 6)
        val program =
            ScreenProgramCompiler().compile(
                ui(Modifier.size(96, 48)) {
                    box(Modifier.offset(1, 2).size(20, 12).background(Color.Red))
                    text(
                        color = value { Color.White },
                        text = value { label },
                        modifier = Modifier.offset(2, 4).size(30, 12).textAlign(TextAlignment.Center),
                    )
                    overlay(
                        modifier = Modifier.size(20, 20),
                        anchor = value { overlayPosition },
                    ) {
                        box(Modifier.size(20, 20).background(value { Color.Blue }))
                        text(
                            modifier = Modifier.offset(1, 1).size(18, 10),
                            color = Color.Green,
                            text = value { label },
                        )
                    }
                },
            )

        assertSameRenderTrace(program)

        label = "longer"
        overlayPosition = Position(11, 9)

        assertSameRenderTrace(program)
    }

    private fun assertSameRenderTrace(program: ScreenProgram<*>) {
        val optimized = program.optimize()
        val runtimeTrace = trace { ScreenRuntimeExecutor(program).render(it) }
        val optimizedTrace = trace { OptimizedScreenRuntimeExecutor(optimized).render(it) }
        val executableTrace = trace { ExecutableScreenRuntimeExecutor(optimized.toExecutablePlan()).render(it) }

        assertEquals(runtimeTrace, optimizedTrace)
        assertEquals(runtimeTrace, executableTrace)
    }

    private fun trace(render: (RenderTraceBackend) -> Unit): List<RenderTraceCall> {
        val backend = RenderTraceBackend()
        render(backend)
        return backend.calls
    }
}
