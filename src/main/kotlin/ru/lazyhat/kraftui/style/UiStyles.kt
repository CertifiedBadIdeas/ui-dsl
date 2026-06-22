package ru.lazyhat.kraftui.style

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.Value
import ru.lazyhat.kraftui.foundation.modifier.TextAlignment
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy
import ru.lazyhat.kraftui.foundation.value

data class Insets(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(left >= 0) { "left inset must be non-negative" }
        require(top >= 0) { "top inset must be non-negative" }
        require(right >= 0) { "right inset must be non-negative" }
        require(bottom >= 0) { "bottom inset must be non-negative" }
    }

    companion object {
        val Zero = Insets(0, 0, 0, 0)
    }
}

sealed interface StyleColor {
    data class Constant(
        val color: Color,
    ) : StyleColor

    data class Dynamic(
        val color: Value<Color>,
    ) : StyleColor
}

fun StyleColor.asValue(): Value<Color> =
    when (this) {
        is StyleColor.Constant -> value(color)
        is StyleColor.Dynamic -> color
    }

internal fun StyleColor.constantOrNull(): Color? =
    when (this) {
        is StyleColor.Constant -> color
        is StyleColor.Dynamic -> null
    }

enum class BakeHint {
    Neutral,
    PreferBakedTexture,
    KeepPrimitiveCommands,
}

typealias TextureAtlas = ru.lazyhat.kraftui.foundation.TextureAtlas
typealias TextureRegion = ru.lazyhat.kraftui.foundation.TextureRegion
typealias TextureInsets = ru.lazyhat.kraftui.foundation.TextureInsets
typealias TextureScaling = ru.lazyhat.kraftui.foundation.TextureScaling
typealias TextureStyle = ru.lazyhat.kraftui.foundation.TextureStyle

data class BorderStyle(
    val color: StyleColor,
    val width: Int,
) {
    init {
        require(width >= 0) { "border width must be non-negative" }
    }
}

data class SurfaceStyle(
    val fill: StyleColor,
    val border: BorderStyle? = null,
    val texture: TextureStyle? = null,
    val padding: Insets = Insets.Zero,
    val bakeHint: BakeHint = BakeHint.Neutral,
) {
    init {
        require(texture == null || border == null) {
            "surface cannot combine texture and border until textured borders are a first-class primitive"
        }
    }
}

data class TextStyle(
    val color: StyleColor,
    val alignment: TextAlignment = TextAlignment.Start,
    val overflow: TextOverflowPolicy,
    val lineHeight: Int,
) {
    init {
        require(lineHeight > 0) { "text lineHeight must be positive" }
    }
}

data class PanelStyle(
    val surface: SurfaceStyle,
    val title: TextStyle,
    val body: TextStyle,
)

data class MetricCardStyle(
    val panel: PanelStyle,
)

data class SlotGridStyle(
    val slot: SurfaceStyle,
    val hoveredSlot: SurfaceStyle,
    val blockedSlot: SurfaceStyle,
    val gap: Int,
) {
    init {
        require(gap >= 0) { "slot grid gap must be non-negative" }
    }
}

data class TooltipStyle(
    val panel: PanelStyle,
)

enum class ButtonState {
    Normal,
    Hovered,
    Pressed,
    Selected,
    Disabled,
}

enum class TabState {
    Normal,
    Selected,
    Hovered,
    Disabled,
}

open class ControlStyle<S : Enum<S>>(
    val states: Map<S, SurfaceStyle>,
    val label: TextStyle,
    private val requiredStates: Set<S>,
    private val styleName: String,
) {
    init {
        val missing = requiredStates - states.keys
        require(missing.isEmpty()) {
            "$styleName is missing states: ${missing.joinToString()}"
        }
    }

    fun surfaceFor(state: S): SurfaceStyle =
        requireNotNull(states[state]) { "$styleName has no surface for $state" }
}

class ButtonStyle(
    states: Map<ButtonState, SurfaceStyle>,
    label: TextStyle,
) : ControlStyle<ButtonState>(
        states = states,
        label = label,
        requiredStates = ButtonState.entries.toSet(),
        styleName = "button style",
    )

class TabStyle(
    states: Map<TabState, SurfaceStyle>,
    label: TextStyle,
) : ControlStyle<TabState>(
        states = states,
        label = label,
        requiredStates = TabState.entries.toSet(),
        styleName = "tab style",
    )
