package com.sareemarket.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoCompressorTest {

    @Test
    fun aBigPhotoIsDecodedAtTheLargestSampleThatKeepsTheLongestSideAtOrAboveTheTarget() {
        assertEquals(2, PhotoCompressor.sampleSize(4000, 3000, 1600)) // 2000 px left, not 1000
        assertEquals(4, PhotoCompressor.sampleSize(8000, 6000, 1600)) // 2000 px left
        assertEquals(1, PhotoCompressor.sampleSize(3000, 2000, 1600)) // 1500 px would be below the target
    }

    @Test
    fun aSmallPhotoIsNotSampledDown() {
        assertEquals(1, PhotoCompressor.sampleSize(1200, 900, 1600))
    }

    @Test
    fun fitKeepsTheShapeAndCapsTheLongestSide() {
        assertEquals(1600 to 1200, PhotoCompressor.fit(2000, 1500, 1600))
        assertEquals(1200 to 1600, PhotoCompressor.fit(1500, 2000, 1600))
    }

    @Test
    fun fitLeavesASmallPhotoAlone() {
        assertEquals(800 to 600, PhotoCompressor.fit(800, 600, 1600))
    }

    @Test
    fun fiftyPhotosAtTheTargetStayUnderFiftyMegabytes() {
        assertEquals(true, 50L * PhotoCompressor.TARGET_BYTES < 50L * 1024 * 1024)
    }
}
