<div align="center">

<h1>IsolPolicy</h1>

<p><strong>7-Pillar Server-Side Process Isolation, Lock-Free Reverse IdentityGraph, and Coherent Binder Environment Shield for Android 10-16</strong></p>

<p>
  <img src="https://img.shields.io/badge/version-2.1.0-4ADE80?style=flat-square&amp;labelColor=0D0F12" alt="Version 2.1.0" />
  <img src="https://img.shields.io/badge/libxposed-API%20102-38BDF8?style=flat-square&amp;labelColor=0D0F12" alt="libxposed API 102" />
  <img src="https://img.shields.io/badge/target-system__server%20only-A78BFA?style=flat-square&amp;labelColor=0D0F12" alt="system_server only" />
  <img src="https://img.shields.io/badge/app%20injection-0%20bytes-4ADE80?style=flat-square&amp;labelColor=0D0F12" alt="0 bytes app injection" />
  <img src="https://img.shields.io/badge/release%20APK-419%20KB-FBBF24?style=flat-square&amp;labelColor=0D0F12" alt="Release APK 419 KB" />
  <img src="https://img.shields.io/badge/license-Apache--2.0-94A3B8?style=flat-square&amp;labelColor=0D0F12" alt="Apache 2.0" />
</p>

<p>
  <a href="#architectural-thesis">Architecture</a> &middot;
  <a href="#system-topology">Topology</a> &middot;
  <a href="#formal-specification">Math Spec</a> &middot;
  <a href="#seven-pillar-engine">7-Pillar Matrix</a> &middot;
  <a href="#kernel-and-binder-internals">Internals</a> &middot;
  <a href="#procedural-pixel-ui">Pixel UI</a> &middot;
  <a href="#ecosystem-synergy">Synergy</a> &middot;
  <a href="#build-and-verification">Verification</a>
</p>

</div>

## Architectural Thesis

Modern Android anti-tamper and financial security SDKs (Alipay `APSecuritySDK`, Promon SHIELD, V-Key `vGuard`, AppSealing, ThreatMetrix, and runtime audit suites) defeat conventional userspace hiding through four structural mechanisms:

1. **AppZygote & Non-AppZygote Isolated Mount-Escape:** On non-GKI kernels without kernel-level mount filtering (e.g., Linux 4.19 without SUSFS), Zygisk userspace unmount tools clean the primary process namespace (`UID 10xxx`). Security SDKs bypass this by spawning either an `AppZygote` service (`FLAG_USE_APP_ZYGOTE`, `UID 90000-98999`) or an internal isolated helper service (`FLAG_ISOLATED_PROCESS`, `UID 99000-99999`) that inherits an uncleaned mount table directly from Zygote.
2. **Cross-API State Contradiction:** Spoofing `Settings.Global.adb_enabled = 0` inside `SettingsProvider.call()` alone leaves contradicting truth values in `SettingsProvider.query()` (SQL cursor bypass), sticky broadcast `android.hardware.usb.action.USB_STATE` (`adb = true`), and `UsbService.getCurrentFunctions()` (`FUNCTION_ADB`). Likewise, hiding `Settings.Secure.accessibility_enabled` fails if `AccessibilityManagerService.addClient()` still returns the live 64-bit `IntPair` bitmask (`STATE_FLAG_ACCESSIBILITY_ENABLED = 0x1`) or pushes live updates via `IAccessibilityManagerClient.setState()`.
3. **Cache Poisoning & Lifecycle Races in `system_server`:** Returning AOSP `_track_generation` keys inside spoofed `SettingsProvider` bundles contaminates shared `MemoryIntArray` caches globally across unrelated processes. Simultaneously, `SettingsProvider.apk` loads into `system_server` via `LoadedApk` after `onSystemServerStarting`, causing single-point hooks to silently miss attachment unless guarded across both `onPackageLoaded` and `onPackageReady`.
4. **IPC Detection Oracles:** Custom telemetry endpoints exposed on system services become trivial detection fingerprints if unauthorized callers receive non-stock exceptions or custom error bundles.

