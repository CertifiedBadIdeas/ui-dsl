package ru.lazyhat.kraftui.styled

import ru.lazyhat.kraftui.foundation.Value
import ru.lazyhat.kraftui.foundation.modifier.Modifier
import ru.lazyhat.kraftui.foundation.value
import ru.lazyhat.kraftui.style.MetricCardStyle
import ru.lazyhat.kraftui.style.UiTheme

class StyledUiScope<Action> {
    private val children = mutableListOf<StyledUiElement<Action>>()

    fun metricCard(
        title: Value<String>,
        lines: List<Value<String>>,
        modifier: Modifier = Modifier,
        style: MetricCardStyle? = null,
    ) {
        @Suppress("UNCHECKED_CAST")
        children +=
            StyledUiElement.MetricCard(
                modifier = modifier,
                style = style,
                title = title,
                lines = lines,
            ) as StyledUiElement<Action>
    }

    fun metricCard(
        title: String,
        lines: List<String>,
        modifier: Modifier = Modifier,
        style: MetricCardStyle? = null,
    ) {
        metricCard(
            title = value(title),
            lines = lines.map(::value),
            modifier = modifier,
            style = style,
        )
    }

    fun build(): List<StyledUiElement<Action>> = children
}

fun <Action> styledUi(
    modifier: Modifier = Modifier,
    theme: UiTheme,
    block: StyledUiScope<Action>.() -> Unit,
): StyledUiRoot<Action> =
    StyledUiRoot(
        modifier = modifier,
        theme = theme,
        children = StyledUiScope<Action>().apply(block).build(),
    )

