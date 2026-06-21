package ru.lazyhat.kraftui.style

import kotlin.test.Test
import kotlin.test.assertTrue

class StyleReportTest {
    @Test
    fun styleReportListsThemeUsageAndDiagnostics() {
        val report =
            StyleReport(
                themeName = "ThermodynamicsUiTheme",
                usages =
                    listOf(
                        StyleUsage(component = "window", count = 1),
                        StyleUsage(component = "tab", count = 3),
                    ),
                diagnostics =
                    listOf(
                        StyleDiagnostic.UnsafeDynamicTextPolicy(
                            path = "/title",
                            policy = ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy.FailInValidation,
                        ),
                    ),
                optimizationHints =
                    listOf(
                        StyleOptimizationHint(
                            path = "/root/background",
                            hint = BakeHint.PreferBakedTexture,
                        ),
                    ),
            )

        val text = report.asText()

        assertTrue("theme: ThermodynamicsUiTheme" in text)
        assertTrue("window: used 1" in text)
        assertTrue("tab: used 3" in text)
        assertTrue("/title: unsafe dynamic text policy FailInValidation" in text)
        assertTrue("/root/background: PreferBakedTexture" in text)
    }
}
