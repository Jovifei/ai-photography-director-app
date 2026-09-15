package com.jovi.photoai.p23r

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jovi.photoai.data.reference.LocalLanAnalysisConnection
import com.jovi.photoai.data.reference.LocalLanReferenceAnalysisProvider
import com.jovi.photoai.data.reference.ReferenceAnalysisRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class P23RLocalProviderAndroidTest {
    @Test fun cancelledHttpCall_propagatesCancellationInsteadOfUnavailable() = runBlocking {
        P23RRoomFixture().use { fixture ->
            fixture.seed(1)
            val context = ApplicationProvider.getApplicationContext<Context>()
            val file = File(context.filesDir, "references/${fixture.records.single().imageFileName}")
            file.parentFile!!.mkdirs()
            file.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
            try {
                val client = OkHttpClient.Builder()
                    .addInterceptor { throw CancellationException("synthetic cancellation") }
                    .build()
                val provider = LocalLanReferenceAnalysisProvider(
                    repository = fixture.repository,
                    connection = LocalLanAnalysisConnection(
                        baseUrl = "https://127.0.0.1",
                        accessToken = "x",
                        certificatePin = "sha256/${"A".repeat(64)}",
                    ),
                    client = client,
                )
                val caught = try {
                    provider.analyze(ReferenceAnalysisRequest(fixture.records.single().photo.id))
                    null
                } catch (error: CancellationException) {
                    error
                }
                assertNotNull(caught)
            } finally {
                file.delete()
            }
        }
    }
}