**IsolPolicy v2.1.0** resolves all four mechanisms entirely inside `system_server` (`scope: system`) with **zero bytes injected into any target application process**, zero `PackageManager` lock contention, zero disk I/O in `system_server`, and a hardware-atomic 2 KB `IdentityGraph`.

<table>
  <thead>
    <tr>
      <th>Dimension</th>
      <th>Upstream v1.1 (API 82)</th>
      <th>IsolPolicy v2.0 (API 102)</th>
      <th>IsolPolicy v2.1.0 (API 102)</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>Execution Boundary</strong></td>
      <td><code>system_server</code></td>
      <td><code>system_server</code> (0 B app inject)</td>
      <td><code>system_server</code> (0 B app inject, <code>autoHotReload=false</code>)</td>
    </tr>
    <tr>
      <td><strong>Process Isolation Control</strong></td>
      <td>Hard cancel (SELinux timeout)</td>
      <td>4-Layer AppZygote Silent Downgrade</td>
      <td>4-Layer AppZygote + 6-Guard Selective Isolated Folding</td>
    </tr>
    <tr>
      <td><strong>UID Attribution</strong></td>
      <td>Package name scan</td>
      <td><code>WeakHashMap</code> record tracking</td>
      <td>Lock-Free 2 KB <code>AtomicLongArray</code> Reverse <code>IdentityGraph</code></td>
    </tr>
    <tr>
      <td><strong>Environment Coherence</strong></td>
      <td>None</td>
      <td>None</td>
      <td><code>Settings.call+query</code>, Sticky <code>USB_STATE</code>, <code>UsbService</code>, <code>AMS.addClient+Push</code>, <code>getChangedPackages</code>, <code>DisplayManager</code></td>
    </tr>
    <tr>
      <td><strong>Telemetry &amp; Diagnostics</strong></td>
      <td>Logcat</td>
      <td>Silent Release</td>
      <td>64-Slot Seqlock Ring Buffer + Zero-Oracle IPC Gate + 7-Bit Hook Health Matrix</td>
    </tr>
    <tr>
      <td><strong>Static Threat Analysis</strong></td>
      <td>None</td>
      <td>Binary AXML AppZygote Radar</td>
      <td>Binary AXML + Zero-Extract Native ELF Antifraud SDK Scanner</td>
    </tr>
    <tr>
      <td><strong>UI Footprint (Release)</strong></td>
      <td>Material / AppCompat</td>
      <td>~305 KB</td>
      <td>419 KB Procedural 16-Bit Pixel Canvas (Zero Compose/AppCompat/PNG)</td>
    </tr>
  </tbody>
</table>

## System Topology

~~~mermaid
flowchart TB
    UI[MainActivity Procedural Pixel UI]
    RADAR[ManifestRadar AXML and ELF Scanner]
    PREFS[(XposedService RemotePreferences)]
    ENGINE[PolicyEngine Lock-Free AtomicReference Snapshot]
    GRAPH[(IdentityGraph 256-Slot AtomicLongArray 2KB)]
    HUB[TelemetryHub 64-Slot Seqlock Ring Buffer]

    subgraph SYSSRV[system_server UID 1000 - Zero Target Process Injection]
        direction TB
        P1[Pillar 1 AppZygoteShield]
        P2[Pillar 2 IsolatedFoldShield]
        P3[Pillar 3 SettingsShield Call and Query]
        P4[Pillar 4 UsbBroadcastShield]
        P5[Pillar 5 AccessibilityShield]
        P6[Pillar 6 PackageChangeShield]
        P7[Pillar 7 DisplayShield]
        GATE{Zero-Oracle IPC Gate}
    end

    UI -->|Write Bitmask and AppId Map| PREFS
    RADAR -->|Risk Score and Preset| UI
    PREFS -->|Read-Only Change Listener| ENGINE
    P1 -->|Register Isolated to Owner UID| GRAPH
    GRAPH -->|O1 Owner AppId Lookup| ENGINE
    ENGINE --> P1
    ENGINE --> P2
    ENGINE --> P3
    ENGINE --> P4
    ENGINE --> P5
    ENGINE --> P6
    ENGINE --> P7
    P1 --> HUB
    P2 --> HUB
    P3 --> HUB
    P4 --> HUB
    P5 --> HUB
    P6 --> HUB
    P7 --> HUB
    P3 --> GATE
    GATE -->|Verified Self AppId| HUB
    HUB -->|Compact Primitive Bundle| UI
    GATE -->|Any Other UID| STOCK[Stock AOSP chain.proceed]
