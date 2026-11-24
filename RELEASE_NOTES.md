# IsolPolicy-Pro v2.2.1

## Pure-Canvas Pixel UI Polish
- Rebuilt all 95 printable ASCII `5x7` glyph bitmasks in `PixelFont` from visually verified pixel matrices, fixing shifted glyphs (`O`, `D`, `S`, `N`, `4`, `8`) and adding `PixelFontTest` regression checks in CI (48 total JVM unit tests).
- Replaced squished `3x5` micro glyphs with crisp `5x7` raster scaling across all labels and counters (`u = 4 px` at density 2.75: `micro = 21 px`, `small/keycap = 28 px`, `body/title = 35 px`).
- Enlarged touch targets and spacing for 1080p high-DPI screens: `PixelHeaderView` (`68 dp`, `80x80 px` shield emblem, drop-shadow title), `PixelDockView` (`64 dp`, `56x56 px` icons), `PixelKeycapButton` (`40-46 dp` with 3-color live hardware swatches on the 6 `VAULT` palette buttons), `PixelSearchBarView` (`48 dp`), `PixelScopeView` (`158 dp`), and `48 dp` framed app icons.
- Fixed vertical text overlap inside `PixelHealthMatrixView` and `PixelCoherenceBarView` by sizing cells dynamically from `3 * lineHeight + padding`.
- Added hardware corner rivets and `2u` depth shadows on `PixelPanelView`.

## Radar Refinement
- Tightened `VKEY` ELF matcher in `ManifestRadar` to require `libvkey` prefix, eliminating false `VKEY` tags on media editors.

## Upgrade Notes
- Signed with the same persistent EC P-256 keystore (`BEA4B3F2...0752FC`) as v2.2.0. Install directly in-place over v2.2.0 and reboot once (no uninstall or HMA-OSS/LSPosed re-check required).
