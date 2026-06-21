package ru.lazyhat.kraftui.preview

import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import javax.imageio.ImageIO

class MinecraftBitmapFont private constructor(
    private val texture: BufferedImage,
    private val glyphs: Map<Char, Glyph>,
) : PreviewFont {
    override fun width(text: String): Int =
        text.sumOf { glyph(it).advance }

    override fun draw(
        target: BufferedImage,
        x: Int,
        y: Int,
        text: String,
        color: Int,
    ) {
        var cursorX = x
        for (char in text) {
            val glyph = glyph(char)
            if (glyph.visible) {
                drawGlyph(target, cursorX, y, glyph, color)
            }
            cursorX += glyph.advance
        }
    }

    private fun glyph(char: Char): Glyph =
        glyphs[char]
            ?: throw IllegalArgumentException(
                "Minecraft UI preview font does not contain character U+${char.code.toString(16).uppercase().padStart(4, '0')} '$char'",
            )

    private fun drawGlyph(
        target: BufferedImage,
        x: Int,
        y: Int,
        glyph: Glyph,
        color: Int,
    ) {
        val colorAlpha = color ushr 24
        val colorRgb = color and 0x00FFFFFF
        for (glyphY in 0 until CellSize) {
            val targetY = y + glyphY
            if (targetY !in 0 until target.height) {
                continue
            }
            for (glyphX in 0 until CellSize) {
                val targetX = x + glyphX
                if (targetX !in 0 until target.width) {
                    continue
                }
                val sourceAlpha = texture.getRGB(glyph.textureX + glyphX, glyph.textureY + glyphY) ushr 24
                if (sourceAlpha == 0) {
                    continue
                }
                val alpha = sourceAlpha * colorAlpha / 255
                target.setRGB(targetX, targetY, (alpha shl 24) or colorRgb)
            }
        }
    }

    companion object {
        private const val CellSize = 8
        private const val SpaceAdvance = 4
        private const val MissingAdvance = 6
        private const val TexturePath = "assets/minecraft/textures/font/ascii.png"

        private val AsciiRows =
            mapOf(
                2 to " !\"#$%&'()*+,-./",
                3 to "0123456789:;<=>?",
                4 to "@ABCDEFGHIJKLMNO",
                5 to "PQRSTUVWXYZ[\\]^_",
                6 to "`abcdefghijklmno",
                7 to "pqrstuvwxyz{|}~",
            )

        fun loadFromMinecraftClientJar(jarPath: Path = MinecraftClientJarLocator.find()): MinecraftBitmapFont {
            ZipFile(jarPath.toFile()).use { jar ->
                val entry =
                    jar.getEntry(TexturePath)
                        ?: throw IllegalStateException("Minecraft client jar '$jarPath' does not contain '$TexturePath'")
                val texture =
                    jar.getInputStream(entry).use { stream ->
                        ImageIO.read(stream)
                            ?: throw IllegalStateException("Failed to read Minecraft bitmap font texture '$TexturePath' from '$jarPath'")
                    }
                return MinecraftBitmapFont(texture, buildGlyphs(texture))
            }
        }

        private fun buildGlyphs(texture: BufferedImage): Map<Char, Glyph> {
            require(texture.width == 128 && texture.height == 128) {
                "Expected Minecraft ASCII font texture to be 128x128, got ${texture.width}x${texture.height}"
            }
            val glyphs = mutableMapOf<Char, Glyph>()
            glyphs[' '] = Glyph(textureX = 0, textureY = 2 * CellSize, advance = SpaceAdvance, visible = false)
            glyphs['?'] = Glyph(textureX = 15 * CellSize, textureY = 3 * CellSize, advance = MissingAdvance, visible = true)
            for ((rowIndex, row) in AsciiRows) {
                for ((columnIndex, char) in row.withIndex()) {
                    if (char == ' ') {
                        continue
                    }
                    val textureX = columnIndex * CellSize
                    val textureY = rowIndex * CellSize
                    glyphs[char] =
                        Glyph(
                            textureX = textureX,
                            textureY = textureY,
                            advance = glyphAdvance(texture, textureX, textureY),
                            visible = true,
                        )
                }
            }
            return glyphs.toMap()
        }

        private fun glyphAdvance(
            texture: BufferedImage,
            textureX: Int,
            textureY: Int,
        ): Int {
            var lastVisibleColumn = -1
            for (x in 0 until CellSize) {
                for (y in 0 until CellSize) {
                    val alpha = texture.getRGB(textureX + x, textureY + y) ushr 24
                    if (alpha != 0) {
                        lastVisibleColumn = x
                    }
                }
            }
            return if (lastVisibleColumn < 0) {
                MissingAdvance
            } else {
                minOf(lastVisibleColumn + 2, CellSize)
            }
        }
    }

    private data class Glyph(
        val textureX: Int,
        val textureY: Int,
        val advance: Int,
        val visible: Boolean,
    )
}

object MinecraftClientJarLocator {
    private const val JarProperty = "kraftui.minecraftClientJar"
    private const val VersionProperty = "kraftui.minecraftVersion"
    private const val JarEnvironment = "KRAFT_UI_MINECRAFT_CLIENT_JAR"

    fun find(): Path {
        val explicit = System.getProperty(JarProperty)?.trim()?.takeIf { it.isNotEmpty() }
            ?: System.getenv(JarEnvironment)?.trim()?.takeIf { it.isNotEmpty() }
        if (explicit != null) {
            return requireExistingJar(Path.of(explicit), "configured Minecraft client jar")
        }

        val userHome = Path.of(System.getProperty("user.home"))
        val version = System.getProperty(VersionProperty)?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("Minecraft version was not configured. Set -D$VersionProperty or -D$JarProperty.")
        val candidates =
            listOf(
                userHome.resolve(".gradle/caches/neoformruntime/artifacts/minecraft_${version}_client.jar"),
                userHome.resolve(".gradle/caches/fabric-loom/$version/minecraft-client.jar"),
            )
        return candidates.firstOrNull { Files.isRegularFile(it) }
            ?: throw IllegalStateException(
                "Minecraft client jar was not found. Set -D$JarProperty or $JarEnvironment. Checked: ${candidates.joinToString()}",
            )
    }

    private fun requireExistingJar(
        path: Path,
        label: String,
    ): Path {
        require(Files.isRegularFile(path)) { "$label does not exist: $path" }
        return path
    }
}
