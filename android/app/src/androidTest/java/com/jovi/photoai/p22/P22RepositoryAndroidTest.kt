package com.jovi.photoai.p22

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.data.reference.KnowledgeBundleApplyErrorCode
import com.jovi.photoai.data.reference.KnowledgeBundleApplyResult
import com.jovi.photoai.data.reference.KnowledgeBundleBinding
import com.jovi.photoai.data.reference.KnowledgeBundleOrigin
import com.jovi.photoai.data.reference.KnowledgeBundlePhotography
import com.jovi.photoai.data.reference.KnowledgeBundleSource
import com.jovi.photoai.data.reference.PhotoAnalysisStatus
import com.jovi.photoai.data.reference.PhotoKnowledgeBundle
import com.jovi.photoai.data.reference.PhotoKnowledgeBundleItem
import com.jovi.photoai.data.reference.ReferenceImportResult
import com.jovi.photoai.data.reference.ReferenceRepository
import com.jovi.photoai.data.reference.canonicalPayloadSha256
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P22RepositoryAndroidTest {
    @Test
    fun explicitMapping_isAtomicAndProjectDeletionDoesNotAffectAnotherProject() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ReferenceRepository.create(context)
        val targetProject = repository.createProject("P22 合成导入")
        val otherProject = repository.createProject("P22 保留项目")
        val imported = importSyntheticReference(context, repository, targetProject.id)
        val bundle = signedBundle()

        val tampered = bundle.copy(
            references = listOf(bundle.references.single().copy(
                photography = bundle.references.single().photography.copy(scene = "摘要后篡改"),
            )),
        )
        assertEquals(
            KnowledgeBundleApplyResult.Failure(KnowledgeBundleApplyErrorCode.BUNDLE_INTEGRITY_INVALID),
            repository.applyKnowledgeBundle(
                targetProject.id,
                tampered,
                listOf(KnowledgeBundleBinding(tampered.references.single().referenceId, imported.photo.id)),
            ),
        )
        assertEquals(PhotoAnalysisStatus.IMPORTED, repository.activeRecord(imported.photo.id)?.analysisStatus)

        val applied = repository.applyKnowledgeBundle(
            targetProject.id,
            bundle,
            listOf(KnowledgeBundleBinding(bundle.references.single().referenceId, imported.photo.id)),
        )
        assertEquals(KnowledgeBundleApplyResult.Success(1), applied)
        val ready = repository.activeRecord(imported.photo.id)!!
        assertEquals(PhotoAnalysisStatus.READY, ready.analysisStatus)
        assertNull(ready.analysisProvenance)
        assertEquals(bundle.bundleId, ready.knowledgeBundleProvenance?.bundleId)
        assertEquals(imported.photo.id, ready.bundle.referenceId)

        val rejectedOverwrite = repository.applyKnowledgeBundle(
            targetProject.id,
            bundle,
            listOf(KnowledgeBundleBinding(bundle.references.single().referenceId, imported.photo.id)),
        )
        assertEquals(
            KnowledgeBundleApplyResult.Failure(KnowledgeBundleApplyErrorCode.REFERENCE_NOT_ELIGIBLE),
            rejectedOverwrite,
        )
        assertEquals(bundle.bundleId, repository.activeRecord(imported.photo.id)?.knowledgeBundleProvenance?.bundleId)

        assertTrue(repository.deleteProject(targetProject.id))
        assertNull(repository.project(targetProject.id))
        assertNull(repository.activeRecord(imported.photo.id))
        assertEquals(otherProject.id, repository.project(otherProject.id)?.id)
        assertTrue(repository.deleteProject(otherProject.id))
    }

    private suspend fun importSyntheticReference(
        context: Context,
        repository: ReferenceRepository,
        projectId: String,
    ): com.jovi.photoai.data.reference.ReferenceRecord {
        val directory = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(directory, "p22-synthetic-reference.jpg")
        file.outputStream().use { stream ->
            val bitmap = Bitmap.createBitmap(24, 16, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(0xff607080.toInt())
            assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream))
            bitmap.recycle()
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val result = repository.importIntoProject(uri, projectId)
        file.delete()
        assertTrue(result is ReferenceImportResult.Success)
        return (result as ReferenceImportResult.Success).record
    }

    private fun signedBundle(): PhotoKnowledgeBundle {
        val unsigned = PhotoKnowledgeBundle(
            "1.0",
            "bundle_repository_001",
            KnowledgeBundleSource(KnowledgeBundleOrigin.PIPELINE, "synthetic_pipeline", "release_001"),
            "0".repeat(64),
            listOf(
                PhotoKnowledgeBundleItem(
                    "producer_photo_001",
                    KnowledgeBundlePhotography(
                        "合成场景", "合成背景", "合成光线", "合成构图", "合成主体", "合成情绪", "合成姿态", "合成机位", "合成指导",
                    ),
                ),
            ),
        )
        return unsigned.copy(payloadSha256 = canonicalPayloadSha256(unsigned))
    }
}
