package com.polls.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationParserTest {

    @Test
    void parsesSupportedUnits() {
        assertEquals(90_000L, DurationParser.parseMillis("90s"));
        assertEquals(1_800_000L, DurationParser.parseMillis("30m"));
        assertEquals(43_200_000L, DurationParser.parseMillis(" 12H "));
        assertEquals(259_200_000L, DurationParser.parseMillis("3d"));
    }

    @Test
    void rejectsInvalidAndOverflowingValues() {
        assertEquals(-1L, DurationParser.parseMillis(null));
        assertEquals(-1L, DurationParser.parseMillis(""));
        assertEquals(-1L, DurationParser.parseMillis("0m"));
        assertEquals(-1L, DurationParser.parseMillis("1w"));
        assertEquals(-1L, DurationParser.parseMillis("9223372036854775807d"));
        assertEquals(-1L, DurationParser.parseMillis("999999999999999999999d"));
    }

    @Test
    void formatsDurations() {
        long duration = 2 * 86_400_000L + 3 * 3_600_000L + 5 * 60_000L;
        assertEquals("2天3小时5分钟", DurationParser.format(duration));
        assertEquals("已结束", DurationParser.format(0));
    }
}
