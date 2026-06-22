package ru.lazyhat.kraftui.preview

import ru.lazyhat.kraftui.editor.EditorViewModel
import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.program.PrimitiveTextureRegion
import ru.lazyhat.kraftui.program.PrimitiveTextureScaling
import ru.lazyhat.kraftui.program.RenderBackend
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.image.BufferedImage

class ImageRenderBackend(
    private val image: BufferedImage,
    private val font: PreviewFont,
    private val textAntialiasing: Boolean = false,
    private val textureResolver: PreviewTextureResolver = MissingPreviewTextureResolver,
) : RenderBackend {
    private val graphics: Graphics2D = image.createGraphics()
    private val clipStack = ArrayDeque<Shape?>()

    init {
        graphics.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            if (textAntialiasing) {
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON
            } else {
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF
            },
        )
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
    }

    override fun fillRect(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Color,
    ) {
        graphics.color = color.toAwtColor()
        graphics.fillRect(x, y, width, height)
    }

    override fun drawText(
        x: Int,
        y: Int,
        text: String,
        color: Color,
    ) {
        font.draw(image, x, y, text, color.value.toInt())
    }

    override fun drawTerminalSurface(
        x: Int,
        y: Int,
        snapshot: Any,
    ) {
        throw UnsupportedOperationException("Terminal surfaces are not supported by image UI previews")
    }

    override fun drawTextureRegion(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        region: PrimitiveTextureRegion,
        scaling: PrimitiveTextureScaling,
    ) {
        if (width <= 0 || height <= 0) return
        val texture = textureResolver.resolve(region)
        require(region.atlasWidth == texture.width && region.atlasHeight == texture.height) {
            "Preview texture ${region.namespace}:${region.path} has size ${texture.width}x${texture.height}, " +
                "but region declares ${region.atlasWidth}x${region.atlasHeight}"
        }
        when (scaling) {
            PrimitiveTextureScaling.Stretch ->
                graphics.drawImage(
                    texture,
                    x,
                    y,
                    x + width,
                    y + height,
                    region.sourceX,
                    region.sourceY,
                    region.sourceX + region.sourceWidth,
                    region.sourceY + region.sourceHeight,
                    null,
                )
            PrimitiveTextureScaling.Tile -> drawTiledTextureRegion(texture, x, y, width, height, region)
        }
    }

    override fun pushClip(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        val previousClip = graphics.clip
        clipStack.addLast(previousClip)

        val requested = Rectangle(x, y, width, height)
        val nextClip =
            if (previousClip == null) {
                requested
            } else {
                previousClip.bounds.intersection(requested)
            }
        graphics.clip = nextClip
    }

    override fun popClip() {
        check(clipStack.isNotEmpty()) { "Cannot pop UI preview clip: clip stack is empty" }
        graphics.clip = clipStack.removeLast()
    }

    override fun drawCodeEditor(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        viewModel: EditorViewModel,
        fontWidth: Int,
        fontHeight: Int,
    ) {
        throw UnsupportedOperationException("Code editors are not supported by image UI previews")
    }

    override fun measureText(text: String): Int =
        font.width(text)

    fun close() {
        check(clipStack.isEmpty()) { "UI preview rendering finished with ${clipStack.size} unclosed clips" }
        graphics.dispose()
    }

    private fun Color.toAwtColor(): java.awt.Color =
        java.awt.Color(value.toInt(), true)

    private fun drawTiledTextureRegion(
        texture: BufferedImage,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        region: PrimitiveTextureRegion,
    ) {
        var dx = 0
        while (dx < width) {
            val tileWidth = minOf(region.sourceWidth, width - dx)
            var dy = 0
            while (dy < height) {
                val tileHeight = minOf(region.sourceHeight, height - dy)
                graphics.drawImage(
                    texture,
                    x + dx,
                    y + dy,
                    x + dx + tileWidth,
                    y + dy + tileHeight,
                    region.sourceX,
                    region.sourceY,
                    region.sourceX + tileWidth,
                    region.sourceY + tileHeight,
                    null,
                )
                dy += tileHeight
            }
            dx += tileWidth
        }
    }
}
