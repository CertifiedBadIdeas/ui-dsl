package ru.lazyhat.kraftui.program

/**
 * Execution-oriented view of [ScreenProgram].
 *
 * This is not a separate source of UI truth. It keeps references to the
 * compiled program and records which render and input work is actually static
 * or dynamic after frame placement, visibility, and clipping are considered.
 */
data class OptimizedScreenProgram<Action>(
    val source: ScreenProgram<Action>,
    val frames: List<OptimizedRenderFrame>,
    val renderOps: List<OptimizedRenderOp>,
    val staticRenderOps: List<OptimizedRenderOp>,
    val dynamicRenderOps: List<OptimizedRenderOp>,
    val renderInvalidation: UiInvalidationMask,
    val hitRegions: List<OptimizedHitRegion<Action>>,
    val staticHitRegions: List<OptimizedHitRegion<Action>>,
    val dynamicHitRegions: List<OptimizedHitRegion<Action>>,
    val inputInvalidation: UiInvalidationMask,
)

data class UiInvalidationMask(
    val value: Boolean = false,
    val visibility: Boolean = false,
    val origin: Boolean = false,
    val input: Boolean = false,
) {
    val isStatic: Boolean
        get() = !value && !visibility && !origin && !input

    operator fun plus(other: UiInvalidationMask): UiInvalidationMask =
        UiInvalidationMask(
            value = value || other.value,
            visibility = visibility || other.visibility,
            origin = origin || other.origin,
            input = input || other.input,
        )
}

val UiDependencies.invalidationMask: UiInvalidationMask
    get() =
        UiInvalidationMask(
            value = dynamicValue,
            visibility = dynamicVisibility,
            origin = dynamicOrigin,
            input = dynamicInput,
        )

data class OptimizedRenderFrame(
    val frameIndex: Int,
    val source: RenderFrame,
    val placementDependencies: UiDependencies,
    val effectiveDependencies: UiDependencies,
    val renderOps: List<OptimizedRenderOp>,
)

data class OptimizedRenderOp(
    val frameIndex: Int,
    val opIndex: Int,
    val source: RenderOp,
    val ownDependencies: UiDependencies,
    val effectiveDependencies: UiDependencies,
)

data class OptimizedHitRegion<out Action>(
    val sourceIndex: Int,
    val source: HitRegion<Action>,
    val ownDependencies: UiDependencies,
    val effectiveDependencies: UiDependencies,
)

fun <Action> ScreenProgram<Action>.optimize(): OptimizedScreenProgram<Action> {
    val optimizedFrames = ArrayList<OptimizedRenderFrame>(frames.size)
    val optimizedRenderOps = ArrayList<OptimizedRenderOp>()

    frames.forEachIndexed { frameIndex, frame ->
        val frameOps = ArrayList<OptimizedRenderOp>(frame.ops.size)
        frame.ops.forEachIndexed { opIndex, op ->
            val ownDependencies = op.dependencies
            val effectiveDependencies = frame.placementDependencies + ownDependencies
            val optimizedOp =
                OptimizedRenderOp(
                    frameIndex = frameIndex,
                    opIndex = opIndex,
                    source = op,
                    ownDependencies = ownDependencies,
                    effectiveDependencies = effectiveDependencies,
                )
            frameOps += optimizedOp
            optimizedRenderOps += optimizedOp
        }
        optimizedFrames +=
            OptimizedRenderFrame(
                frameIndex = frameIndex,
                source = frame,
                placementDependencies = frame.placementDependencies,
                effectiveDependencies = frame.dependencies,
                renderOps = frameOps,
            )
    }

    val optimizedHitRegions =
        hitRegions.mapIndexed { index, region ->
            OptimizedHitRegion(
                sourceIndex = index,
                source = region,
                ownDependencies = region.dependencies,
                effectiveDependencies = dependenciesFor(region),
            )
        }

    return OptimizedScreenProgram(
        source = this,
        frames = optimizedFrames,
        renderOps = optimizedRenderOps,
        staticRenderOps = optimizedRenderOps.filter { it.effectiveDependencies.isStatic },
        dynamicRenderOps = optimizedRenderOps.filter { !it.effectiveDependencies.isStatic },
        renderInvalidation =
            optimizedRenderOps.fold(UiInvalidationMask()) { acc, op ->
                acc + op.effectiveDependencies.invalidationMask
            },
        hitRegions = optimizedHitRegions,
        staticHitRegions = optimizedHitRegions.filter { it.effectiveDependencies.isStatic },
        dynamicHitRegions = optimizedHitRegions.filter { !it.effectiveDependencies.isStatic },
        inputInvalidation =
            optimizedHitRegions.fold(UiInvalidationMask()) { acc, region ->
                acc + region.effectiveDependencies.invalidationMask
            },
    )
}
