package com.jovi.photoai.domain.pose

@JvmInline
value class PoseEngineId(val value: String) {
    init {
        require(value.isNotBlank()) { "engine id must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        val FAKE = PoseEngineId("fake")
    }
}
