package ru.lazyhat.kraftui.style

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.Value
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class StyleAnalysisReport(
    val diagnostics: List<StyleDiagnostic>,
) {
    val isValid: Boolean
        get() = diagnostics.isEmpty()
}

sealed interface StyleDiagnostic {
    val path: String

    data class MissingControlState(
        override val path: String,
        val style: String,
        val state: String,
    ) : StyleDiagnostic

    data class UnsupportedTextureForTarget(
        override val path: String,
        val target: String,
        val texture: String,
    ) : StyleDiagnostic

    data class LowTextContrast(
        override val path: String,
        val foreground: Color,
        val background: Color,
        val ratio: Double,
    ) : StyleDiagnostic

    data class UnsafeDynamicTextPolicy(
        override val path: String,
        val policy: TextOverflowPolicy,
    ) : StyleDiagnostic
}

fun analyzeStyleText(
    path: String,
    text: Value<String>,
    style: TextStyle,
): StyleAnalysisReport {
    val diagnostics = ArrayList<StyleDiagnostic>()
    if (!text.isStatic && style.overflow == TextOverflowPolicy.FailInValidation) {
        diagnostics += StyleDiagnostic.UnsafeDynamicTextPolicy(path = path, policy = style.overflow)
    }
    return StyleAnalysisReport(diagnostics)
}

fun analyzeStyleContrast(
    path: String,
    foreground: StyleColor,
    background: StyleColor,
    minimumRatio: Double = 1.5,
): StyleAnalysisReport {
    val foregroundColor = foreground.constantOrNull()
    val backgroundColor = background.constantOrNull()
    if (foregroundColor == null || backgroundColor == null) {
        return StyleAnalysisReport(emptyList())
    }
    val ratio = contrastRatio(foregroundColor, backgroundColor)
    return StyleAnalysisReport(
        diagnostics =
            if (ratio < minimumRatio) {
                listOf(
                    StyleDiagnostic.LowTextContrast(
                        path = path,
                        foreground = foregroundColor,
                        background = backgroundColor,
                        ratio = ratio,
                    ),
                )
            } else {
                emptyList()
            },
    )
}

private fun contrastRatio(
    first: Color,
    second: Color,
): Double {
    val firstLuminance = first.relativeLuminance()
    val secondLuminance = second.relativeLuminance()
    return (max(firstLuminance, secondLuminance) + 0.05) / (min(firstLuminance, secondLuminance) + 0.05)
}

private fun Color.relativeLuminance(): Double {
    val raw = value.toInt()
    val red = ((raw ushr 16) and 0xFF).linearRgb()
    val green = ((raw ushr 8) and 0xFF).linearRgb()
    val blue = (raw and 0xFF).linearRgb()
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue
}

private fun Int.linearRgb(): Double {
    val channel = this / 255.0
    return if (channel <= 0.03928) {
        channel / 12.92
    } else {
        ((channel + 0.055) / 1.055).pow(2.4)
    }
}

