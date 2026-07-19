package com.jovi.photoai.pose.fake

sealed interface FakePoseScenario {
    data object SinglePerson : FakePoseScenario
    data object NoPerson : FakePoseScenario
    data object Partial : FakePoseScenario
    data object LowConfidence : FakePoseScenario
    data object MultiPersonUnknown : FakePoseScenario
    data class EngineError(val message: String = "fake engine error") : FakePoseScenario
    data class Delayed(val delayMs: Long) : FakePoseScenario
    data object NeverCallback : FakePoseScenario
}
