## IsolPolicy v2.1.0 — 7-Pillar Server-Side Coherent Engine & Procedural Canvas Console

Major architectural release re-engineering IsolPolicy from a single-pillar AppZygote hook (`v2.0`) into a **26-class modular, 7-pillar server-side stealth and telemetry architecture** targeting `libxposed` API 102 (`system_server` only, **0 bytes injected into target applications**).

<table>
  <thead>
    <tr>
      <th>Release Metadata</th>
      <th>Architecture &amp; Footprint</th>
      <th>Verified Runtime Telemetry (Android 14 / 4.19)</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>Version:</strong> <code>2.1.0</code> (<code>versionCode = 4</code>)<br/><strong>Package:</strong> <code>io.github.taro902.isolationpolicy</code><br/><strong>API Contract:</strong> <code>libxposed:102.0.0</code></td>
      <td><strong>Scope:</strong> <code>system</code> (<code>system_server</code> only)<br/><strong>App Injection:</strong> <code>0 bytes</code> (<code>autoHotReload=false</code>)<br/><strong>Release APK:</strong> <code>426 KB</code> (R8 + EC P-256 v1/v2/v3)</td>
      <td><strong>XposedService:</strong> <code>OK (LSPosed API 102)</code><br/><strong>ZeroOracleIPC:</strong> <code>AUTHENTICATED (v2.1.0)</code><br/><strong>Active Hooks:</strong> <code>7/7</code> &middot; <code>&lt; 300 KB</code> RAM &middot; <code>0 B/s</code> Disk I/O</td>
    </tr>
  </tbody>
</table>

> [!IMPORTANT]
> **Zero Target Process Footprint:** Every shield executes exclusively inside `system_server` (`UID 1000`). No DEX classes, ART hook trampolines, or native libraries are loaded into banking, e-wallet, or detector processes. All non-guarded processes and privileged system UIDs (`appId < 10000`) bypass in a single integer comparison.

### What Changed from `v2.0` to `v2.1.0`

| Area | IsolPolicy `v2.0` (`versionCode = 3`) | IsolPolicy `v2.1.0` (`versionCode = 4`) |
| :--- | :--- | :--- |
| **Codebase Structure** | Monolithic single-pillar `BindHook` | **26 modular Java classes** across 5 strict acyclic layers (`core`, `hook`, `radar`, `ui`, `ui/pixel`) |
| **Process Isolation** | 4-layer AppZygote Silent Downgrade / Hard Drop | 4-layer AppZygote + **Pillar 2 Selective Non-External Isolated Folding** (6-gate WebView anti-crash protection) |
| **Isolated UID Tracking** | `WeakHashMap` per `HostingRecord` | **2048 B Hardware-Atomic `IdentityGraph`** (`AtomicLongArray[256]`) mapping `UID 90000-99999` back to `ownerAppId` in `O(1)` |
| **DevOpts / ADB / USB** | Not implemented | **Coherent 4-path projection:** `SettingsProvider.call()` (no `_track_generation`) + `SettingsProvider.query()` (`MatrixCursor`) + cloned sticky `USB_STATE` + `UsbService` `FUNCTION_ADB` mask |
| **Accessibility Hiding** | Not implemented | **Coherent 5-path projection:** `Settings.Secure` + `AMS.addClient()` 64-bit `IntPair` strip (`0x1B`) + dynamic `ParceledListSlice`/`List` + live `setState` push suppressor |
| **Package Delta &amp; Display** | Not implemented | **`getChangedPackages` nullifier** (orthogonal to HMA-OSS) + **Virtual/Presentation Display filter** (`displayId 0` only, zero overlap with `Enable Screenshot`) |
| **Telemetry &amp; Diagnostics** | Silent release only | **64-slot Wait-Free Seqlock Ring Buffer** + **Zero-Oracle IPC Gate** + **16-bit 7-Pillar Hook Health Matrix** + `Self-Coherence Verifier` |
| **Static Threat Scanner** | Binary AXML AppZygote check | Binary AXML + **Zero-Extract Native ELF Antifraud SDK Scanner** (8 SDK families) + deterministic `0-100` risk score |
| **Management Console** | Basic list UI (~305 KB) | **Procedural 16-Bit Pixel Canvas Console** (`426 KB`, 4 tabs, Dual Themes, 16x8 live oscilloscope, `PIXEL-16`/`NATIVE` icons, zero Compose/AppCompat/PNG) |

