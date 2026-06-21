package ru.lazyhat.kraftui.program

data class PrimitiveClickProbe(
    val x: Int,
    val y: Int,
)

data class PrimitiveTargetTextMetrics(
    val reference: (String) -> Int = { it.length * 6 },
    val preview: (String) -> Int = reference,
    val minecraft: (String) -> Int = reference,
)

data class PrimitiveTargetComparisonRequest<Action>(
    val program: ScreenProgram<Action>,
    val resolvePrimitiveValue: (PrimitiveValueExpression) -> Any?,
    val clicks: List<PrimitiveClickProbe> = emptyList(),
    val optimization: PrimitiveOptimizationOptions = PrimitiveOptimizationOptions(),
    val measureText: PrimitiveTargetTextMetrics = PrimitiveTargetTextMetrics(),
)

enum class PrimitiveComparisonTarget(
    val id: String,
) {
    ReferenceRuntime("reference-runtime"),
    PreviewRenderBackend("preview-render-backend"),
    MinecraftGuiGraphics("minecraft-gui-graphics"),
}

data class PrimitiveTargetSnapshot(
    val target: PrimitiveComparisonTarget,
    val renderTrace: List<RenderTraceCall>,
    val clickResults: List<PrimitiveClickResult>,
    val analysisReport: PrimitiveProgramAnalysisReport,
)

sealed interface PrimitiveClickResult {
    data object Ignored : PrimitiveClickResult

    data class Action(
        val value: Any?,
    ) : PrimitiveClickResult
}

data class PrimitiveTargetComparisonReport(
    val snapshots: List<PrimitiveTargetSnapshot>,
    val differences: List<PrimitiveTargetDifference>,
    val optimizationReport: PrimitiveOptimizationReport,
) {
    val matches: Boolean
        get() = differences.isEmpty()

    fun asText(): String =
        if (matches) {
            "Primitive target comparison matched ${snapshots.size} targets"
        } else {
            differences.joinToString(separator = "\n") { it.asText() }
        }
}

sealed interface PrimitiveTargetDifference {
    val expectedTarget: PrimitiveComparisonTarget
    val actualTarget: PrimitiveComparisonTarget

    data class RenderTraceMismatch(
        override val expectedTarget: PrimitiveComparisonTarget,
        override val actualTarget: PrimitiveComparisonTarget,
    ) : PrimitiveTargetDifference

    data class ClickResultMismatch(
        override val expectedTarget: PrimitiveComparisonTarget,
        override val actualTarget: PrimitiveComparisonTarget,
        val click: PrimitiveClickProbe,
    ) : PrimitiveTargetDifference

    data class AnalysisMismatch(
        override val expectedTarget: PrimitiveComparisonTarget,
        override val actualTarget: PrimitiveComparisonTarget,
    ) : PrimitiveTargetDifference
}

fun <Action> comparePrimitiveTargets(request: PrimitiveTargetComparisonRequest<Action>): PrimitiveTargetComparisonReport {
    val primitive =
        request.program
            .toPrimitiveScreenProgram()
            .optimizePrimitive(request.optimization)

    val snapshots =
        listOf(
            referenceSnapshot(
                program = request.program,
                clicks = request.clicks,
                measureText = request.measureText.reference,
            ),
            primitiveSnapshot(
                target = PrimitiveComparisonTarget.PreviewRenderBackend,
                program = primitive.program,
                clicks = request.clicks,
                resolve = request.resolvePrimitiveValue,
                measureText = request.measureText.preview,
                capabilities = PrimitiveSourceTargets.previewRenderBackend.capabilities,
            ),
            primitiveSnapshot(
                target = PrimitiveComparisonTarget.MinecraftGuiGraphics,
                program = primitive.program,
                clicks = request.clicks,
                resolve = request.resolvePrimitiveValue,
                measureText = request.measureText.minecraft,
                capabilities = PrimitiveSourceTargets.minecraftGuiGraphics.capabilities,
            ),
        )

    return PrimitiveTargetComparisonReport(
        snapshots = snapshots,
        differences = snapshots.compareAgainstReference(request.clicks),
        optimizationReport = primitive.report,
    )
}

