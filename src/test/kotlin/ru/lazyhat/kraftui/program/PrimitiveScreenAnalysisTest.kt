package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy
import ru.lazyhat.kraftui.text.TextFlow
import kotlin.test.Test
import kotlin.test.assertEquals

class PrimitiveScreenAnalysisTest {
    @Test
    fun analysisReportsTextProblemsWithoutChangingProgram() {
        val program =
            PrimitiveScreenProgram(
                renderInstructions =
                    listOf(
                        PrimitiveRenderInstruction(
                            path = "constant-text",
                            visible = null,
                            origin = null,
                            op =
                                PrimitiveRenderOp.DrawText(
                                    x = 0,
                                    y = 0,
                                    width = 12,
                                    height = 9,
                                    text = PrimitiveValueExpression.Constant("abcdef"),
                                    color = PrimitiveValueExpression.Constant(Color.White),
                                    overflow = TextOverflowPolicy.FailInValidation,
                                ),
                        ),
                        PrimitiveRenderInstruction(
                            path = "dynamic-text",
                            visible = null,
                            origin = null,
                            op =
                                PrimitiveRenderOp.DrawText(
                                    x = 0,
                                    y = 10,
                                    width = 50,
                                    height = 9,
                                    text = PrimitiveValueExpression.StateField("title"),
                                    color = PrimitiveValueExpression.Constant(Color.White),
                                    overflow = TextOverflowPolicy.FailInValidation,
                                ),
                        ),
                        PrimitiveRenderInstruction(
                            path = "too-low-text",
                            visible = null,
                            origin = null,
                            op =
                                PrimitiveRenderOp.DrawText(
                                    x = 0,
                                    y = 20,
                                    width = 100,
                                    height = 9,
                                    text = PrimitiveValueExpression.Constant("a\nb"),
                                    color = PrimitiveValueExpression.Constant(Color.White),
                                    overflow = TextOverflowPolicy.FailInValidation,
                                    flow = TextFlow(lineHeight = 9),
                                ),
                        ),
                    ),
                inputInstructions = emptyList(),
            )

        assertEquals(
            PrimitiveProgramAnalysisReport(
                diagnostics =
                    listOf(
                        PrimitiveProgramDiagnostic.TextWidthOverflow(
                            path = "constant-text",
                            text = "abcdef",
                            width = 12,
                            textWidth = 36,
                            policy = TextOverflowPolicy.FailInValidation,
                        ),
                        PrimitiveProgramDiagnostic.DynamicTextRequiresRuntimeSafeOverflow(
                            path = "dynamic-text",
                            policy = TextOverflowPolicy.FailInValidation,
                        ),
                        PrimitiveProgramDiagnostic.TextHeightOverflow(
                            path = "too-low-text",
                            text = "a\nb",
                            height = 9,
                            textHeight = 18,
                            lineCount = 2,
                            policy = TextOverflowPolicy.FailInValidation,
                        ),
                    ),
            ),
            program.analyze(
                options =
                    PrimitiveProgramAnalysisOptions(
                        measureText = { it.length * 6 },
                    ),
            ),
        )
        assertEquals(3, program.renderInstructions.size)
    }

