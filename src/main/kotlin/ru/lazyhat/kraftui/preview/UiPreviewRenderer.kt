package ru.lazyhat.kraftui.preview

import ru.lazyhat.kraftui.foundation.UiElement
import ru.lazyhat.kraftui.program.FontMetrics
import ru.lazyhat.kraftui.program.ScreenProgram
import ru.lazyhat.kraftui.program.ScreenProgramDiagnosticReport
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
    val root: UiElement<*>,
)

class UiPreviewRenderer(
    private val font: PreviewFont,
) {
    fun render(spec: UiPreviewSpec): BufferedImage {
        require(spec.width > 0) { "UI preview width must be positive: ${spec.id}" }
        require(spec.height > 0) { "UI preview height must be positive: ${spec.id}" }

        val compiled = renderCompiled(spec)
        check(compiled.program.diagnostics.isEmpty()) {
            "UI preview '${spec.id}' has layout diagnostics:\n${ScreenProgramDiagnosticReport(compiled.program.diagnostics).asText()}"
        }
        return compiled.image
    }

    private fun renderCompiled(spec: UiPreviewSpec): CompiledPreview {
        val image = BufferedImage(spec.width, spec.height, BufferedImage.TYPE_INT_ARGB)
        val backend = ImageRenderBackend(image, font)
        val program =
            ScreenProgramCompiler(fontMetrics = FontMetrics { text -> font.width(text) })
                .compile(
                    root = spec.root,
                    rootWidth = spec.width,
                    rootHeight = spec.height,
                )
        ScreenRuntimeExecutor(program).render(backend)
        backend.close()
        return CompiledPreview(image, program)
    }

    fun renderAll(
        previews: Iterable<UiPreviewSpec>,
        outputDirectory: Path,
    ): List<Path> {
        Files.createDirectories(outputDirectory)
        val diagnostics = mutableListOf<Path>()
        val outputs =
            previews.map { preview ->
                val output = outputDirectory.resolve("${preview.id}.png")
                val diagnosticOutput = outputDirectory.resolve("${preview.id}.diagnostics.txt")
                val compiled = renderCompiled(preview)
                ImageIO.write(compiled.image, "png", output.toFile())
                val report = ScreenProgramDiagnosticReport(compiled.program.diagnostics).asText()
                Files.writeString(diagnosticOutput, report)
                if (compiled.program.diagnostics.isNotEmpty()) {
                    diagnostics.add(diagnosticOutput)
                }
                output
            }
        check(diagnostics.isEmpty()) {
            "UI previews have layout diagnostics:\n${diagnostics.joinToString(separator = "\n")}"
        }
        return outputs
    }

    private data class CompiledPreview(
        val image: BufferedImage,
        val program: ScreenProgram<*>,
    )
}
