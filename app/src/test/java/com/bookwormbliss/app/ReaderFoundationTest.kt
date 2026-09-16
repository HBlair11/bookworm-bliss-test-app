package com.bookwormbliss.app

import com.bookwormbliss.app.core.location.ReaderPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderFoundationTest {
    @Test fun readerPositionIsStable(){ val p=ReaderPosition(3,7,42); assertEquals(3,p.spineIndex); assertEquals(7,p.pageIndex); assertEquals(42,p.offset) }
}
