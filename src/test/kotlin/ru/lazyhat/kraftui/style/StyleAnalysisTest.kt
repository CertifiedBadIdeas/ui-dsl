package ru.lazyhat.kraftui.style

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy
import ru.lazyhat.kraftui.foundation.stateValue
import kotlin.test.Test
import kotlin.test.assertEquals

class StyleAnalysisTest {
    data class State(
        val label: String,
    )

    @Test
    fun analysisRejectsRuntimeUnsafeDynamicTextPolicy() {
        var state = State(label = "dynamic")
        val report =
            analyzeStyleText(
                path = "/label",
                text = stateValue(State::label) { state },
                style = TextStyle(
                    color = StyleColor.Constant(Color.White),
                    overflow = TextOverflowPolicy.FailInValidation,
                    lineHeight = 9,
                ),
            )

        assertEquals(
            listOf(
                StyleDiagnostic.UnsafeDynamicTextPolicy(
                    path = "/label",
                    policy = TextOverflowPolicy.FailInValidation,
                ),
            ),
            report.diagnostics,
        )
    }

    @Test
    fun analysisReportsLowContrastForConstantColors() {
        val report =
            analyzeStyleContrast(
                path = "/label",
                foreground = StyleColor.Constant(Color.rgb(30, 30, 30)),
                background = StyleColor.Constant(Color.rgb(35, 35, 35)),
            )

        assertEquals(
            listOf(
                StyleDiagnostic.LowTextContrast(
                    path = "/label",
                    foreground = Color.rgb(30, 30, 30),
                    background = Color.rgb(35, 35, 35),
                    ratio = 1.0607202173108714,
                ),
            ),
            report.diagnostics,
        )
    }
}
