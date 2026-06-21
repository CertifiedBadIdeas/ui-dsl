package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Color
import ru.lazyhat.kraftui.foundation.modifier.Position
import ru.lazyhat.kraftui.foundation.modifier.TextAlignment
import ru.lazyhat.kraftui.foundation.modifier.TextOverflowPolicy

fun PrimitiveScreenProgram.generateMinecraftScreenSource(
    packageName: String,
    className: String,
    stateType: String,
    actionType: String,
    optimization: PrimitiveOptimizationOptions = PrimitiveOptimizationOptions(),
): PrimitiveScreenSource {
    require(packageName.isValidQualifiedIdentifier()) {
        "packageName must be a valid qualified Kotlin identifier"
    }
    require(className.isValidIdentifier()) {
        "className must be a valid Kotlin identifier"
    }
    require(stateType.isValidTypeName()) {
        "stateType must be a valid Kotlin type name"
    }
    require(actionType.isValidTypeName()) {
        "actionType must be a valid Kotlin type name"
    }
    rejectUnsupportedMinecraftOps()

    val source =
        KotlinSourceFile(
            packageName = packageName,
            imports =
                buildSet {
                    add("net.minecraft.client.Minecraft")
                    add("net.minecraft.client.gui.Font")
                    add("net.minecraft.client.gui.GuiGraphics")
                    if (bakedTextures.isNotEmpty()) {
                        add("net.minecraft.resources.ResourceLocation")
                    }
                },
            declarations =
                listOf(
                    KotlinClassDeclaration(
                        name = className,
                        members = minecraftClassMembers(stateType, actionType, optimization),
                    ),
                ),
        ).render()

    return PrimitiveScreenSource(
        packageName = packageName,
        className = className,
        source = source,
        assets = minecraftBakedTextureAssets(optimization),
    )
}

private fun PrimitiveScreenProgram.minecraftBakedTextureAssets(optimization: PrimitiveOptimizationOptions): List<PrimitiveGeneratedAsset> {
    if (bakedTextures.isEmpty()) return emptyList()
    val bakingOptions =
        optimization.staticTextureBaking as? PrimitiveStaticTextureBakingOptions.Enabled
            ?: error("Minecraft source generation requires enabled static texture baking options for baked textures")
    return bakedTextures.map { texture ->
        PrimitiveGeneratedAsset(
            path = texture.assetPath(bakingOptions),
            bytes = texture.toPngBytes(),
        )
    }
}

private fun PrimitiveScreenProgram.rejectUnsupportedMinecraftOps() {
    val unsupported =
        analyze(
            options =
                PrimitiveProgramAnalysisOptions(
                    target = PrimitiveTargetCapabilities.minecraftGuiGraphics,
                ),
        ).diagnostics.filterIsInstance<PrimitiveProgramDiagnostic.UnsupportedTargetOperation>()
    require(unsupported.isEmpty()) {
        "Minecraft target cannot generate unsupported primitive operations:\n" +
            unsupported.joinToString(separator = "\n") {
                "${it.path}: ${it.operation}"
            }
        }
}