~~~

## Coherent Interception Sequence

~~~mermaid
sequenceDiagram
    participant App as Guarded Financial App
    participant AM as ActivityManager and ActiveServices
    participant Graph as PolicyEngine and IdentityGraph
    participant SP as SettingsProvider and AMS and DMS
    participant Hub as TelemetryHub Ring Buffer
    participant UI as IsolPolicy Pixel UI

    App->>AM: bindService with useAppZygote or isolatedProcess
    AM->>Graph: Lookup 32-bit PolicyFlags by AppId or Package
    alt AppZygote Service and AZ_DOWNGRADE
        AM->>AM: Clear mHostingZygote to REGULAR_ZYGOTE across ProcessStart thread
        AM->>Hub: Record PILLAR_APP_ZYGOTE ACT_DOWNGRADED
    else Internal Isolated Service and FLAG_ISO_FOLD
        AM->>AM: Verify 6 WebView safety guards then clear FLAG_ISOLATED_PROCESS
        AM->>Hub: Record PILLAR_ISO_FOLD ACT_FOLDED
    else Isolated Process Permitted
        AM->>Graph: Atomic pack isoUid and ownerAppId into AtomicLongArray
    end

    App->>SP: Query Settings.Global call and SQL cursor
    SP->>Graph: Resolve effective ownerAppId from callingUid
    SP-->>App: Clean Bundle value only without _track_generation and MatrixCursor
    SP->>Hub: Record PILLAR_SETTINGS ACT_SPOOFED

    App->>SP: registerReceiverWithFeature ACTION_USB_STATE
    SP-->>App: Cloned sticky Intent with adb false preserving MTP
    SP->>Hub: Record PILLAR_USB ACT_SANITIZED

    App->>SP: AccessibilityManagerService addClient and getEnabledList
    SP-->>App: IntPair state cleared of A11y flags and empty service list
    SP->>Hub: Record PILLAR_A11Y ACT_SANITIZED

    App->>SP: Probe ISOLPOLICY_IPC_V21 unauthorized UID
    SP-->>App: Stock AOSP chain.proceed response with zero oracle

    UI->>SP: Call ISOLPOLICY_IPC_V21 verified selfAppId
    SP->>Hub: Read tear-free seqlock 64-slot ring buffer
    SP-->>UI: Live counters and 7-bit hook health matrix
~~~

## Formal Specification

### 1. Compact 32-Bit Per-Package Policy Encoding

Every target application policy is packed into a single 32-bit primitive integer $\Phi_u \in [0, 127]$ stored inside an immutable copy-on-write `SparseIntArray`:

$$\Phi_u = m_{\text{AZ}} + 2^{2} f_{\text{ISO}} + 2^{3} f_{\text{DEV}} + 2^{4} f_{\text{A11Y}} + 2^{5} f_{\text{PKG}} + 2^{6} f_{\text{DISP}}$$

$$m_{\text{AZ}} \in \{0:\text{OFF},\quad 1:\text{AUTO},\quad 2:\text{DOWNGRADE},\quad 3:\text{HARD\_DROP}\}, \quad f_k \in \{0, 1\}$$

