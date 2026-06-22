package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.foundation.Value
import ru.lazyhat.kraftui.foundation.andValues
import ru.lazyhat.kraftui.foundation.modifier.Position

/**
 * Last checked intermediate form before generated screen executors.
 *
 * [ExecutableScreenPlan] is deliberately flat: each render or input step has
 * its frame guard, origin, dependencies, and source operation attached. Code
 * generation can lower these steps directly without rediscovering frame or
 * clipping relationships.
 */
data class ExecutableScreenPlan<Action>(
    val source: OptimizedScreenProgram<Action>,
    val screenProgram: ScreenProgram<Action>,
    val renderSteps: List<ExecutableRenderStep>,
    val hitSteps: List<ExecutableHitStep<Action>>,
    val renderInvalidation: UiInvalidationMask,
    val inputInvalidation: UiInvalidationMask,
)

data class ExecutableRenderStep(
    val stepIndex: Int,
    val frameIndex: Int,
    val opIndex: Int,
    val frame: RenderFrame,
    val visible: Value<Boolean>?,
    val origin: Value<Position>?,
    val op: RenderOp,
    val effectiveDependencies: UiDependencies,
    val staticCacheable: Boolean,
)

data class ExecutableHitStep<out Action>(
    val stepIndex: Int,
    val regionIndex: Int,
    val region: HitRegion<Action>,
    val frame: RenderFrame,
    val visible: Value<Boolean>?,
    val origin: Value<Position>?,
    val clip: HitClip?,
    val clipFrame: RenderFrame?,
    val clipVisible: Value<Boolean>?,
    val clipOrigin: Value<Position>?,
    val effectiveDependencies: UiDependencies,
)

fun <Action> OptimizedScreenProgram<Action>.toExecutablePlan(): ExecutableScreenPlan<Action> {
    val renderSteps = ArrayList<ExecutableRenderStep>(renderOps.size)
    frames.forEach { optimizedFrame ->
        val frame = optimizedFrame.source
        optimizedFrame.renderOps.forEach { optimizedOp ->
            renderSteps +=
                ExecutableRenderStep(
                    stepIndex = renderSteps.size,
                    frameIndex = optimizedOp.frameIndex,
                    opIndex = optimizedOp.opIndex,
                    frame = frame,
                    visible = frame.visible,
                    origin = frame.origin,
                    op = optimizedOp.source,
                    effectiveDependencies = optimizedOp.effectiveDependencies,
                    staticCacheable = optimizedOp.canUseStaticRenderCommandCache,
                )
        }
    }

    val hitSteps =
        hitRegions.map { optimizedHit ->
            val region = optimizedHit.source
            val frame = source.frames[region.frameIndex]
            val clip = region.clip
            val clipFrame = clip?.let { source.frames[it.frameIndex] }
            ExecutableHitStep(
                stepIndex = optimizedHit.sourceIndex,
                regionIndex = optimizedHit.sourceIndex,
                region = region,
                frame = frame,
                visible = andValues(frame.visible, region.visible),
                origin = frame.origin,
                clip = clip,
                clipFrame = clipFrame,
                clipVisible = clipFrame?.visible,
                clipOrigin = clipFrame?.origin,
                effectiveDependencies = optimizedHit.effectiveDependencies,
            )
        }

    return ExecutableScreenPlan(
        source = this,
        screenProgram = source,
        renderSteps = renderSteps,
        hitSteps = hitSteps,
        renderInvalidation = renderInvalidation,
        inputInvalidation = inputInvalidation,
    )
}

val OptimizedRenderOp.canUseStaticRenderCommandCache: Boolean
    get() =
        effectiveDependencies.isStatic &&
            when (source) {
                is RenderOp.FillRect,
                is RenderOp.DrawTerminalSurface,
                is RenderOp.DrawTextureRegion,
                is RenderOp.PushClip,
                RenderOp.PopClip,
                -> true
                is RenderOp.PushVisibility,
                RenderOp.PopVisibility,
                -> false
                is RenderOp.DrawText,
                is RenderOp.DrawCanvas,
                is RenderOp.DrawCodeEditor,
                -> false
            }
