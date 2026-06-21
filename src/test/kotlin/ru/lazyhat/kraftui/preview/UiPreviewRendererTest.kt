package ru.lazyhat.kraftui.preview

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.Modifier
import ru.lazyhat.kraftui.foundation.modifier.background
import ru.lazyhat.kraftui.foundation.modifier.size
import ru.lazyhat.kraftui.foundation.ui
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class UiPreviewRendererTest {
    private val font = FixedPreviewFont()

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun renderDrawsUiElementIntoImage() {
        val image =
            UiPreviewRenderer(font)
                .render(
                    UiPreviewSpec(
                        id = "solid",
                        width = 8,
                        height = 8,
                        root = ui(Modifier.size(8, 8).background(Color.rgb(10, 20, 30))) {},
                    ),
                )

        assertTrue(image.getRGB(4, 4) != 0)
    }

    @Test
    fun renderFailsOnLayoutDiagnostics() {
        val error =
            assertFailsWith<IllegalStateException> {
                UiPreviewRenderer(font)
                    .render(
                        UiPreviewSpec(
                            id = "overflow",
                            width = 12,
                            height = 12,
                            root =
                                ui(Modifier.size(12, 12)) {
                                    text("too wide", modifier = Modifier.size(8, 9))
                                },
                        ),
                    )
            }

        assertTrue("overflow" in error.message.orEmpty())
        assertTrue("text overflow" in error.message.orEmpty())
        assertTrue("text width 48 px, available 8 px" in error.message.orEmpty())
    }

    @Test
    fun renderAllWritesImageAndDiagnosticReportBeforeFailing() {
        val error =
            assertFailsWith<IllegalStateException> {
                UiPreviewRenderer(font)
                    .renderAll(
                        listOf(
                            UiPreviewSpec(
                                id = "overflow",
                                width = 12,
                                height = 12,
                                root =
                                    ui(Modifier.size(12, 12)) {
                                        text("too wide", modifier = Modifier.size(8, 9))
                                    },
                            ),
                        ),
                        tempDir,
                    )
            }

        val image = tempDir.resolve("overflow.png")
        val report = tempDir.resolve("overflow.diagnostics.txt")
        assertTrue(Files.isRegularFile(image))
        assertTrue(Files.isRegularFile(report))
        assertTrue("overflow.diagnostics.txt" in error.message.orEmpty())
        assertTrue("root-0: text overflow" in Files.readString(report))
    }

    private class FixedPreviewFont : PreviewFont {
        override fun width(text: String): Int = text.length * 6

        override fun draw(
            target: java.awt.image.BufferedImage,
            x: Int,
            y: Int,
            text: String,
            color: Int,
        ) {
            for (offset in text.indices) {
                target.setRGB(x + offset, y, color)
            }
        }
    }
}
