package com.sareemarket.app.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayInputStream
import java.io.InputStream

data class PhotoInfo(val name: String, val mimeType: String, val size: Long)

/** Reads the photos a seller picked. Photos are passed around as URI strings. */
interface PhotoSource {
    fun info(photo: String): PhotoInfo
    fun open(photo: String): InputStream
}

/**
 * Reads photos from the device and hands them over already compressed by [PhotoCompressor], so the name, type and size
 * reported by [info] are those of the JPEG that [open] returns. A photo that cannot be decoded is passed on untouched.
 * The compressed bytes are kept for the few uploads in flight (bounded by [CACHE_BYTES]) so a retry does not compress again.
 */
class ContentResolverPhotoSource(private val resolver: ContentResolver) : PhotoSource {

    private val cache = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {}

    private var cachedBytes = 0L

    override fun info(photo: String): PhotoInfo {
        val original = originalInfo(photo)
        val bytes = compressed(photo) ?: return original
        return PhotoInfo(original.name.substringBeforeLast('.', original.name) + ".jpg", "image/jpeg", bytes.size.toLong())
    }

    override fun open(photo: String): InputStream =
        compressed(photo)?.let { ByteArrayInputStream(it) } ?: openOriginal(photo)

    @Synchronized
    private fun compressed(photo: String): ByteArray? {
        cache[photo]?.let { return it }
        val bytes = try {
            PhotoCompressor.compress { openOriginal(photo) }
        } catch (e: Exception) {
            null // unreadable or out of memory: send the original and let the server judge it
        } catch (e: OutOfMemoryError) {
            null
        } ?: return null
        cache[photo] = bytes
        cachedBytes += bytes.size
        val oldest = cache.entries.iterator()
        while (cachedBytes > CACHE_BYTES && cache.size > 1 && oldest.hasNext()) {
            val entry = oldest.next()
            if (entry.key != photo) {
                cachedBytes -= entry.value.size
                oldest.remove()
            }
        }
        return bytes
    }

    private fun originalInfo(photo: String): PhotoInfo {
        val uri = Uri.parse(photo)
        var name = "photo.jpg"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                size = c.getLong(1)
            }
        }
        return PhotoInfo(name, resolver.getType(uri) ?: "image/jpeg", size)
    }

    private fun openOriginal(photo: String): InputStream =
        resolver.openInputStream(Uri.parse(photo)) ?: throw java.io.IOException("Cannot open $photo")

    private companion object {
        /** Compressed photos held in memory at once: room for a full 50-photo upload at the per-photo target. */
        const val CACHE_BYTES = 50L * 1024 * 1024
    }
}
