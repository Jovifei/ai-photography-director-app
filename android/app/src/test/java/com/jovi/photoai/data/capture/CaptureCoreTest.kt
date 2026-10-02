package com.jovi.photoai.data.capture

import org.junit.Test

class CaptureCoreTest {
    @Test fun captureAndRecovery() = CaptureCoreCases.captureAndRecovery()
    @Test fun exportIdentityAndSuccess() = CaptureCoreCases.exportIdentityAndSuccess()
    @Test fun exportFailuresAndRestart() = CaptureCoreCases.exportFailuresAndRestart()
    @Test fun deletionAndProjectOwnership() = CaptureCoreCases.deletionAndProjectOwnership()
    @Test fun concurrentExportAndCapture() = CaptureCoreCases.concurrentExportAndCapture()
    @Test fun fileSafety() = CaptureCoreCases.fileSafety()
}
