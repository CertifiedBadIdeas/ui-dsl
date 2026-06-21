package ru.lazyhat.kraftui.preview

import java.awt.image.BufferedImage

interface PreviewFont {
    fun width(text: String): Int

    fun draw(
        target: BufferedImage,
        x: Int,
        y: Int,
        text: String,
        color: Int,
    )
}
