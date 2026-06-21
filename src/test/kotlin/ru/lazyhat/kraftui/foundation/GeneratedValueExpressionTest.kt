package ru.lazyhat.kraftui.foundation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GeneratedValueExpressionTest {
    private data class ScreenState(
        val title: String,
    )

    @Test
    fun constantValueHasGeneratedExpression() {
        val constant = value("ready")

        assertEquals("ready", constant.value)
        assertEquals(GeneratedValueExpression.Constant("ready"), constant.generatedExpression)
    }

    @Test
    fun stateValueReadsCurrentStateAndKeepsFieldExpression() {
        var state = ScreenState(title = "first")
        val title = stateValue(ScreenState::title) { state }

        assertEquals("first", title.value)
        assertEquals(GeneratedValueExpression.StateField("title"), title.generatedExpression)

        state = ScreenState(title = "second")

        assertEquals("second", title.value)
    }

    @Test
    fun runtimeLambdaValueIsNotGenerated() {
        val runtimeOnly = value { "runtime" }

        assertEquals("runtime", runtimeOnly.value)
        assertNull(runtimeOnly.generatedExpression)
    }
}
