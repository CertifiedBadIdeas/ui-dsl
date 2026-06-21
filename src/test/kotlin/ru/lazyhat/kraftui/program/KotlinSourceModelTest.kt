package ru.lazyhat.kraftui.program

import kotlin.test.Test
import kotlin.test.assertEquals

class KotlinSourceModelTest {
    @Test
    fun kotlinSourceModelWritesFileStructureAndBlocks() {
        val source =
            KotlinSourceFile(
                packageName = "ru.lazyhat.generated",
                imports = setOf("net.minecraft.client.gui.GuiGraphics"),
                declarations =
                    listOf(
                        KotlinClassDeclaration(
                            name = "GeneratedScreen",
                            members =
                                listOf(
                                    KotlinPropertyDeclaration(
                                        name = "hitRegions",
                                        initializer = "arrayOf<HitRegion>()",
                                        modifiers = listOf("private"),
                                    ),
                                    KotlinRawClassMember(
                                        lines =
                                            listOf(
                                                "private data class HitRegion(",
                                                "    val id: Int,",
                                                ")",
                                            ),
                                    ),
                                    KotlinFunctionDeclaration(
                                        name = "render",
                                        parameters =
                                            listOf(
                                                KotlinParameter("graphics", "GuiGraphics"),
                                                KotlinParameter("state", "ScreenState"),
                                            ),
                                        body =
                                            KotlinBlock(
                                                statements =
                                                    listOf(
                                                        KotlinStatement.Expression("graphics.pose()"),
                                                        KotlinStatement.If(
                                                            condition = "state.visible",
                                                            body =
                                                                KotlinBlock(
                                                                    statements =
                                                                        listOf(
                                                                            KotlinStatement.Expression("drawPanel(graphics)"),
                                                                        ),
                                                                ),
                                                        ),
                                                    ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            ).render()

        assertEquals(
            """
            package ru.lazyhat.generated

            import net.minecraft.client.gui.GuiGraphics

            class GeneratedScreen {
                private val hitRegions = arrayOf<HitRegion>()

                private data class HitRegion(
                    val id: Int,
                )

                fun render(graphics: GuiGraphics, state: ScreenState) {
                    graphics.pose()
                    if (state.visible) {
                        drawPanel(graphics)
                    }
                }
            }
            """.trimIndent() + "\n",
            source,
        )
    }
}