### Seven-Pillar Server-Side Engine (`hook/`)

1. **Pillar 1 — `AppZygoteShield` (`AZ: OFF / AUTO / DOWN / DROP`):** Preserves and strengthens the 4-layer cross-thread interception across `HostingRecord.byAppZygote`, `usesAppZygote`, `ProcessList.startProcessLocked`, and `ProcessList.startProcess` (`ActivityManager:procStart`) with `module.deoptimize()`, `ExceptionMode.PROTECTIVE`, and live `IdentityGraph` registration/eviction via `removeProcessNameLocked`.
2. **Pillar 2 — `IsolatedFoldShield` (`ISO` — Opt-In):** Folds internal non-external `FLAG_ISOLATED_PROCESS` helpers (`UID 99000-99999`) into the package's primary `UID 10xxx` process (already unmounted by Treat Wheel) without requiring global `umount` of system module binaries. Protected by 6 strict safety gates blocking `FLAG_EXTERNAL_SERVICE`, `FLAG_USE_APP_ZYGOTE`, dynamic `bindIsolatedService`, and WebView/Chrome/GMS packages.
3. **Pillar 3 — `SettingsShield` (`DEV` &amp; `A11Y` + Zero-Oracle IPC):** Attached via a **Dual-Checkpoint Idempotent Guard** (`onPackageLoaded` + `onPackageReady` for `com.android.providers.settings`). Spoofs `GET_global` and `GET_secure` returning a clean single-key `Bundle("value")` omitting `_track_generation` (preventing global `MemoryIntArray` cache poisoning), sanitizes direct SQLite `SettingsProvider.query()` cursors (both full and single-column projections), and hosts the authenticated `ISOLPOLICY_IPC_V21` gate (unauthorized UIDs fall through verbatim to stock `chain.proceed()`).
4. **Pillar 4 — `UsbBroadcastShield` (`DEV`):** Clones sticky `android.hardware.usb.action.USB_STATE` (`new Intent(sticky)`) with `adb=false` without mutating system `mStickyBroadcasts`, and clears bit `0` (`FUNCTION_ADB = 1L`) in `UsbService.getCurrentFunctions()` and `isFunctionEnabled("adb")` while keeping USB MTP and USB Tethering intact.
5. **Pillar 5 — `AccessibilityShield` (`A11Y`):** Clears accessibility capability bits (`0x1B`) from the 64-bit `IntPair` returned by `AccessibilityManagerService.addClient()`, empties `getEnabledAccessibilityServiceList()` and `getInstalledAccessibilityServiceList()` (dynamically returning `ParceledListSlice.emptyList()` on Android 13-16 or `Collections.emptyList()` on Android 10-12), and filters live push updates on `IAccessibilityManagerClient$Stub$Proxy.setState` via `WeakHashMap<IBinder, Integer>`.
6. **Pillar 6 — `PackageChangeShield` (`PKG`):** Intercepts `getChangedPackages` at Binder entrypoints (`IPackageManagerBase`, `PackageManagerService$IPackageManagerImpl`, `PackageManagerService`, fallback `ComputerEngine`) returning `null` for guarded UIDs, closing the *Package Changes Since Boot* gap orthogonal to HMA-OSS.
7. **Pillar 7 — `DisplayShield` (`DISP`):** Projects `DisplayManagerService$BinderService.getDisplayIds()` to physical `Display.DEFAULT_DISPLAY (0)` and returns `null` for non-zero virtual/presentation `getDisplayInfo()` queries without touching `WindowManagerService` or `FLAG_SECURE`.

### Procedural Canvas Console, Radar & Stability Fixes

