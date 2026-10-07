package com.jovi.photoai.pose

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PoseDirectionDocumentLoaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun blankOrMissingPath_returnsNull() {
        assertNull(PoseDirectionDocumentLoader.readExternalOrNull(null))
        assertNull(PoseDirectionDocumentLoader.readExternalOrNull(""))
        assertNull(PoseDirectionDocumentLoader.readExternalOrNull("   "))
        assertNull(PoseDirectionDocumentLoader.readExternalOrNull(File(folder.root, "missing.json").absolutePath))
    }

    @Test
    fun readableExternal_winsOverAssetFallback() {
        val file = folder.newFile("pose_direction_bundle_v1.json")
        file.writeText(fixtureText(), Charsets.UTF_8)
        val resolved = PoseDirectionDocumentLoader.resolveDocument("asset-fallback", file.absolutePath)
        assertEquals(fixtureText(), resolved)
        val parsed = PoseDirectionBundleParser.parse(resolved!!)
        assertTrue(parsed is PoseDirectionParseResult.Success)
    }

    @Test
    fun unreadableExternal_fallsBackToAsset() {
        val missing = File(folder.root, "gone.json").absolutePath
        assertEquals("asset-only", PoseDirectionDocumentLoader.resolveDocument("asset-only", missing))
        assertNull(PoseDirectionDocumentLoader.resolveDocument(null, missing))
        assertNull(PoseDirectionDocumentLoader.resolveDocument("  ", null))
    }

    @Test
    fun oversizedExternal_failsClosed() {
        val file = folder.newFile("too-big.json")
        file.writeText("x".repeat(PoseDirectionDocumentLoader.MAX_EXTERNAL_CHARS + 1), Charsets.UTF_8)
        assertNull(PoseDirectionDocumentLoader.readExternalOrNull(file.absolutePath))
        assertEquals("asset", PoseDirectionDocumentLoader.resolveDocument("asset", file.absolutePath))
    }
}
