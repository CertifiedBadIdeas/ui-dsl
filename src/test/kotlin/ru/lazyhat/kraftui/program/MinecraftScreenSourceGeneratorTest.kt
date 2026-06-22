package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.Modifier
import ru.lazyhat.kraftui.foundation.modifier.Position
import ru.lazyhat.kraftui.foundation.modifier.background
import ru.lazyhat.kraftui.foundation.modifier.offset
import ru.lazyhat.kraftui.foundation.modifier.size
import ru.lazyhat.kraftui.foundation.stateValue
import ru.lazyhat.kraftui.foundation.uiActions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MinecraftScreenSourceGeneratorTest {
    private data class ScreenState(
        val title: String,
        val action: TestAction?,
        val origin: Position,
        val visible: Boolean = true,
    )

    private sealed interface TestAction {
        data object Open : TestAction
    }

    @Test
    fun minecraftSourceWritesDirectGuiGraphicsCallsWithoutRuntimeRenderer() {
        val state = ScreenState("Zone 0", TestAction.Open, Position(4, 5))
        val primitive =
            ScreenProgramCompiler().compile(
                uiActions<TestAction>(Modifier.size(80, 30)) {
                    box(Modifier.offset(1, 2).size(10, 10).background(Color.Red))
                    overlay(
                        modifier = Modifier.size(50, 20),
                        anchor = stateValue(ScreenState::origin) { state },
                    ) {
                        button(
                            modifier = Modifier.size(50, 20).background(Color.Blue),
                            action = stateValue(ScreenState::action) { state },
                        ) {
                            text(
                                modifier = Modifier.size(50, 20),
                                color = Color.White,
                                text = stateValue(ScreenState::title) { state },
                            )
                        }
                    }
                },
            ).toPrimitiveScreenProgram()

        val generated =
            primitive.generateMinecraftScreenSource(
                packageName = "ru.lazyhat.generated",
                className = "GeneratedMinecraftScreen",
                stateType = "ScreenState",
                actionType = "TestAction",
        )

        assertEquals("ru.lazyhat.generated", generated.packageName)
        assertEquals("GeneratedMinecraftScreen", generated.className)
        assertTrue("import net.minecraft.client.Minecraft" in generated.source)
        assertTrue("import net.minecraft.client.gui.GuiGraphics" in generated.source)
        assertTrue("fun render(graphics: GuiGraphics, state: ScreenState, screenOriginX: Int, screenOriginY: Int)" in generated.source)
        assertTrue("graphics.fill(1 + screenOriginX, 2 + screenOriginY, 11 + screenOriginX, 12 + screenOriginY, 0xFFFF0000.toInt())" in generated.source)
        assertTrue("val origin1 = state.origin" in generated.source)
        assertTrue("graphics.fill(0 + screenOriginX + ox1, 0 + screenOriginY + oy1, 50 + screenOriginX + ox1, 20 + screenOriginY + oy1, 0xFF0000FF.toInt())" in generated.source)
        assertTrue("val font2 = Minecraft.getInstance().font" in generated.source)
        assertTrue("drawText(graphics, font2, state.title, 0 + screenOriginX + ox2, 0 + screenOriginY + oy2, 50, 20, 0xFFFFFFFF.toInt(), TextAlignment.Start, TextOverflowPolicy.FailInValidation, false, null, 9)" in generated.source)
        assertTrue("fun mouseClicked(state: ScreenState, x: Int, y: Int): TestAction?" in generated.source)
        assertTrue("return hitRegionAction(state, region.id)" in generated.source)
        assertTrue("0 -> state.action" in generated.source)
        assertFalse("RenderBackend" in generated.source)
        assertFalse("PrimitiveScreenProgram" in generated.source)
        assertFalse("GeneratedValueExpression" in generated.source)
        assertFalse("RenderOp" in generated.source)
        assertFalse("Create" in generated.source)
        assertFalse("ResourceLocation" in generated.source)
        assertFalse("drawTextureRegion" in generated.source)
    }

    @Test
    fun minecraftSourceKeepsVisibilityLocalToInstruction() {
        val state = ScreenState("Zone 0", TestAction.Open, Position.Zero, visible = false)
        val primitive =
            ScreenProgramCompiler().compile(
                uiActions<TestAction>(Modifier.size(80, 30)) {
                    If(stateValue(ScreenState::visible) { state }) {
                        box(Modifier.offset(1, 2).size(10, 10).background(Color.Red))
                    }
                    box(Modifier.offset(20, 2).size(10, 10).background(Color.Blue))
                },
            ).toPrimitiveScreenProgram()

        val generated =
            primitive.generateMinecraftScreenSource(
                packageName = "ru.lazyhat.generated",
                className = "GeneratedMinecraftScreen",
                stateType = "ScreenState",
                actionType = "TestAction",
            )

        assertTrue("if (state.visible) {" in generated.source)
        assertTrue("graphics.fill(20 + screenOriginX, 2 + screenOriginY, 30 + screenOriginX, 12 + screenOriginY, 0xFF0000FF.toInt())" in generated.source)
        assertFalse("if (!state.visible) return" in generated.source)
    }

    @Test
    fun minecraftSourceGeneratesBoundedTextLayoutCacheInsideGeneratedClass() {
        val primitive =
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
                                ),
                        ),
                    ),
                inputInstructions = emptyList(),
            )

        val generated =
            primitive.generateMinecraftScreenSource(
                packageName = "ru.lazyhat.generated",
                className = "GeneratedMinecraftScreen",
                stateType = "ScreenState",
                actionType = "TestAction",
            )

        assertTrue("private val textLayoutCache =" in generated.source)
        assertTrue("LinkedHashMap<TextLayoutKey, List<TextLine>>(256, 0.75f, true)" in generated.source)
        assertTrue("override fun removeEldestEntry" in generated.source)
        assertTrue("TextLayoutKey(" in generated.source)
        assertTrue("textLayoutCache.getOrPut(key)" in generated.source)
        assertTrue("private data class TextLayoutKey" in generated.source)
        assertTrue("private data class TextLine" in generated.source)
        assertFalse("CachedPrimitiveScreenRuntimeExecutor" in generated.source)
        assertFalse("RenderBackend" in generated.source)
    }

    @Test
    fun minecraftSourceGroupsConsecutiveVisibilityBlocks() {
        val primitive =
            PrimitiveScreenProgram(
                renderInstructions =
                    listOf(
                        PrimitiveRenderInstruction(
                            path = "overview/background",
                            visible = PrimitiveValueExpression.StateField("overviewVisible"),
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
                            path = "overview/title",
                            visible = PrimitiveValueExpression.StateField("overviewVisible"),
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

        val generated =
            primitive.generateMinecraftScreenSource(
                packageName = "ru.lazyhat.generated",
                className = "GeneratedMinecraftScreen",
                stateType = "ScreenState",
                actionType = "TestAction",
            )

        assertEquals(1, generated.source.countOccurrences("if (state.overviewVisible) {"))
        assertTrue("graphics.fill(0 + screenOriginX, 0 + screenOriginY, 20 + screenOriginX, 20 + screenOriginY, 0xFF0000FF.toInt())" in generated.source)
        assertTrue("drawText(graphics" in generated.source)
    }

    @Test
    fun minecraftSourceCanDisableGeneratorOptimizations() {
        val primitive =
            PrimitiveScreenProgram(
                renderInstructions =
                    listOf(
                        PrimitiveRenderInstruction(
                            path = "first",
                            visible = PrimitiveValueExpression.StateField("visible"),
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
                        PrimitiveRenderInstruction(
                            path = "second",
                            visible = PrimitiveValueExpression.StateField("visible"),
                            origin = null,
                            op =
                                PrimitiveRenderOp.FillRect(
                                    x = 0,
                                    y = 10,
                                    width = 20,
                                    height = 10,
                                    color = PrimitiveValueExpression.Constant(Color.Blue),
                                ),
                        ),
                    ),
                inputInstructions =
                    listOf(
                        PrimitiveInputInstruction.ClickRegion(
                            path = "click",
                            visible = null,
                            origin = PrimitiveValueExpression.StateField("origin"),
                            x = 0,
                            y = 0,
                            width = 20,
                            height = 20,
                            action = PrimitiveValueExpression.StateField("action"),
                        ),
                    ),
            )

        val generated =
            primitive.generateMinecraftScreenSource(
                packageName = "ru.lazyhat.generated",
                className = "GeneratedMinecraftScreen",
                stateType = "ScreenState",
                actionType = "TestAction",
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
        )

        assertFalse("textLayoutCache" in generated.source)
        assertFalse("hitRegions" in generated.source)
        assertEquals(2, generated.source.countOccurrences("if (state.visible) {"))
        assertTrue("return state.action" in generated.source, generated.source)
    }

    @Test
    fun minecraftSourceUsesPrecomputedHitRegions() {
        val primitive =
            PrimitiveScreenProgram(
                renderInstructions = emptyList(),
                inputInstructions =
                    listOf(
                        PrimitiveInputInstruction.ClickRegion(
                            path = "front",
                            visible = null,
                            origin = null,
                            x = 20,
                            y = 0,
                            width = 20,
                            height = 20,
                            action = PrimitiveValueExpression.Constant("open"),
                        ),
                        PrimitiveInputInstruction.ClickRegion(
                            path = "back",
                            visible = PrimitiveValueExpression.StateField("visible"),
                            origin = PrimitiveValueExpression.StateField("origin"),
                            x = 0,
                            y = 0,
                            width = 20,
                            height = 20,
                            action = PrimitiveValueExpression.StateField("action"),
                        ),
                    ),
            )

        val generated =
            primitive.generateMinecraftScreenSource(
                packageName = "ru.lazyhat.generated",
                className = "GeneratedMinecraftScreen",
                stateType = "ScreenState",
                actionType = "String",
            )

        assertTrue("private val hitRegions = arrayOf(" in generated.source)
        assertTrue("HitRegion(id = 0, x = 20, y = 0, width = 20, height = 20)" in generated.source)
        assertTrue("HitRegion(id = 1, x = 0, y = 0, width = 20, height = 20)" in generated.source)
        assertTrue("for (region in hitRegions) {" in generated.source)
        assertTrue("when (id) {" in generated.source)
        assertTrue("0 -> \"open\"" in generated.source)
        assertTrue("1 -> state.action" in generated.source)
    }

    @Test
    fun minecraftSourceDrawsBakedTextureAssets() {
        val primitive =
            PrimitiveScreenProgram(
                renderInstructions = emptyList(),
                inputInstructions = emptyList(),
                bakedTextures =
                    listOf(
                        PrimitiveBakedTexture(
                            id = "baked_0",
                            width = 2,
                            height = 1,
                            argb = intArrayOf(Color.Red.value.toInt(), Color.Blue.value.toInt()),
                        ),
                    ),
            ).copy(
                renderInstructions =
                    listOf(
                        PrimitiveRenderInstruction(
                            path = "baked",
                            visible = null,
                            origin = null,
                            op =
                                PrimitiveRenderOp.DrawBakedTexture(
                                    x = 4,
                                    y = 5,
                                    width = 2,
                                    height = 1,
                                    textureId = "baked_0",
                                ),
                        ),
                    ),
            )

        val generated =
            primitive.generateMinecraftScreenSource(
                packageName = "ru.lazyhat.generated",
                className = "GeneratedMinecraftScreen",
                stateType = "ScreenState",
                actionType = "String",
                optimization =
                    PrimitiveOptimizationOptions(
                        passes = PrimitiveOptimizationPass.default + PrimitiveOptimizationPass.StaticTextureBaking,
                        staticTextureBaking =
                            PrimitiveStaticTextureBakingOptions.Enabled(
                                textureNamespace = "testmod",
                                texturePathPrefix = "textures/gui/generated",
                            ),
                    ),
            )

        assertTrue("import net.minecraft.resources.ResourceLocation" in generated.source)
        assertTrue(
            "ResourceLocation.fromNamespaceAndPath(\"testmod\", \"textures/gui/generated/baked_0.png\")" in
                generated.source,
        )
        assertTrue("graphics.blit(baked_0ResourceName, 4 + screenOriginX, 5 + screenOriginY, 0.0f, 0.0f, 2, 1, 2, 1)" in generated.source)
        assertFalse("drawTextureRegion" in generated.source)
        assertEquals("assets/testmod/textures/gui/generated/baked_0.png", generated.assets.single().path)
        assertEquals(0x89.toByte(), generated.assets.single().bytes.first())
    }

    @Test
    fun minecraftSourceDrawsExternalTextureRegionsWithoutCopyingAssets() {
        val primitive =
            PrimitiveScreenProgram(
                renderInstructions =
                    listOf(
                        PrimitiveRenderInstruction(
                            path = "create-panel",
                            visible = null,
                            origin = null,
                            op =
                                PrimitiveRenderOp.DrawTextureRegion(
                                    x = 4,
                                    y = 5,
                                    width = 32,
                                    height = 12,
                                    region =
                                        PrimitiveTextureRegion(
                                            namespace = "testui",
                                            path = "textures/gui/value_settings.png",
                                            atlasWidth = 256,
                                            atlasHeight = 256,
                                            sourceX = 0,
                                            sourceY = 24,
                                            sourceWidth = 16,
                                            sourceHeight = 3,
                                        ),
                                    scaling = PrimitiveTextureScaling.Tile,
                                ),
                        ),
                    ),
                inputInstructions = emptyList(),
            )

        val generated =
            primitive.generateMinecraftScreenSource(
                packageName = "ru.lazyhat.generated",
                className = "GeneratedMinecraftScreen",
                stateType = "ScreenState",
                actionType = "String",
            )

        assertTrue("ResourceLocation.fromNamespaceAndPath(\"testui\", \"textures/gui/value_settings.png\")" in generated.source)
        assertTrue("drawTextureRegion(graphics, testui_textures_gui_value_settings_pngResourceName" in generated.source)
        assertTrue("PrimitiveTextureScaling.Tile" !in generated.source)
        assertTrue("tile = true" in generated.source)
        assertEquals(emptyList(), generated.assets)
    }

    @Test
    fun minecraftSourceRejectsUnsupportedTargetOperationsThroughAnalysis() {
        val primitive =
            PrimitiveScreenProgram(
                renderInstructions =
                    listOf(
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
                inputInstructions = emptyList(),
            )

        val failure =
            assertFailsWith<IllegalArgumentException> {
                primitive.generateMinecraftScreenSource(
                    packageName = "ru.lazyhat.generated",
                    className = "GeneratedMinecraftScreen",
                    stateType = "ScreenState",
                    actionType = "TestAction",
                )
            }

        assertEquals(
            "Minecraft target cannot generate unsupported primitive operations:\n" +
                "editor: DrawCodeEditor",
            failure.message,
        )
    }

    private fun String.countOccurrences(value: String): Int =
        split(value).size - 1
}
