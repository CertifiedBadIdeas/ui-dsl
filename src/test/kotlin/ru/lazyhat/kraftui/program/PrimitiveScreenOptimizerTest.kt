package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PrimitiveScreenOptimizerTest {
    @Test
    fun optimizationReportTextIncludesAppliedSkippedAndWarnings() {
        val report =
            PrimitiveOptimizationReport(
                applied =
                    listOf(
                        PrimitiveAppliedOptimization.CachedTextLayout(drawTextInstructionCount = 3),
                    ),
                skipped =
                    listOf(
                        PrimitiveSkippedOptimization.PassDisabled(PrimitiveOptimizationPass.StaticTextureBaking),
                    ),
                warnings =
                    listOf(
                        PrimitiveOptimizationWarning.UnsupportedPass(
                            pass = PrimitiveOptimizationPass.StaticTextureBaking,
                            targetId = "preview",
                        ),
                    ),
            )

        assertEquals(
            """
            Applied optimizations:
              enabled text layout cache for 3 text instructions

            Skipped optimizations:
              pass disabled: StaticTextureBaking

            Warnings:
              unsupported pass StaticTextureBaking for target preview
            """.trimIndent() + "\n",
            report.asText(),
        )
    }

    @Test
    fun optimizerRemovesAlwaysInvisibleInstructionsAndFoldsConstantOrigins() {
        val program =
            PrimitiveScreenProgram(
                renderInstructions =
                    listOf(
                        PrimitiveRenderInstruction(
                            path = "hidden-fill",
                            visible = PrimitiveValueExpression.Constant(false),
                            origin = null,
                            op = fill(x = 0, y = 0, width = 10, height = 10),
                        ),
                        PrimitiveRenderInstruction(
                            path = "shifted-fill",
                            visible = PrimitiveValueExpression.Constant(true),
                            origin = PrimitiveValueExpression.Constant(Position(4, 5)),
                            op = fill(x = 1, y = 2, width = 10, height = 10),
                        ),
                    ),
                inputInstructions =
                    listOf(
                        PrimitiveInputInstruction.ClickRegion(
                            path = "hidden-click",
                            visible = PrimitiveValueExpression.Constant(false),
                            origin = null,
                            x = 0,
                            y = 0,
                            width = 10,
                            height = 10,
                            action = PrimitiveValueExpression.StateField("hidden"),
                        ),
                        PrimitiveInputInstruction.ClickRegion(
                            path = "shifted-click",
                            visible = PrimitiveValueExpression.Constant(true),
                            origin = PrimitiveValueExpression.Constant(Position(8, 9)),
                            x = 1,
                            y = 2,
                            width = 10,
                            height = 10,
                            action = PrimitiveValueExpression.StateField("open"),
                        ),
                    ),
            )

        val result = program.optimizePrimitive()

        assertEquals(
            PrimitiveScreenProgram(
                renderInstructions =
                    listOf(
                        PrimitiveRenderInstruction(
                            path = "shifted-fill",
                            visible = null,
                            origin = null,
                            op = fill(x = 5, y = 7, width = 10, height = 10),
                        ),
                    ),
                inputInstructions =
                    listOf(
                        PrimitiveInputInstruction.ClickRegion(
                            path = "shifted-click",
                            visible = null,
                            origin = null,
                            x = 9,
                            y = 11,
                            width = 10,
                            height = 10,
                            action = PrimitiveValueExpression.StateField("open"),
                        ),
                    ),
            ),
            result.program,
        )
        assertEquals(
            PrimitiveOptimizationReport(
                applied =
                    listOf(
                        PrimitiveAppliedOptimization.RemovedAlwaysInvisibleInstruction("hidden-fill"),
                        PrimitiveAppliedOptimization.FoldedConstantVisibility("shifted-fill", visible = true),
                        PrimitiveAppliedOptimization.FoldedConstantOrigin("shifted-fill", x = 4, y = 5),
                        PrimitiveAppliedOptimization.RemovedAlwaysInvisibleInstruction("hidden-click"),
                        PrimitiveAppliedOptimization.FoldedConstantVisibility("shifted-click", visible = true),
                        PrimitiveAppliedOptimization.FoldedConstantOrigin("shifted-click", x = 8, y = 9),
                    ),
            ),
            result.report,
        )
    }

    @Test
    fun optimizerMergesAdjacentStaticFillsAndReportsIt() {
        val program =
            PrimitiveScreenProgram(
                renderInstructions =
                    listOf(
                        PrimitiveRenderInstruction(
                            path = "left",
                            visible = null,
                            origin = null,
                            op = fill(x = 0, y = 0, width = 10, height = 10),
                        ),
                        PrimitiveRenderInstruction(
                            path = "right",
                            visible = null,
                            origin = null,
                            op = fill(x = 10, y = 0, width = 5, height = 10),
                        ),
                        PrimitiveRenderInstruction(
                            path = "different-row",
                            visible = null,
                            origin = null,
                            op = fill(x = 0, y = 20, width = 10, height = 10),
                        ),
                    ),
                inputInstructions = emptyList(),
            )

        val result = program.optimizePrimitive()

        assertEquals(
            listOf(
                PrimitiveRenderInstruction(
                    path = "left",
                    visible = null,
                    origin = null,
                    op = fill(x = 0, y = 0, width = 15, height = 10),
                ),
                PrimitiveRenderInstruction(
                    path = "different-row",
                    visible = null,
                    origin = null,
                    op = fill(x = 0, y = 20, width = 10, height = 10),
                ),
            ),
            result.program.renderInstructions,
        )
        assertEquals(
            PrimitiveOptimizationReport(
                applied =
                    listOf(
                        PrimitiveAppliedOptimization.MergedAdjacentFills(
                            firstPath = "left",
                            secondPath = "right",
                            mergedPath = "left",
                        ),
                    ),
            ),
            result.report,
        )
    }

    @Test
    fun optimizerCanBeDisabledOrSkippedForSpecificPaths() {
        val program =
            PrimitiveScreenProgram(
                renderInstructions =
                    listOf(
                        PrimitiveRenderInstruction(
                            path = "protected",
                            visible = PrimitiveValueExpression.Constant(true),
                            origin = PrimitiveValueExpression.Constant(Position(1, 2)),
                            op = fill(x = 0, y = 0, width = 10, height = 10),
                        ),
                        PrimitiveRenderInstruction(
                            path = "normal",
                            visible = PrimitiveValueExpression.Constant(true),
                            origin = PrimitiveValueExpression.Constant(Position(3, 4)),
                            op = fill(x = 0, y = 0, width = 10, height = 10),
                        ),
                    ),
                inputInstructions = emptyList(),
            )

        assertEquals(
            PrimitiveOptimizationResult(
                program = program,
                report =
                    PrimitiveOptimizationReport(
                        skipped = listOf(PrimitiveSkippedOptimization.OptimizationDisabled),
                    ),
            ),
            program.optimizePrimitive(PrimitiveOptimizationOptions(enabled = false)),
        )

        val result =
            program.optimizePrimitive(
                PrimitiveOptimizationOptions(
                    disabledRegions = setOf("protected"),
                ),
            )

        assertEquals(program.renderInstructions.first(), result.program.renderInstructions.first())
        assertEquals(
            PrimitiveRenderInstruction(
                path = "normal",
                visible = null,
                origin = null,
                op = fill(x = 3, y = 4, width = 10, height = 10),
            ),
            result.program.renderInstructions[1],
        )
        assertEquals(
            PrimitiveOptimizationReport(
                applied =
                    listOf(
                        PrimitiveAppliedOptimization.FoldedConstantVisibility("normal", visible = true),
                        PrimitiveAppliedOptimization.FoldedConstantOrigin("normal", x = 3, y = 4),
                    ),
                skipped =
                    listOf(
                        PrimitiveSkippedOptimization.SkippedByRegion("protected"),
                    ),
            ),
            result.report,
        )
    }

    @Test
    fun optimizerPassesCanBeTargetedAndReported() {
        val program =
            PrimitiveScreenProgram(
                renderInstructions =
                    listOf(
                        PrimitiveRenderInstruction(
                            path = "hidden",
                            visible = PrimitiveValueExpression.Constant(false),
                            origin = PrimitiveValueExpression.Constant(Position(2, 3)),
                            op = fill(x = 0, y = 0, width = 10, height = 10),
                        ),
                        PrimitiveRenderInstruction(
                            path = "visible",
                            visible = PrimitiveValueExpression.Constant(true),
                            origin = PrimitiveValueExpression.Constant(Position(4, 5)),
                            op = fill(x = 0, y = 0, width = 10, height = 10),
                        ),
                    ),
                inputInstructions = emptyList(),
            )

        val result =
            program.optimizePrimitive(
                PrimitiveOptimizationOptions(
                    passes =
                        setOf(
                            PrimitiveOptimizationPass.ConstantFolding,
                        ),
                ),
            )

        assertEquals(2, result.program.renderInstructions.size)
        assertEquals(
            PrimitiveRenderInstruction(
                path = "hidden",
                visible = PrimitiveValueExpression.Constant(false),
                origin = null,
                op = fill(x = 2, y = 3, width = 10, height = 10),
            ),
            result.program.renderInstructions[0],
        )
        assertEquals(
            PrimitiveRenderInstruction(
                path = "visible",
                visible = null,
                origin = null,
                op = fill(x = 4, y = 5, width = 10, height = 10),
            ),
            result.program.renderInstructions[1],
        )
        assertEquals(
            listOf(
                PrimitiveAppliedOptimization.FoldedConstantOrigin("hidden", x = 2, y = 3),
                PrimitiveAppliedOptimization.FoldedConstantVisibility("visible", visible = true),
                PrimitiveAppliedOptimization.FoldedConstantOrigin("visible", x = 4, y = 5),
            ),
            result.report.applied,
        )
        assertEquals(
            listOf(
                PrimitiveSkippedOptimization.PassDisabled(PrimitiveOptimizationPass.DeadBranchElimination),
                PrimitiveSkippedOptimization.PassDisabled(PrimitiveOptimizationPass.AdjacentFillMerging),
            ),
            result.report.skipped,
        )
    }

    @Test
    fun optimizerBakesStaticFillRunIntoTexture() {
        val program =
            PrimitiveScreenProgram(
                renderInstructions =
                    listOf(
                        PrimitiveRenderInstruction(
                            path = "background/left",
                            visible = null,
                            origin = null,
                            op =
                                PrimitiveRenderOp.FillRect(
                                    x = 2,
                                    y = 3,
                                    width = 4,
                                    height = 2,
                                    color = PrimitiveValueExpression.Constant(Color.Red),
                                ),
                        ),
                        PrimitiveRenderInstruction(
                            path = "background/right",
                            visible = null,
                            origin = null,
                            op =
                                PrimitiveRenderOp.FillRect(
                                    x = 6,
                                    y = 3,
                                    width = 2,
                                    height = 2,
                                    color = PrimitiveValueExpression.Constant(Color.Blue),
                                ),
                        ),
                    ),
                inputInstructions = emptyList(),
            )

        val result =
            program.optimizePrimitive(
                PrimitiveOptimizationOptions(
                    passes = PrimitiveOptimizationPass.default + PrimitiveOptimizationPass.StaticTextureBaking,
                    staticTextureBaking =
                        PrimitiveStaticTextureBakingOptions.Enabled(
                            minInstructionCount = 2,
                            maxTexturePixels = 64,
                        ),
                ),
            )

        val bakedInstruction = result.program.renderInstructions.single()
        val bakedOp = assertIs<PrimitiveRenderOp.DrawBakedTexture>(bakedInstruction.op)
        assertEquals("background/left..background/right", bakedInstruction.path)
        assertEquals(2, bakedOp.x)
        assertEquals(3, bakedOp.y)
        assertEquals(6, bakedOp.width)
        assertEquals(2, bakedOp.height)
        assertEquals("baked_0", bakedOp.textureId)

        val texture = result.program.bakedTextures.single()
        assertEquals("baked_0", texture.id)
        assertEquals(6, texture.width)
        assertEquals(2, texture.height)
        assertEquals(Color.Red.value.toInt(), texture.argb[0])
        assertEquals(Color.Blue.value.toInt(), texture.argb[4])
        assertEquals(
            PrimitiveAppliedOptimization.BakedStaticTexture(
                textureId = "baked_0",
                firstPath = "background/left",
                lastPath = "background/right",
                width = 6,
                height = 2,
                instructionCount = 2,
            ),
            result.report.applied.last(),
        )
    }

    @Test
    fun optimizationResultSeparatesStaticAndDynamicInstructions() {
        val staticRender =
            PrimitiveRenderInstruction(
                path = "static-render",
                visible = null,
                origin = null,
                op = fill(x = 0, y = 0, width = 10, height = 10),
            )
        val dynamicRender =
            PrimitiveRenderInstruction(
                path = "dynamic-render",
                visible = null,
                origin = null,
                op =
                    PrimitiveRenderOp.DrawText(
                        x = 0,
                        y = 0,
                        width = 10,
                        height = 10,
                        text = PrimitiveValueExpression.StateField("title"),
                        color = PrimitiveValueExpression.Constant(Color.White),
                    ),
            )
        val staticInput =
            PrimitiveInputInstruction.ClickRegion(
                path = "static-input",
                visible = null,
                origin = null,
                x = 0,
                y = 0,
                width = 10,
                height = 10,
                action = PrimitiveValueExpression.Constant(null),
            )
        val dynamicInput =
            PrimitiveInputInstruction.ClickRegion(
                path = "dynamic-input",
                visible = null,
                origin = null,
                x = 20,
                y = 0,
                width = 10,
                height = 10,
                action = PrimitiveValueExpression.StateField("open"),
            )
        val program =
            PrimitiveScreenProgram(
                renderInstructions = listOf(staticRender, dynamicRender),
                inputInstructions = listOf(staticInput, dynamicInput),
            )

        val result = program.optimizePrimitive()

        assertEquals(listOf(staticRender), result.staticRenderInstructions)
        assertEquals(listOf(dynamicRender), result.dynamicRenderInstructions)
        assertEquals(listOf(staticInput), result.staticInputInstructions)
        assertEquals(listOf(dynamicInput), result.dynamicInputInstructions)
    }

    private fun fill(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ): PrimitiveRenderOp.FillRect =
        PrimitiveRenderOp.FillRect(
            x = x,
            y = y,
            width = width,
            height = height,
            color = PrimitiveValueExpression.Constant(Color.White),
        )
}