private fun PrimitiveScreenProgram.minecraftClassMembers(
    stateType: String,
    actionType: String,
    optimization: PrimitiveOptimizationOptions,
): List<KotlinClassMember> =
    buildList {
        val program = this@minecraftClassMembers
        val cacheTextLayout = optimization.enables(PrimitiveOptimizationPass.TextLayoutCaching)
        val precomputeHitRegions = optimization.enables(PrimitiveOptimizationPass.HitRegionPrecompute)
        val groupVisibilityBlocks = optimization.enables(PrimitiveOptimizationPass.VisibilityBlockGrouping)
        add(
            KotlinPropertyDeclaration(
                name = "clipStack",
                initializer = "ArrayDeque<ClipRect>()",
                modifiers = listOf("private"),
            ),
        )
        if (cacheTextLayout) {
            add(minecraftTextLayoutCacheMember())
        }
        if (precomputeHitRegions) {
            add(program.inputInstructions.minecraftHitRegionsMember())
        }
        program.bakedTextures.forEachIndexed { index, texture ->
            add(
                KotlinPropertyDeclaration(
                    name = "${texture.id.kotlinIdentifier()}ResourceName",
                    initializer = texture.minecraftResourceLocationExpression(optimization),
                    modifiers = listOf("private"),
                ),
            )
        }
        add(
            KotlinFunctionDeclaration(
                name = "render",
                parameters =
                    listOf(
                        KotlinParameter("graphics", "GuiGraphics"),
                        KotlinParameter("state", stateType),
                        KotlinParameter("screenOriginX", "Int"),
                        KotlinParameter("screenOriginY", "Int"),
                    ),
                body =
                    KotlinBlock(
                        statements =
                            buildList {
                                add(KotlinStatement.Expression("clipStack.clear()"))
                                addAll(program.renderInstructions.minecraftRenderStatements(groupVisibilityBlocks))
                                add(
                                    KotlinStatement.If(
                                        condition = "clipStack.isNotEmpty()",
                                        body =
                                            KotlinBlock(
                                                statements =
                                                    listOf(
                                                        KotlinStatement.Expression("graphics.disableScissor()"),
                                                        KotlinStatement.Expression("clipStack.clear()"),
                                                    ),
                                            ),
                                    ),
                                )
                            },
                    ),
            ),
        )
        add(
            KotlinFunctionDeclaration(
                name = "clearDynamicCaches",
                body =
                    KotlinBlock(
                        statements =
                            if (cacheTextLayout) {
                                listOf(KotlinStatement.Expression("textLayoutCache.clear()"))
                            } else {
                                emptyList()
                            },
                    ),
            ),
        )
        add(
            KotlinFunctionDeclaration(
                name = "mouseClicked",
                parameters =
                    listOf(
                        KotlinParameter("state", stateType),
                        KotlinParameter("x", "Int"),
                        KotlinParameter("y", "Int"),
                    ),
                returnType = "$actionType?",
                body =
                    KotlinBlock(
                        statements =
                            buildList {
                                addAll(program.inputInstructions.minecraftInputStatements(precomputeHitRegions))
                                add(KotlinStatement.Return("null"))
                            },
                    ),
            ),
        )
        if (precomputeHitRegions) {
            program.inputInstructions.minecraftInputHelpersMember(stateType, actionType)?.let(::add)
        }
        add(minecraftHelpersMember(cacheTextLayout))
    }

private fun minecraftTextLayoutCacheMember(): KotlinClassMember =
    KotlinRawClassMember(
        lines =
            listOf(
                "private val textLayoutCache =",
                "    object : LinkedHashMap<TextLayoutKey, List<TextLine>>(256, 0.75f, true) {",
                "        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TextLayoutKey, List<TextLine>>): Boolean =",
                "            size > 256",
                "    }",
            ),
    )

private fun List<PrimitiveInputInstruction>.minecraftHitRegionsMember(): KotlinClassMember {
    val regions = filterIsInstance<PrimitiveInputInstruction.ClickRegion>()
    if (regions.isEmpty()) {
        return KotlinPropertyDeclaration(
            name = "hitRegions",
            initializer = "arrayOf<HitRegion>()",
            modifiers = listOf("private"),
        )
    }
    return KotlinRawClassMember(
        lines =
            buildList {
                add("private val hitRegions = arrayOf(")
                regions.forEachIndexed { index, region ->
                    add("    HitRegion(id = $index, x = ${region.x}, y = ${region.y}, width = ${region.width}, height = ${region.height}),")
                }
                add(")")
            },
    )
}

private fun List<PrimitiveRenderInstruction>.minecraftRenderStatements(groupVisibilityBlocks: Boolean): List<KotlinStatement> =
    if (groupVisibilityBlocks) {
        groupedMinecraftRenderStatements()
    } else {
        flatMinecraftRenderStatements()
    }

