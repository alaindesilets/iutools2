package org.iutools.corpus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/*
 * Downloads the prebuilt Nunavut Hansard index (hansard.db, gzip-compressed,
 * ~156 MiB) to a caller-chosen file, so a user can get bilingual examples
 * working without a developer's `adb push` / manual copy.
 *
 * The caller decides where it goes: the Android app puts it in its
 * external-files directory (see NunavutHansardLocalIndex.dbFile), a CLI
 * would use a cache directory. downloadTo() only needs the destination
 * File; it downloads, decompresses, and atomically renames into place.
 *
 * The published copy lives on this repo's own GitHub Releases, not a third
 * party -- release assets are separate storage from git itself (no
 * clone-size cost, 2 GiB per-file limit, no LFS quota), and the corpus's
 * CC BY 4.0 license explicitly permits redistributing a derivative work
 * like this compressed index. DOWNLOAD_URL is pinned to one immutable
 * release tag -- see the tag-per-build, never-overwrite-an-existing-tag
 * discipline in tools/README-hansard.md, matching this project's normal
 * "never force-push" git discipline. Bumping
 * NunavutHansardLocalIndex.SCHEMA_VERSION means cutting a new tag and
 * updating this constant in the same commit, so the two stay pinned.
 */
sealed interface HansardDownloadProgress {
    data class InProgress(val bytesDownloaded: Long, val totalBytes: Long) : HansardDownloadProgress
    data object Decompressing : HansardDownloadProgress
    data object Done : HansardDownloadProgress
    data class Failed(val message: String) : HansardDownloadProgress
}

object HansardIndexDownloader {
    const val DOWNLOAD_URL =
        "https://github.com/alaindesilets/iutools2/releases/download/hansard-db-v1/hansard.db.gz"
    private const val TIMEOUT_MS = 15_000

    // Progress is only emitted every ~1 MiB downloaded, not per read() call --
    // a naive per-chunk emit would fire ~2500 times over the full download
    // (64 KiB buffer, ~156 MiB file), far more than a progress readout needs.
    private const val PROGRESS_EMIT_INTERVAL_BYTES = 1_048_576L

    fun downloadTo(destination: File): Flow<HansardDownloadProgress> = flow {
        val compressedFile = File(destination.parentFile, "${destination.name}.download")
        val decompressedFile = File(destination.parentFile, "${destination.name}.tmp")
        try {
            fetchCompressed(compressedFile) { downloaded, total ->
                emit(HansardDownloadProgress.InProgress(downloaded, total))
            }
            emit(HansardDownloadProgress.Decompressing)
            decompressTo(compressedFile, decompressedFile)
            compressedFile.delete()
            if (!decompressedFile.renameTo(destination)) {
                throw IOException("Could not move decompressed database into place at $destination")
            }
            emit(HansardDownloadProgress.Done)
        } catch (e: IOException) {
            compressedFile.delete()
            decompressedFile.delete()
            emit(HansardDownloadProgress.Failed(e.message ?: e.toString()))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun fetchCompressed(file: File, onProgress: suspend (downloaded: Long, total: Long) -> Unit) {
        val connection = URL(DOWNLOAD_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode} from $DOWNLOAD_URL")
            }
            val totalBytes = connection.contentLengthLong
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastEmitted = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastEmitted >= PROGRESS_EMIT_INTERVAL_BYTES) {
                            lastEmitted = downloaded
                            onProgress(downloaded, totalBytes)
                        }
                    }
                    onProgress(downloaded, totalBytes)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    // public (not private): unit-tested directly (from :cli) against a small
    // hand-built gzip fixture, without needing a real network download.
    fun decompressTo(compressed: File, destination: File) {
        GZIPInputStream(compressed.inputStream()).use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
    }
}
