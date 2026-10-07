package com.jovi.photoai.ui.pose

import com.jovi.photoai.pose.fixtureText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseDirectionViewModelTest {
    @Test
    fun tap_showsStickFigureAndSpokenPromptTogether() {
        val model = PoseDirectionViewModel(fixtureText())
        val list = model.uiState() as PoseDirectionUiState.ListReady
        assertEquals(listOf("img-01-aaaaaaaa", "img-02-bbbbbbbb"), list.items.map { it.id })

        model.select("img-01-aaaaaaaa")
        val detail = model.uiState() as PoseDirectionUiState.Detail
        assertEquals(list.items[0].spokenDirection, detail.item.spokenDirection)
        assertTrue(detail.item.spokenDirection.contains("\u955c\u5934\u5de6\u4fa7"))
        assertNotNull(detail.graphic)
        assertNull(detail.figureNote)
        assertEquals(
            listOf(
                "\u6784\u56fe\uff1a\u4fa7\u8eab\u8ba9\u8f6e\u5ed3\u66f4\u5e72\u51c0",
                "\u5149\u7ebf\uff1a\u8138\u90e8\u7559\u5728\u4eae\u9762",
            ),
            detail.whyLines,
        )

        model.showList()
        assertTrue(model.uiState() is PoseDirectionUiState.ListReady)
    }

    @Test
    fun invalidBundle_staysAPlainFailure() {
        val model = PoseDirectionViewModel("{")
        assertTrue(model.uiState() is PoseDirectionUiState.Failed)
        model.select("img-01-aaaaaaaa")
        assertTrue(model.uiState() is PoseDirectionUiState.Failed)
        assertTrue(PoseDirectionViewModel("").uiState() is PoseDirectionUiState.Failed)
    }

    @Test
    fun selectedPose_isNullUntilDetailThenCarriesSpokenAndFigure() {
        val model = PoseDirectionViewModel(fixtureText())
        assertNull(model.selectedPose())

        model.select("img-02-bbbbbbbb")
        val selected = model.selectedPose()
        assertNotNull(selected)
        assertEquals("img-02-bbbbbbbb", selected!!.id)
        assertEquals("\u6784\u56fe\u793a\u610f", selected.title)
        assertTrue(selected.spokenDirection.contains("\u91cd\u5fc3"))
        assertNotNull(selected.graphic)
        assertNull(selected.figureNote)

        model.showList()
        assertNull(model.selectedPose())
    }

    @Test
    fun selectedPose_staysNullWhenBundleFailed() {
        val model = PoseDirectionViewModel("{")
        model.select("img-01-aaaaaaaa")
        assertNull(model.selectedPose())
    }
}