private fun List<PrimitiveRenderInstruction>.groupedMinecraftRenderStatements(): List<KotlinStatement> =
    buildList {
        var index = 0
        while (index < this@groupedMinecraftRenderStatements.size) {
            val visible = this@groupedMinecraftRenderStatements[index].visible
            val end = nextDifferentVisibilityIndex(index)
            val statements =
                buildList {
                    for (instructionIndex in index until end) {
                        addAll(this@groupedMinecraftRenderStatements[instructionIndex].minecraftRenderStatements(instructionIndex))
                    }
                }
            addVisibleStatements(visible, statements)
            index = end
        }
    }

private fun List<PrimitiveRenderInstruction>.flatMinecraftRenderStatements(): List<KotlinStatement> =
    buildList {
        this@flatMinecraftRenderStatements.forEachIndexed { index, instruction ->
            addVisibleStatements(
                visible = instruction.visible,
                statements = instruction.minecraftRenderStatements(index),
            )
        }
    }

private fun MutableList<KotlinStatement>.addVisibleStatements(
    visible: PrimitiveValueExpression?,
    statements: List<KotlinStatement>,
) {
    if (visible == null) {
        addAll(statements)
    } else {
        add(
            KotlinStatement.If(
                condition = visible.kotlinExpression(),
                body = KotlinBlock(statements),
            ),
        )
    }
}

private fun PrimitiveRenderInstruction.minecraftRenderStatements(index: Int): List<KotlinStatement> =
    buildList {
        if (origin != null) {
            add(KotlinStatement.Expression("val origin$index = ${origin.kotlinExpression()}"))
            add(KotlinStatement.Expression("val ox$index = origin$index.x"))
            add(KotlinStatement.Expression("val oy$index = origin$index.y"))
        }
        addAll(op.minecraftRenderStatements(index, origin != null))
    }

private fun PrimitiveRenderOp.minecraftRenderStatements(
    index: Int,
    hasOrigin: Boolean,
): List<KotlinStatement> {
    val ox = " + screenOriginX" + if (hasOrigin) " + ox$index" else ""
    val oy = " + screenOriginY" + if (hasOrigin) " + oy$index" else ""
    return when (this) {
        is PrimitiveRenderOp.FillRect ->
            listOf(
                KotlinStatement.Expression(
                    "graphics.fill($x$ox, $y$oy, ${x + width}$ox, ${y + height}$oy, ${color.minecraftColorExpression()})",
                ),
            )
        is PrimitiveRenderOp.DrawText -> minecraftDrawTextStatements(index, ox, oy)
        is PrimitiveRenderOp.PushClip ->
            listOf(
                KotlinStatement.Expression("pushClip(graphics, $x$ox, $y$oy, $width, $height)"),
            )
        PrimitiveRenderOp.PopClip ->
            listOf(
                KotlinStatement.Expression("popClip(graphics)"),
            )
        is PrimitiveRenderOp.DrawTerminalSurface,
        is PrimitiveRenderOp.DrawCodeEditor,
        -> error("unsupported Minecraft operation should have been rejected before generation")
        is PrimitiveRenderOp.DrawBakedTexture ->
            listOf(
                KotlinStatement.Expression(
                    "graphics.blit(${textureId.kotlinIdentifier()}ResourceName, $x$ox, $y$oy, 0.0f, 0.0f, $width, $height, $width, $height)",
                ),
            )
    }
}

private fun PrimitiveRenderOp.DrawText.minecraftDrawTextStatements(
    index: Int,
    ox: String,
    oy: String,
): List<KotlinStatement> {
    val textFlow = flow
    return listOf(
        KotlinStatement.Expression("val font$index = Minecraft.getInstance().font"),
        KotlinStatement.Expression(
            "drawText(graphics, font$index, ${text.kotlinExpression()}, $x$ox, $y$oy, $width, $height, ${color.minecraftColorExpression()}, ${alignment.kotlinExpression()}, ${overflow.kotlinExpression()}, ${textFlow.wrap.minecraftBooleanExpression()}, ${textFlow.maxLines.kotlinNullableInt()}, ${textFlow.lineHeight})",
        ),
    )
}

