package com.music.bitchord.ui

import com.music.bitchord.data.model.DetailPage

fun DetailPage.belongsToListener(generation: Long): Boolean =
    listenerGeneration == null || listenerGeneration == generation

/** A response begun by an old listener may not populate a newly opened same-id page. */
fun canApplyListenerDetail(origin: Long?, pageOrigin: Long?, current: Long): Boolean =
    origin == pageOrigin && (origin == null || origin == current)

/** Unknown restored playback provenance is never promoted to a current account page. */
fun canApplyBoundedListenerDetail(pageOrigin: Long?, requestOrigin: Long?, current: Long): Boolean =
    pageOrigin == null || (pageOrigin == current && requestOrigin == pageOrigin)
