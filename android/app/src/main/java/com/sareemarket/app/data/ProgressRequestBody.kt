package com.sareemarket.app.data

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

/** Streams one photo from the device and reports how many bytes of the whole upload have been sent. */
private class PhotoRequestBody(
    private val source: PhotoSource,
    private val photo: String,
    private val info: PhotoInfo,
    private val onBytes: (Long) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType = info.mimeType.toMediaType()

    override fun contentLength(): Long = info.size

    override fun writeTo(sink: BufferedSink) {
        source.open(photo).source().use { input ->
            var read = input.read(sink.buffer, CHUNK)
            while (read != -1L) {
                sink.flush()
                onBytes(read)
                read = input.read(sink.buffer, CHUNK)
            }
        }
    }

    private companion object {
        const val CHUNK = 64 * 1024L
    }
}

/** One multipart "files" part per photo. [onProgress] gets 0..1 across all photos together. */
fun photoParts(source: PhotoSource, photos: List<String>, onProgress: (Float) -> Unit): List<MultipartBody.Part> {
    val infos = photos.map { source.info(it) }
    val total = infos.sumOf { it.size.coerceAtLeast(0) }.coerceAtLeast(1)
    var sent = 0L
    return photos.mapIndexed { i, photo ->
        val body = PhotoRequestBody(source, photo, infos[i]) { bytes ->
            sent += bytes
            onProgress((sent.toFloat() / total).coerceAtMost(1f))
        }
        MultipartBody.Part.createFormData("files", infos[i].name, body)
    }
}

/** The payment screenshot, as the multipart part named "proof". */
fun proofPart(source: PhotoSource, photo: String): MultipartBody.Part {
    val info = source.info(photo)
    return MultipartBody.Part.createFormData("proof", info.name, PhotoRequestBody(source, photo, info) {})
}

/** The picture a customer searches with, as the multipart part named "image". */
fun imagePart(source: PhotoSource, photo: String): MultipartBody.Part {
    val info = source.info(photo)
    return MultipartBody.Part.createFormData("image", info.name, PhotoRequestBody(source, photo, info) {})
}
