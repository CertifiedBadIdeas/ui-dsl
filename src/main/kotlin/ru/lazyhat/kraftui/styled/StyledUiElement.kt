package ru.lazyhat.kraftui.styled

import ru.lazyhat.kraftui.foundation.Value
import ru.lazyhat.kraftui.foundation.modifier.Modifier
import ru.lazyhat.kraftui.style.MetricCardStyle
import ru.lazyhat.kraftui.style.UiTheme

data class StyledUiRoot<Action>(
    val modifier: Modifier,
    val theme: UiTheme,
    val children: List<StyledUiElement<Action>>,
)

sealed interface StyledUiElement<out Action> {
    val modifier: Modifier

    data class MetricCard(
        override val modifier: Modifier,
        val style: MetricCardStyle?,
        val title: Value<String>,
        val lines: List<Value<String>>,
    ) : StyledUiElement<Nothing>
}

