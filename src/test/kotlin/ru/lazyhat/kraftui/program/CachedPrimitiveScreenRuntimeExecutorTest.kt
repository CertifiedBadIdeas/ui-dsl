package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.TextAlignment
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy
import ru.lazyhat.kraftui.editor.EditorViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CachedPrimitiveScreenRuntimeExecutorTest {
    @Test
    fun cachedExecutorReusesTextLayoutUntilTextChanges() {
        var title = "ready"
        val executor =
            CachedPrimitiveScreenRuntimeExecutor(
                PrimitiveScreenProgram(
                    renderInstructions =
                        listOf(
                            PrimitiveRenderInstruction(
                                path = "title",
                                visible = null,
                                origin = null,
                                op =
                                    PrimitiveRenderOp.DrawText(
                                        x = 0,
                                        y = 0,
                                        width = 40,
                                        height = 9,
                                        text = PrimitiveValueExpression.StateField("title"),
                                        color = PrimitiveValueExpression.Constant(Color.White),
                                        alignment = TextAlignment.End,
                                        overflow = TextOverflowPolicy.Ellipsize,
                                    ),
                            ),
                        ),
                    inputInstructions = emptyList(),
                ),
            )
        val backend = CountingTraceBackend()
        val resolve = resolver { title }

        executor.render(
            backend = backend,
            resolve = resolve,
            textMetricsKey = "test-font",
        )
        val firstMeasureCount = backend.measureTextCalls
        assertEquals(1, executor.textLayoutCacheSize)

        executor.render(
            backend = backend,
            resolve = resolve,
            textMetricsKey = "test-font",
        )
        assertEquals(firstMeasureCount, backend.measureTextCalls)
        assertEquals(1, executor.textLayoutCacheSize)

        title = "changed"
        executor.render(
            backend = backend,
            resolve = resolve,
            textMetricsKey = "test-font",
        )

        val changedMeasureCount = backend.measureTextCalls
        assertTrue(changedMeasureCount > firstMeasureCount)
        assertEquals(2, executor.textLayoutCacheSize)
    }

    @Test
    fun cachedExecutorDoesNotReuseTextLayoutAcrossDifferentTextMetrics() {
        val executor =
            CachedPrimitiveScreenRuntimeExecutor(
                PrimitiveScreenProgram(
                    renderInstructions =
                        listOf(
                            PrimitiveRenderInstruction(
                                path = "title",
                                visible = null,
                                origin = null,
                                op =
                                    PrimitiveRenderOp.DrawText(
                                        x = 0,
                                        y = 0,
                                        width = 40,
                                        height = 9,
                                        text = PrimitiveValueExpression.Constant("ready"),
                                        color = PrimitiveValueExpression.Constant(Color.White),
                                        overflow = TextOverflowPolicy.Ellipsize,
                                    ),
                            ),
                        ),
                    inputInstructions = emptyList(),
                ),
            )
        val backend = CountingTraceBackend()

        executor.render(
            backend,
            resolve = { expression: PrimitiveValueExpression -> expression.constantValue() },
            textMetricsKey = "font-a",
        )
        val firstMeasureCount = backend.measureTextCalls
        executor.render(
            backend,
            resolve = { expression: PrimitiveValueExpression -> expression.constantValue() },
            textMetricsKey = "font-b",
        )

        assertTrue(backend.measureTextCalls > firstMeasureCount)
        assertEquals(2, executor.textLayoutCacheSize)
    }

    @Test
    fun cachedExecutorBoundsTextLayoutCache() {
        var title = "first"
        val executor =
            CachedPrimitiveScreenRuntimeExecutor(
                PrimitiveScreenProgram(
                    renderInstructions =
                        listOf(
                            PrimitiveRenderInstruction(
                                path = "title",
                                visible = null,
                                origin = null,
                                op =
                                    PrimitiveRenderOp.DrawText(
                                        x = 0,
                                        y = 0,
                                        width = 40,
                                        height = 9,
                                        text = PrimitiveValueExpression.StateField("title"),
                                        color = PrimitiveValueExpression.Constant(Color.White),
                                        overflow = TextOverflowPolicy.Ellipsize,
                                    ),
                            ),
                        ),
                    inputInstructions = emptyList(),
                ),
                cacheLimits = PrimitiveRuntimeCacheLimits(maxTextLayouts = 2),
            )
        val backend = CountingTraceBackend()
        val resolve = resolver { title }

        listOf("first", "second", "third").forEach { next ->
            title = next
            executor.render(backend, resolve, textMetricsKey = "test-font")
        }

        assertEquals(2, executor.textLayoutCacheSize)
    }

    @Test
    fun cachedExecutorReplaysStaticRenderCommands() {
        val executor =
            CachedPrimitiveScreenRuntimeExecutor(
                PrimitiveScreenProgram(
                    renderInstructions =
                        listOf(
                            PrimitiveRenderInstruction(
                                path = "background",
                                visible = null,
                                origin = null,
                                op =
                                    PrimitiveRenderOp.FillRect(
                                        x = 0,
                                        y = 0,
                                        width = 20,
                                        height = 20,
                                        color = PrimitiveValueExpression.Constant(Color.Blue),
                                    ),
                            ),
                            PrimitiveRenderInstruction(
                                path = "status",
                                visible = null,
                                origin = null,
                                op =
                                    PrimitiveRenderOp.FillRect(
                                        x = 20,
                                        y = 0,
                                        width = 20,
                                        height = 20,
                                        color = PrimitiveValueExpression.StateField("statusColor"),
                                    ),
                            ),
                        ),
                    inputInstructions = emptyList(),
                ),
            )
        val resolve: (PrimitiveValueExpression) -> Any? =
            { expression ->
                when (expression) {
                    is PrimitiveValueExpression.Constant -> expression.value
                    is PrimitiveValueExpression.StateField -> Color.Red
                }
            }

        executor.render(CountingTraceBackend(), resolve, textMetricsKey = "test-font")
        assertEquals(1, executor.staticRenderCacheSize)

        executor.render(CountingTraceBackend(), resolve, textMetricsKey = "test-font")
        assertEquals(1, executor.staticRenderCacheSize)
    }

    @Test
    fun cachedExecutorSkipsInactivePageDataAndReusesCompiledInputRegions() {
        var activePage = "overview"
        val executor =
            CachedPrimitiveScreenRuntimeExecutor(
                PrimitiveScreenProgram(
                    renderInstructions =
                        listOf(
                            PrimitiveRenderInstruction(
                                path = "overview/title",
                                visible = PrimitiveValueExpression.StateField("overviewVisible"),
                                origin = null,
                                op =
                                    PrimitiveRenderOp.DrawText(
                                        x = 0,
                                        y = 0,
                                        width = 50,
                                        height = 9,
                                        text = PrimitiveValueExpression.StateField("overviewTitle"),
                                        color = PrimitiveValueExpression.Constant(Color.White),
                                        overflow = TextOverflowPolicy.Ellipsize,
                                    ),
                            ),
                            PrimitiveRenderInstruction(
                                path = "mixture/title",
                                visible = PrimitiveValueExpression.StateField("mixtureVisible"),
                                origin = null,
                                op =
                                    PrimitiveRenderOp.DrawText(
                                        x = 0,
                                        y = 10,
                                        width = 50,
                                        height = 9,
                                        text = PrimitiveValueExpression.StateField("mixtureTitle"),
                                        color = PrimitiveValueExpression.Constant(Color.White),
                                        overflow = TextOverflowPolicy.Ellipsize,
                                    ),
                            ),
                        ),
                    inputInstructions =
                        listOf(
                            PrimitiveInputInstruction.ClickRegion(
                                path = "overview/button",
                                visible = PrimitiveValueExpression.StateField("overviewVisible"),
                                origin = null,
                                x = 0,
                                y = 0,
                                width = 20,
                                height = 20,
                                action = PrimitiveValueExpression.Constant("overview"),
                            ),
                            PrimitiveInputInstruction.ClickRegion(
                                path = "mixture/button",
                                visible = PrimitiveValueExpression.StateField("mixtureVisible"),
                                origin = null,
                                x = 20,
                                y = 0,
                                width = 20,
                                height = 20,
                                action = PrimitiveValueExpression.Constant("mixture"),
                            ),
                        ),
                ),
            )
        val reads = LinkedHashMap<String, Int>()
        val resolve = resolver(reads) { field ->
            when (field) {
                "overviewVisible" -> activePage == "overview"
                "mixtureVisible" -> activePage == "mixture"
                "overviewTitle" -> "Overview"
                "mixtureTitle" -> "Mixture"
                else -> error("Unexpected field $field")
            }
        }

        executor.render(CountingTraceBackend(), resolve, textMetricsKey = "test-font")
        assertEquals(1, reads.getValue("overviewTitle"))
        assertEquals(null, reads["mixtureTitle"])
        assertEquals(2, executor.compiledInputRegionCount)
        assertEquals(PrimitiveClickResult.Action("overview"), executor.mouseClicked(resolve, 5, 5))

        activePage = "mixture"
        executor.render(CountingTraceBackend(), resolve, textMetricsKey = "test-font")
        assertEquals(1, reads.getValue("mixtureTitle"))
        assertEquals(PrimitiveClickResult.Action("mixture"), executor.mouseClicked(resolve, 25, 5))
    }

    private fun resolver(title: () -> String): (PrimitiveValueExpression) -> Any? =
        { expression ->
            when (expression) {
                is PrimitiveValueExpression.Constant -> expression.value
                is PrimitiveValueExpression.StateField ->
                    when (expression.fieldName) {
                        "title" -> title()
                        else -> error("Unexpected field ${expression.fieldName}")
                    }
            }
        }

    private fun resolver(
        reads: MutableMap<String, Int>,
        value: (String) -> Any?,
    ): (PrimitiveValueExpression) -> Any? =
        { expression ->
            when (expression) {
                is PrimitiveValueExpression.Constant -> expression.value
                is PrimitiveValueExpression.StateField -> {
                    reads[expression.fieldName] = (reads[expression.fieldName] ?: 0) + 1
                    value(expression.fieldName)
                }
            }
        }

    private fun PrimitiveValueExpression.constantValue(): Any? =
        when (this) {
            is PrimitiveValueExpression.Constant -> value
            is PrimitiveValueExpression.StateField -> error("Unexpected state field $fieldName")
        }

    private class CountingTraceBackend : RenderBackend {
        var measureTextCalls: Int = 0
            private set

        override fun fillRect(
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            color: Color,
        ) {
        }

        override fun drawText(
            x: Int,
            y: Int,
            text: String,
            color: Color,
        ) {
        }

        override fun drawTerminalSurface(
            x: Int,
            y: Int,
            snapshot: Any,
        ) {
        }

        override fun pushClip(
            x: Int,
            y: Int,
            width: Int,
            height: Int,
        ) {
        }

        override fun popClip() {
        }

        override fun drawCodeEditor(
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            viewModel: EditorViewModel,
            fontWidth: Int,
            fontHeight: Int,
        ) {
        }

        override fun measureText(text: String): Int {
            measureTextCalls++
            return text.length * 6
        }
    }
}