    @Test
    fun analysisReportsInvalidBoundsUnreachableInputOverlapsAndUnsupportedTargetOps() {
        val program =
            PrimitiveScreenProgram(
                renderInstructions =
                    listOf(
                        PrimitiveRenderInstruction(
                            path = "empty-fill",
                            visible = null,
                            origin = null,
                            op =
                                PrimitiveRenderOp.FillRect(
                                    x = 0,
                                    y = 0,
                                    width = 0,
                                    height = 10,
                                    color = PrimitiveValueExpression.Constant(Color.Red),
                                ),
                        ),
                        PrimitiveRenderInstruction(
                            path = "editor",
                            visible = null,
                            origin = null,
                            op =
                                PrimitiveRenderOp.DrawCodeEditor(
                                    x = 0,
                                    y = 0,
                                    width = 10,
                                    height = 10,
                                    viewModel = PrimitiveValueExpression.StateField("editor"),
                                    fontWidth = 6,
                                    fontHeight = 9,
                                ),
                        ),
                    ),
                inputInstructions =
                    listOf(
                        PrimitiveInputInstruction.ClickRegion(
                            path = "hidden-button",
                            visible = PrimitiveValueExpression.Constant(false),
                            origin = null,
                            x = 0,
                            y = 0,
                            width = 10,
                            height = 10,
                            action = PrimitiveValueExpression.StateField("open"),
                        ),
                        PrimitiveInputInstruction.ClickRegion(
                            path = "first-button",
                            visible = null,
                            origin = null,
                            x = 0,
                            y = 0,
                            width = 10,
                            height = 10,
                            action = PrimitiveValueExpression.StateField("first"),
                        ),
                        PrimitiveInputInstruction.ClickRegion(
                            path = "second-button",
                            visible = null,
                            origin = null,
                            x = 5,
                            y = 5,
                            width = 10,
                            height = 10,
                            action = PrimitiveValueExpression.StateField("second"),
                        ),
                        PrimitiveInputInstruction.ClickRegion(
                            path = "empty-button",
                            visible = null,
                            origin = null,
                            x = 20,
                            y = 20,
                            width = 0,
                            height = 10,
                            action = PrimitiveValueExpression.StateField("empty"),
                        ),
                    ),
            )

        assertEquals(
            PrimitiveProgramAnalysisReport(
                diagnostics =
                    listOf(
                        PrimitiveProgramDiagnostic.InvalidRenderBounds(
                            path = "empty-fill",
                            width = 0,
                            height = 10,
                        ),
                        PrimitiveProgramDiagnostic.UnsupportedTargetOperation(
                            path = "editor",
                            target = "minecraft-gui-graphics",
                            operation = "DrawCodeEditor",
                        ),
                        PrimitiveProgramDiagnostic.UnreachableInputRegion(
                            path = "hidden-button",
                            reason = "visibility is always false",
                        ),
                        PrimitiveProgramDiagnostic.InvalidInputBounds(
                            path = "empty-button",
                            width = 0,
                            height = 10,
                        ),
                        PrimitiveProgramDiagnostic.OverlappingInputRegions(
                            firstPath = "hidden-button",
                            secondPath = "first-button",
                        ),
                        PrimitiveProgramDiagnostic.OverlappingInputRegions(
                            firstPath = "hidden-button",
                            secondPath = "second-button",
                        ),
                        PrimitiveProgramDiagnostic.OverlappingInputRegions(
                            firstPath = "first-button",
                            secondPath = "second-button",
                        ),
                    ),
            ),
            program.analyze(
                options =
                    PrimitiveProgramAnalysisOptions(
                        target = PrimitiveTargetCapabilities.minecraftGuiGraphics,
                        rejectOverlappingInputRegions = true,
                    ),
            ),
        )
    }

    @Test
    fun analysisReportsInvalidBakedTextureReferences() {
        val program =
            PrimitiveScreenProgram(
                renderInstructions =
                    listOf(
                        PrimitiveRenderInstruction(
                            path = "panel",
                            visible = null,
                            origin = null,
                            op =
                                PrimitiveRenderOp.DrawBakedTexture(
                                    x = 0,
                                    y = 0,
                                    width = 2,
                                    height = 2,
                                    textureId = "missing",
                                ),
                        ),
                    ),
                inputInstructions = emptyList(),
                bakedTextures =
                    listOf(
                        PrimitiveBakedTexture(
                            id = "duplicate",
                            width = 1,
                            height = 1,
                            argb = intArrayOf(0xFF000000.toInt()),
                        ),
                        PrimitiveBakedTexture(
                            id = "duplicate",
                            width = 1,
                            height = 1,
                            argb = intArrayOf(0xFFFFFFFF.toInt()),
                        ),
                    ),
            )

        assertEquals(
            PrimitiveProgramAnalysisReport(
                diagnostics =
                    listOf(
                        PrimitiveProgramDiagnostic.DuplicateBakedTextureId(
                            path = "bakedTextures/duplicate",
                            textureId = "duplicate",
                        ),
                        PrimitiveProgramDiagnostic.MissingBakedTexture(
                            path = "panel",
                            textureId = "missing",
                        ),
                    ),
            ),
            program.analyze(),
        )
    }
}