### 2. Hardware-Atomic Reverse `IdentityGraph`

For any incoming Binder `callingUid` $u$, the fast-path effective application identifier $a_{\text{eff}}(u)$ bypasses all system UIDs in a single integer comparison and resolves isolated/AppZygote UIDs ($u \bmod 100000 \ge 90000$) via Fibonacci multiplicative hashing over a fixed 256-slot `AtomicLongArray`:

$$a(u) = u \bmod 100000, \qquad h(u) = \left(\left(w \oplus \left\lfloor \frac{w}{2^{16}} \right\rfloor\right) \bmod 256\right), \quad w = (u \cdot \text{0x9E3779B9}) \bmod 2^{32}$$

$$E_i = \left(u_{\text{iso}} \cdot 2^{32}\right) + a_{\text{owner}}, \qquad \operatorname{Mem}(\mathcal{G}) = 256 \times 8\text{ B} = 2048\text{ B}$$

Because both `isoUid` (upper 32 bits) and `ownerAppId` (lower 32 bits) occupy a single 64-bit word, every ARM64 Kryo 260 read/write compiles to hardware-atomic `LDAR`/`STLR` semantics with zero locks, zero heap allocation, and bounded 8-step linear probing ($\mathcal{O}(1)$ worst-case latency).

### 3. Coherent `AccessibilityManagerService.addClient()` Projection

AOSP packs client state flags and relevant event types into a 64-bit `IntPair` return value $R_{\text{raw}}$. IsolPolicy masks active accessibility state bits while preserving non-accessibility event flags intact:

$$R_{\text{raw}} = \left(S_{\text{state}} \cdot 2^{32}\right) + E_{\text{events}}, \qquad M_{\text{clear}} = 2^{0} + 2^{1} + 2^{3} + 2^{4} = \text{0x1B}$$

$$S_{\text{clean}} = \sum_{b \in \{0..31\} \setminus \{0,1,3,4\}} s_b \cdot 2^{b}, \qquad R_{\text{out}} = \left(S_{\text{clean}} \cdot 2^{32}\right) + E_{\text{events}}$$

### 4. Deterministic Static Threat Score

`ManifestRadar` computes an offline severity vector $\mathcal{R}(p) \in [0, 100]$ from Binary AXML parsing and zero-extract ZIP Central Directory inspection:

$$\mathcal{R}(p) = \min\!\left(100,\quad 45\cdot\mathbf{1}_{\text{AZ}} + 25\cdot\mathbf{1}_{\text{ISO}} + \min\!\left(35,\quad 20\cdot|\mathcal{S}_{\text{ELF}}|\right) + 10\cdot\mathbf{1}_{\text{QAP}} + 15\cdot\mathbf{1}_{\text{FIN}}\right)$$

## Seven-Pillar Engine

