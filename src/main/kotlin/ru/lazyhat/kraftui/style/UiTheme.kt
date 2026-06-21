package ru.lazyhat.kraftui.style

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.TextAlignment
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy

data class UiTheme(
    val tokens: UiTokens,
    val styles: UiStyleSheet,
)

data class UiStyleSheet(
    val window: PanelStyle,
    val panel: PanelStyle,
    val button: ButtonStyle,
    val tab: TabStyle,
    val metricCard: MetricCardStyle,
    val listRow: ButtonStyle,
    val slotGrid: SlotGridStyle,
    val tooltip: TooltipStyle,
) {
    companion object {
        fun defaults(): UiStyleSheet {
            val panelSurface = SurfaceStyle(StyleColor.Constant(Color.rgb(80, 80, 80)))
            val selectedSurface = SurfaceStyle(StyleColor.Constant(Color.rgb(110, 110, 110)))
            val disabledSurface = SurfaceStyle(StyleColor.Constant(Color.rgb(50, 50, 50)))
            val text =
                TextStyle(
                    color = StyleColor.Constant(Color.White),
                    alignment = TextAlignment.Start,
                    overflow = TextOverflowPolicy.Ellipsize,
                    lineHeight = 9,
                )
            val muted =
                text.copy(
                    color = StyleColor.Constant(Color.rgb(180, 180, 180)),
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
                    label = text.copy(alignment = TextAlignment.Center),
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
                    label = text.copy(alignment = TextAlignment.Center),
                )
            return UiStyleSheet(
                window = panel,
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
}

