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
                )
            },
        )
}

fun PrimitiveScreenProgram.generateTargetSource(
    target: PrimitiveSourceTarget,
    request: PrimitiveTargetSourceRequest,
): PrimitiveTargetSourceResult {
    val optimized = optimizePrimitive(request.optimization)
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
        optimizationReport = optimized.report,
    )
}