private fun List<PrimitiveRenderInstruction>.nextDifferentVisibilityIndex(start: Int): Int {
    val visible = this[start].visible
    var index = start + 1
    while (index < size && this[index].visible == visible) {
        index++
    }
    return index
}

private fun List<PrimitiveInputInstruction>.minecraftInputStatements(precomputeHitRegions: Boolean): List<KotlinStatement> =
    if (precomputeHitRegions) {
        precomputedMinecraftInputStatements()
    } else {
        inlineMinecraftInputStatements()
    }

private fun List<PrimitiveInputInstruction>.precomputedMinecraftInputStatements(): List<KotlinStatement> {
    val regions = filterIsInstance<PrimitiveInputInstruction.ClickRegion>()
    if (regions.isEmpty()) return emptyList()
    return listOf(
        KotlinStatement.For(
            header = "region in hitRegions",
            body =
                KotlinBlock(
                    statements =
                        listOf(
                            KotlinStatement.Expression("if (!isHitRegionVisible(state, region.id)) continue"),
                            KotlinStatement.Expression("val left = region.x + hitRegionOriginX(state, region.id)"),
                            KotlinStatement.Expression("val top = region.y + hitRegionOriginY(state, region.id)"),
                            KotlinStatement.If(
                                condition = "x >= left && y >= top && x < left + region.width && y < top + region.height",
                                body =
                                    KotlinBlock(
                                        statements =
                                            listOf(
                                                KotlinStatement.Return("hitRegionAction(state, region.id)"),
                                            ),
                                    ),
                            ),
                        ),
                ),
        ),
    )
}

private fun List<PrimitiveInputInstruction>.inlineMinecraftInputStatements(): List<KotlinStatement> =
    buildList {
        this@inlineMinecraftInputStatements
            .filterIsInstance<PrimitiveInputInstruction.ClickRegion>()
            .forEachIndexed { index, region ->
                val statements =
                    buildList {
                        if (region.origin != null) {
                            add(KotlinStatement.Expression("val inputOrigin$index = ${region.origin.kotlinExpression()}"))
                            add(KotlinStatement.Expression("val inputOx$index = inputOrigin$index.x"))
                            add(KotlinStatement.Expression("val inputOy$index = inputOrigin$index.y"))
                        }
                        val ox = if (region.origin == null) "" else " + inputOx$index"
                        val oy = if (region.origin == null) "" else " + inputOy$index"
                        add(
                            KotlinStatement.If(
                                condition =
                                    "x >= ${region.x}$ox && y >= ${region.y}$oy && x < ${region.x}$ox + ${region.width} && y < ${region.y}$oy + ${region.height}",
                                body =
                                    KotlinBlock(
                                        statements =
                                            listOf(
                                                KotlinStatement.Return(region.action?.kotlinExpression() ?: "null"),
                                            ),
                                    ),
                            ),
                        )
                    }
                addVisibleStatements(region.visible, statements)
            }
    }

private fun List<PrimitiveInputInstruction>.minecraftInputHelpersMember(
    stateType: String,
    actionType: String,
): KotlinClassMember? {
    val source =
        buildString {
            appendMinecraftInputHelpers(stateType, actionType, this@minecraftInputHelpersMember)
        }.toClassMemberLines()
    if (source.isEmpty()) return null
    return KotlinRawClassMember(source)
}

