package com.jovi.photoai.reference

/**
 * Phase 1 reuses the established Android-free reference-photo domain type.
 *
 * The type deliberately contains no Android Uri. A Photo Picker Uri remains a short-lived UI
 * session value and is never written to a Bundle, a database, or a shared contract.
 */
typealias ReferencePhoto = com.jovi.photoai.domain.model.ReferencePhoto
