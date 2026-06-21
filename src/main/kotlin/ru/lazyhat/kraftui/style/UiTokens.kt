package ru.lazyhat.kraftui.style

import ru.lazyhat.kraftui.foundation.Color

data class UiTokens(
    val colors: UiColorTokens,
    val spacing: UiSpacingTokens,
    val typography: UiTypographyTokens,
    val borders: UiBorderTokens,
    val textures: UiTextureTokens,
)

data class UiColorTokens(
    val background: Color,
    val panel: Color,
    val panelAccent: Color,
    val text: Color,
    val mutedText: Color,
    val selected: Color,
    val warning: Color,
    val error: Color,
)

data class UiSpacingTokens(
    val xs: Int,
    val sm: Int,
    val md: Int,
    val lg: Int,
) {
    init {
        require(xs >= 0) { "xs spacing must be non-negative" }
        require(sm >= 0) { "sm spacing must be non-negative" }
        require(md >= 0) { "md spacing must be non-negative" }
        require(lg >= 0) { "lg spacing must be non-negative" }
    }
}

data class UiTypographyTokens(
    val lineHeight: Int,
) {
    init {
        require(lineHeight > 0) { "line height must be positive" }
    }
}

data class UiBorderTokens(
    val thin: Int,
) {
    init {
        require(thin >= 0) { "thin border must be non-negative" }
    }
}

data class UiTextureTokens(
    val empty: Map<String, TextureStyle> = emptyMap(),
)

