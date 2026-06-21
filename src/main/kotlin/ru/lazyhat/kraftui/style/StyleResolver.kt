package ru.lazyhat.kraftui.style

import ru.lazyhat.kraftui.foundation.UiElement
import ru.lazyhat.kraftui.foundation.modifier.Modifier
import ru.lazyhat.kraftui.foundation.modifier.background
import ru.lazyhat.kraftui.foundation.modifier.fillMaxWidth
import ru.lazyhat.kraftui.foundation.modifier.height
import ru.lazyhat.kraftui.foundation.modifier.padding
import ru.lazyhat.kraftui.foundation.modifier.textAlign
import ru.lazyhat.kraftui.foundation.modifier.textFlow
import ru.lazyhat.kraftui.foundation.modifier.textOverflow
import ru.lazyhat.kraftui.styled.StyledUiElement
import ru.lazyhat.kraftui.styled.StyledUiRoot

data class StyleResolutionResult<Action>(
    val element: UiElement<Action>,
    val diagnostics: List<StyleDiagnostic>,
)

fun <Action> StyledUiRoot<Action>.resolveStyles(): StyleResolutionResult<Action> {
    val diagnostics = ArrayList<StyleDiagnostic>()
    val resolvedChildren =
        children.mapIndexed { index, child ->
            child.resolve(theme, "/root/$index", diagnostics)
        }
    return StyleResolutionResult(
        element = UiElement.Box(modifier = modifier, children = resolvedChildren),
        diagnostics = diagnostics,
    )
}

private fun <Action> StyledUiElement<Action>.resolve(
    theme: UiTheme,
    path: String,
    diagnostics: MutableList<StyleDiagnostic>,
): UiElement<Action> =
    when (this) {
        is StyledUiElement.MetricCard -> {
            @Suppress("UNCHECKED_CAST")
            resolveMetricCard(
                theme = theme,
                path = path,
                diagnostics = diagnostics,
            ) as UiElement<Action>
        }
    }

private fun StyledUiElement.MetricCard.resolveMetricCard(
    theme: UiTheme,
    path: String,
    diagnostics: MutableList<StyleDiagnostic>,
): UiElement<Nothing> {
    val style = style ?: theme.styles.metricCard
    val panel = style.panel
    val surface = panel.surface
    diagnostics += analyzeStyleText("$path/title", title, panel.title).diagnostics
    lines.forEachIndexed { index, line ->
        diagnostics += analyzeStyleText("$path/line[$index]", line, panel.body).diagnostics
    }
    return UiElement.Box(
        modifier =
            modifier
                .background(surface.fill.asValue())
                .padding(
                    surface.padding.left,
                    surface.padding.top,
                    surface.padding.right,
                    surface.padding.bottom,
                ),
        children =
            buildList {
                add(
                    UiElement.Text(
                        modifier = Modifier.fillMaxWidth().height(panel.title.lineHeight).textStyle(panel.title),
                        color = panel.title.color.asValue(),
                        text = title,
                    ),
                )
                lines.forEach { line ->
                    add(
                        UiElement.Text(
                            modifier = Modifier.fillMaxWidth().height(panel.body.lineHeight).textStyle(panel.body),
                            color = panel.body.color.asValue(),
                            text = line,
                        ),
                    )
                }
            },
    )
}

private fun Modifier.textStyle(style: TextStyle): Modifier =
    textAlign(style.alignment)
        .textOverflow(style.overflow)
        .textFlow(lineHeight = style.lineHeight)

