package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Value

data class GeneratedProgramValidationResult(
    val diagnostics: List<GeneratedProgramDiagnostic>,
) {
    val isValid: Boolean
        get() = diagnostics.isEmpty()
}

sealed interface GeneratedProgramDiagnostic {
    val path: String

    data class RuntimeOnlyValue(
        override val path: String,
    ) : GeneratedProgramDiagnostic

    data class RuntimeOnlyOperation(
        override val path: String,
        val reason: String,
    ) : GeneratedProgramDiagnostic
}

fun ScreenProgram<*>.validateGeneratedProgram(): GeneratedProgramValidationResult {
    val diagnostics = ArrayList<GeneratedProgramDiagnostic>()

    frames.forEachIndexed { frameIndex, frame ->
        diagnostics.requireGeneratedValue("frame[$frameIndex].origin", frame.origin)
        diagnostics.requireGeneratedValue("frame[$frameIndex].visible", frame.visible)
        frame.ops.forEachIndexed { opIndex, op ->
            diagnostics.validateRenderOp("frame[$frameIndex].op[$opIndex]", op)
        }
    }

    hitRegions.forEach { region ->
        diagnostics.requireGeneratedValue("hitRegion[${region.nodeId}].visible", region.visible)
        diagnostics.requireGeneratedValue("hitRegion[${region.nodeId}].action", region.action)
        if (region.onClickAt != null) {
            diagnostics +=
                GeneratedProgramDiagnostic.RuntimeOnlyOperation(
                    path = "hitRegion[${region.nodeId}].onClickAt",
                    reason = "onClickAt uses a runtime callback",
                )
        }
        if (region.onDragStart != null || region.onDrag != null || region.onDragEnd != null) {
            diagnostics +=
                GeneratedProgramDiagnostic.RuntimeOnlyOperation(
                    path = "hitRegion[${region.nodeId}].drag",
                    reason = "drag handling uses runtime callbacks",
                )
        }
    }

    tooltipRegions.forEach { region ->
        diagnostics.requireGeneratedValue("tooltipRegion[${region.nodeId}].visible", region.visible)
        diagnostics.requireGeneratedValue("tooltipRegion[${region.nodeId}].text", region.text)
    }

    scrollRegions.forEach { region ->
        diagnostics.requireGeneratedValue("scrollRegion[${region.nodeId}].visible", region.visible)
        diagnostics +=
            GeneratedProgramDiagnostic.RuntimeOnlyOperation(
                path = "scrollRegion[${region.nodeId}].onScroll",
                reason = "scroll handling uses a runtime callback",
            )
    }

    focusNodes.forEach { node ->
        diagnostics.requireGeneratedValue("focusNode[${node.nodeId}].visible", node.visible)
        diagnostics +=
            GeneratedProgramDiagnostic.RuntimeOnlyOperation(
                path = "focusNode[${node.nodeId}].handler",
                reason = "focus handling uses runtime callbacks",
            )
    }

    return GeneratedProgramValidationResult(diagnostics)
}

private fun MutableList<GeneratedProgramDiagnostic>.validateRenderOp(
    path: String,
    op: RenderOp,
) {
    when (op) {
        is RenderOp.FillRect -> requireGeneratedValue("$path.color", op.color)
        is RenderOp.DrawText -> {
            requireGeneratedValue("$path.text", op.value)
            requireGeneratedValue("$path.color", op.color)
        }
        is RenderOp.DrawTerminalSurface -> requireGeneratedValue("$path.snapshot", op.snapshot)
        is RenderOp.DrawCanvas ->
            add(
                GeneratedProgramDiagnostic.RuntimeOnlyOperation(
                    path = path,
                    reason = "DrawCanvas uses a runtime drawing callback",
                ),
            )
        is RenderOp.PushClip -> Unit
        RenderOp.PopClip -> Unit
        is RenderOp.PushVisibility -> requireGeneratedValue("$path.condition", op.condition)
        RenderOp.PopVisibility -> Unit
        is RenderOp.DrawCodeEditor -> requireGeneratedValue("$path.viewModel", op.viewModel)
    }
}

private fun MutableList<GeneratedProgramDiagnostic>.requireGeneratedValue(
    path: String,
    value: Value<*>?,
) {
    if (value != null && value.generatedExpression == null) {
        add(GeneratedProgramDiagnostic.RuntimeOnlyValue(path))
    }
}
