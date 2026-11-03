package com.sareemarket.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoCompressorInstrumentedTest {

    private fun jpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // Noise-like stripes so the JPEG is not trivially tiny.
        for (x in 0 until width step 7) for (y in 0 until height step 5) bitmap.setPixel(x, y, Color.rgb(x % 256, y % 256, (x + y) % 256))
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }.toByteArray()
    }

    private fun size(bytes: ByteArray): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        return o.outWidth to o.outHeight
    }

    @Test
    fun aBigPhotoComesOutAtMost1600PxAndMuchSmaller() {
        val original = jpeg(4000, 3000)

        val result = PhotoCompressor.compress { ByteArrayInputStream(original) }

        assertNotNull(result)
        val (w, h) = size(result!!)
        assertEquals(1600, maxOf(w, h))
        assertTrue(w > h) // still landscape
        assertTrue(result.size < original.size)
        assertTrue(result.size <= PhotoCompressor.TARGET_BYTES)
    }

    @Test
    fun aSmallPhotoKeepsItsSize() {
        val result = PhotoCompressor.compress { ByteArrayInputStream(jpeg(800, 600)) }

        assertEquals(800 to 600, size(result!!))
    }

    @Test
    fun somethingThatIsNotAnImageGivesNull() {
        assertNull(PhotoCompressor.compress { ByteArrayInputStream("not a picture".toByteArray()) })
    }
}
