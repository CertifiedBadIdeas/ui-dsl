package ru.lazyhat.kraftui.preview

import ru.lazyhat.kraftui.program.PrimitiveTextureRegion
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.imageio.ImageIO

interface PreviewTextureResolver {
    fun resolve(region: PrimitiveTextureRegion): BufferedImage
}

object MissingPreviewTextureResolver : PreviewTextureResolver {
    override fun resolve(region: PrimitiveTextureRegion): BufferedImage =
        error("No preview texture resolver is configured for ${region.namespace}:${region.path}")
}

class DirectoryPreviewTextureResolver(
    private val root: Path,
) : PreviewTextureResolver {
    override fun resolve(region: PrimitiveTextureRegion): BufferedImage {
        val path = root.resolve("assets").resolve(region.namespace).resolve(region.path).normalize()
        require(path.startsWith(root.normalize())) {
            "Preview texture path escapes resource root: ${region.namespace}:${region.path}"
        }
        require(Files.isRegularFile(path)) {
            "Preview texture file does not exist: $path"
        }
        return checkNotNull(ImageIO.read(path.toFile())) {
            "Preview texture file is not a readable image: $path"
        }
    }
}

class JarPreviewTextureResolver(
    private val jarPath: Path,
) : PreviewTextureResolver {
    override fun resolve(region: PrimitiveTextureRegion): BufferedImage {
        require(Files.isRegularFile(jarPath)) {
            "Preview texture jar does not exist: $jarPath"
        }
        val entryName = "assets/${region.namespace}/${region.path}"
        ZipFile(jarPath.toFile()).use { zip ->
            val entry =
                zip.getEntry(entryName)
                    ?: error("Preview texture jar '$jarPath' does not contain '$entryName'")
            zip.getInputStream(entry).use { input ->
                return checkNotNull(ImageIO.read(input)) {
                    "Preview texture '$entryName' in '$jarPath' is not a readable image"
                }
            }
        }
    }
}

class CompositePreviewTextureResolver(
    private val resolvers: List<PreviewTextureResolver>,
) : PreviewTextureResolver {
    init {
        require(resolvers.isNotEmpty()) { "Composite preview texture resolver must contain at least one resolver" }
    }

    override fun resolve(region: PrimitiveTextureRegion): BufferedImage {
        val errors = ArrayList<String>()
        for (resolver in resolvers) {
            try {
                return resolver.resolve(region)
            } catch (error: IllegalStateException) {
                errors += error.message ?: error::class.qualifiedName.orEmpty()
            } catch (error: IllegalArgumentException) {
                errors += error.message ?: error::class.qualifiedName.orEmpty()
            }
        }
        error(
            "Cannot resolve preview texture ${region.namespace}:${region.path}:\n" +
                errors.joinToString(separator = "\n"),
        )
    }
}
