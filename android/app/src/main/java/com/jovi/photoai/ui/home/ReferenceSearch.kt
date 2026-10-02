package com.jovi.photoai.ui.home

import com.jovi.photoai.data.reference.ReferenceRecord
import com.jovi.photoai.data.reference.PhotoAnalysisStatus

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

/** Project-wide library search uses metadata only; it never opens private image files. */
internal data class ProjectReferenceFilters(
    val query: String = "",
    val scene: String? = null,
    val source: String? = null,
    val status: PhotoAnalysisStatus? = null,
    val projectId: String? = null,
)

internal data class ProjectReferenceSearchItem(
    val record: ReferenceRecord,
    val projectTitle: String?,
)

internal fun filterProjectReferences(
    records: List<ReferenceRecord>,
    projectTitles: Map<String, String>,
    filters: ProjectReferenceFilters,
): List<ProjectReferenceSearchItem> {
    val query = filters.query.trim().lowercase()
    val scene = filters.scene?.trim()?.takeIf(String::isNotEmpty)
    val source = filters.source?.trim()?.takeIf(String::isNotEmpty)
    return records.mapNotNull { record ->
        val projectTitle = projectTitles[record.projectId]
        val searchable = listOf(
            record.photo.title,
            record.bundle.scene,
            record.photo.sourceLabel,
            projectTitle.orEmpty(),
        ).joinToString(" ").lowercase()
        if ((query.isNotEmpty() && query !in searchable) ||
            (scene != null && !record.bundle.scene.equals(scene, ignoreCase = true)) ||
            (source != null && !record.photo.sourceLabel.equals(source, ignoreCase = true)) ||
            (filters.status != null && record.analysisStatus != filters.status) ||
            (filters.projectId != null && record.projectId != filters.projectId)
        ) null else ProjectReferenceSearchItem(record, projectTitle)
    }
}
