package com.sareemarket.app.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream

data class PhotoInfo(val name: String, val mimeType: String, val size: Long)

/** Reads the photos a seller picked. Photos are passed around as URI strings. */
interface PhotoSource {
    fun info(photo: String): PhotoInfo
    fun open(photo: String): InputStream
}

class ContentResolverPhotoSource(private val resolver: ContentResolver) : PhotoSource {

    override fun info(photo: String): PhotoInfo {
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

    override fun open(photo: String): InputStream =
        resolver.openInputStream(Uri.parse(photo)) ?: throw java.io.IOException("Cannot open $photo")
}
