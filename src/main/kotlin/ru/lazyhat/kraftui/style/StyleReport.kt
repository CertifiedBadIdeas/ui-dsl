package ru.lazyhat.kraftui.style

data class StyleReport(
    val themeName: String,
    val usages: List<StyleUsage> = emptyList(),
    val diagnostics: List<StyleDiagnostic> = emptyList(),
    val optimizationHints: List<StyleOptimizationHint> = emptyList(),
) {
    init {
        require(themeName.isNotBlank()) { "style report themeName must not be blank" }
    }

    val isValid: Boolean
        get() = diagnostics.isEmpty()

    fun asText(): String =
        buildString {
            appendLine("Theme:")
            appendLine("  theme: $themeName")
            appendLine()
            appendLine("Styles:")
            if (usages.isEmpty()) {
                appendLine("  none")
            } else {
                usages.forEach { usage ->
                    appendLine("  ${usage.component}: used ${usage.count}")
                }
            }
            appendLine()
            appendLine("Diagnostics:")
            if (diagnostics.isEmpty()) {
                appendLine("  none")
            } else {
                diagnostics.forEach { diagnostic ->
                    appendLine("  ${diagnostic.asReportText()}")
                }
            }
            appendLine()
            appendLine("Optimization hints:")
            if (optimizationHints.isEmpty()) {
                appendLine("  none")
            } else {
                optimizationHints.forEach { hint ->
                    appendLine("  ${hint.path}: ${hint.hint}")
                }
            }
        }
}

data class StyleUsage(
    val component: String,
    val count: Int,
) {
    init {
        require(component.isNotBlank()) { "style usage component must not be blank" }
        require(count >= 0) { "style usage count must be non-negative" }
    }
}

data class StyleOptimizationHint(
    val path: String,
    val hint: BakeHint,
) {
    init {
        require(path.isNotBlank()) { "style optimization hint path must not be blank" }
    }
}

private fun StyleDiagnostic.asReportText(): String =
    when (this) {
        is StyleDiagnostic.MissingControlState -> "$path: $style missing state $state"
        is StyleDiagnostic.UnsupportedTextureForTarget -> "$path: texture $texture is unsupported for $target"
        is StyleDiagnostic.LowTextContrast -> "$path: low text contrast $ratio"
        is StyleDiagnostic.UnsafeDynamicTextPolicy -> "$path: unsafe dynamic text policy $policy"
    }

