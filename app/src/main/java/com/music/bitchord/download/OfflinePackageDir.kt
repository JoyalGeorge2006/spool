package com.music.bitchord.download

import java.io.File
import java.util.UUID

/** A new owned generation, never a path derived from an untrusted media ID.
 * Historical absolute paths remain playable/deletable. Keep their parent name
 * because DownloadStore recognizes it. Do not sweep or reuse legacy children.
 * A failed or cancelled build removes only its own new directory; replacing a
 * record can leave the previous generation behind, which is safer than deleting
 * a file that playback or another record might still own.
 */
internal fun newOfflinePackageDir(filesDir: File, format: String): File {
    require(format == "hls" || format == "dash")
    return File(File(filesDir, "offline-hls"), "v2-$format-${UUID.randomUUID()}")
}
