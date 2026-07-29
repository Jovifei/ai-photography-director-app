package com.jovi.photoai.ui.home

import com.jovi.photoai.data.reference.ReferenceRecord

internal data class SearchableReference(
    val id: String,
    val title: String,
    val scene: String,
    val lighting: String,
    val composition: String,
    val tags: Set<String>,
)

internal fun SearchableReference.matches(query: String, sceneFilter: String?): Boolean {
    val normalizedQuery = query.trim().lowercase()
    val haystack = listOf(title, scene, lighting, composition).plus(tags).joinToString(" ").lowercase()
    return (normalizedQuery.isBlank() || normalizedQuery in haystack) &&
        (sceneFilter == null || sceneFilter in scene || tags.any { sceneFilter in it })
}

internal fun ReferenceRecord.toSearchableReference(): SearchableReference = SearchableReference(
    id = photo.id,
    title = photo.title,
    scene = bundle.scene,
    lighting = bundle.lighting,
    composition = bundle.composition,
    tags = setOf(bundle.scene, bundle.lighting, bundle.composition),
)
