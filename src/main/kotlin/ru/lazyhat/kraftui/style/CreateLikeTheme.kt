package ru.lazyhat.kraftui.style

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.TextAlignment
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy

object CreateLikeTheme {
    private val themeTokens: UiTokens =
        UiTokens(
            colors =
                UiColorTokens(
                    background = Color.rgb(182, 135, 103),
                    panel = Color.rgb(205, 185, 148),
                    panelAccent = Color.rgb(229, 211, 176),
                    text = Color.rgb(61, 60, 72),
                    mutedText = Color.rgb(111, 106, 117),
                    selected = Color.rgb(229, 211, 176),
                    warning = Color.rgb(214, 151, 60),
                    error = Color.rgb(160, 54, 54),
                ),
            spacing = UiSpacingTokens(xs = 1, sm = 3, md = 8, lg = 12),
            typography = UiTypographyTokens(lineHeight = 9),
            borders = UiBorderTokens(thin = 1),
            textures = UiTextureTokens(),
        )

    val theme: UiTheme =
        UiTheme(
            tokens = themeTokens,
            styles = createStyleSheet(),
        )

    private fun createStyleSheet(): UiStyleSheet {
        val colors = themeTokens.colors
        val text =
            TextStyle(
                color = StyleColor.Constant(colors.text),
                overflow = TextOverflowPolicy.Ellipsize,
                lineHeight = themeTokens.typography.lineHeight,
            )
        val muted = text.copy(color = StyleColor.Constant(colors.mutedText))
        val centered = text.copy(alignment = TextAlignment.Center)
        val panelSurface =
            SurfaceStyle(
                fill = StyleColor.Constant(colors.panel),
                padding = Insets(3, 3, 3, 3),
                bakeHint = BakeHint.PreferBakedTexture,
            )
        val selectedSurface =
            panelSurface.copy(
                fill = StyleColor.Constant(colors.selected),
            )
        val disabledSurface =
            panelSurface.copy(
                fill = StyleColor.Constant(Color.rgb(168, 145, 114)),
            )
        val window =
            PanelStyle(
                surface =
                    SurfaceStyle(
                        fill = StyleColor.Constant(colors.background),
                        padding = Insets(8, 8, 8, 8),
                        bakeHint = BakeHint.PreferBakedTexture,
                    ),
                title = centered,
                body = text,
            )
        val panel = PanelStyle(surface = panelSurface, title = muted, body = text)
        val button =
            ButtonStyle(
                states =
                    mapOf(
                        ButtonState.Normal to panelSurface,
                        ButtonState.Hovered to selectedSurface,
                        ButtonState.Pressed to selectedSurface,
                        ButtonState.Selected to selectedSurface,
                        ButtonState.Disabled to disabledSurface,
                    ),
                label = centered,
            )
        val tab =
            TabStyle(
                states =
                    mapOf(
                        TabState.Normal to panelSurface,
                        TabState.Selected to selectedSurface,
                        TabState.Hovered to selectedSurface,
                        TabState.Disabled to disabledSurface,
                    ),
                label = centered,
            )
        return UiStyleSheet(
            window = window,
            panel = panel,
            button = button,
            tab = tab,
            metricCard = MetricCardStyle(panel),
            listRow = button,
            slotGrid =
                SlotGridStyle(
                    slot = panelSurface,
                    hoveredSlot = selectedSurface,
                    blockedSlot = disabledSurface,
                    gap = 1,
                ),
            tooltip = TooltipStyle(panel),
        )
    }
}
