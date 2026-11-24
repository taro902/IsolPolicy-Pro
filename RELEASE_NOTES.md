# IsolPolicy-Pro v2.2.2

## Adjustable Pure-Canvas UI Scale and Visual Craft
- Added persistent 3-tier integer pixel UI scale selector (`SCALE S` Compact, `SCALE M` Balanced default, `SCALE L` Large) in `VAULT -> HARDWARE DISPLAY AND ENGINE`, preserving active tab across seamless `recreate()`.
- Calibrated default `SCALE M` (`BALANCED`) for 392 dp / 1080p screens (`u = 3 px`, crisp `5x7` raster fonts at `21 px` / `28 px`, `36-40 dp` keycaps, `58 dp` header, `56 dp` dock, `42 dp` framed icons) with zero text overlap across all 3 scale modes.
- Added illuminated top-right LED indicator dots and `1u` specular bevel highlights on active `PixelKeycapButton` switches.
- Added symmetric left/right hardware corner rivets and sharper `1u` card drop shadows on `PixelPanelView`, plus Bayer 4x4 stippled active cartridge wells in `PixelDockView`.
- Expanded JVM unit test suite to 49 test cases including `PixelMetrics` scale clamping and `PixelFont` glyph bitmask verification.

## Upgrade Notes
- Signed with the same persistent EC P-256 keystore (`BEA4B3F2...0752FC`) as v2.2.0 and v2.2.1. Install directly in-place without uninstalling or re-checking HMA-OSS/LSPosed.