private fun <Action> referenceSnapshot(
    program: ScreenProgram<Action>,
    clicks: List<PrimitiveClickProbe>,
    measureText: (String) -> Int,
): PrimitiveTargetSnapshot {
    val executor = ScreenRuntimeExecutor(program)
    val backend = RenderTraceBackend(measureText)
    executor.render(backend)
    return PrimitiveTargetSnapshot(
        target = PrimitiveComparisonTarget.ReferenceRuntime,
        renderTrace = backend.calls,
        clickResults =
            clicks.map { click ->
                executor.mouseClicked(click.x, click.y).toPrimitiveClickResult()
            },
        analysisReport = PrimitiveProgramAnalysisReport(emptyList()),
    )
}

private fun primitiveSnapshot(
    target: PrimitiveComparisonTarget,
    program: PrimitiveScreenProgram,
    clicks: List<PrimitiveClickProbe>,
    resolve: (PrimitiveValueExpression) -> Any?,
    measureText: (String) -> Int,
    capabilities: PrimitiveTargetCapabilities,
): PrimitiveTargetSnapshot {
    val backend = RenderTraceBackend(measureText)
    program.render(backend, resolve)
    return PrimitiveTargetSnapshot(
        target = target,
        renderTrace = backend.calls,
        clickResults =
            clicks.map { click ->
                program.mouseClicked(resolve, click.x, click.y).toPrimitiveClickResult()
            },
        analysisReport =
            program.analyze(
                PrimitiveProgramAnalysisOptions(
                    measureText = measureText,
                    target = capabilities,
                ),
            ),
    )
}

private fun UiInputResult<*>.toPrimitiveClickResult(): PrimitiveClickResult =
    when (this) {
        is UiInputResult.Action -> PrimitiveClickResult.Action(action)
        UiInputResult.Consumed,
        UiInputResult.Ignored,
        -> PrimitiveClickResult.Ignored
    }

private fun Any?.toPrimitiveClickResult(): PrimitiveClickResult =
    if (this == null) {
        PrimitiveClickResult.Ignored
    } else {
        PrimitiveClickResult.Action(this)
    }

private fun List<PrimitiveTargetSnapshot>.compareAgainstReference(clicks: List<PrimitiveClickProbe>): List<PrimitiveTargetDifference> {
    val reference = firstOrNull { it.target == PrimitiveComparisonTarget.ReferenceRuntime }
        ?: error("Primitive target comparison requires a reference snapshot")
    val differences = ArrayList<PrimitiveTargetDifference>()
    filter { it.target != PrimitiveComparisonTarget.ReferenceRuntime }.forEach { actual ->
        if (actual.analysisReport.diagnostics != reference.analysisReport.diagnostics) {
            differences +=
                PrimitiveTargetDifference.AnalysisMismatch(
                    expectedTarget = reference.target,
                    actualTarget = actual.target,
                )
        }
        if (actual.renderTrace != reference.renderTrace) {
            differences +=
                PrimitiveTargetDifference.RenderTraceMismatch(
                    expectedTarget = reference.target,
                    actualTarget = actual.target,
                )
        }
        actual.clickResults.forEachIndexed { index, result ->
            if (result != reference.clickResults[index]) {
                differences +=
                    PrimitiveTargetDifference.ClickResultMismatch(
                        expectedTarget = reference.target,
                        actualTarget = actual.target,
                        click = clicks[index],
                    )
            }
        }
    }
    return differences
}

private fun PrimitiveTargetDifference.asText(): String =
    when (this) {
        is PrimitiveTargetDifference.AnalysisMismatch ->
            "${actualTarget.id} analysis differs from ${expectedTarget.id}"
        is PrimitiveTargetDifference.RenderTraceMismatch ->
            "${actualTarget.id} render trace differs from ${expectedTarget.id}"
        is PrimitiveTargetDifference.ClickResultMismatch ->
            "${actualTarget.id} click at ${click.x},${click.y} differs from ${expectedTarget.id}"
    }