<table>
  <thead>
    <tr>
      <th>Pillar</th>
      <th>Module Class</th>
      <th>AOSP Target Class &amp; Methods</th>
      <th>Coherent Invariant &amp; Safety Guard</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>1. AppZygote</strong></td>
      <td><code>AppZygoteShield</code></td>
      <td><code>HostingRecord.byAppZygote</code>, <code>usesAppZygote</code>, <code>ProcessList.startProcessLocked</code>, <code>startProcess</code>, <code>removeProcessNameLocked</code></td>
      <td>4-layer cross-thread Silent Downgrade (<code>mHostingZygote = 0</code>) or Hard Drop + live <code>IdentityGraph</code> registration/eviction + <code>module.deoptimize()</code>.</td>
    </tr>
    <tr>
      <td><strong>2. Isolated Fold</strong></td>
      <td><code>IsolatedFoldShield</code></td>
      <td><code>ActiveServices.bringUpServiceLocked</code></td>
      <td>Opt-in folding of internal <code>FLAG_ISOLATED_PROCESS</code> helpers into clean main UID <code>10xxx</code>. Strictly blocks <code>FLAG_EXTERNAL_SERVICE</code>, <code>FLAG_USE_APP_ZYGOTE</code>, dynamic <code>bindIsolatedService</code>, and WebView/Chrome packages.</td>
    </tr>
    <tr>
      <td><strong>3. Settings &amp; IPC</strong></td>
      <td><code>SettingsShield</code></td>
      <td><code>SettingsProvider.call</code>, <code>SettingsProvider.query</code> (attached via Dual-Checkpoint <code>onPackageLoaded</code> + <code>onPackageReady</code>)</td>
      <td>Spoofs <code>GET_global</code> (Dev/ADB/Debug) and <code>GET_secure</code> (A11y/Mock) returning clean single-key <code>Bundle</code> without <code>_track_generation</code> + SQL <code>MatrixCursor</code> sanitizer (both full and single-column projections) + Zero-Oracle IPC Gate.</td>
    </tr>
    <tr>
      <td><strong>4. USB &amp; ADB</strong></td>
      <td><code>UsbBroadcastShield</code></td>
      <td><code>ActivityManagerService.registerReceiverWithFeature</code>, <code>UsbService.getCurrentFunctions</code>, <code>isFunctionEnabled</code></td>
      <td>Clones sticky <code>ACTION_USB_STATE</code> with <code>adb=false</code> without mutating system <code>mStickyBroadcasts</code> + strips <code>FUNCTION_ADB (1L)</code> while preserving USB MTP.</td>
    </tr>
    <tr>
      <td><strong>5. Accessibility</strong></td>
      <td><code>AccessibilityShield</code></td>
      <td><code>AccessibilityManagerService.addClient</code>, <code>getEnabledAccessibilityServiceList</code>, <code>getInstalledAccessibilityServiceList</code>, <code>IAccessibilityManagerClient$Stub$Proxy.setState</code></td>
      <td>Strips <code>IntPair</code> state bits, dynamically matches <code>ParceledListSlice</code> (Android 13-16) vs <code>List</code> (Android 10-12), and filters live push notifications via <code>WeakHashMap&lt;IBinder, Integer&gt;</code>.</td>
    </tr>
    <tr>
      <td><strong>6. Package Delta</strong></td>
      <td><code>PackageChangeShield</code></td>
      <td><code>IPackageManagerBase</code>, <code>PackageManagerService$IPackageManagerImpl</code>, <code>PackageManagerService</code>, fallback <code>ComputerEngine.getChangedPackages</code></td>
      <td>Returns <code>null</code> at Binder entrypoints before <code>clearCallingIdentity()</code>, closing the post-boot package delta leak orthogonal to HMA-OSS.</td>
    </tr>
    <tr>
      <td><strong>7. Display Stealth</strong></td>
      <td><code>DisplayShield</code></td>
      <td><code>DisplayManagerService$BinderService.getDisplayIds</code>, <code>getDisplayInfo</code></td>
      <td>Projects display topology to physical <code>Display.DEFAULT_DISPLAY (0)</code> only, hiding virtual/presentation displays without touching <code>FLAG_SECURE</code>.</td>
    </tr>
  </tbody>
</table>

## Kernel and Binder Internals

### Linux 4.19 Non-SUSFS Mount Namespace & SELinux Context Escape

On non-GKI kernels (such as SDM660 `4.19.304`) lacking SUSFS, Zygisk unmount operates per forked application process in `untrusted_app`. When a security SDK spawns a child service in `app_zygote` or `isolated_app`, the forked task clones the parent Zygote `mnt_namespace` before userspace unmount runs, exposing overlay or bind mounts inside `/proc/self/mountinfo`:

