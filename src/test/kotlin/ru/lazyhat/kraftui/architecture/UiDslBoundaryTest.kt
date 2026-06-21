package ru.lazyhat.kraftui.architecture

import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiDslBoundaryTest {
    @Test
    fun uiDslSourcesDoNotImportHostOrModTypes() {
        val sourceRoot = Path("src/main/kotlin")
        val forbiddenImports =
            listOf(
                "net.minecraft.",
                "dev.architectury.",
                "net.neoforged.",
                "ru.lazyhat.compukterkraft.lang.",
                "ru.lazyhat.compukterkraft.core.",
                "ru.lazyhat.compukterkraft.common.",
                "ru.lazyhat.compukterkraft.impl.",
            )

        val violations =
            if (Files.exists(sourceRoot)) {
                Files
                    .walk(sourceRoot)
                    .filter { it.isRegularFile() && it.extension == "kt" }
                    .flatMap { path ->
                        Files.readAllLines(path).mapIndexed { index, line ->
                            val trimmed = line.trim()
                            if (trimmed.startsWith("import ") && forbiddenImports.any { trimmed.contains(it) }) {
                                "${path.relativeTo(sourceRoot)}:${index + 1}: $trimmed"
                            } else {
                                null
                            }
                        }.filterNotNull().stream()
                    }
                    .toList()
            } else {
                emptyList()
            }

        assertTrue(
            violations.isEmpty(),
            "ui-dsl must not import host/mod/core types:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun programPackageDoesNotOwnLayoutOrTextSystems() {
        val programRoot = Path("src/main/kotlin/ru/lazyhat/kraftui/program")
        val forbiddenFiles =
            listOf(
                "UiLayoutResolver.kt",
                "TextLayouter.kt",
            )

        for (fileName in forbiddenFiles) {
            assertFalse(
                Files.exists(programRoot.resolve(fileName)),
                "program package must compile UI programs only; $fileName belongs in a dedicated subsystem package",
            )
        }
    }
}
