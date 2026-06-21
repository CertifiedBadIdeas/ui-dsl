package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.Position
import kotlin.test.Test
import kotlin.test.assertEquals

class PrimitiveScreenOptimizerTest {
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
