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
                applied =
                    listOf(
                        PrimitiveAppliedOptimization.FoldedConstantVisibility("panel", visible = true),
                        PrimitiveAppliedOptimization.FoldedConstantOrigin("panel", x = 2, y = 3),
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
            PrimitiveOptimizationReport(
                skipped = listOf(PrimitiveSkippedOptimization.OptimizationDisabled),
            ),
            result.optimizationReport,
        )
        assertTrue("if (true) {" in result.source.source)
        assertTrue("val origin0 = Position(2, 3)" in result.source.source)
    }

    @Test
    fun targetGeneratorReportsMinecraftSourceOptimizations() {
        val program = programWithMinecraftSourceOptimizations()

        val result =
            program.generateTargetSource(
                target = PrimitiveSourceTargets.minecraftGuiGraphics,
                request = request("GeneratedMinecraft").copy(actionType = "String"),
            )

        assertTrue(
            PrimitiveAppliedOptimization.GroupedVisibilityBlock(
                visibleExpression = "state.visible",
                instructionCount = 2,
            ) in result.optimizationReport.applied,
        )
        assertTrue(
            PrimitiveAppliedOptimization.CachedTextLayout(drawTextInstructionCount = 2) in
                result.optimizationReport.applied,
        )
        assertTrue(
            PrimitiveAppliedOptimization.PrecomputedHitRegions(regionCount = 1) in
                result.optimizationReport.applied,
        )
    }

    @Test
    fun targetGeneratorReportsDisabledMinecraftSourceOptimizations() {
        val program = programWithMinecraftSourceOptimizations()

        val result =
            program.generateTargetSource(
                target = PrimitiveSourceTargets.minecraftGuiGraphics,
                request =
                    request("GeneratedMinecraft").copy(
                        actionType = "String",
                        optimization =
                            PrimitiveOptimizationOptions(
                                passes =
                                    PrimitiveOptimizationPass.default -
                                        setOf(
                                            PrimitiveOptimizationPass.TextLayoutCaching,
                                            PrimitiveOptimizationPass.HitRegionPrecompute,
                                            PrimitiveOptimizationPass.VisibilityBlockGrouping,
                                        ),
                            ),
                    ),
            )

        assertTrue(PrimitiveSkippedOptimization.PassDisabled(PrimitiveOptimizationPass.TextLayoutCaching) in result.optimizationReport.skipped)
        assertTrue(PrimitiveSkippedOptimization.PassDisabled(PrimitiveOptimizationPass.HitRegionPrecompute) in result.optimizationReport.skipped)
        assertTrue(PrimitiveSkippedOptimization.PassDisabled(PrimitiveOptimizationPass.VisibilityBlockGrouping) in result.optimizationReport.skipped)
    }

    @Test
    fun targetGeneratorBakesStaticTexturesWhenEnabled() {
        val program =
            PrimitiveScreenProgram(
                renderInstructions =
                    listOf(
                        PrimitiveRenderInstruction(
                            path = "left",
                            visible = null,
                            origin = null,
                            op =
                                PrimitiveRenderOp.FillRect(
                                    x = 0,
                                    y = 0,
                                    width = 2,
                                    height = 2,
                                    color = PrimitiveValueExpression.Constant(Color.Red),
                                ),
                        ),
                        PrimitiveRenderInstruction(
                            path = "right",
                            visible = null,
                            origin = null,
                            op =
                                PrimitiveRenderOp.FillRect(
                                    x = 2,
                                    y = 0,
                                    width = 2,
                                    height = 2,
                                    color = PrimitiveValueExpression.Constant(Color.Blue),
                                ),
                        ),
                    ),
                inputInstructions = emptyList(),
            )

        val result =
            program.generateTargetSource(
                target = PrimitiveSourceTargets.minecraftGuiGraphics,
                request =
                    request("GeneratedMinecraft").copy(
                        actionType = "String",
                        optimization =
                            PrimitiveOptimizationOptions(
                                passes = PrimitiveOptimizationPass.default + PrimitiveOptimizationPass.StaticTextureBaking,
                                staticTextureBaking =
                                    PrimitiveStaticTextureBakingOptions.Enabled(
                                        minInstructionCount = 2,
                                        textureNamespace = "testmod",
                                        texturePathPrefix = "textures/gui/generated",
                                    ),
                            ),
                    ),
            )

        assertTrue(
            PrimitiveAppliedOptimization.BakedStaticTexture(
                textureId = "baked_0",
                firstPath = "left",
                lastPath = "right",
                width = 4,
                height = 2,
                instructionCount = 2,
            ) in result.optimizationReport.applied,
        )
        assertEquals(1, result.optimizedProgram.bakedTextures.size)
        assertEquals("assets/testmod/textures/gui/generated/baked_0.png", result.source.assets.single().path)
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

    private fun programWithMinecraftSourceOptimizations(): PrimitiveScreenProgram =
        PrimitiveScreenProgram(
            renderInstructions =
                listOf(
                    PrimitiveRenderInstruction(
                        path = "title",
                        visible = PrimitiveValueExpression.StateField("visible"),
                        origin = null,
                        op =
                            PrimitiveRenderOp.DrawText(
                                x = 0,
                                y = 0,
                                width = 80,
                                height = 9,
                                text = PrimitiveValueExpression.Constant("Title"),
                                color = PrimitiveValueExpression.Constant(Color.White),
                            ),
                    ),
                    PrimitiveRenderInstruction(
                        path = "subtitle",
                        visible = PrimitiveValueExpression.StateField("visible"),
                        origin = null,
                        op =
                            PrimitiveRenderOp.DrawText(
                                x = 0,
                                y = 10,
                                width = 80,
                                height = 9,
                                text = PrimitiveValueExpression.Constant("Subtitle"),
                                color = PrimitiveValueExpression.Constant(Color.White),
                            ),
                    ),
                ),
            inputInstructions =
                listOf(
                    PrimitiveInputInstruction.ClickRegion(
                        path = "open",
                        visible = null,
                        origin = null,
                        x = 0,
                        y = 0,
                        width = 80,
                        height = 20,
                        action = PrimitiveValueExpression.Constant("open"),
                    ),
                ),
        )
}
