package com.jovi.photoai.domain.pose

data class PoseEngineDescriptor(
    val id: PoseEngineId,
    val displayName: String,
    val version: String,
    val canonicalSchemaVersion: String = "pose-33-v1",
)