private fun StringBuilder.appendMinecraftInputHelpers(
    stateType: String,
    actionType: String,
    inputInstructions: List<PrimitiveInputInstruction>,
) {
    val regions = inputInstructions.filterIsInstance<PrimitiveInputInstruction.ClickRegion>()
    if (regions.isEmpty()) return
    appendLine()
    appendLine("    private fun isHitRegionVisible(state: $stateType, id: Int): Boolean =")
    appendLine("        when (id) {")
    regions.forEachIndexed { index, region ->
        appendLine("            $index -> ${region.visible?.kotlinExpression() ?: "true"}")
    }
    appendLine("            else -> false")
    appendLine("        }")
    appendLine()
    appendLine("    private fun hitRegionOriginX(state: $stateType, id: Int): Int =")
    appendLine("        when (id) {")
    regions.forEachIndexed { index, region ->
        appendLine("            $index -> ${region.origin?.componentExpression("x") ?: "0"}")
    }
    appendLine("            else -> 0")
    appendLine("        }")
    appendLine()
    appendLine("    private fun hitRegionOriginY(state: $stateType, id: Int): Int =")
    appendLine("        when (id) {")
    regions.forEachIndexed { index, region ->
        appendLine("            $index -> ${region.origin?.componentExpression("y") ?: "0"}")
    }
    appendLine("            else -> 0")
    appendLine("        }")
    appendLine()
    appendLine("    private fun hitRegionAction(state: $stateType, id: Int): $actionType? =")
    appendLine("        when (id) {")
    regions.forEachIndexed { index, region ->
        appendLine("            $index -> ${region.action?.kotlinExpression() ?: "null"}")
    }
    appendLine("            else -> null")
    appendLine("        }")
}

private fun minecraftHelpersMember(cacheTextLayout: Boolean): KotlinClassMember =
    KotlinRawClassMember(
        lines =
            buildString {
                appendMinecraftHelpers(cacheTextLayout)
            }.toClassMemberLines(),
    )

