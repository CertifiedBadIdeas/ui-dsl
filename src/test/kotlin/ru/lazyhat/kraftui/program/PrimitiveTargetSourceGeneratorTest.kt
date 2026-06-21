package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrimitiveTargetSourceGeneratorTest {
    @Test
    fun targetGeneratorProducesPreviewAndMinecraftSourcesThroughOneEntryPoint() {
        val program =
            PrimitiveScreenProgram(
                renderInstructions =
                    listOf(
                        PrimitiveRenderInstruction(
                            path = "panel",
                            visible = PrimitiveValueExpression.Constant(true),
                            origin = PrimitiveValueExpression.Constant(Position(2, 3)),
                            op =
                                PrimitiveRenderOp.FillRect(
                                    x = 1,
                                    y = 1,
                                    width = 10,
                                    height = 10,
                                    color = PrimitiveValueExpression.Constant(Color.Blue),
                                ),
                        ),
                    ),
                inputInstructions = emptyList(),
            )

        val preview =
            program.generateTargetSource(
                target = PrimitiveSourceTargets.previewRenderBackend,
                request = request("GeneratedPreview"),
            )
        val minecraft =
            program.generateTargetSource(
                target = PrimitiveSourceTargets.minecraftGuiGraphics,
                request = request("GeneratedMinecraft"),
            )

        assertEquals("preview-render-backend", preview.target.id)
        assertEquals("minecraft-gui-graphics", minecraft.target.id)
        assertTrue("fun render(target: RenderBackend, state: ScreenState)" in preview.source.source)
        assertTrue("target.fillRect(3, 4, 10, 10, Color.Blue)" in preview.source.source)
        assertTrue("fun render(graphics: GuiGraphics, state: ScreenState)" in minecraft.source.source)
        assertTrue("graphics.fill(3, 4, 13, 14, 0xFF0000FF.toInt())" in minecraft.source.source)
        assertEquals(
            PrimitiveOptimizationReport(
                entries =
                    listOf(
                        PrimitiveOptimizationEntry.FoldedConstantVisibility("panel", visible = true),
                        PrimitiveOptimizationEntry.FoldedConstantOrigin("panel", x = 2, y = 3),
                    ),
            ),
            preview.optimizationReport,
        )
        assertEquals(preview.optimizationReport, minecraft.optimizationReport)
        assertEquals(PrimitiveProgramAnalysisReport(emptyList()), preview.analysisReport)
        assertEquals(PrimitiveProgramAnalysisReport(emptyList()), minecraft.analysisReport)
    }

    @Test
    fun targetGeneratorCanRunWithoutOptimization() {
        val program =
            PrimitiveScreenProgram(
                renderInstructions =
                    listOf(
                        PrimitiveRenderInstruction(
                            path = "panel",
                            visible = PrimitiveValueExpression.Constant(true),
                            origin = PrimitiveValueExpression.Constant(Position(2, 3)),
                            op =
                                PrimitiveRenderOp.FillRect(
                                    x = 1,
                                    y = 1,
                                    width = 10,
                                    height = 10,
                                    color = PrimitiveValueExpression.Constant(Color.Blue),
                                ),
                        ),
                    ),
                inputInstructions = emptyList(),
            )

        val result =
            program.generateTargetSource(
                target = PrimitiveSourceTargets.previewRenderBackend,
                request =
                    request("GeneratedPreview").copy(
                        optimization = PrimitiveOptimizationOptions(enabled = false),
                    ),
            )

        assertEquals(
            PrimitiveOptimizationReport(listOf(PrimitiveOptimizationEntry.OptimizationDisabled)),
            result.optimizationReport,
        )
        assertTrue("if (true) {" in result.source.source)
        assertTrue("val origin0 = Position(2, 3)" in result.source.source)
    }

    @Test
    fun targetGeneratorFailsBeforeSourceGenerationWhenAnalysisFindsDiagnostics() {
        val program =
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
                                    width = 20,
                                    height = 9,
                                    text = PrimitiveValueExpression.StateField("title"),
                                    color = PrimitiveValueExpression.Constant(Color.White),
                                ),
                        ),
                    ),
                inputInstructions = emptyList(),
            )

        val failure =
            assertFailsWith<IllegalArgumentException> {
                program.generateTargetSource(
                    target = PrimitiveSourceTargets.minecraftGuiGraphics,
                    request = request("GeneratedMinecraft"),
                )
            }

        assertTrue("Primitive program is invalid for target minecraft-gui-graphics" in failure.message.orEmpty())
        assertTrue("title: dynamic text requires runtime-safe overflow policy" in failure.message.orEmpty())
        assertFalse("GeneratedMinecraft" in failure.message.orEmpty())
    }

    private fun request(className: String): PrimitiveTargetSourceRequest =
        PrimitiveTargetSourceRequest(
            packageName = "ru.lazyhat.generated",
            className = className,
            stateType = "ScreenState",
            actionType = "TestAction",
        )
}
