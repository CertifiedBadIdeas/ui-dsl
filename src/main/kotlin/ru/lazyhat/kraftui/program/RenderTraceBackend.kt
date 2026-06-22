package ru.lazyhat.kraftui.program

import ru.lazyhat.kraftui.editor.EditorViewModel
import ru.lazyhat.kraftui.foundation.Color

sealed interface RenderTraceCall {
    data class FillRect(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val color: Color,
    ) : RenderTraceCall

    data class DrawText(
        val x: Int,
        val y: Int,
        val text: String,
        val color: Color,
    ) : RenderTraceCall

    data class DrawTerminalSurface(
        val x: Int,
        val y: Int,
        val snapshot: Any,
    ) : RenderTraceCall

    data class PushClip(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    ) : RenderTraceCall

    data object PopClip : RenderTraceCall

    data class DrawCodeEditor(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val viewModel: EditorViewModel,
        val fontWidth: Int,
        val fontHeight: Int,
    ) : RenderTraceCall

    data class DrawTextureRegion(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val region: PrimitiveTextureRegion,
        val scaling: PrimitiveTextureScaling,
    ) : RenderTraceCall
}

class RenderTraceBackend(
    private val textWidth: (String) -> Int = { it.length * 6 },
) : RenderBackend {
    val calls: List<RenderTraceCall>
        get() = mutableCalls.toList()

    private val mutableCalls = ArrayList<RenderTraceCall>()

    override fun fillRect(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Color,
    ) {
        mutableCalls += RenderTraceCall.FillRect(x, y, width, height, color)
    }

    override fun drawText(
        x: Int,
        y: Int,
        text: String,
        color: Color,
    ) {
        mutableCalls += RenderTraceCall.DrawText(x, y, text, color)
    }

    override fun drawTerminalSurface(
        x: Int,
        y: Int,
        snapshot: Any,
    ) {
        mutableCalls += RenderTraceCall.DrawTerminalSurface(x, y, snapshot)
    }

    override fun pushClip(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        mutableCalls += RenderTraceCall.PushClip(x, y, width, height)
    }

    override fun popClip() {
        mutableCalls += RenderTraceCall.PopClip
    }

    override fun drawCodeEditor(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        viewModel: EditorViewModel,
        fontWidth: Int,
        fontHeight: Int,
    ) {
        mutableCalls += RenderTraceCall.DrawCodeEditor(x, y, width, height, viewModel, fontWidth, fontHeight)
    }

    override fun drawTextureRegion(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        region: PrimitiveTextureRegion,
        scaling: PrimitiveTextureScaling,
    ) {
        mutableCalls += RenderTraceCall.DrawTextureRegion(x, y, width, height, region, scaling)
    }

    override fun measureText(text: String): Int = textWidth(text)
}
