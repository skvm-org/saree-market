package com.sareemarket.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.sareemarket.app.ui.components.clampPan
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoViewerTest {

    private val screen = IntSize(1000, 2000)

    @Test
    fun atNormalSizeThePhotoCannotMove() {
        assertEquals(Offset.Zero, clampPan(Offset(300f, -200f), 1f, screen))
    }

    @Test
    fun zoomedInItMovesOnlyUntilAnEdgeWouldShow() {
        // At 2x the picture is 1000 px wider and 2000 px taller, so it may move 500 and 1000 each way.
        assertEquals(Offset(500f, -1000f), clampPan(Offset(900f, -5000f), 2f, screen))
        assertEquals(Offset(-120f, 340f), clampPan(Offset(-120f, 340f), 2f, screen))
    }
}
