# IsolPolicy-Pro v2.2.0

## Engine and Core
- Replaced hot-path lookup in `PolicySnapshot` with primitive open-addressing `FlatIntIntMap` (Fibonacci hash `0x9E3779B9`, power-of-two capacity, load factor < 0.5, zero boxing on Binder threads).
- Extended the 32-bit policy word to bits `7..10` (`FLAG_PROC_SURFACE`, `FLAG_INPUT_SURFACE`, `FLAG_OBS_COHERENCE`, `FLAG_AUDIT_ONLY`) while preserving bits `0..6` and `RemotePreferences` keys for backward compatibility with v2.0 and v2.1 profiles.
- Fixed open-addressing collision miss on isolated UID eviction in `PolicyEngine` by writing tombstone sentinel `-1L` instead of `0L`, preserving probe chains and reusing tombstone slots on insertion.
- Routed all 10 pillars through unified `PolicyEngine.resolvePolicy(callingUid)` so Reverse IdentityGraph propagation covers `allowIsolated=true` services (such as `DISPLAY_SERVICE`) and multi-user profiles (`uid % 100000`).
- Added `CompatMatrix` 3-tier method resolution (`TIER_EXACT`, `TIER_OVERLOAD`, `TIER_FALLBACK`) and `HookGuard` per-callback circuit breaker (trips into pass-through after 8 consecutive internal faults without counting normal target exceptions).
- Upgraded `TelemetryHub` to a 256-slot wait-free Seqlock ring buffer, 64x10 per-app per-pillar hit matrix, 32-bit `HookHealthMask` (10x2-bit pillar status + 10-bit fault indicators + IPC/Settings checkpoint bits), and authenticated `ISOLPOLICY_IPC_V22` (backward-compatible with `V21`).
- Replaced `org.json` dependency in `PolicyCodec` with a standalone RFC-8259 parser to enable full JVM unit testing and strict depth/size bounds.

## SystemServer Pillars
- `AppZygoteShield` (Pillar 1): dynamic `APP_ZYGOTE` constant lookup, `boolean`/`ProcessRecord` return-type guard on `AZ_HARD_DROP`, and full isolated UID capture across pre/post `startProcessLocked`.
- `IsolatedFoldShield` (Pillar 2): preserved all 6 WebView/Chrome/external/dynamic safety gates with atomic rollback on exception and `FLAG_AUDIT_ONLY` dry-run reporting.
- `SettingsShield` (Pillar 3): expanded guarded table to 17 DevOpts/ADB/WiFi-ADB/Mock/Verifier/A11y keys across 4 paths (`GET_global/secure/system`, `LIST_global/secure/system` on `result_settings_list`, single-key `query`, and bounded 512-row bulk `query`); returns clean `Bundle` without `_generation_index` to avoid stale client `NameValueCache` retention.
- `UsbBroadcastShield` (Pillar 4): added `UsbService.getScreenUnlockedFunctions()` ADB bit stripping and optional `AdbService.getAdbWirelessPort() -> 0` (Android 11-16).
- `AccessibilityShield` (Pillar 5): integrated `HookGuard`, `FLAG_AUDIT_ONLY`, and dynamic `List`/`ParceledListSlice` empty-collection synthesis.
- `PackageSurfaceShield` (Pillar 6): replaced `PackageChangeShield`, adding `PackageInstallerService.getAllSessions` filtering so guarded apps only observe their own installation sessions.
- `DisplayShield` (Pillar 7): added `CallbackRecord.notifyDisplayEventAsync(int, int)` suppression for secondary displays with dynamic Android 14+ `boolean` vs Android 10-13 `void` return handling while leaving `displayId == 0` untouched.
- `ProcessSurfaceShield` (Pillar 8, opt-in): filters `REASON_SIGNALED`, `REASON_CRASH_NATIVE`, `REASON_USER_REQUESTED`, and `REASON_FREEZER` in `getHistoricalProcessExitReasons`, plus `isUserAMonkey() -> false` and `getProcessesInErrorState() -> null`.
- `InputSurfaceShield` (Pillar 9, opt-in): filters synthetic injector devices (`scrcpy`, `uhid`, `virtio`, `monkey`, `inject`, external zero-VID/PID pointers) while unconditionally preserving `id <= 0` (`VIRTUAL_KEYBOARD`), internal hardware/fingerprint sensors, and valid USB/Bluetooth peripherals.
- `ObserverCoherenceShield` (Pillar 10, opt-in): suppresses guarded Settings URI notifications in `ContentService$ObserverCollector.collect` (Android 11-16) with `registerContentObserver` fallback (Android 10).

## Radar, Telemetry, and Pure-Canvas Pixel UI
- Expanded `ManifestRadar` zero-extract Central Directory ELF SDK fingerprinting (`ALIPAY`, `VKEY`, `PROMON`, `APPSEAL`, `TMX`, `BANGCLE`, `JIAGU`, `ZIMPERIUM`, `ROOTBEER`, `HARDENED`) while strictly isolating `SANDBOX` packages (`SAFE OFF`) from `FINANCE` batch presets.
- Rebuilt `ui.pixel` into 19 allocation-free Pure-Canvas hardware widgets: integer `PixelMetrics`, 6 WCAG-verified palettes (`CRT PHOSPHOR`, `DMG PAPER`, `AMBER CRT`, `POCKET OLIVE`, `SLATE TOKYO`, `SEPIA TERM`), full 95-glyph `5x7` + micro `3x5` `PixelFont`, 10 unique `9x9` pillar sprites, 2-channel `PixelScopeView`, interactive 2x5 `PixelCounterStrip`, `PixelHealthMatrixView`, `PixelCoherenceBarView`, and `PixelAppHeatmapBar`.
- Added `AppInspectorDialog` with symmetric 2x5 10-pillar matrix, `SHADOW AUDIT DRY-RUN` toggle, per-app heatmap, and 1-tap presets; kept `GUARD` list cards uncluttered for 392 dp screens.
- Added APK signing certificate SHA-256 display in `VAULT`, `EXPORT TRACE` in `SCOPE`, and dynamic `BuildConfig.VERSION_NAME` SAF backup filenames.

## Tests, Signing, and CI
- Added 45 JUnit 4 JVM unit tests across 6 test suites executed via `:app:testDebugUnitTest` prior to APK assembly.
- Added CI hygiene gates verifying zero orphan `.aidl` files, `aidl = false`, zero `*-unsigned.apk` outputs, `< 650 KB` release APK budget, `keytool -list` validation on persistent EC P-256 PKCS#12 keystore, and mandatory `apksigner verify --verbose --print-certs`.
- Removed unused `app/src/main/aidl/io/github/taro902/isolationpolicy/IIsolationPolicyService.aidl` and locked `buildFeatures { aidl = false }`.

## Upgrade Notes
- Upgrading from ephemeral-signed v2.1.x to persistent EC P-256 signed v2.2.0 is a one-time signature transition: export JSON backup, uninstall v2.1.x, install v2.2.0, re-select in HMA-OSS Blacklist and LSPosed (`system` scope), import JSON backup, and reboot. Subsequent v2.2.x and newer releases install directly in-place.
