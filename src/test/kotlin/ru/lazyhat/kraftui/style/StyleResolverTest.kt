package ru.lazyhat.kraftui.style

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.Modifier
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy
import ru.lazyhat.kraftui.foundation.modifier.height
import ru.lazyhat.kraftui.foundation.modifier.size
import ru.lazyhat.kraftui.foundation.value
import ru.lazyhat.kraftui.program.ScreenProgramCompiler
import ru.lazyhat.kraftui.styled.styledUi
import kotlin.test.Test
import kotlin.test.assertEquals

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

    private fun testTheme(): UiTheme =
        UiTheme(
            tokens = UiTokens(
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
