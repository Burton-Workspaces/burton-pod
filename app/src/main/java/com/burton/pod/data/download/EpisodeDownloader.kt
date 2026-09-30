package com.burton.pod.data.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpisodeDownloader @Inject constructor(
    private val client: OkHttpClient,
    @ApplicationContext private val context: Context,
) {
    fun fileFor(episodeId: String): File {
        val dir = File(context.filesDir, "episodes").apply { mkdirs() }
        return File(dir, "${safeName(episodeId)}.audio")
    }

    fun download(
        episodeId: String,
        url: String,
        onProgress: (bytes: Long, total: Long) -> Unit,
    ): File {
        val dest = fileFor(episodeId)
        val temp = File(dest.parentFile, "${dest.name}.part")
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Download failed (${response.code})")
            }
            val body = response.body ?: error("Empty download body")
            val total = body.contentLength()
            body.byteStream().use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                }
            }
        }
        if (dest.exists()) dest.delete()
        if (!temp.renameTo(dest)) {
            temp.copyTo(dest, overwrite = true)
            temp.delete()
        }
        return dest
    }

    fun delete(episodeId: String) {
        fileFor(episodeId).delete()
    }

    private fun safeName(episodeId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(episodeId.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
