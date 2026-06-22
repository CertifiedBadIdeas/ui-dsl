package ru.lazyhat.kraftui.style

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.TextureAtlas
import ru.lazyhat.kraftui.foundation.TextureRegion
import ru.lazyhat.kraftui.foundation.TextureScaling
import ru.lazyhat.kraftui.foundation.TextureStyle
import ru.lazyhat.kraftui.foundation.ui
import ru.lazyhat.kraftui.foundation.modifier.Modifier
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy
import ru.lazyhat.kraftui.foundation.modifier.height
import ru.lazyhat.kraftui.foundation.modifier.size
import ru.lazyhat.kraftui.foundation.modifier.texture
import ru.lazyhat.kraftui.foundation.value
import ru.lazyhat.kraftui.program.PrimitiveTextureRegion
import ru.lazyhat.kraftui.program.PrimitiveTextureScaling
import ru.lazyhat.kraftui.program.PrimitiveOptimizationOptions
import ru.lazyhat.kraftui.program.PrimitiveOptimizationPass
import ru.lazyhat.kraftui.program.PrimitiveRenderOp
import ru.lazyhat.kraftui.program.PrimitiveStaticTextureBakingOptions
import ru.lazyhat.kraftui.program.RenderOp
import ru.lazyhat.kraftui.program.ScreenProgramCompiler
import ru.lazyhat.kraftui.program.optimizePrimitive
import ru.lazyhat.kraftui.program.toPrimitiveScreenProgram
import ru.lazyhat.kraftui.styled.styledUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StyleResolverTest {
    @Test
    fun metricCardResolvesToRegularUiElementTree() {
        val theme = testTheme()
        val styled =
            styledUi<Nothing>(Modifier.size(96, 36), theme) {
                metricCard(
                    title = value("Zone"),
                    lines = listOf(value("298.0 K"), value("101.3 kPa")),
                    modifier = Modifier.height(36),
                )
            }

        val resolved = styled.resolveStyles()
        val program = ScreenProgramCompiler().compile(resolved.element)

        assertEquals(emptyList(), resolved.diagnostics)
        assertEquals(4, program.frames.single().ops.size)
    }

    @Test
    fun texturedMetricCardResolvesToTextureRenderOperation() {
        val texture =
            TextureStyle.Region(
                region =
                    TextureRegion(
                        atlas = TextureAtlas(namespace = "testui", path = "textures/gui/widgets.png", width = 256, height = 256),
                        x = 0,
                        y = 0,
                        width = 18,
                        height = 18,
                    ),
                scaling = TextureScaling.Tile,
            )
        val theme =
            testTheme().copy(
                styles =
                    UiStyleSheet.defaults().copy(
                        metricCard =
                            MetricCardStyle(
                                PanelStyle(
                                    surface =
                                        SurfaceStyle(
                                            fill = StyleColor.Constant(Color.Black),
                                            texture = texture,
                                            padding = Insets(1, 1, 1, 1),
                                        ),
                                    title =
                                        TextStyle(
                                            color = StyleColor.Constant(Color.White),
                                            overflow = TextOverflowPolicy.Ellipsize,
                                            lineHeight = 9,
                                        ),
                                    body =
                                        TextStyle(
                                            color = StyleColor.Constant(Color.White),
                                            overflow = TextOverflowPolicy.Ellipsize,
                                            lineHeight = 9,
                                        ),
                                ),
                            ),
                    ),
            )
        val styled =
            styledUi<Nothing>(Modifier.size(24, 24), theme) {
                metricCard(
                    title = value("State"),
                    lines = listOf(value("formed")),
                    modifier = Modifier.size(24, 24),
                )
            }

        val resolved = styled.resolveStyles()
        val program = ScreenProgramCompiler().compile(resolved.element)

        val textureOp = program.frames.single().ops.filterIsInstance<RenderOp.DrawTextureRegion>().single()
        assertEquals(
            RenderOp.DrawTextureRegion(
                x = 0,
                y = 0,
                width = 24,
                height = 24,
                region =
                    PrimitiveTextureRegion(
                        namespace = "testui",
                        path = "textures/gui/widgets.png",
                        atlasWidth = 256,
                        atlasHeight = 256,
                        sourceX = 0,
                        sourceY = 0,
                        sourceWidth = 18,
                        sourceHeight = 18,
                ),
                scaling = PrimitiveTextureScaling.Tile,
            ),
            textureOp,
        )
    }

    @Test
    fun checkerboardSurfaceResolvesToRepeatedFillOperations() {
        val root =
            ui(Modifier.size(4, 4)) {
                box(
                    Modifier
                        .size(4, 4)
                        .texture(
                            TextureStyle.Checkerboard(
                                first = Color.rgb(10, 20, 30),
                                second = Color.rgb(20, 30, 40),
                                cellSize = 2,
                            ),
                        ),
                )
            }

        val program = ScreenProgramCompiler().compile(root)

        val fills = program.frames.single().ops.filterIsInstance<RenderOp.FillRect>()
        assertEquals(4, fills.size)
        assertEquals(Color.rgb(10, 20, 30), fills[0].color.value)
        assertEquals(Color.rgb(20, 30, 40), fills[1].color.value)
        assertEquals(0, fills[0].x)
        assertEquals(0, fills[0].y)
        assertEquals(2, fills[0].width)
        assertEquals(2, fills[0].height)
        assertEquals(2, fills[1].x)
        assertEquals(0, fills[1].y)
    }

    @Test
    fun checkerboardSurfaceCanBeBakedIntoStaticTexture() {
        val root =
            ui(Modifier.size(4, 4)) {
                box(
                    Modifier
                        .size(4, 4)
                        .texture(
                            TextureStyle.Checkerboard(
                                first = Color.rgb(10, 20, 30),
                                second = Color.rgb(20, 30, 40),
                                cellSize = 2,
                            ),
                        ),
                )
            }
        val primitive = ScreenProgramCompiler().compile(root).toPrimitiveScreenProgram()

        val result =
            primitive.optimizePrimitive(
                PrimitiveOptimizationOptions(
                    passes = PrimitiveOptimizationPass.default + PrimitiveOptimizationPass.StaticTextureBaking,
                    staticTextureBaking =
                        PrimitiveStaticTextureBakingOptions.Enabled(
                            minInstructionCount = 2,
                            maxTexturePixels = 64,
                        ),
                ),
            )

        val bakedOp = result.program.renderInstructions.single().op as PrimitiveRenderOp.DrawBakedTexture
        val texture = result.program.bakedTextures.single()
        assertEquals("baked_0", bakedOp.textureId)
        assertEquals(4, texture.width)
        assertEquals(4, texture.height)
        assertEquals(Color.rgb(10, 20, 30).value.toInt(), texture.argb[0])
        assertEquals(Color.rgb(20, 30, 40).value.toInt(), texture.argb[2])
        assertEquals(Color.rgb(20, 30, 40).value.toInt(), texture.argb[8])
        assertEquals(Color.rgb(10, 20, 30).value.toInt(), texture.argb[10])
    }

    @Test
    fun brassFrameDrawsBorderWithoutCoveringCenter() {
        val root =
            ui(Modifier.size(10, 8)) {
                box(
                    Modifier
                        .size(10, 8)
                        .texture(
                            TextureStyle.BrassFrame(
                                base = Color.rgb(184, 137, 65),
                                borderWidth = 2,
                                noiseStrength = 0,
                                ornament = false,
                            ),
                        ),
                )
            }

        val program = ScreenProgramCompiler().compile(root)
        val fills = program.frames.single().ops.filterIsInstance<RenderOp.FillRect>()

        assertTrue(fills.any { it.x == 0 && it.y == 0 && it.width == 1 && it.height == 1 })
        assertTrue(fills.any { it.x == 9 && it.y == 7 && it.width == 1 && it.height == 1 })
        assertTrue(fills.none { it.x == 4 && it.y == 4 })
    }

    @Test
    fun layeredTextureKeepsBackgroundBelowBrassFrame() {
        val root =
            ui(Modifier.size(6, 6)) {
                box(
                    Modifier
                        .size(6, 6)
                        .texture(
                            TextureStyle.Layered(
                                listOf(
                                    TextureStyle.Checkerboard(
                                        first = Color.rgb(10, 20, 30),
                                        second = Color.rgb(20, 30, 40),
                                        cellSize = 1,
                                    ),
                                    TextureStyle.BrassFrame(
                                        base = Color.rgb(184, 137, 65),
                                        borderWidth = 1,
                                        noiseStrength = 0,
                                        ornament = false,
                                    ),
                                ),
                            ),
                        ),
                )
            }

        val primitive = ScreenProgramCompiler().compile(root).toPrimitiveScreenProgram()
        val result =
            primitive.optimizePrimitive(
                PrimitiveOptimizationOptions(
                    passes = PrimitiveOptimizationPass.default + PrimitiveOptimizationPass.StaticTextureBaking,
                    staticTextureBaking =
                        PrimitiveStaticTextureBakingOptions.Enabled(
                            minInstructionCount = 2,
                            maxTexturePixels = 64,
                        ),
                ),
            )

        val texture = result.program.bakedTextures.single()
        assertEquals(Color.rgb(214, 167, 95).value.toInt(), texture.argb[0])
        assertEquals(Color.rgb(10, 20, 30).value.toInt(), texture.argb[14])
        assertEquals(Color.rgb(20, 30, 40).value.toInt(), texture.argb[15])
    }

    private fun testTheme(): UiTheme =
        UiTheme(
            tokens =
                UiTokens(
                colors = UiColorTokens(
                    background = Color.Black,
                    panel = Color.rgb(80, 80, 80),
                    panelAccent = Color.rgb(100, 100, 100),
                    text = Color.White,
                    mutedText = Color.rgb(180, 180, 180),
                    selected = Color.rgb(120, 120, 120),
                    warning = Color.rgb(255, 220, 80),
                    error = Color.Red,
                ),
                spacing = UiSpacingTokens(xs = 1, sm = 2, md = 4, lg = 8),
                typography = UiTypographyTokens(lineHeight = 9),
                borders = UiBorderTokens(thin = 1),
                textures = UiTextureTokens(),
            ),
            styles = UiStyleSheet.defaults(),
        )
}
