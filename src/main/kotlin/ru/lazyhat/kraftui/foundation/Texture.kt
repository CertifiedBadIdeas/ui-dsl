package ru.lazyhat.kraftui.foundation

data class TextureAtlas(
    val namespace: String,
    val path: String,
    val width: Int,
    val height: Int,
) {
    init {
        require(namespace.isNotBlank()) { "texture namespace must not be blank" }
        require(path.isNotBlank()) { "texture path must not be blank" }
        require(!path.startsWith('/')) { "texture path must be relative" }
        require(width > 0) { "texture atlas width must be positive" }
        require(height > 0) { "texture atlas height must be positive" }
    }
}

data class TextureRegion(
    val atlas: TextureAtlas,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    init {
        require(x >= 0) { "texture region x must be non-negative" }
        require(y >= 0) { "texture region y must be non-negative" }
        require(width > 0) { "texture region width must be positive" }
        require(height > 0) { "texture region height must be positive" }
        require(x + width <= atlas.width) { "texture region must fit atlas width" }
        require(y + height <= atlas.height) { "texture region must fit atlas height" }
    }
}

data class TextureInsets(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(left >= 0) { "left texture inset must be non-negative" }
        require(top >= 0) { "top texture inset must be non-negative" }
        require(right >= 0) { "right texture inset must be non-negative" }
        require(bottom >= 0) { "bottom texture inset must be non-negative" }
    }
}

enum class TextureScaling {
    Stretch,
    Tile,
}

sealed interface TextureStyle {
    data class Layered(
        val layers: List<TextureStyle>,
    ) : TextureStyle {
        init {
            require(layers.isNotEmpty()) { "layered texture must contain at least one layer" }
            require(layers.none { it is Layered }) { "layered texture must be flattened before construction" }
        }
    }

    data class Resource(
        val namespace: String,
        val path: String,
    ) : TextureStyle {
        init {
            require(namespace.isNotBlank()) { "texture namespace must not be blank" }
            require(path.isNotBlank()) { "texture path must not be blank" }
            require(!path.startsWith('/')) { "texture path must be relative" }
        }
    }

    data class Region(
        val region: TextureRegion,
        val scaling: TextureScaling = TextureScaling.Stretch,
    ) : TextureStyle

    data class Checkerboard(
        val first: Color,
        val second: Color,
        val cellSize: Int,
    ) : TextureStyle {
        init {
            require(cellSize > 0) { "checkerboard cellSize must be positive" }
        }
    }

    data class BrassFrame(
        val base: Color,
        val borderWidth: Int,
        val noiseStrength: Int = 6,
        val ornament: Boolean = true,
        val ornamentSpacing: Int = 12,
        val seed: Int = 0,
    ) : TextureStyle {
        init {
            require(borderWidth > 0) { "brass frame borderWidth must be positive" }
            require(noiseStrength >= 0) { "brass frame noiseStrength must be non-negative" }
            require(ornamentSpacing > 1) { "brass frame ornamentSpacing must be greater than one" }
        }
    }

    data class NineSlice(
        val region: TextureRegion,
        val border: TextureInsets,
        val edgeScaling: TextureScaling = TextureScaling.Tile,
        val centerScaling: TextureScaling = TextureScaling.Stretch,
    ) : TextureStyle {
        init {
            require(border.left + border.right <= region.width) {
                "nine-slice horizontal borders must fit texture region"
            }
            require(border.top + border.bottom <= region.height) {
                "nine-slice vertical borders must fit texture region"
            }
        }
    }

    data class SegmentedFrame(
        val topLeft: TextureRegion,
        val top: TextureRegion,
        val topRight: TextureRegion,
        val left: TextureRegion,
        val right: TextureRegion,
        val bottomLeft: TextureRegion,
        val bottom: TextureRegion,
        val bottomRight: TextureRegion,
        val center: Region? = null,
        val edgeScaling: TextureScaling = TextureScaling.Tile,
    ) : TextureStyle {
        init {
            val atlas = topLeft.atlas
            val regions = listOf(top, topRight, left, right, bottomLeft, bottom, bottomRight)
            require(regions.all { it.atlas == atlas }) {
                "segmented frame regions must belong to the same texture atlas"
            }
            center?.let {
                require(it.region.atlas == atlas) {
                    "segmented frame center must belong to the same texture atlas"
                }
            }
            require(topLeft.width == bottomLeft.width) { "segmented frame left corner widths must match" }
            require(topRight.width == bottomRight.width) { "segmented frame right corner widths must match" }
            require(topLeft.height == topRight.height) { "segmented frame top corner heights must match" }
            require(bottomLeft.height == bottomRight.height) { "segmented frame bottom corner heights must match" }
        }
    }
}