* **4-Tab Procedural Canvas Interface (`GUARD`, `SCOPE`, `RADAR`, `VAULT`):** Hardware-accelerated custom `Canvas` engine with zero `onDraw()` allocations, partial `DiffUtil` payload rebinding, 16-column `PixelScopeView` live oscilloscope (polling halts completely on `onPause()`), 5-segment `PixelThreatBar`, `AppInspectorDialog`, and dual switchable palettes (`OBSIDIAN PHOSPHOR` dark / `PAPER LCD` daylight).
* **`ManifestRadar` Offline Profiler:** Zero `BlockedPrivateApi` reflection (`AssetManager.openXmlResourceParser`) + zero-extract ZIP Central Directory ELF scanner classifying `ALIPAY_APSE`, `PROMON_SHIELD`, `VKEY_VGUARD`, `APPSEALING`, `THREATMETRIX`, `DEXPROTECTOR`, `PACKER_GUARD`, and `HARDENED_SDK`.
* **Backward-Compatible JSON `v2.1.0` Backup:** Supports Clipboard and SAF `.json` Export/Import and automatically migrates legacy `v2.0` configurations.
* **Android 14 Window &amp; Lifecycle Fixes:** Resolved `PhoneWindow.getInsetsController()` NPE by applying system bar appearance after `setContentView()` via `decor.post(...)` and guarded background `ExecutorService` submissions during theme `recreate()`.
* **CI Signing &amp; Verification Hardening:** Restored EC P-256 signing (`v1 + v2 + v3`) in `app/build.gradle.kts`, enforced mandatory `apksigner verify --verbose --print-certs` before release upload, and automated dynamic `versionName` resolution in `.github/workflows/build.yml`.

### Release Assets

| Asset | Size | Purpose |
| :--- | :---: | :--- |
| `IsolPolicy-v2.1.0-release.apk` | `426 KB` | **Recommended production build.** R8 minified, resource-shrunk, EC P-256 (`v1/v2/v3`) signed, `Logger` stripped to 0 bytecode (0 Logcat). |
| `IsolPolicy-v2.1.0-debug.apk` | `6.63 MB` | Unminified diagnostic build with `BuildConfig.DEBUG == true` logging enabled for troubleshooting. |

### Upgrade, Installation & Recommended Profiles

> [!NOTE]
> **Upgrading from `v2.0`:** Because CI currently signs with an ephemeral EC P-256 keystore per build unless `SIGNING_KEYSTORE_B64` is configured, export your `v2.0` JSON backup to Clipboard, uninstall `v2.0`, install `IsolPolicy-v2.1.0-release.apk`, then re-check `io.github.taro902.isolationpolicy` in **HMA-OSS Blacklist Template** and activate it in **LSPosed (Vector)** with scope set exclusively to **System Framework (`system`)**.

1. Reboot once after activating in LSPosed (Vector).
2. Open IsolPolicy -> **`RADAR`** -> tap **`RUN TEST`** and confirm `XposedService: OK`, `ZeroOracleIPC: AUTHENTICATED (v2.1.0)`, and `Active Hooks: 7/7`.
3. Configure shields selectively in **`GUARD`** (takes effect in `0 ms` without reboot; force-stop target app once if already running):
   * **E-Wallet / Alipay SDK (`id.dana`):** `AZ:DOWN` + `DEV` + `A11Y` + `PKG` (`ISO: OFF`; `DISP` optional)
   * **Banking (`myBCA`, `BCA mobile`, `OCTO Mobile`, `BRImo`, `Livin'`, `SeaBank`):** `AZ:DOWN` + `DEV` + `A11Y` + `PKG` + `DISP` (`ISO: OFF` unless isolated mount detection occurs)
   * **Runtime Audit Detectors:** `AZ:DOWN` + `ISO` + `DEV` + `A11Y` + `PKG` + `DISP`
   * **Browsers (`Chrome`, `Brave`, `Firefox`) &amp; Chat/Social (`WhatsApp`, `Telegram`, `Instagram`, `Facebook`):** Keep **all shields OFF** (their AppZygote/isolated processes are web/media sandboxes; avoid batch `+THREATS` on `v2.1.0`).

### License & Attribution

Licensed under the **Apache License 2.0** (`LICENSE` and `NOTICE` embedded at `META-INF/`). Derived from upstream [MhmRdd/Isolation-Policy](https://github.com/MhmRdd/Isolation-Policy) and independently re-architected under `io.github.taro902.isolationpolicy` for `libxposed` API 102.
