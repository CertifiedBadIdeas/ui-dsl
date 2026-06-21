package ru.lazyhat.kraftui.style

import ru.lazyhat.kraftui.foundation.UiScope
import ru.lazyhat.kraftui.foundation.Value
import ru.lazyhat.kraftui.foundation.matchValue
import ru.lazyhat.kraftui.foundation.modifier.Modifier
import ru.lazyhat.kraftui.foundation.modifier.UiAlignment
import ru.lazyhat.kraftui.foundation.modifier.align
import ru.lazyhat.kraftui.foundation.modifier.background
import ru.lazyhat.kraftui.foundation.modifier.fillMaxSize
import ru.lazyhat.kraftui.foundation.modifier.fillMaxWidth
import ru.lazyhat.kraftui.foundation.modifier.height
import ru.lazyhat.kraftui.foundation.modifier.padding
import ru.lazyhat.kraftui.foundation.modifier.textAlign
import ru.lazyhat.kraftui.foundation.modifier.textFlow
import ru.lazyhat.kraftui.foundation.modifier.textOverflow
import ru.lazyhat.kraftui.foundation.value

fun <Action> UiScope<Action>.styledPanel(
    modifier: Modifier = Modifier,
    style: PanelStyle,
    block: UiScope<Action>.() -> Unit,
) {
    val padding = style.surface.padding
    box(
        modifier =
            modifier
                .background(style.surface.fill.asValue())
                .padding(padding.left, padding.top, padding.right, padding.bottom),
        block = block,
    )
}

fun <Action> UiScope<Action>.styledText(
    modifier: Modifier = Modifier,
    text: Value<String>,
    style: TextStyle,
) {
    text(
        modifier = modifier.textStyle(style),
        color = style.color.asValue(),
        text = text,
    )
}

fun <Action> UiScope<Action>.styledText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
) {
    styledText(modifier = modifier, text = value(text), style = style)
}

fun <Action> UiScope<Action>.metricCard(
    title: String,
    lines: List<Value<String>>,
    modifier: Modifier = Modifier,
    style: MetricCardStyle,
) {
    val panel = style.panel
    column(
        modifier =
            modifier
                .background(panel.surface.fill.asValue())
                .padding(
                    panel.surface.padding.left,
                    panel.surface.padding.top,
                    panel.surface.padding.right,
                    panel.surface.padding.bottom,
                ),
        gap = 0,
        horizontalAlignment = UiAlignment.Stretch,
    ) {
        styledText(Modifier.fillMaxWidth().height(panel.title.lineHeight), value(title), panel.title)
        lines.forEach { line ->
            styledText(Modifier.fillMaxWidth().height(panel.body.lineHeight), line, panel.body)
        }
    }
}

fun <Action> UiScope<Action>.styledButton(
    label: String,
    state: Value<ButtonState>,
    action: Value<Action?>,
    modifier: Modifier = Modifier,
    style: ButtonStyle,
) {
    button(
        modifier = modifier,
        action = action,
    ) {
        box(
            Modifier
                .fillMaxSize()
                .background(style.fillFor(state)),
        )
        styledText(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(style.label.lineHeight)
                    .align(UiAlignment.Center),
            text = value(label),
            style = style.label,
        )
    }
}

fun <Action> UiScope<Action>.styledTab(
    label: String,
    selected: Value<Boolean>,
    action: Value<Action?>,
    modifier: Modifier = Modifier,
    style: TabStyle,
) {
    val fill =
        matchValue(
            subject = selected,
            cases =
                mapOf(
                    true to style.surfaceFor(TabState.Selected).fill.asValue(),
                    false to style.surfaceFor(TabState.Normal).fill.asValue(),
                ),
            default = style.surfaceFor(TabState.Normal).fill.asValue(),
        )
    button(
        modifier = modifier,
        action = action,
    ) {
        box(
            Modifier
                .fillMaxSize()
                .background(fill),
        )
        styledText(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(style.label.lineHeight)
                    .align(UiAlignment.Center),
            text = value(label),
            style = style.label,
        )
    }
}

fun <S : Enum<S>> ControlStyle<S>.fillFor(state: Value<S>) =
    matchValue(
        subject = state,
        cases = states.mapValues { (_, surface) -> surface.fill.asValue() },
        default = surfaceFor(states.keys.first()).fill.asValue(),
    )

private fun Modifier.textStyle(style: TextStyle): Modifier =
    textAlign(style.alignment)
        .textOverflow(style.overflow)
        .textFlow(lineHeight = style.lineHeight)
