### IsolPolicy v2.1.0

`versionCode 4` · `libxposed API 102` · `system_server` only (`0 bytes` target injection) · Verified `7/7` active hooks on Android 14 / Linux 4.19 non-SUSFS

#### Upgrade from v2.0
- Re-architected from a single-pillar `BindHook` (`v2.0`, ~305 KB) into a **26-class modular 7-pillar engine** (`core`, `hook`, `radar`, `ui`, `ui/pixel`) with `autoHotReload=false` and `< 300 KB` resident `system_server` overhead (`0 B/s` disk I/O).
- Replaced per-record lookup with a lock-free `AtomicReference<PolicySnapshot>` (`0 ms` live policy swap, zero `PackageManager` locks) and a **2048 B hardware-atomic Reverse `IdentityGraph`** (`AtomicLongArray[256]`) mapping isolated/AppZygote UIDs (`90000-99999`) back to `ownerAppId` in `O(1)`.
- Added cross-API state coherence across `SettingsProvider`, `ActivityManager`, `UsbService`, `AccessibilityManagerService`, `PackageManagerService`, and `DisplayManagerService`.

#### 7-Pillar Server-Side Engine (`hook/`)
- **`AZ` (`AppZygoteShield`):** 4-layer cross-thread Silent Downgrade (`mHostingZygote = 0`) / Hard Drop across `HostingRecord` and `ProcessList` (`startProcessLocked`, `startProcess`, `removeProcessNameLocked`) with `module.deoptimize()`, `ExceptionMode.PROTECTIVE`, and live `IdentityGraph` eviction.
- **`ISO` (`IsolatedFoldShield`):** Opt-in folding of internal non-external `FLAG_ISOLATED_PROCESS` helpers (`UID 99000-99999`) into the package's Treat Wheel-cleaned `UID 10xxx` process, guarded by 6 strict anti-crash gates protecting WebView, Chrome, external services, and dynamic `bindIsolatedService`.
- **`DEV` (`SettingsShield` + `UsbBroadcastShield`):** Dual-Checkpoint attachment (`onPackageLoaded` + `onPackageReady`) spoofing `GET_global` / `GET_secure` via clean single-key `Bundle("value")` (omitting `_track_generation` to prevent `MemoryIntArray` cache poisoning), sanitizing direct SQLite `SettingsProvider.query()` cursors (`MatrixCursor`, full and single-column projections), cloning sticky `ACTION_USB_STATE` (`adb=false`), and stripping `FUNCTION_ADB (1L)` in `UsbService` while preserving USB MTP and Tethering.
- **`A11Y` (`AccessibilityShield` + `SettingsShield`):** Clears accessibility capability bits (`0x1B`) in the 64-bit `IntPair` returned by `AccessibilityManagerService.addClient()`, empties `getEnabledAccessibilityServiceList()` and `getInstalledAccessibilityServiceList()` (dynamically matching `ParceledListSlice` on Android 13-16 vs `List` on Android 10-12), and filters live push callbacks on `IAccessibilityManagerClient$Stub$Proxy.setState`.
- **`PKG` (`PackageChangeShield`):** Returns `null` on Binder entrypoints (`IPackageManagerBase` / `PackageManagerService`) for `getChangedPackages()`, closing the post-boot package delta leak orthogonal to HMA-OSS.
- **`DISP` (`DisplayShield`):** Projects `DisplayManagerService` to physical `Display.DEFAULT_DISPLAY (0)` only, hiding virtual/presentation displays without touching `FLAG_SECURE` (`Enable Screenshot` compatible).
- **Telemetry (`TelemetryHub`):** 64-slot wait-free Seqlock ring buffer + 16-bit 7-pillar `HookHealthMask` exposed through an authenticated **Zero-Oracle IPC Gate** (`ISOLPOLICY_IPC_V21` — unauthorized UIDs fall through untouched to stock `chain.proceed()`).

#### Procedural Canvas Console & Static Radar (`ui/`, `radar/`)
- **Zero-Bloat Procedural UI (`426 KB` Release APK):** Built purely on hardware-accelerated `Canvas` without Jetpack Compose, AppCompat, Material Components, or PNG assets; zero allocations in `onDraw()`, partial `DiffUtil` payload rebinding, and `onPause()` polling halt (`0%` background CPU).
- **4 Functional Workspaces:** `GUARD` (16x8 `PixelScopeView` live oscilloscope, 6 granular 1-tap chips, 5-segment `PixelThreatBar`, `AppInspectorDialog`), `SCOPE` (7-bit Hook Health Matrix, live 64-event stream, copy/purge), `RADAR` (`Self-Coherence Verifier` `RUN TEST` + threat index), and `VAULT` (presets, dual themes `OBSIDIAN PHOSPHOR` / `PAPER LCD`, `PIXEL-16` / `NATIVE` icon quantizer, Clipboard & SAF `.json` Export/Import backward-compatible with `v2.0`).
- **Offline `ManifestRadar`:** Binary AXML scanner (`AssetManager.openXmlResourceParser`, zero `BlockedPrivateApi`) + zero-extract ZIP Central Directory ELF classifier (`ALIPAY_APSE`, `PROMON_SHIELD`, `VKEY_VGUARD`, `APPSEALING`, `THREATMETRIX`, `DEXPROTECTOR`, `PACKER_GUARD`, `HARDENED_SDK`).

#### Fixes & CI Hardening
- Fixed `PhoneWindow.getInsetsController()` NPE on Android 14 by invoking system bar styling after `setContentView()` via `decor.post(...)` and guarded `ExecutorService` calls during theme `recreate()`.
- Restored EC P-256 release signing (`v1 + v2 + v3`) in `app/build.gradle.kts`, enforced mandatory `apksigner verify --verbose --print-certs` before release upload, and automated dynamic `versionName` resolution in `.github/workflows/build.yml`.

#### Assets, Upgrade & Recommended Profiles
- **`IsolPolicy-v2.1.0-release.apk` (`426 KB`):** Recommended production build (R8 minified, EC P-256 `v1/v2/v3` signed, `0` Logcat).
- **`IsolPolicy-v2.1.0-debug.apk` (`6.63 MB`):** Unminified diagnostic build with debug logging enabled.
- **Upgrade from `v2.0`:** Export JSON backup -> uninstall `v2.0` (ephemeral CI keystore) -> install `v2.1.0-release.apk` -> re-check `io.github.taro902.isolationpolicy` in **HMA-OSS Blacklist Template** -> enable in **LSPosed (Vector)** with scope `System Framework (system)` -> reboot -> verify `7/7` in `RADAR -> RUN TEST`.
- **DANA (`id.dana`):** `AZ:DOWN` + `DEV` + `A11Y` + `PKG` (`ISO: OFF`, `DISP` optional).
- **Banking (`OCTO`, `myBCA`, `BCA mobile`, `BRImo`, `Livin'`, `SeaBank`):** `AZ:DOWN` + `DEV` + `A11Y` + `PKG` + `DISP` (`ISO: OFF`).
- **Audit Detectors:** `AZ:DOWN` + `ISO` + `DEV` + `A11Y` + `PKG` + `DISP`.
- **Browsers & Chat/Social (`Chrome`, `Brave`, `Firefox`, `WhatsApp`, `Telegram`, `Instagram`):** Keep **all shields OFF** (avoid batch `+THREATS` on `v2.1.0`).

Licensed under **Apache-2.0** (`META-INF/LICENSE` & `META-INF/NOTICE` embedded). Derived from [MhmRdd/Isolation-Policy](https://github.com/MhmRdd/Isolation-Policy) under `io.github.taro902.isolationpolicy`.