~~~c
struct task_struct *zygote_fork_isolated(struct task_struct *zygote, uid_t iso_uid) {
    struct nsproxy *ns = copy_nsproxy(zygote->nsproxy);
    if (iso_uid >= 90000 && iso_uid <= 99999) {
        return create_isolated_task(ns, SELINUX_DOMAIN_ISOLATED_APP);
    }
    return create_untrusted_task(unmount_zygisk_mounts(ns), SELINUX_DOMAIN_UNTRUSTED_APP);
}
~~~

By downgrading `AppZygote` (Pillar 1) and selectively folding internal non-external `FLAG_ISOLATED_PROCESS` helpers (Pillar 2) inside `system_server` prior to Zygote fork, the service executes inside the target package's already-sanitized `UID 10xxx` namespace, eliminating isolated mount leaks without performing destructive global `umount`.

### Lock-Free 64-Bit `IdentityGraph` & Seqlock Ring Buffer (Java)

~~~java
private void putIdentityGraph(int isoUid, int ownerAppId) {
    long target = (((long) isoUid) << 32) | (ownerAppId & 0xFFFFFFFFL);
    int base = mixIndex(isoUid);
    int freeSlot = -1;
    for (int step = 0; step < GRAPH_PROBE_LIMIT; step++) {
        int idx = (base + step) & GRAPH_MASK;
        long existing = identityGraph.get(idx);
        if (existing == 0L) {
            if (freeSlot < 0) freeSlot = idx;
            break;
        }
        if (((int) (existing >>> 32)) == isoUid) {
            identityGraph.set(idx, target);
            return;
        }
    }
    identityGraph.set(freeSlot >= 0 ? freeSlot : base, target);
}
~~~

### Architectural Evolution Diff (`v2.0` to `v2.1.0`)

~~~diff
--- a/app/src/main/resources/META-INF/xposed/java_init.list
+++ b/app/src/main/resources/META-INF/xposed/java_init.list
-io.github.taro902.isolationpolicy.Entry
+io.github.taro902.isolationpolicy.hook.Entry

--- a/app/src/main/java/io/github/taro902/isolationpolicy/hook/Entry.java
+++ b/app/src/main/java/io/github/taro902/isolationpolicy/hook/Entry.java
+    @Override
+    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
+        if (systemServerMode && SETTINGS_PROVIDER_PKG.equals(param.getPackageName())) {
+            settingsShield.attachIfNeeded(this, param.getDefaultClassLoader(), engine, telemetry);
+        }
+    }
+
+    @Override
+    public void onPackageReady(@NonNull PackageReadyParam param) {
+        if (systemServerMode && SETTINGS_PROVIDER_PKG.equals(param.getPackageName())) {
+            settingsShield.attachIfNeeded(this, param.getClassLoader(), engine, telemetry);
+        }
+    }
~~~

### Policy Export Schema (`v2.1.0` - Backward-Compatible with `v2.0`)

~~~json
{
  "schema": "isolpolicy",
  "version": "2.1.0",
  "defaultAzMode": 0,
  "policies": {
    "id.dana": {
      "flags": 62,
      "az": 2,
      "iso": true,
      "dev": true,
      "a11y": true,
      "pkg": true,
      "disp": false
    },
    "id.co.cimbniaga.octomobile": {
      "flags": 122,
      "az": 2,
      "iso": false,
      "dev": true,
      "a11y": true,
      "pkg": true,
      "disp": true
    }
  }
}
~~~

## Procedural Pixel UI

The control console (`io.github.taro902.isolationpolicy.ui.*`) is built without Jetpack Compose, AppCompat, Material Components, or raster PNG assets. All geometry, stepped 1-unit chamfer borders, 3D mechanical bevels, 8x8 and 10x10 bitmask icons, live oscilloscope bars, and threat meters are drawn directly on hardware-accelerated `Canvas` with zero object allocations inside `onDraw()`.

