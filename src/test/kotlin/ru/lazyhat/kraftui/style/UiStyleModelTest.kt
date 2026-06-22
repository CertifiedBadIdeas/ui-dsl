package ru.lazyhat.kraftui.style

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.TextureAtlas
import ru.lazyhat.kraftui.foundation.TextureInsets
import ru.lazyhat.kraftui.foundation.TextureRegion
import ru.lazyhat.kraftui.foundation.TextureStyle
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy
import kotlin.test.Test
import kotlin.test.assertFailsWith

class UiStyleModelTest {
    @Test
    fun buttonStyleRequiresAllButtonStates() {
        assertFailsWith<IllegalArgumentException> {
            ButtonStyle(
                states = mapOf(
                    ButtonState.Normal to SurfaceStyle(fill = StyleColor.Constant(Color.Black)),
                ),
                label = TextStyle(
                    color = StyleColor.Constant(Color.White),
                    overflow = TextOverflowPolicy.Ellipsize,
                    lineHeight = 9,
                ),
            )
        }
    }

    @Test
    fun tabStyleRequiresSelectedState() {
        assertFailsWith<IllegalArgumentException> {
            TabStyle(
                states = mapOf(
                    TabState.Normal to SurfaceStyle(fill = StyleColor.Constant(Color.Black)),
                    TabState.Hovered to SurfaceStyle(fill = StyleColor.Constant(Color.Black)),
                    TabState.Disabled to SurfaceStyle(fill = StyleColor.Constant(Color.Black)),
                ),
                label = TextStyle(
                    color = StyleColor.Constant(Color.White),
                    overflow = TextOverflowPolicy.Ellipsize,
                    lineHeight = 9,
                ),
            )
        }
    }

    @Test
    fun surfaceRejectsNegativeInsets() {
        assertFailsWith<IllegalArgumentException> {
            SurfaceStyle(
                fill = StyleColor.Constant(Color.Black),
                padding = Insets(left = -1, top = 0, right = 0, bottom = 0),
            )
        }
    }

    @Test
    fun textureRegionRejectsInvalidCoordinates() {
        assertFailsWith<IllegalArgumentException> {
            TextureRegion(
                atlas = TextureAtlas(namespace = "testui", path = "textures/gui/widgets.png", width = 256, height = 256),
                x = 0,
                y = 0,
                width = 0,
                height = 1,
            )
        }
    }

    @Test
    fun nineSliceRejectsBordersLargerThanRegion() {
        assertFailsWith<IllegalArgumentException> {
            TextureStyle.NineSlice(
                region =
                    TextureRegion(
                        atlas = TextureAtlas(namespace = "testui", path = "textures/gui/widgets.png", width = 256, height = 256),
                        x = 0,
                        y = 0,
                        width = 8,
                        height = 8,
                    ),
                border = TextureInsets(left = 4, top = 2, right = 5, bottom = 2),
            )
        }
    }

    @Test
    fun textStyleRejectsNonPositiveLineHeight() {
        assertFailsWith<IllegalArgumentException> {
            TextStyle(
                color = StyleColor.Constant(Color.White),
                overflow = TextOverflowPolicy.Ellipsize,
                lineHeight = 0,
            )
        }
    }
}
