package com.sareemarket.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Shrinks a photo on the phone to what the server keeps anyway (longest side 1600 px, JPEG). A phone photo of 5 to 30 MB
 * becomes a few hundred KB, so a 50-photo upload stays well under 50 MB, goes up faster and costs less mobile data.
 * Quality starts at 85 like the server's own re-encode and steps down, then the size steps down, until the photo fits
 * [TARGET_BYTES]; 50 photos at the target add up to 45 MB at most.
 */
object PhotoCompressor {

    const val MAX_SIDE = 1600

    /** One photo's budget: 50 of them stay under 50 MB. */
    const val TARGET_BYTES = 900 * 1024

    private val QUALITIES = intArrayOf(85, 75, 65)

    private val SIDES = intArrayOf(MAX_SIDE, 1280)

    /**
     * Returns the compressed JPEG, or null when the file cannot be decoded so the caller can send the original and let
     * the server give its normal "not a valid image" answer. [open] is called more than once (bounds, pixels, rotation).
     */
    fun compress(open: () -> InputStream): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE) }
        val decoded = open().use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val upright = rotated(decoded, orientation(open))
        var best: ByteArray? = null
        for (side in SIDES) {
            val scaled = scaledTo(upright, side)
            for (quality in QUALITIES) {
                val bytes = encode(scaled, quality)
                if (best == null || bytes.size < best.size) best = bytes
                if (bytes.size <= TARGET_BYTES) return bytes
            }
        }
        return best
    }

    /** The largest power of two that still leaves the decoded photo at least [maxSide] px on its longest side. */
    fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        val longest = maxOf(width, height)
        while (longest / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    /** The size that fits [maxSide] on the longest side with the same shape; a smaller photo is left alone. */
    fun fit(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
        val longest = maxOf(width, height)
        if (longest <= maxSide) return width to height
        val scale = maxSide.toDouble() / longest
        return maxOf(1, Math.round(width * scale).toInt()) to maxOf(1, Math.round(height * scale).toInt())
    }

    private fun scaledTo(bitmap: Bitmap, maxSide: Int): Bitmap {
        val (w, h) = fit(bitmap.width, bitmap.height, maxSide)
        return if (w == bitmap.width && h == bitmap.height) bitmap else Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun orientation(open: () -> InputStream): Int =
        try {
            open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL // a photo with no readable tags is simply taken as upright
        }

    /** JPEG output drops the EXIF tags, so the rotation they asked for has to be applied to the pixels here. */
    private fun rotated(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray {
        // JPEG has no transparency; a PNG with a clear background would otherwise turn black.
        val flat = if (bitmap.hasAlpha()) {
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also {
                Canvas(it).apply { drawColor(Color.WHITE); drawBitmap(bitmap, 0f, 0f, null) }
            }
        } else {
            bitmap
        }
        return ByteArrayOutputStream().also { flat.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
    }
}
