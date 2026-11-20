package com.stillshelf.app.scrobbling

import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.assertEquals

class LocalTrendScaleTest {
    @Test fun shortLocalHistoryUsesReadableDurationUnits() {
        val date = LocalDate.of(2026, 11, 20)
        assertEquals(1.0 to "s", trendDurationUnit(listOf(TrendPoint(date, 1, 45))))
        assertEquals(60.0 to "min", trendDurationUnit(listOf(TrendPoint(date, 1, 138))))
        assertEquals(3600.0 to "h", trendDurationUnit(listOf(TrendPoint(date, 50, 7200))))
    }
}