private fun StringBuilder.appendMinecraftHelpers(cacheTextLayout: Boolean) {
    appendLine()
    appendLine("    private fun drawText(")
    appendLine("        graphics: GuiGraphics,")
    appendLine("        font: Font,")
    appendLine("        text: String,")
    appendLine("        x: Int,")
    appendLine("        y: Int,")
    appendLine("        width: Int,")
    appendLine("        height: Int,")
    appendLine("        color: Int,")
    appendLine("        alignment: TextAlignment,")
    appendLine("        overflow: TextOverflowPolicy,")
    appendLine("        wrap: Boolean,")
    appendLine("        maxLines: Int?,")
    appendLine("        lineHeight: Int,")
    appendLine("    ) {")
    appendLine("        val visibleLineCount = (height / lineHeight).coerceAtLeast(0)")
    appendLine("        val effectiveMaxLines =")
    appendLine("            when {")
    appendLine("                visibleLineCount == 0 -> 0")
    appendLine("                maxLines == null -> visibleLineCount")
    appendLine("                else -> minOf(maxLines, visibleLineCount)")
    appendLine("            }")
    appendLine("        if (effectiveMaxLines <= 0) return")
    if (cacheTextLayout) {
        appendLine("        val key = TextLayoutKey(font, text, width, height, wrap, maxLines, lineHeight, overflow)")
        appendLine("        val finalLines =")
        appendLine("            textLayoutCache.getOrPut(key) {")
        appendLine("                buildFinalTextLines(font, text, width, wrap, overflow, effectiveMaxLines)")
        appendLine("            }")
    } else {
        appendLine("        val finalLines = buildFinalTextLines(font, text, width, wrap, overflow, effectiveMaxLines)")
    }
    appendLine("        pushClip(graphics, x, y, width, height)")
    appendLine("        try {")
    appendLine("            finalLines.forEachIndexed { index, line ->")
    appendLine("                val textX =")
    appendLine("                    when (alignment) {")
    appendLine("                        TextAlignment.Start -> x")
    appendLine("                        TextAlignment.Center -> x + (width - line.width) / 2")
    appendLine("                        TextAlignment.End -> x + width - line.width")
    appendLine("                    }")
    appendLine("                graphics.drawString(font, line.text, textX, y + index * lineHeight, color, false)")
    appendLine("            }")
    appendLine("        } finally {")
    appendLine("            popClip(graphics)")
    appendLine("        }")
    appendLine("    }")
    appendLine()
    appendLine("    private fun buildFinalTextLines(")
    appendLine("        font: Font,")
    appendLine("        text: String,")
    appendLine("        width: Int,")
    appendLine("        wrap: Boolean,")
    appendLine("        overflow: TextOverflowPolicy,")
    appendLine("        effectiveMaxLines: Int,")
    appendLine("    ): List<TextLine> {")
    appendLine("        val sourceLines = buildTextLines(font, text, width, wrap)")
    appendLine("        val visibleLines = sourceLines.take(effectiveMaxLines)")
    appendLine("        val truncatedByLineLimit = sourceLines.size > visibleLines.size")
    appendLine("        val finalLines =")
    appendLine("            if (overflow == TextOverflowPolicy.Ellipsize) {")
    appendLine("                ellipsizeVisibleLines(font, visibleLines, width, truncatedByLineLimit)")
    appendLine("            } else {")
    appendLine("                visibleLines")
    appendLine("            }")
    appendLine("        return finalLines.map { TextLine(it, font.width(it)) }")
    appendLine("    }")
    appendLine()
    appendLine("    private fun buildTextLines(")
    appendLine("        font: Font,")
    appendLine("        text: String,")
    appendLine("        width: Int,")
    appendLine("        wrap: Boolean,")
    appendLine("    ): List<String> =")
    appendLine("        text")
    appendLine("            .split('\\n')")
    appendLine("            .flatMap { paragraph ->")
    appendLine("                if (wrap) wrapParagraph(font, paragraph, width) else listOf(paragraph)")
    appendLine("            }")
    appendLine("            .ifEmpty { listOf(\"\") }")
    appendLine()
    appendLine("    private fun wrapParagraph(")
    appendLine("        font: Font,")
    appendLine("        paragraph: String,")
    appendLine("        width: Int,")
    appendLine("    ): List<String> {")
    appendLine("        if (paragraph.isEmpty()) return listOf(\"\")")
    appendLine("        if (width <= 0) return listOf(paragraph)")
    appendLine("        val words = paragraph.split(Regex(\"\\\\s+\")).filter { it.isNotEmpty() }")
    appendLine("        if (words.isEmpty()) return listOf(\"\")")
    appendLine("        val lines = mutableListOf<String>()")
    appendLine("        var current = \"\"")
    appendLine("        for (word in words) {")
    appendLine("            val candidate = if (current.isEmpty()) word else \"\$current \$word\"")
    appendLine("            if (current.isNotEmpty() && font.width(candidate) > width) {")
    appendLine("                lines += current")
    appendLine("                current = word")
    appendLine("            } else {")
    appendLine("                current = candidate")
    appendLine("            }")
    appendLine("        }")
    appendLine("        if (current.isNotEmpty()) lines += current")
    appendLine("        return lines")
    appendLine("    }")
    appendLine()
    appendLine("    private fun ellipsizeVisibleLines(")
    appendLine("        font: Font,")
    appendLine("        lines: List<String>,")
    appendLine("        width: Int,")
    appendLine("        truncatedByLineLimit: Boolean,")
    appendLine("    ): List<String> {")
    appendLine("        if (lines.isEmpty()) return lines")
    appendLine("        return lines.mapIndexed { index, line ->")
    appendLine("            val isLastVisibleLine = index == lines.lastIndex")
    appendLine("            if (font.width(line) > width || (isLastVisibleLine && truncatedByLineLimit)) {")
    appendLine("                ellipsize(font, line, width, forceMarker = isLastVisibleLine && truncatedByLineLimit)")
    appendLine("            } else {")
    appendLine("                line")
    appendLine("            }")
    appendLine("        }")
    appendLine("    }")
    appendLine()
    appendLine("    private fun ellipsize(")
    appendLine("        font: Font,")
    appendLine("        text: String,")
    appendLine("        width: Int,")
    appendLine("        forceMarker: Boolean,")
    appendLine("    ): String {")
    appendLine("        if (!forceMarker && font.width(text) <= width) return text")
    appendLine("        if (width <= 0) return \"\"")
    appendLine("        var marker = \"...\"")
    appendLine("        while (marker.isNotEmpty() && font.width(marker) > width) {")
    appendLine("            marker = marker.dropLast(1)")
    appendLine("        }")
    appendLine("        if (marker.isEmpty()) return \"\"")
    appendLine("        var end = text.length")
    appendLine("        while (end > 0 && font.width(text.take(end) + marker) > width) {")
    appendLine("            end--")
    appendLine("        }")
    appendLine("        return text.take(end) + marker")
    appendLine("    }")
    appendLine()
    appendLine("    private fun pushClip(")
    appendLine("        graphics: GuiGraphics,")
    appendLine("        x: Int,")
    appendLine("        y: Int,")
    appendLine("        width: Int,")
    appendLine("        height: Int,")
    appendLine("    ) {")
    appendLine("        val requested = ClipRect(x, y, x + width, y + height)")
    appendLine("        val clipped = clipStack.lastOrNull()?.intersect(requested) ?: requested")
    appendLine("        clipStack.addLast(clipped)")
    appendLine("        graphics.enableScissor(clipped.left, clipped.top, clipped.right, clipped.bottom)")
    appendLine("    }")
    appendLine()
    appendLine("    private fun popClip(graphics: GuiGraphics) {")
    appendLine("        check(clipStack.isNotEmpty()) { \"Cannot pop an empty clip stack\" }")
    appendLine("        clipStack.removeLast()")
    appendLine("        val current = clipStack.lastOrNull()")
    appendLine("        if (current == null) {")
    appendLine("            graphics.disableScissor()")
    appendLine("        } else {")
    appendLine("            graphics.enableScissor(current.left, current.top, current.right, current.bottom)")
    appendLine("        }")
    appendLine("    }")
    appendLine()
    appendLine("    private data class ClipRect(")
    appendLine("        val left: Int,")
    appendLine("        val top: Int,")
    appendLine("        val right: Int,")
    appendLine("        val bottom: Int,")
    appendLine("    ) {")
    appendLine("        fun intersect(other: ClipRect): ClipRect =")
    appendLine("            ClipRect(")
    appendLine("                left = maxOf(left, other.left),")
    appendLine("                top = maxOf(top, other.top),")
    appendLine("                right = minOf(right, other.right),")
    appendLine("                bottom = minOf(bottom, other.bottom),")
    appendLine("            )")
    appendLine("    }")
    appendLine()
    appendLine("    private data class Position(")
    appendLine("        val x: Int,")
    appendLine("        val y: Int,")
    appendLine("    )")
    appendLine()
    appendLine("    private data class TextLine(")
    appendLine("        val text: String,")
    appendLine("        val width: Int,")
    appendLine("    )")
    appendLine()
    appendLine("    private data class HitRegion(")
    appendLine("        val id: Int,")
    appendLine("        val x: Int,")
    appendLine("        val y: Int,")
    appendLine("        val width: Int,")
    appendLine("        val height: Int,")
    appendLine("    )")
    appendLine()
    appendLine("    private data class TextLayoutKey(")
    appendLine("        val font: Font,")
    appendLine("        val text: String,")
    appendLine("        val width: Int,")
    appendLine("        val height: Int,")
    appendLine("        val wrap: Boolean,")
    appendLine("        val maxLines: Int?,")
    appendLine("        val lineHeight: Int,")
    appendLine("        val overflow: TextOverflowPolicy,")
    appendLine("    )")
    appendLine()
    appendLine("    private enum class TextAlignment {")
    appendLine("        Start,")
    appendLine("        Center,")
    appendLine("        End,")
    appendLine("    }")
    appendLine()
    appendLine("    private enum class TextOverflowPolicy {")
    appendLine("        FailInValidation,")
    appendLine("        Ellipsize,")
    appendLine("        Clip,")
    appendLine("    }")
}