| Workspace Tab | Functional Surface | Hardware Budget Characteristics |
| :--- | :--- | :--- |
| **`GUARD`** | Live daemon header, 16-column `PixelScopeView` oscilloscope, 7-pillar counters, 80 ms debounced search, `ALL/THREATS/GUARDED/FINANCE/SYSTEM` filters, 6 one-tap granular chips (`AZ`, `ISO`, `DEV`, `A11Y`, `PKG`, `DISP`), and `AppInspectorDialog`. | Partial `DiffUtil` payload rebind (`PAYLOAD_FLAGS`), 4 MB bounded `PixelIconQuantizer` LruCache (`PIXEL-16` nearest-neighbor or `NATIVE`), 60 FPS on SDM660 / Adreno 512. |
| **`SCOPE`** | Live 7-bit Hook Health Matrix (`OK`/`FB`/`ER`), uptime/graph counters, per-pillar filter chips, `[LIVE/PAUSE]`, `[COPY LOG]`, `[PURGE]`, and 64-event ring buffer viewer. | Polling runs only during `onResume()` and halts completely on `onPause()` (0% background CPU/battery). |
| **`RADAR`** | Offline `Self-Coherence Verifier` (`RUN TEST`: probes `XposedService`, Zero-Oracle IPC `PING`, active hook count, and real host `DEV`/`ADB`/`A11Y` state) plus Static Threat Surface index. | Zero `BlockedPrivateApi` reflection; reads `AndroidManifest.xml` via `AssetManager.openXmlResourceParser` and scans ZIP Central Directory headers without writing to eMMC. |
| **`VAULT`** | Batch presets (`BANK STRICT`, `WALLET/ALIPAY`, `AUDIT MAX`, `RESET ALL`), Clipboard & SAF `.json` Export/Import (backward-compatible with `v2.0`), theme switcher (`OBSIDIAN PHOSPHOR` / `PAPER LCD`), icon mode, polling cadence, and system app toggle. | Total R8 Release APK size: **419 KB**; active `system_server` RAM overhead: **&lt; 300 KB**; disk I/O in `system_server`: **0 B/s**. |

## Modular Codebase Layout

~~~text
app/src/main/
├── AndroidManifest.xml
├── java/io/github/taro902/isolationpolicy/
│   ├── core/
│   │   ├── Logger.java
│   │   ├── PolicyCodec.java
│   │   ├── PolicyFlags.java
│   │   └── PolicySnapshot.java
│   ├── hook/
│   │   ├── AccessibilityShield.java
│   │   ├── AppZygoteShield.java
│   │   ├── DisplayShield.java
│   │   ├── Entry.java
│   │   ├── IsolatedFoldShield.java
│   │   ├── PackageChangeShield.java
│   │   ├── PolicyEngine.java
│   │   ├── SettingsShield.java
│   │   ├── TelemetryHub.java
│   │   └── UsbBroadcastShield.java
│   ├── radar/
│   │   ├── ManifestRadar.java
│   │   └── TelemetryClient.java
│   └── ui/
│       ├── AppInspectorDialog.java
│       ├── AppPolicyAdapter.java
│       ├── MainActivity.java
│       └── pixel/
│           ├── PixelChipView.java
│           ├── PixelDrawable.java
│           ├── PixelIconQuantizer.java
│           ├── PixelIcons.java
│           ├── PixelScopeView.java
│           ├── PixelTheme.java
│           └── PixelThreatBar.java
├── res/
│   ├── drawable/
│   │   ├── ic_launcher_background.xml
│   │   └── ic_launcher_foreground.xml
│   ├── mipmap-anydpi-v26/
│   │   ├── ic_launcher.xml
│   │   └── ic_launcher_round.xml
│   └── values/
│       ├── strings.xml
│       └── styles.xml
└── resources/META-INF/
    ├── LICENSE
    ├── NOTICE
    └── xposed/
        ├── java_init.list
        ├── module.prop
        └── scope.list
~~~

## Ecosystem Synergy

