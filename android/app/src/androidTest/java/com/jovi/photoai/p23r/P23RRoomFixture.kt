package com.jovi.photoai.p23r

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jovi.photoai.data.reference.*
import com.jovi.photoai.domain.model.ReferencePhoto
import com.jovi.photoai.reference.ReferenceBundle

/** Real isolated Room, synthetic metadata only. No JPEG, URI read, network or default DB. */
internal class P23RRoomFixture(
    afterKnowledgeBundleCommit: () -> Unit = {},
    afterAnalysisQueueCommit: () -> Unit = {},
) : AutoCloseable {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    val database = Room.inMemoryDatabaseBuilder(context, ReferenceLibraryDatabase::class.java)
        .build()
    val repository = ReferenceRepository.createForTest(
        context = context,
        database = database,
        afterKnowledgeBundleCommit = afterKnowledgeBundleCommit,
        afterAnalysisQueueCommit = afterAnalysisQueueCommit,
    )
    val dao = database.referenceDao()
    lateinit var project: PhotographyProject
    lateinit var records: List<ReferenceRecord>

    suspend fun seed(count: Int = 2) {
        project = repository.createProject("P23R 合成事务测试")
        records = (0 until count).map { index ->
            val id = "synthetic_$index"
            ReferenceRecord(
                ReferencePhoto(id, "合成条目", "仅测试元数据", "已导入", "private/$id.jpg", 1f),
                ReferenceBundle(id, "待分析", "待分析", "待分析", "待分析", "待分析", "待分析", "待分析", "待分析", "待分析", "1.0"),
                "$id.jpg", 1L, project.id, index, PhotoAnalysisStatus.IMPORTED,
            ).also { dao.insert(it.toEntity()) }
        }
    }

    fun bundle(count: Int = records.size): PhotoKnowledgeBundle {
        val raw = PhotoKnowledgeBundle("1.0", "synthetic_bundle",
            KnowledgeBundleSource(KnowledgeBundleOrigin.PIPELINE, "synthetic_producer", "synthetic_release"),
            "0".repeat(64), (0 until count).map {
                PhotoKnowledgeBundleItem("producer_$it", KnowledgeBundlePhotography(
                    "合成场景", "合成背景", "侧光", "三分构图", "站立", "平静", "肩部放松", "眼平", "保持自然呼吸。",
                ))
            })
        return raw.copy(payloadSha256 = canonicalPayloadSha256(raw))
    }

    fun bindings(count: Int = records.size) = (0 until count).map {
        KnowledgeBundleBinding("producer_$it", records[it].photo.id)
    }

    override fun close() { database.close() }
}
