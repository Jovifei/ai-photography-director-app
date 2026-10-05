package com.jovi.photoai.ui.pose

import com.jovi.photoai.pose.PoseDirectionBundleParser
import com.jovi.photoai.pose.PoseDirectionItem
import com.jovi.photoai.pose.PoseDirectionParseResult
import com.jovi.photoai.pose.PoseDirectionReject
import com.jovi.photoai.pose.PoseWhyItWorks
import com.jovi.photoai.pose.StickFigureGraphic
import com.jovi.photoai.pose.parseStickFigureSvg

internal sealed interface PoseDirectionUiState {
    data object Failed : PoseDirectionUiState
    data class ListReady(val items: List<PoseDirectionItem>) : PoseDirectionUiState
    data class Detail(
        val item: PoseDirectionItem,
        val graphic: StickFigureGraphic?,
        val figureNote: String?,
        val whyLines: List<String>,
    ) : PoseDirectionUiState
}

/** Holds the parsed producer bundle and the composition selected by a tap. */
internal class PoseDirectionViewModel(documentText: String?) {
    private val parsed: PoseDirectionParseResult = when {
        documentText.isNullOrBlank() -> PoseDirectionParseResult.Failure(PoseDirectionReject.EMPTY)
        else -> PoseDirectionBundleParser.parse(documentText)
    }
    private var selectedId: String? = null

    fun uiState(): PoseDirectionUiState {
        val success = parsed as? PoseDirectionParseResult.Success ?: return PoseDirectionUiState.Failed
        val selected = success.bundle.items.firstOrNull { it.id == selectedId } ?: return PoseDirectionUiState.ListReady(success.bundle.items)
        val graphic = selected.svg?.let(::parseStickFigureSvg)
        return PoseDirectionUiState.Detail(
            item = selected,
            graphic = graphic,
            figureNote = figureNote(selected, graphic),
            whyLines = whyLines(selected.whyItWorks),
        )
    }

    fun select(id: String) {
        val success = parsed as? PoseDirectionParseResult.Success ?: return
        if (success.bundle.items.any { it.id == id }) selectedId = id
    }

    fun showList() {
        selectedId = null
    }
}

internal fun whyLines(why: PoseWhyItWorks?): List<String> {
    if (why == null) return emptyList()
    return listOfNotNull(
        why.composition?.let { "\u6784\u56fe\uff1a$it" },
        why.light?.let { "\u5149\u7ebf\uff1a$it" },
        why.gaze?.let { "\u89c6\u7ebf\uff1a$it" },
        why.clothing?.let { "\u670d\u88c5\uff1a$it" },
    )
}

internal fun spokenPreview(text: String, maxCodePoints: Int = 36): String {
    val count = text.codePointCount(0, text.length)
    if (count <= maxCodePoints) return text
    return text.substring(0, text.offsetByCodePoints(0, maxCodePoints)) + "\u2026"
}

private fun figureNote(item: PoseDirectionItem, graphic: StickFigureGraphic?): String? = when {
    item.svg == null -> "\u706b\u67f4\u4eba\u56fe\u672a\u5185\u8054\uff0c\u53ea\u8bb0\u5f55\u4e86\u5b89\u5168\u7684\u76f8\u5bf9\u6587\u4ef6\u540d\u3002"
    graphic == null -> "\u65e0\u6cd5\u7ed8\u5236\u706b\u67f4\u4eba"
    else -> null
}