package com.jovi.photoai.data.reference

import kotlinx.coroutines.runBlocking
import org.junit.Test

class P23RCoreTest {
    @Test fun ownershipIsRequiredForEveryTransition() { P23RCoreCases.fencing() }
    @Test fun failedAcknowledgementIsNotRollbackProof() = runBlocking { P23RCoreCases.outcomes(); Unit }
}
