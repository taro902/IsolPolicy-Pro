### IsolPolicy v2.1.1

`versionCode 5` · `libxposed API 102` · `system_server` only (`0 bytes` target injection) · Zero changes to the verified `7/7` `system_server` hook core

#### Procedural 16-Bit Pixel UI Overhaul
- **True 5x7 Bitmap Pixel Typography (`PixelFont` & `PixelTextView`):** Replaced generic anti-aliased system chrome typography with an integer-scaled procedural raster font across all headers, keycaps, badges, counters, and dock labels (0 `.ttf` bloat).
- **9-Slice Stepped Frames & Bayer Dithering (`PixelChrome`):** Fixed corner gaps with explicit stepped corner connectors, 1-unit hard pixel drop shadows, Bayer ordered dithered headers, and guarded left status rails.
- **3D Mechanical Keycaps (`PixelKeycapView`):** Replaced flat chips with tactile keycaps featuring a 2-unit solid 3D base lip that physically depresses on tap, integrated 4x4 pixel LEDs with specular glint, and 3-frame raster scan-fill.
- **Dual-Tone Sprite Atlas (`PixelSpriteAtlas`):** Upgraded icons to palette-indexed 14x14 and 12x12 dual-tone sprites (Shield crest, CRT Scope, Radar dish, Vault safe, Search lens, Close `X`).
- **4 Curated Hardware Palettes (`PixelPalette`):** `CRT PHOSPHOR` (dark studio), `DMG PAPER` (refined clash-free daylight LCD), `AMBER CRT` (monochrome warm terminal), and `POCKET OLIVE` (pure 4-shade handheld) with 4-block live color swatches in `VAULT`.
- **Interactive Telemetry & Physical Cartridge Dock:** Added 7 clickable pillar counter tiles (`AZ` to `DISP`, tap any tile to jump straight to `SCOPE` filtered to that pillar), auto-seeded `PixelScopeView` v2 with floating peak-hold caps, per-app live `HIT` badges on cards, interactive 6-keycap `AppInspectorDialog`, and pop-up `PixelDockBar`.
- **Removed All AI-Slop Formatting:** Eliminated `//`, `|`, and raw `[BRACKET]` ASCII text across the entire interface in favor of framed `PixelTagBarView` pills and LED diagnostic rows.

#### Smart Radar & Safety Improvements
- **Web & Media Sandbox Separation (`ManifestRadar`):** Browsers (`Chrome`, `Brave`, `Firefox`) and chat/social apps (`WhatsApp`, `Telegram`, `Instagram`, `Facebook`) are now classified cleanly as `SANDBOX` (`SAFE OFF`) instead of false red threats.
- **Safe `AUTO BANK` & Batch Presets:** `AUTO BANK` and `VAULT` batch presets target actionable financial/antifraud apps only and never touch browsers or chat apps; refined `VKEY` native library matching to prevent false positives.

#### Upgrade Notes
- All 10 `hook/` files in `system_server` remain untouched from `v2.1.0` (`7/7` hooks verified live on Android 14 / Linux 4.19 non-SUSFS).
- Export your JSON backup in `VAULT`, uninstall `v2.1.0` (ephemeral CI keystore), install `IsolPolicy-v2.1.1-release.apk`, re-check `io.github.taro902.isolationpolicy` in **HMA-OSS Blacklist Template**, enable in **LSPosed (Vector)** (`System Framework`), import JSON, and reboot once.

Licensed under **Apache-2.0** (`META-INF/LICENSE` & `META-INF/NOTICE` embedded). Derived from [MhmRdd/Isolation-Policy](https://github.com/MhmRdd/Isolation-Policy) under `io.github.taro902.isolationpolicy`.
