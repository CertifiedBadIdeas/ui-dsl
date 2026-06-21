package ru.lazyhat.kraftui.program

data class PrimitiveTargetSourceRequest(
    val packageName: String,
    val className: String,
    val stateType: String,
    val actionType: String,
    val optimization: PrimitiveOptimizationOptions = PrimitiveOptimizationOptions(),
    val measureText: (String) -> Int = String::length,
    val failOnAnalysisDiagnostics: Boolean = true,
)

data class PrimitiveTargetSourceResult(
    val source: PrimitiveScreenSource,
    val target: PrimitiveSourceTarget,
    val optimizedProgram: PrimitiveScreenProgram,
    val analysisReport: PrimitiveProgramAnalysisReport,
    val optimizationReport: PrimitiveOptimizationReport,
)

class PrimitiveSourceTarget internal constructor(
    val id: String,
    val capabilities: PrimitiveTargetCapabilities,
    private val generate: (PrimitiveScreenProgram, PrimitiveTargetSourceRequest) -> PrimitiveScreenSource,
) {
    fun generateSource(
        program: PrimitiveScreenProgram,
        request: PrimitiveTargetSourceRequest,
    ): PrimitiveScreenSource = generate(program, request)
}

object PrimitiveSourceTargets {
    val previewRenderBackend: PrimitiveSourceTarget =
        PrimitiveSourceTarget(
            id = "preview-render-backend",
            capabilities = PrimitiveTargetCapabilities(name = "preview-render-backend"),
            generate = { program, request ->
                program.generatePrimitiveScreenSource(
                    packageName = request.packageName,
                    className = request.className,
                    stateType = request.stateType,
                    actionType = request.actionType,
                )
            },
        )

    val minecraftGuiGraphics: PrimitiveSourceTarget =
        PrimitiveSourceTarget(
            id = "minecraft-gui-graphics",
            capabilities = PrimitiveTargetCapabilities.minecraftGuiGraphics,
            generate = { program, request ->
                program.generateMinecraftScreenSource(
                    packageName = request.packageName,
                    className = request.className,
                    stateType = request.stateType,
                    actionType = request.actionType,
                    optimization = request.optimization,
                )
            },
        )
}

fun PrimitiveScreenProgram.generateTargetSource(
    target: PrimitiveSourceTarget,
    request: PrimitiveTargetSourceRequest,
): PrimitiveTargetSourceResult {
    val optimized = optimizePrimitive(request.optimization)
    val sourceOptimizationReport =
        optimized.program.sourceOptimizationReport(
            target = target,
            options = request.optimization,
        )
    val analysis =
        optimized.program.analyze(
            options =
                PrimitiveProgramAnalysisOptions(
                    measureText = request.measureText,
                    target = target.capabilities,
                ),
        )
    require(!request.failOnAnalysisDiagnostics || analysis.isValid) {
        "Primitive program is invalid for target ${target.id}:\n${analysis.asText()}"
    }

    return PrimitiveTargetSourceResult(
        source = target.generateSource(optimized.program, request),
        target = target,
        optimizedProgram = optimized.program,
        analysisReport = analysis,
        optimizationReport = optimized.report + sourceOptimizationReport,
    )
}

private fun PrimitiveScreenProgram.sourceOptimizationReport(
    target: PrimitiveSourceTarget,
    options: PrimitiveOptimizationOptions,
): PrimitiveOptimizationReport {
    if (!options.enabled) {
        return PrimitiveOptimizationReport()
    }

    val applied = mutableListOf<PrimitiveAppliedOptimization>()
    val skipped = mutableListOf<PrimitiveSkippedOptimization>()
    val warnings = mutableListOf<PrimitiveOptimizationWarning>()

    if (options.enables(PrimitiveOptimizationPass.StaticTextureBaking)) {
        warnings +=
            PrimitiveOptimizationWarning.UnsupportedPass(
                pass = PrimitiveOptimizationPass.StaticTextureBaking,
                targetId = target.id,
            )
    }

    if (target != PrimitiveSourceTargets.minecraftGuiGraphics) {
        return PrimitiveOptimizationReport(warnings = warnings)
    }

    val drawTextInstructionCount =
        renderInstructions.count { instruction ->
            instruction.op is PrimitiveRenderOp.DrawText
        }
    if (drawTextInstructionCount > 0) {
        if (options.enables(PrimitiveOptimizationPass.TextLayoutCaching)) {
            applied += PrimitiveAppliedOptimization.CachedTextLayout(drawTextInstructionCount)
        } else {
            skipped += PrimitiveSkippedOptimization.PassDisabled(PrimitiveOptimizationPass.TextLayoutCaching)
        }
    }

    val hitRegionCount =
        inputInstructions.count { instruction ->
            instruction is PrimitiveInputInstruction.ClickRegion
        }
    if (hitRegionCount > 0) {
        if (options.enables(PrimitiveOptimizationPass.HitRegionPrecompute)) {
            applied += PrimitiveAppliedOptimization.PrecomputedHitRegions(hitRegionCount)
        } else {
            skipped += PrimitiveSkippedOptimization.PassDisabled(PrimitiveOptimizationPass.HitRegionPrecompute)
        }
    }

    val visibilityGroups = renderInstructions.visibilityGroupsWorthReporting()
    if (visibilityGroups.isNotEmpty()) {
        if (options.enables(PrimitiveOptimizationPass.VisibilityBlockGrouping)) {
            applied += visibilityGroups
        } else {
            skipped += PrimitiveSkippedOptimization.PassDisabled(PrimitiveOptimizationPass.VisibilityBlockGrouping)
        }
    }

    return PrimitiveOptimizationReport(
        applied = applied,
        skipped = skipped,
        warnings = warnings,
    )
}

private fun List<PrimitiveRenderInstruction>.visibilityGroupsWorthReporting(): List<PrimitiveAppliedOptimization.GroupedVisibilityBlock> =
    buildList {
        var index = 0
        while (index < this@visibilityGroupsWorthReporting.size) {
            val visible = this@visibilityGroupsWorthReporting[index].visible
            var end = index + 1
            while (
                end < this@visibilityGroupsWorthReporting.size &&
                this@visibilityGroupsWorthReporting[end].visible == visible
            ) {
                end++
            }
            if (visible != null && end - index > 1) {
                add(
                    PrimitiveAppliedOptimization.GroupedVisibilityBlock(
                        visibleExpression = visible.reportExpression(),
                        instructionCount = end - index,
                    ),
                )
            }
            index = end
        }
    }

private fun PrimitiveValueExpression.reportExpression(): String =
    when (this) {
        is PrimitiveValueExpression.Constant -> value.toString()
        is PrimitiveValueExpression.StateField -> "state.$fieldName"
    }