private fun PrimitiveValueExpression.kotlinExpression(): String =
    when (this) {
        is PrimitiveValueExpression.Constant -> value.kotlinLiteral()
        is PrimitiveValueExpression.StateField -> "state.$fieldName"
        is PrimitiveValueExpression.And -> terms.joinToString(separator = " && ") { "(${it.kotlinExpression()})" }
    }

private fun PrimitiveValueExpression.componentExpression(component: String): String =
    when (this) {
        is PrimitiveValueExpression.Constant -> "${value.kotlinLiteral()}.$component"
        is PrimitiveValueExpression.StateField -> "state.$fieldName.$component"
        is PrimitiveValueExpression.And -> error("Boolean expression cannot provide component $component")
    }

private fun PrimitiveValueExpression.minecraftColorExpression(): String =
    when (this) {
        is PrimitiveValueExpression.Constant -> value.minecraftColorLiteral()
        is PrimitiveValueExpression.StateField -> "state.$fieldName"
        is PrimitiveValueExpression.And -> error("Boolean expression cannot be used as a Minecraft color")
    }

private fun Any?.kotlinLiteral(): String =
    when (this) {
        null -> "null"
        is String -> "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        is Boolean -> toString()
        is Int -> toString()
        is UInt -> "${toString()}u"
        is Position -> "Position($x, $y)"
        else -> error("Cannot generate Kotlin literal for ${this::class.qualifiedName}")
    }

