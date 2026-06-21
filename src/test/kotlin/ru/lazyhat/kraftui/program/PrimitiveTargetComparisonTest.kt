package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.Modifier
import ru.lazyhat.kraftui.foundation.modifier.Position
import ru.lazyhat.kraftui.foundation.modifier.TextAlignment
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy
import ru.lazyhat.kraftui.foundation.modifier.background
import ru.lazyhat.kraftui.foundation.modifier.offset
import ru.lazyhat.kraftui.foundation.modifier.size
import ru.lazyhat.kraftui.foundation.modifier.textAlign
import ru.lazyhat.kraftui.foundation.modifier.textOverflow
import ru.lazyhat.kraftui.foundation.stateValue
import ru.lazyhat.kraftui.foundation.uiActions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrimitiveTargetComparisonTest {
    private data class ScreenState(
        val label: String,
        val origin: Position,
        val action: TestAction?,
    )

    private sealed interface TestAction {
        data object Open : TestAction
    }

    @Test
    fun comparisonMatchesReferencePreviewAndMinecraftLikeTargets() {
        var state = ScreenState("ok", Position(4, 5), TestAction.Open)
        val program =
            ScreenProgramCompiler().compile(
                uiActions<TestAction>(Modifier.size(120, 60)) {
                    box(Modifier.offset(1, 2).size(20, 12).background(Color.Red))
                    overlay(
                        modifier = Modifier.size(40, 20),
                        anchor = stateValue(ScreenState::origin) { state },
                    ) {
                        button(
                            modifier = Modifier.size(40, 20).background(Color.Blue),
                            action = stateValue(ScreenState::action) { state },
                        ) {
                            text(
                                modifier =
                                    Modifier
                                        .size(40, 20)
                                        .textAlign(TextAlignment.Center)
                                        .textOverflow(TextOverflowPolicy.Ellipsize),
                                color = Color.White,
                                text = stateValue(ScreenState::label) { state },
                            )
                        }
                    }
                },
            )

        val report =
            comparePrimitiveTargets(
                PrimitiveTargetComparisonRequest(
                    program = program,
                    resolvePrimitiveValue = primitiveResolver { state },
                    clicks =
                        listOf(
                            PrimitiveClickProbe(6, 7),
                            PrimitiveClickProbe(50, 50),
                        ),
                ),
            )

        assertTrue(report.matches, report.asText())
        assertEquals(
            listOf(
                PrimitiveComparisonTarget.ReferenceRuntime,
                PrimitiveComparisonTarget.PreviewRenderBackend,
                PrimitiveComparisonTarget.MinecraftGuiGraphics,
            ),
            report.snapshots.map { it.target },
        )
        assertEquals(emptyList(), report.differences)

        state = ScreenState("longer", Position(8, 9), TestAction.Open)

        val movedReport =
            comparePrimitiveTargets(
                PrimitiveTargetComparisonRequest(
                    program = program,
                    resolvePrimitiveValue = primitiveResolver { state },
                    clicks = listOf(PrimitiveClickProbe(10, 11)),
                ),
            )

        assertTrue(movedReport.matches, movedReport.asText())
    }

    @Test
    fun comparisonReportsRenderDifferencesBetweenTargets() {
        val state = ScreenState("abc", Position.Zero, TestAction.Open)
        val program =
            ScreenProgramCompiler().compile(
                uiActions<TestAction>(Modifier.size(80, 30)) {
                    text(
                        modifier =
                            Modifier
                                .offset(0, 0)
                                .size(30, 9)
                                .textAlign(TextAlignment.End)
                                .textOverflow(TextOverflowPolicy.Ellipsize),
                        color = Color.White,
                        text = stateValue(ScreenState::label) { state },
                    )
                },
            )

        val report =
            comparePrimitiveTargets(
                PrimitiveTargetComparisonRequest(
                    program = program,
                    resolvePrimitiveValue = primitiveResolver { state },
                    measureText =
                        PrimitiveTargetTextMetrics(
                            reference = { it.length * 6 },
                            preview = { it.length * 5 },
                            minecraft = { it.length * 6 },
                        ),
                ),
            )

        assertFalse(report.matches)
        assertEquals(
            listOf(
                PrimitiveTargetDifference.RenderTraceMismatch(
                    expectedTarget = PrimitiveComparisonTarget.ReferenceRuntime,
                    actualTarget = PrimitiveComparisonTarget.PreviewRenderBackend,
                ),
            ),
            report.differences,
        )
        assertTrue("preview-render-backend render trace differs from reference-runtime" in report.asText())
    }

    private fun primitiveResolver(state: () -> ScreenState): (PrimitiveValueExpression) -> Any? =
        { expression ->
            when (expression) {
                is PrimitiveValueExpression.Constant -> expression.value
                is PrimitiveValueExpression.StateField ->
                    when (expression.fieldName) {
                        "label" -> state().label
                        "origin" -> state().origin
                        "action" -> state().action
                        else -> error("Unexpected field ${expression.fieldName}")
                    }
                is PrimitiveValueExpression.And -> expression.terms.all { primitiveResolver(state).invoke(it) as Boolean }
            }
        }
}
