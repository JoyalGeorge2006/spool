package com.music.bitchord.data

import com.music.bitchord.data.model.*
import kotlinx.coroutines.CancellationException

val TopPick.isPreparedStation: Boolean
    get() = kind == TopPickKind.PERSONAL_STATION || kind == TopPickKind.DISCOVERY_STATION

/** A metadata-only preview. Does not download audio or change playback. */
suspend fun prepareStationArtwork(
    pick: TopPick,
    load: suspend (TopPick) -> Result<List<Song>>,
): TopPick {
    if (!pick.isPreparedStation || pick.preparedSongs.isNotEmpty()) return pick
    val songs = try { load(pick).getOrNull().orEmpty().toList() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { emptyList() }
    if (songs.isEmpty()) return pick.copy(images = emptyList())
    val covers = songs.mapNotNull { it.thumbnailUrl?.takeIf { url ->
        url.startsWith("https://") || url.startsWith("http://") || url.startsWith("content://") || url.startsWith("file:")
    } }.distinct().take(3)
    return pick.copy(images = covers, preparedSongs = songs)
}