private fun Any?.minecraftColorLiteral(): String =
    when (this) {
        is Color -> "0x${value.toLong().toString(16).uppercase().padStart(8, '0')}.toInt()"
        is Int -> "0x${toUInt().toLong().toString(16).uppercase().padStart(8, '0')}.toInt()"
        is UInt -> "0x${toLong().toString(16).uppercase().padStart(8, '0')}.toInt()"
        else -> error("Cannot generate Minecraft color literal for ${this?.let { it::class.qualifiedName } ?: "null"}")
    }

private fun PrimitiveBakedTexture.minecraftResourceLocationExpression(optimization: PrimitiveOptimizationOptions): String {
    val bakingOptions =
        optimization.staticTextureBaking as? PrimitiveStaticTextureBakingOptions.Enabled
            ?: error("Minecraft source generation requires enabled static texture baking options for baked textures")
    return "ResourceLocation.fromNamespaceAndPath(\"${bakingOptions.textureNamespace}\", \"${bakingOptions.texturePathPrefix}/$id.png\")"
}

private fun TextAlignment.kotlinExpression(): String =
    when (this) {
        TextAlignment.Start -> "TextAlignment.Start"
        TextAlignment.Center -> "TextAlignment.Center"
        TextAlignment.End -> "TextAlignment.End"
    }

private fun TextOverflowPolicy.kotlinExpression(): String =
    when (this) {
        TextOverflowPolicy.FailInValidation -> "TextOverflowPolicy.FailInValidation"
        TextOverflowPolicy.Ellipsize -> "TextOverflowPolicy.Ellipsize"
        TextOverflowPolicy.Clip -> "TextOverflowPolicy.Clip"
    }

private fun ru.lazyhat.kraftui.foundation.modifier.TextWrapPolicy.minecraftBooleanExpression(): String =
    when (this) {
        ru.lazyhat.kraftui.foundation.modifier.TextWrapPolicy.NoWrap -> "false"
        ru.lazyhat.kraftui.foundation.modifier.TextWrapPolicy.WordWrap -> "true"
    }

private fun Int?.kotlinNullableInt(): String = this?.toString() ?: "null"

private fun String.toClassMemberLines(): List<String> =
    trim('\n', '\r')
        .lines()
        .dropWhile { it.isEmpty() }
        .dropLastWhile { it.isEmpty() }
        .map { line ->
            if (line.startsWith("    ")) line.drop(4) else line
        }

private fun String.isValidQualifiedIdentifier(): Boolean =
    split('.').all { it.isValidIdentifier() }

private fun String.isValidTypeName(): Boolean =
    split('.').all { it.isValidIdentifier() }

private fun String.isValidIdentifier(): Boolean =
    isNotEmpty() &&
        first().let { it == '_' || it.isLetter() } &&
        drop(1).all { it == '_' || it.isLetterOrDigit() }
