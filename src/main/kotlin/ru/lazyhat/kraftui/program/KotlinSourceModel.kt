package ru.lazyhat.kraftui.program

internal data class KotlinSourceFile(
    val packageName: String,
    val imports: Set<String> = emptySet(),
    val declarations: List<KotlinDeclaration>,
) {
    fun render(): String =
        buildString {
            appendLine("package $packageName")
            appendLine()
            imports.sorted().forEach { importName ->
                appendLineWithImport(importName)
            }
            if (imports.isNotEmpty()) appendLine()
            declarations.forEachIndexed { index, declaration ->
                declaration.writeTo(this, indent = 0)
                if (index != declarations.lastIndex) appendLine()
            }
        }

    private fun StringBuilder.appendLineWithImport(importName: String) {
        appendLine("import $importName")
    }
}

internal sealed interface KotlinDeclaration {
    fun writeTo(
        builder: StringBuilder,
        indent: Int,
    )
}

internal data class KotlinClassDeclaration(
    val name: String,
    val members: List<KotlinClassMember>,
    val modifiers: List<String> = emptyList(),
) : KotlinDeclaration {
    override fun writeTo(
        builder: StringBuilder,
        indent: Int,
    ) {
        val prefix = modifiers.joinToPrefix()
        builder.appendIndentedLine(indent, "${prefix}class $name {")
        members.forEachIndexed { index, member ->
            member.writeTo(builder, indent + 1)
            if (index != members.lastIndex) builder.appendLine()
        }
        builder.appendIndentedLine(indent, "}")
    }
}

internal sealed interface KotlinClassMember {
    fun writeTo(
        builder: StringBuilder,
        indent: Int,
    )
}

internal data class KotlinPropertyDeclaration(
    val name: String,
    val initializer: String,
    val type: String? = null,
    val modifiers: List<String> = emptyList(),
) : KotlinClassMember {
    override fun writeTo(
        builder: StringBuilder,
        indent: Int,
    ) {
        val prefix = modifiers.joinToPrefix()
        val typePart = type?.let { ": $it" } ?: ""
        builder.appendIndentedLine(indent, "${prefix}val $name$typePart = $initializer")
    }
}

internal data class KotlinFunctionDeclaration(
    val name: String,
    val parameters: List<KotlinParameter> = emptyList(),
    val returnType: String? = null,
    val body: KotlinBlock,
    val modifiers: List<String> = emptyList(),
) : KotlinClassMember {
    override fun writeTo(
        builder: StringBuilder,
        indent: Int,
    ) {
        val prefix = modifiers.joinToPrefix()
        val params = parameters.joinToString(", ") { "${it.name}: ${it.type}" }
        val returnPart = returnType?.let { ": $it" } ?: ""
        builder.appendIndentedLine(indent, "${prefix}fun $name($params)$returnPart {")
        body.writeTo(builder, indent + 1)
        builder.appendIndentedLine(indent, "}")
    }
}

internal data class KotlinRawClassMember(
    val lines: List<String>,
) : KotlinClassMember {
    override fun writeTo(
        builder: StringBuilder,
        indent: Int,
    ) {
        lines.forEach { line ->
            if (line.isEmpty()) {
                builder.appendLine()
            } else {
                builder.appendIndentedLine(indent, line)
            }
        }
    }
}

internal data class KotlinParameter(
    val name: String,
    val type: String,
)

internal data class KotlinBlock(
    val statements: List<KotlinStatement> = emptyList(),
) {
    fun writeTo(
        builder: StringBuilder,
        indent: Int,
    ) {
        statements.forEach { statement ->
            statement.writeTo(builder, indent)
        }
    }
}

internal sealed interface KotlinStatement {
    fun writeTo(
        builder: StringBuilder,
        indent: Int,
    )

    data class Expression(
        val code: String,
    ) : KotlinStatement {
        override fun writeTo(
            builder: StringBuilder,
            indent: Int,
        ) {
            builder.appendIndentedLine(indent, code)
        }
    }

    data class Return(
        val expression: String,
    ) : KotlinStatement {
        override fun writeTo(
            builder: StringBuilder,
            indent: Int,
        ) {
            builder.appendIndentedLine(indent, "return $expression")
        }
    }

    data class If(
        val condition: String,
        val body: KotlinBlock,
    ) : KotlinStatement {
        override fun writeTo(
            builder: StringBuilder,
            indent: Int,
        ) {
            builder.appendIndentedLine(indent, "if ($condition) {")
            body.writeTo(builder, indent + 1)
            builder.appendIndentedLine(indent, "}")
        }
    }

    data class For(
        val header: String,
        val body: KotlinBlock,
    ) : KotlinStatement {
        override fun writeTo(
            builder: StringBuilder,
            indent: Int,
        ) {
            builder.appendIndentedLine(indent, "for ($header) {")
            body.writeTo(builder, indent + 1)
            builder.appendIndentedLine(indent, "}")
        }
    }

    data class Block(
        val header: String,
        val body: KotlinBlock,
    ) : KotlinStatement {
        override fun writeTo(
            builder: StringBuilder,
            indent: Int,
        ) {
            builder.appendIndentedLine(indent, "$header {")
            body.writeTo(builder, indent + 1)
            builder.appendIndentedLine(indent, "}")
        }
    }

    data class Raw(
        val lines: List<String>,
    ) : KotlinStatement {
        constructor(vararg lines: String) : this(lines.toList())

        override fun writeTo(
            builder: StringBuilder,
            indent: Int,
        ) {
            lines.forEach { line ->
                if (line.isEmpty()) {
                    builder.appendLine()
                } else {
                    builder.appendIndentedLine(indent, line)
                }
            }
        }
    }

    data object BlankLine : KotlinStatement {
        override fun writeTo(
            builder: StringBuilder,
            indent: Int,
        ) {
            builder.appendLine()
        }
    }
}

private fun List<String>.joinToPrefix(): String =
    if (isEmpty()) "" else joinToString(separator = " ", postfix = " ")

private fun StringBuilder.appendIndentedLine(
    indent: Int,
    line: String,
) {
    repeat(indent) {
        append("    ")
    }
    appendLine(line)
}
