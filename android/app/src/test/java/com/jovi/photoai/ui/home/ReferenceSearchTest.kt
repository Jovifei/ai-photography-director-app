package com.jovi.photoai.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceSearchTest {
    private val reference = SearchableReference(
        id = "r1",
        title = "窗边柔光人像",
        scene = "窗边",
        lighting = "柔和侧光",
        composition = "人物位于右侧三分线",
        tags = setOf("人像", "留白"),
    )

    @Test
    fun `search matches title scene lighting composition and tags`() {
        listOf("柔光", "窗边", "三分线", "留白").forEach { query ->
            assertTrue(reference.matches(query, sceneFilter = null))
        }
    }

    @Test
    fun `scene filter combines with search`() {
        assertTrue(reference.matches("人像", sceneFilter = "窗边"))
        assertFalse(reference.matches("人像", sceneFilter = "街道"))
        assertFalse(reference.matches("夜景", sceneFilter = "窗边"))
    }
}
