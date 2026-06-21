package ru.lazyhat.kraftui.preview

import ru.lazyhat.kraftui.foundation.UiElement
import ru.lazyhat.kraftui.program.FontMetrics
import ru.lazyhat.kraftui.program.ScreenProgramCompiler
import ru.lazyhat.kraftui.program.ScreenRuntimeExecutor
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

data class UiPreviewSpec(
    val id: String,
    val width: Int,
    val height: Int,
    val root: UiElement,
)

class UiPreviewRenderer(
    private val font: PreviewFont,
) {
    fun render(spec: UiPreviewSpec): BufferedImage {
        require(spec.width > 0) { "UI preview width must be positive: ${spec.id}" }
        require(spec.height > 0) { "UI preview height must be positive: ${spec.id}" }

        val image = BufferedImage(spec.width, spec.height, BufferedImage.TYPE_INT_ARGB)
        val backend = ImageRenderBackend(image, font)
        val program =
            ScreenProgramCompiler(fontMetrics = FontMetrics { text -> font.width(text) })
                .compile(
                    root = spec.root,
                    rootWidth = spec.width,
                    rootHeight = spec.height,
                )
        check(program.diagnostics.isEmpty()) {
            "UI preview '${spec.id}' has layout diagnostics: ${program.diagnostics.joinToString()}"
        }
        ScreenRuntimeExecutor(program).render(backend)
        backend.close()
        return image
    }

    fun renderAll(
        previews: Iterable<UiPreviewSpec>,
        outputDirectory: Path,
    ): List<Path> {
        Files.createDirectories(outputDirectory)
        return previews.map { preview ->
            val output = outputDirectory.resolve("${preview.id}.png")
            ImageIO.write(render(preview), "png", output.toFile())
            output
        }
    }
}
