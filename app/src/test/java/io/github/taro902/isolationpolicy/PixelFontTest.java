package io.github.taro902.isolationpolicy;

import org.junit.Test;

import io.github.taro902.isolationpolicy.ui.pixel.PixelFont;
import io.github.taro902.isolationpolicy.ui.pixel.PixelMetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class PixelFontTest {

    @Test
    public void testAllVisibleAsciiGlyphsNonZero() {
        assertEquals(0L, PixelFont.glyph5x7(' '));
        for (char c = 33; c <= 126; c++) {
            assertTrue("Empty glyph for " + c, PixelFont.glyph5x7(c) != 0L);
        }
    }

    @Test
    public void testCriticalLettersExactBitPatterns() {
        assertEquals(0x0E, PixelFont.glyphRowBits('O', 0));
        assertEquals(0x11, PixelFont.glyphRowBits('O', 2));
        assertEquals(0x11, PixelFont.glyphRowBits('O', 3));
        assertEquals(0x11, PixelFont.glyphRowBits('O', 4));
        assertEquals(0x0E, PixelFont.glyphRowBits('O', 6));

        assertEquals(0x0F, PixelFont.glyphRowBits('S', 0));
        assertEquals(0x10, PixelFont.glyphRowBits('S', 2));
        assertEquals(0x0E, PixelFont.glyphRowBits('S', 3));
        assertEquals(0x01, PixelFont.glyphRowBits('S', 4));
        assertEquals(0x1E, PixelFont.glyphRowBits('S', 6));

        assertEquals(0x1E, PixelFont.glyphRowBits('D', 0));
        assertEquals(0x11, PixelFont.glyphRowBits('D', 3));
        assertEquals(0x1E, PixelFont.glyphRowBits('D', 6));

        assertEquals(0x19, PixelFont.glyphRowBits('N', 1));
        assertEquals(0x15, PixelFont.glyphRowBits('N', 2));
        assertEquals(0x13, PixelFont.glyphRowBits('N', 3));

        assertEquals(0x1F, PixelFont.glyphRowBits('4', 4));
        assertEquals(0x0E, PixelFont.glyphRowBits('8', 3));
    }

    @Test
    public void testMeasureAndFitWidth() {
        assertEquals(0, PixelFont.measureWidth("", 4));
        assertEquals(20, PixelFont.measureWidth("A", 4));
        assertEquals(44, PixelFont.measureWidth("AB", 4));
        assertEquals(28, PixelFont.lineHeight(4));
        assertEquals("ISOL..", PixelFont.fitText("ISOLPOLICY", 145, 4));
    }

    @Test
    public void testUiScaleModeClampingAndLabels() {
        assertEquals(PixelMetrics.SCALE_COMPACT, PixelMetrics.clampScaleMode(-5));
        assertEquals(PixelMetrics.SCALE_COMPACT, PixelMetrics.clampScaleMode(0));
        assertEquals(PixelMetrics.SCALE_BALANCED, PixelMetrics.clampScaleMode(1));
        assertEquals(PixelMetrics.SCALE_LARGE, PixelMetrics.clampScaleMode(2));
        assertEquals(PixelMetrics.SCALE_LARGE, PixelMetrics.clampScaleMode(9));
        assertEquals("COMPACT", PixelMetrics.scaleLabel(0));
        assertEquals("BALANCED", PixelMetrics.scaleLabel(1));
        assertEquals("LARGE", PixelMetrics.scaleLabel(2));
    }
}
