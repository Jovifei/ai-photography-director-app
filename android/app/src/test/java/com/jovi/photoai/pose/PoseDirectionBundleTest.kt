package com.jovi.photoai.pose

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseDirectionBundleTest {
    @Test
    fun syntheticFixture_parsesTwoItemsWithoutAuthority() {
        val raw = fixtureText()
        assertTrue(raw.contains("\"authority\": false"))
        val result = PoseDirectionBundleParser.parse(raw)
        assertTrue(result is PoseDirectionParseResult.Success)
        val bundle = (result as PoseDirectionParseResult.Success).bundle
        assertEquals(2, bundle.items.size)
        assertEquals("img-01-aaaaaaaa", bundle.items[0].id)
        assertTrue(bundle.items[0].spokenDirection.startsWith("\u8bf7\u6a21\u7279"))
        assertEquals("\u4fa7\u8eab\u8ba9\u8f6e\u5ed3\u66f4\u5e72\u51c0", bundle.items[0].whyItWorks?.composition)
        assertEquals("\u8138\u90e8\u7559\u5728\u4eae\u9762", bundle.items[0].whyItWorks?.light)
        assertNull(bundle.items[0].whyItWorks?.gaze)
        assertTrue(bundle.producerNote.contains("not a T14"))
        val graphic = parseStickFigureSvg(bundle.items[0].svg!!)
        assertNotNull(graphic)
        assertEquals(6, graphic!!.shapes.size)
    }

    @Test
    fun authorityNotFalse_failsClosed() {
        assertEquals(PoseDirectionReject.AUTHORITY, reason(mutated { it.put("authority", true) }))
        assertEquals(PoseDirectionReject.AUTHORITY, reason(mutated { it.put("authority", "false") }))
        assertEquals(PoseDirectionReject.AUTHORITY, reason(mutated { it.remove("authority") }))
    }

    @Test
    fun missingSpokenOrFigure_failsClosed() {
        assertEquals(PoseDirectionReject.MISSING_SPOKEN, reason(mutatedItem { it.put("spoken_direction", "  ") }))
        assertEquals(PoseDirectionReject.MISSING_SPOKEN, reason(mutatedItem { it.remove("spoken_direction") }))
        assertEquals(
            PoseDirectionReject.MISSING_FIGURE,
            reason(mutatedItem { it.put("stick_figure", JSONObject()) }),
        )
    }

    @Test
    fun privatePathOrFilename_failsClosed() {
        assertEquals(PoseDirectionReject.PRIVATE_PATH, reason(mutatedItem { it.put("id", "DSC_1001.JPG") }))
        assertEquals(
            PoseDirectionReject.PRIVATE_PATH,
            reason(mutatedItem { it.put("stick_figure", JSONObject().put("svg_file", "..\\private\\shot.jpg")) }),
        )
        assertEquals(
            PoseDirectionReject.PRIVATE_PATH,
            reason(mutatedItem { it.put("spoken_direction", "look at C:/Users/private/photo.jpg") }),
        )
    }

    @Test
    fun relativeSvgFile_isAcceptedWhenItMatchesThePublicPattern() {
        val result = PoseDirectionBundleParser.parse(
            mutatedItem { it.put("stick_figure", JSONObject().put("svg_file", "img-03-cccccccc.svg")) },
        )
        val item = (result as PoseDirectionParseResult.Success).bundle.items[0]
        assertNull(item.svg)
        assertEquals("img-03-cccccccc.svg", item.svgFile)
    }

    @Test
    fun emptyOrMalformedBundle_failsClosed() {
        assertEquals(PoseDirectionReject.EMPTY, reason("   "))
        assertEquals(PoseDirectionReject.MALFORMED, reason("{"))
        assertEquals(PoseDirectionReject.EMPTY, reason(mutated { it.put("items", JSONArray()) }))
        assertEquals(PoseDirectionReject.SCHEMA, reason(mutated { it.put("schema_id", "photo-knowledge-bundle") }))
    }

    private fun reason(text: String): PoseDirectionReject =
        (PoseDirectionBundleParser.parse(text) as PoseDirectionParseResult.Failure).reason

    private fun mutated(edit: (JSONObject) -> Unit): String {
        val root = JSONObject(fixtureText())
        edit(root)
        return root.toString()
    }

    private fun mutatedItem(edit: (JSONObject) -> Unit): String = mutated { root ->
        edit(root.getJSONArray("items").getJSONObject(0))
    }
}

internal fun fixtureText(): String {
    val relative = "src/main/assets/pose_direction/synthetic_pose_direction_bundle_v1.json"
    val candidates = listOf(File(relative), File("app/$relative"), File("android/app/$relative"))
    val file = candidates.firstOrNull { it.isFile }
        ?: error("synthetic fixture missing from ${File(".").absolutePath}")
    return file.readText(Charsets.UTF_8)
}