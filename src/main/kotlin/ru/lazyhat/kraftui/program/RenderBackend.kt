package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.editor.EditorViewModel
import ru.lazyhat.kraftui.foundation.Color

interface RenderBackend {
    fun fillRect(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Color,
    )

    fun drawText(
        x: Int,
        y: Int,
        text: String,
        color: Color,
    )

    fun drawBakedTexture(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        argb: IntArray,
        textureWidth: Int,
        textureHeight: Int,
    ) {
        require(width == textureWidth && height == textureHeight) {
            "Scaled baked textures are not supported by the generic render backend"
        }
        require(argb.size == textureWidth * textureHeight) {
            "Baked texture pixel count must be exactly textureWidth * textureHeight"
        }
        for (py in 0 until textureHeight) {
            for (px in 0 until textureWidth) {
                val color = argb[py * textureWidth + px]
                if ((color ushr 24) != 0) {
                    fillRect(x + px, y + py, 1, 1, Color.hex(color))
                }
            }
        }
    }

    fun drawTextureRegion(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        region: PrimitiveTextureRegion,
        scaling: PrimitiveTextureScaling,
    ) {
        throw UnsupportedOperationException("External texture regions are not supported by this render backend")
    }

    fun drawTerminalSurface(
        x: Int,
        y: Int,
        snapshot: Any,
    )

    /**
     * Pushes a rectangular clip region onto an internal clip stack. Subsequent
     * draw calls are restricted to the intersection of all currently pushed
     * clips. Implementations are expected to support arbitrary nesting.
     */
    fun pushClip(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    )

    /** Pops the most recently pushed clip region. */
    fun popClip()

    /**
     * Draws a code editor at `(x, y)` with the given [width]/[height]. The
     * backend renders text, gutter, cursor, highlights and diagnostics from
     * [viewModel], using [fontWidth]/[fontHeight] as the monospace glyph
     * size.
     */
    fun drawCodeEditor(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        viewModel: EditorViewModel,
        fontWidth: Int,
        fontHeight: Int,
    )

    /**
     * Returns the rendered width of [text] in pixels. Used by canvas overlays that must align
     * with text rendered through [drawText] / [drawCodeEditor]. Backends with a real font use
     * the font's variable-width metrics; in-memory test backends typically return
     * `text.length * defaultFontWidth`.
     */
    fun measureText(text: String): Int
}