IsolPolicy v2.1.0 is engineered to remain strictly orthogonal to standard Zygisk/LSPosed stealth stacks on non-GKI kernels:

| Layer | Component | Scope | Conflict Surface |
| :--- | :--- | :--- | :--- |
| Zygisk &amp; Main Unmount | ReZygisk + Treat Wheel | Primary process (`UID 10xxx`) mount/maps cleanup | Complements Pillar 2 (`ISO-FOLD`) |
| Package Visibility | HMA-OSS (`oss-175`) | App list hiding, activity launch guard, install source | Orthogonal; Pillar 6 adds `getChangedPackages` filtering |
| Hardware Attestation | AlwaysStrong [Inject] | KeyMint / Keybox / VBMeta digest alignment | Zero overlap (does not touch `/data/adb/tricky_store/`) |
| Overlay &amp; Network | mountify + PingPimp | Global OverlayFS &amp; network tuning | Zero global `umount`; WebUI binaries remain functional |
| Window Security | Enable Screenshot | `WindowManagerService` `FLAG_SECURE` | Zero overlap; Pillar 7 only filters virtual display IDs |

## Build and Verification

### Toolchain & CI Constraints

* **Framework API:** `io.github.libxposed:api:102.0.0` (`compileOnly`) + `io.github.libxposed:service:102.0.0` (`implementation`) + `androidx.recyclerview:recyclerview:1.4.0`
* **Build System:** AGP `8.13.1`, Gradle `8.13`, JDK `21`, `compileSdk = 36`, `minSdk = 29`, `targetSdk = 36`
* **CI Guardrails:** `org.gradle.configuration-cache=false`, `AarMetadata` task disabled for `compileSdk=36` compatibility with `service:102.0.0`, R8 minification & resource shrinking enabled, `BuildConfig.DEBUG == false` strips all `Logger` calls to 0 bytecode.

### Deployment & Post-Boot Verification Checklist

1. Install `IsolPolicy-v2.1.0-release.apk` (upgrade in-place over `v2.0` preserves `io.github.taro902.isolationpolicy` preferences and automatic v2.0 policy migration).
2. Confirm `io.github.taro902.isolationpolicy` is checked in the **HMA-OSS Blacklist Template**.
3. Confirm **IsolPolicy** is enabled in **LSPosed (Vector)** with scope set exclusively to **System Framework (`system`)**.
4. Reboot once to attach the 7 `system_server` hooks (`autoHotReload=false`).
5. Open IsolPolicy, navigate to **`RADAR`**, and tap **`RUN TEST`** to verify:
   * `XposedService : OK (LSPosed API 102)`
   * `ZeroOracleIPC : AUTHENTICATED (v2.1.0)`
   * `Active Hooks  : 7/7`
6. Apply recommended presets in **`GUARD`** (takes effect immediately in 0 ms via lock-free snapshot swap, no reboot required):
   * **E-Wallet / Alipay SDK (`id.dana`):** `WALLET/ALIPAY` (`AZ:DOWN + ISO + DEV + A11Y + PKG`)
   * **Banking (`myBCA`, `BCA mobile`, `OCTO Mobile`, `BRImo`, `Livin'`):** `BANK STRICT` (`AZ:DOWN + DEV + A11Y + PKG + DISP`)

## License & Upstream Attribution

Licensed under the **Apache License 2.0**. Full license text and modification disclosures pursuant to Section 4(b) and Section 6 are preserved in `LICENSE`, `NOTICE`, and inside the compiled APK under `META-INF/LICENSE` and `META-INF/NOTICE`.

Derived from upstream [MhmRdd/Isolation-Policy](https://github.com/MhmRdd/Isolation-Policy), independently re-architected under namespace `io.github.taro902.isolationpolicy` for `libxposed` API 102 with 7 coherent server-side pillars, lock-free `IdentityGraph`, Zero-Oracle IPC telemetry, and a procedural pixel UI engine.
