<div align="center">

<h1>ISOLPOLICY</h1>

<p><code>RFC-ISOL-210</code> &middot; <strong>7-Pillar Server-Side Process Isolation, Hardware-Atomic Reverse IdentityGraph &amp; Cross-API Coherent Binder Shield</strong></p>

<p>
  <img src="https://img.shields.io/badge/release-v2.1.0-4ADE80?style=flat-square&amp;labelColor=0D0F12" alt="Release v2.1.0" />
  <img src="https://img.shields.io/badge/libxposed-API%20102-38BDF8?style=flat-square&amp;labelColor=0D0F12" alt="libxposed API 102" />
  <img src="https://img.shields.io/badge/android-10%20--%2016-94A3B8?style=flat-square&amp;labelColor=0D0F12" alt="Android 10 to 16" />
  <img src="https://img.shields.io/badge/execution-system__server%20only-A78BFA?style=flat-square&amp;labelColor=0D0F12" alt="system_server only" />
  <img src="https://img.shields.io/badge/app%20injection-0%20bytes-4ADE80?style=flat-square&amp;labelColor=0D0F12" alt="0 bytes app injection" />
  <img src="https://img.shields.io/badge/IdentityGraph-2048%20B%20atomic-38BDF8?style=flat-square&amp;labelColor=0D0F12" alt="2048 B atomic IdentityGraph" />
  <img src="https://img.shields.io/badge/release%20APK-419%20KB-FBBF24?style=flat-square&amp;labelColor=0D0F12" alt="Release APK 419 KB" />
  <img src="https://img.shields.io/badge/license-Apache--2.0-94A3B8?style=flat-square&amp;labelColor=0D0F12" alt="Apache 2.0" />
</p>

<p>
  <a href="#1-threat-surface-and-contradiction-model">01 Threat Model</a> &middot;
  <a href="#2-multi-layer-system-architecture">02 Architecture</a> &middot;
  <a href="#3-hardware-register-and-bitfield-specifications">03 Bitfields</a> &middot;
  <a href="#4-formal-mathematical-specification">04 Math Proofs</a> &middot;
  <a href="#5-seven-pillar-server-side-matrix">05 Seven Pillars</a> &middot;
  <a href="#6-polyglot-systems-internals">06 Polyglot Internals</a> &middot;
  <a href="#7-procedural-canvas-console">07 Console Engine</a> &middot;
  <a href="#8-modular-codebase-map">08 Codebase</a> &middot;
  <a href="#9-failure-mode-and-effects-analysis">09 FMEA</a> &middot;
  <a href="#10-hardware-budget-and-ecosystem-synergy">10 Telemetry</a> &middot;
  <a href="#11-build-and-verification-protocol">11 Verification</a> &middot;
  <a href="#12-license-and-upstream-attribution">12 License</a>
</p>

</div>

<table>
  <thead>
    <tr>
      <th>Execution Plane</th>
      <th>Memory &amp; Storage Budget</th>
      <th>Concurrency &amp; Latency</th>
      <th>Stealth Invariant</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>system_server</code> (<code>UID 1000</code>)<br/><code>scope: system</code><br/><code>autoHotReload=false</code></td>
      <td><code>&lt; 300 KB</code> resident RAM<br/><code>2048 B</code> IdentityGraph (32 L1 lines)<br/><code>0 B/s</code> eMMC disk writes</td>
      <td><code>O(1)</code> lock-free UID lookup<br/>0 <code>PackageManager</code> locks<br/>Wait-free 64-slot Seqlock</td>
      <td><code>0 bytes</code> injected in target apps<br/>Zero-Oracle IPC Gate<br/>Cross-API state coherence</td>
    </tr>
  </tbody>
</table>

> [!IMPORTANT]
> **Zero Target Process Injection Contract:** IsolPolicy v2.1.0 never loads DEX bytecode, never modifies ART method tables, and never maps foreign pages inside any banking, e-wallet, or audit application process. Every interception executes exclusively across Binder and ActivityManager service boundaries inside `system_server`.

## 1. Threat Surface and Contradiction Model

Contemporary financial anti-tamper suites (Alipay `APSecuritySDK`, Promon SHIELD, V-Key `vGuard`, AppSealing, ThreatMetrix, DexProtector) and forensic audit frameworks defeat single-layer root hiding through five structural detection vectors:

1. **AppZygote Namespace Escape (`UID 90000-98999`):** On non-GKI kernels without kernel mount filtering (e.g., Linux `4.19` without SUSFS), Zygisk userspace unmount sanitizes only the primary `untrusted_app` process (`UID 10xxx`). Spawning an `AppZygote` service (`FLAG_USE_APP_ZYGOTE = 0x8`) clones Zygote's uncleaned mount namespace directly into `/proc/self/mountinfo`.
2. **Non-AppZygote Isolated Helper Escape (`UID 99000-99999`):** Independent audit services declared with `android:isolatedProcess="true"` and `android:externalService="false"` (`FLAG_ISOLATED_PROCESS = 0x2`) bypass both AppZygote hooks and primary-process unmount, exposing module binaries mounted under `/system/bin/`.
3. **Cross-API Environment Contradiction:** Returning `"0"` for `Settings.Global.adb_enabled` inside `SettingsProvider.call()` alone produces deterministic contradictions against `SettingsProvider.query()` (direct SQLite cursor queries), sticky broadcast `android.hardware.usb.action.USB_STATE` (`adb = true`), and `UsbService.getCurrentFunctions()` (`FUNCTION_ADB = 1L`). Similarly, spoofing `Settings.Secure.accessibility_enabled` fails while `AccessibilityManagerService.addClient()` returns the live 64-bit `IntPair` bitmask (`STATE_FLAG_ACCESSIBILITY_ENABLED = 0x1`) or pushes live updates over `IAccessibilityManagerClient.setState()`.
4. **Global Cache Contamination & `LoadedApk` Attachment Races:** Returning AOSP `_track_generation` keys inside spoofed `SettingsProvider` bundles corrupts shared `MemoryIntArray` caches globally. Furthermore, `SettingsProvider.apk` loads into `system_server` via `LoadedApk` after `onSystemServerStarting`, silently defeating single-callback hooks unless guarded across both `onPackageLoaded` and `onPackageReady`.
5. **Telemetry IPC Oracles & Post-Boot Package Deltas:** Custom IPC endpoints on system providers become trivial fingerprint oracles unless unauthorized callers fall straight through to stock `chain.proceed()`. Simultaneously, `PackageManager.getChangedPackages()` leaks post-boot installations of root tools outside standard app-list filtering.

### Evolutionary Architecture Matrix (`v1.1` vs `v2.0` vs `v2.1.0`)

<table>
  <thead>
    <tr>
      <th>Subsystem Dimension</th>
      <th>Upstream v1.1 (API 82)</th>
      <th>IsolPolicy v2.0 (API 102)</th>
      <th>IsolPolicy v2.1.0 (API 102)</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>Framework Contract</strong></td>
      <td>Legacy Xposed API 82</td>
      <td>Pure <code>libxposed:102.0.0</code></td>
      <td>Pure <code>libxposed:102.0.0</code> (<code>autoHotReload=false</code>)</td>
    </tr>
    <tr>
      <td><strong>AppZygote Control</strong></td>
      <td>Hard abort (SELinux timeout)</td>
      <td>4-Layer Silent Downgrade</td>
      <td>4-Layer Cross-Thread Silent Downgrade / Hard Drop</td>
    </tr>
    <tr>
      <td><strong>Isolated Service Control</strong></td>
      <td>None</td>
      <td>None</td>
      <td>6-Guard Opt-In Selective Isolated Folding (WebView-Safe)</td>
    </tr>
    <tr>
      <td><strong>Isolated UID Attribution</strong></td>
      <td>None</td>
      <td><code>WeakHashMap</code> per record</td>
      <td>2048 B Lock-Free <code>AtomicLongArray</code> Reverse <code>IdentityGraph</code></td>
    </tr>
    <tr>
      <td><strong>Settings Interception</strong></td>
      <td>None</td>
      <td>None</td>
      <td>Dual-Checkpoint <code>call()</code> (no <code>_track_generation</code>) + <code>query()</code> <code>MatrixCursor</code></td>
    </tr>
    <tr>
      <td><strong>USB / ADB Coherence</strong></td>
      <td>None</td>
      <td>None</td>
      <td>Non-mutating sticky <code>USB_STATE</code> clone + <code>UsbService</code> bit-strip</td>
    </tr>
    <tr>
      <td><strong>Accessibility Coherence</strong></td>
      <td>None</td>
      <td>None</td>
      <td><code>addClient()</code> <code>IntPair</code> strip + dynamic <code>ParceledListSlice</code>/<code>List</code> + push guard</td>
    </tr>
    <tr>
      <td><strong>Package &amp; Display Stealth</strong></td>
      <td>None</td>
      <td>None</td>
      <td><code>getChangedPackages</code> nullifier + Virtual/Presentation Display filter</td>
    </tr>
    <tr>
      <td><strong>Telemetry &amp; IPC Security</strong></td>
      <td>Logcat</td>
      <td>Silent Release</td>
      <td>64-Slot Wait-Free Seqlock + Zero-Oracle IPC Gate + 7-Bit Health Matrix</td>
    </tr>
    <tr>
      <td><strong>Static Threat Analysis</strong></td>
      <td>None</td>
      <td>Binary AXML AppZygote scan</td>
      <td>Binary AXML + Zero-Extract Native ELF Antifraud SDK Scanner</td>
    </tr>
    <tr>
      <td><strong>Console Engine &amp; APK Size</strong></td>
      <td>AppCompat / Material</td>
      <td>~305 KB</td>
      <td>419 KB Procedural Hardware-Accelerated Canvas (0 Compose/AppCompat/PNG)</td>
    </tr>
  </tbody>
</table>

## 2. Multi-Layer System Architecture

### 2.1 Four-Plane Isolation Topology (`Kernel -> Native -> system_server -> Userspace`)

~~~mermaid
flowchart TB
    classDef kernel fill:#1A1416,stroke:#F87171,color:#E6ECF5,stroke-width:1.5px
    classDef native fill:#1C1912,stroke:#FBBF24,color:#E6ECF5,stroke-width:1.5px
    classDef engine fill:#112634,stroke:#38BDF8,color:#E6ECF5,stroke-width:1.5px
    classDef shield fill:#122C1E,stroke:#4ADE80,color:#E6ECF5,stroke-width:1.5px
    classDef console fill:#231B36,stroke:#A78BFA,color:#E6ECF5,stroke-width:1.5px

    subgraph PLANE_K[Linux 4.19 Non-GKI Kernel Plane - No SUSFS / No KPM]
        K_NS[mnt_namespace and nsproxy]:::kernel
        K_SEL[selinuxfs untrusted_app vs isolated_app]:::kernel
        K_BND[binderfs Calling UID Enforcement]:::kernel
    end

    subgraph PLANE_N[Native Zygisk and Overlay Plane]
        N_ZYG[Zygote64 + ReZygisk v1.0.0]:::native
        N_TW[Treat Wheel v0.0.11 Primary Unmount]:::native
        N_MNT[mountify v2.0.3 OverlayFS]:::native
        N_HMA[HMA-OSS oss-175 Package List Filter]:::native
    end

    subgraph PLANE_S[system_server UID 1000 - IsolPolicy v2.1.0 Execution Boundary]
        direction TB
        S_ENG[PolicyEngine - AtomicReference PolicySnapshot]:::engine
        S_GRP[(IdentityGraph - 256-Slot AtomicLongArray 2048 B)]:::engine
        S_HUB[(TelemetryHub - 64-Slot Seqlock + 16-Bit Health)]:::engine
        S_GATE{Zero-Oracle IPC Gate}:::engine

        P1[P1 AppZygoteShield]:::shield
        P2[P2 IsolatedFoldShield]:::shield
        P3[P3 SettingsShield Call+Query]:::shield
        P4[P4 UsbBroadcastShield]:::shield
        P5[P5 AccessibilityShield]:::shield
        P6[P6 PackageChangeShield]:::shield
        P7[P7 DisplayShield]:::shield
    end

    subgraph PLANE_U[Userspace Consoles and Guarded Application Plane]
        U_UI[MainActivity Procedural Canvas Console]:::console
        U_RAD[ManifestRadar AXML + ELF Scanner]:::console
        U_PREFS[(XposedService RemotePreferences)]:::console
        U_APP[Guarded Target UID 10xxx - 0 Bytes Injected]:::shield
        U_STOCK[Stock AOSP chain.proceed]:::native
    end

    U_RAD -->|Threat Score + Preset| U_UI
    U_UI -->|Write Bitmask + AppId Map| U_PREFS
    U_PREFS -->|Read-Only Listener 0ms Swap| S_ENG

    K_BND -->|Verified callingUid| P3
    K_BND -->|Verified callingUid| P4
    K_BND -->|Verified callingUid| P5
    K_BND -->|Verified callingUid| P6
    K_BND -->|Verified callingUid| P7

    P1 -->|Fold AppZygote| N_ZYG
    P2 -->|Fold Internal Iso| N_TW
    N_ZYG --> K_NS
    N_TW --> K_SEL
    N_MNT --> K_NS
    N_HMA --> U_APP

    P1 -->|Pack isoUid to ownerAppId| S_GRP
    S_GRP -->|O1 Lookup| S_ENG
    S_ENG --> P1
    S_ENG --> P2
    S_ENG --> P3
    S_ENG --> P4
    S_ENG --> P5
    S_ENG --> P6
    S_ENG --> P7

    P1 --> S_HUB
    P2 --> S_HUB
    P3 --> S_HUB
    P4 --> S_HUB
    P5 --> S_HUB
    P6 --> S_HUB
    P7 --> S_HUB

    P3 --> S_GATE
    S_GATE -->|Verified selfAppId| S_HUB
    S_HUB -->|Compact Primitive Bundle| U_UI
    S_GATE -->|Any Other UID| U_STOCK
    U_APP -->|Clean Binder IPC| PLANE_S
~~~

### 2.2 Five-Phase Cross-Thread & Coherent Binder Choreography

~~~mermaid
sequenceDiagram
    participant App as Guarded Financial App
    participant AM as ActivityManager and ActiveServices
    participant ProcT as ActivityManager procStart Worker
    participant Eng as PolicyEngine and IdentityGraph
    participant Svc as SettingsProvider AMS Usb DMS PMS
    participant Hub as TelemetryHub Seqlock
    participant UI as IsolPolicy Console

    App->>AM: Phase 1 - bindService useAppZygote or isolatedProcess
    AM->>Eng: O1 Lookup 32-bit PolicyFlags by AppId or Package
    alt AppZygote Service with AZ_DOWNGRADE
        AM->>AM: Mutate mHostingZygote to REGULAR_ZYGOTE and track WeakHashMap
        AM->>ProcT: Dispatch startProcess on worker thread
        ProcT->>ProcT: Enforce REGULAR_ZYGOTE invariant across threads
        ProcT->>Hub: Wait-free record PILLAR_APP_ZYGOTE ACT_DOWNGRADED
    else Internal Isolated Helper with FLAG_ISO_FOLD
        AM->>AM: Validate 6 WebView safety gates then clear FLAG_ISOLATED_PROCESS
        AM->>Hub: Wait-free record PILLAR_ISO_FOLD ACT_FOLDED
    else Isolated Process Permitted
        AM->>Eng: Atomic STLR pack isoUid and ownerAppId into AtomicLongArray
    end

    App->>Svc: Phase 2 - Settings.Global call and SQL query bypass
    Svc->>Eng: Resolve effective ownerAppId including 90000-99999 via IdentityGraph
    Svc-->>App: Single-key Bundle omitting _track_generation and sanitized MatrixCursor
    Svc->>Hub: Record PILLAR_SETTINGS ACT_SPOOFED

    App->>Svc: Phase 3 - registerReceiverWithFeature ACTION_USB_STATE and UsbService
    Svc-->>App: Cloned sticky Intent adb false and cleared FUNCTION_ADB bit
    Svc->>Hub: Record PILLAR_USB ACT_SANITIZED

    App->>Svc: Phase 4 - AMS addClient getEnabledList DMS getDisplayIds PMS getChangedPackages
    Svc-->>App: Cleared IntPair state empty list physical display 0 only and null delta
    Svc->>Hub: Record PILLAR_A11Y PILLAR_DISPLAY PILLAR_PKG_CHANGE

    App->>Svc: Phase 5A - Probe ISOLPOLICY_IPC_V21 from unauthorized UID
    Svc-->>App: Direct stock AOSP chain.proceed pass-through with zero oracle

    UI->>Svc: Phase 5B - Call ISOLPOLICY_IPC_V21 from verified selfAppId
    Svc->>Hub: Tear-free seqlock scan of 64-slot ring buffer
    Svc-->>UI: Primitive arrays counts uptime graphSize and 16-bit hookHealthMask
~~~

### 2.3 Complete 26-Class Software Architecture Graph

~~~mermaid
classDiagram
    namespace core {
        class PolicyFlags {
            +int AZ_MASK
            +int FLAG_ISO_FOLD
            +int FLAG_DEV_COHERENT
            +int FLAG_A11Y_COHERENT
            +int FLAG_PKG_CHANGES
            +int FLAG_DISP_STEALTH
            +getAzMode(int) int
            +cycleAzMode(int) int
            +toggle(int, int) int
        }
        class PolicySnapshot {
            +SparseIntArray appIdFlags
            +Map pkgFlags
            +SparseArray appIdToPkg
            +int selfAppId
            +long revision
        }
        class PolicyCodec {
            +encodePolicyMap(Map) String
            +encodeAppIdMap(SparseIntArray, SparseArray) String
            +decodeSnapshot(String, String) PolicySnapshot
            +exportJson(Map, int) String
            +importJson(String) ImportResult
        }
        class Logger {
            +bind(XposedInterface)
            +d(String, String)
            +e(String, String, Throwable)
        }
    }

    namespace hook {
        class Entry {
            +onModuleLoaded(ModuleLoadedParam)
            +onSystemServerStarting(SystemServerStartingParam)
            +onPackageLoaded(PackageLoadedParam)
            +onPackageReady(PackageReadyParam)
        }
        class PolicyEngine {
            -AtomicReference snapshotRef
            -AtomicLongArray identityGraph
            +resolveEffectiveAppId(int) int
            +resolveFlagsForUid(int) int
            +registerLiveProcess(int, String, int)
            +evictIsolatedUid(int)
            +isVerifiedSelfUid(int) boolean
        }
        class TelemetryHub {
            -EventSlot ringSlots
            -AtomicLong writeSeq
            -AtomicInteger hookHealthMask
            +record(int, int, int, int)
            +buildSnapshotBundle(PolicyEngine) Bundle
        }
        class AppZygoteShield
        class IsolatedFoldShield
        class SettingsShield
        class UsbBroadcastShield
        class AccessibilityShield
        class PackageChangeShield
        class DisplayShield
    }

    namespace radar {
        class ManifestRadar {
            +scanPackage(Context, PackageInfo) ScanReport
        }
        class TelemetryClient {
            +fetchSnapshot(Context) SnapshotData
            +runSelfDiagnostic(Context) DiagnosticResult
        }
    }

    namespace ui {
        class MainActivity
        class AppPolicyAdapter
        class AppInspectorDialog
        class PixelTheme
        class PixelIcons
        class PixelDrawable
        class PixelChipView
        class PixelScopeView
        class PixelThreatBar
        class PixelIconQuantizer
    }

    Entry --> PolicyEngine
    Entry --> TelemetryHub
    Entry --> AppZygoteShield
    Entry --> IsolatedFoldShield
    Entry --> SettingsShield
    Entry --> UsbBroadcastShield
    Entry --> AccessibilityShield
    Entry --> PackageChangeShield
    Entry --> DisplayShield
    PolicyEngine --> PolicySnapshot
    PolicyEngine --> PolicyCodec
    TelemetryHub --> PolicyEngine
    MainActivity --> PolicyCodec
    MainActivity --> ManifestRadar
    MainActivity --> TelemetryClient
    MainActivity --> AppPolicyAdapter
    MainActivity --> AppInspectorDialog
    AppPolicyAdapter --> PixelChipView
    AppPolicyAdapter --> PixelThreatBar
    AppPolicyAdapter --> PixelIconQuantizer
    MainActivity --> PixelScopeView
    MainActivity --> PixelTheme
    MainActivity --> PixelIcons
    MainActivity --> PixelDrawable
~~~

### 2.4 Dual-Checkpoint `SettingsProvider` & Lock-Free Snapshot State Machine

~~~mermaid
stateDiagram-v2
    [*] --> ModuleLoaded: Zygote loads module APK
    ModuleLoaded --> Detached: Non-system_server process calls detach()
    Detached --> [*]

    ModuleLoaded --> EngineInit: isSystemServer true bind RemotePreferences
    EngineInit --> SystemServerBoot: onSystemServerStarting(sysCl)
    SystemServerBoot --> CoreShieldsAttached: Install Pillars 1 2 4 5 6 7 + attempt P3

    CoreShieldsAttached --> CheckpointLoaded: onPackageLoaded com.android.providers.settings
    CoreShieldsAttached --> CheckpointReady: onPackageReady com.android.providers.settings

    CheckpointLoaded --> SettingsAttached: AtomicBoolean CAS false to true
    CheckpointReady --> SettingsAttached: Idempotent verification guard

    SettingsAttached --> SteadyState: All 7 Pillars Operational (0 B/s Disk I/O)
    SteadyState --> AtomicSwap: User toggles shield in MainActivity
    AtomicSwap --> SteadyState: AtomicReference PolicySnapshot replaced in 0ms
    SteadyState --> GraphEvict: ProcessList removeProcessNameLocked isolated UID
    GraphEvict --> SteadyState: AtomicLongArray slot cleared via CAS
~~~

### 2.5 In-Memory Data & Wire-Protocol Entity Model

~~~mermaid
erDiagram
    REMOTE_PREFERENCES ||--|| POLICY_SNAPSHOT : decodes_to
    POLICY_SNAPSHOT ||--o{ POLICY_FLAGS : indexes_by_appId
    IDENTITY_GRAPH_SLOT }o--|| POLICY_SNAPSHOT : resolves_ownerAppId
    TELEMETRY_RING_SLOT }o--|| HOOK_HEALTH_MASK : reported_with
    MANIFEST_SCAN_REPORT ||--|| POLICY_FLAGS : recommends_preset

    REMOTE_PREFERENCES {
        string policy_map_v21
        string appid_map_v21
        int self_app_id
        int default_az_v21
    }
    POLICY_SNAPSHOT {
        SparseIntArray appIdFlags
        Map pkgFlags
        SparseArray appIdToPkg
        int selfAppId
        long revision
    }
    POLICY_FLAGS {
        int azMode_bits_0_1
        bool isoFold_bit_2
        bool devCoherent_bit_3
        bool a11yCoherent_bit_4
        bool pkgChanges_bit_5
        bool dispStealth_bit_6
    }
    IDENTITY_GRAPH_SLOT {
        int index_0_to_255
        int upper32_isoUid
        int lower32_ownerAppId
    }
    TELEMETRY_RING_SLOT {
        long volatile_seqlock
        long timestampMs
        int appId
        int pillar_1_to_7
        int vectorCode_1_to_17
        int actionCode_1_to_7
    }
    HOOK_HEALTH_MASK {
        int bits_0_1_AZ
        int bits_2_3_ISO
        int bits_4_5_SET
        int bits_6_7_USB
        int bits_8_9_A11Y
        int bits_10_11_PKG
        int bits_12_13_DISP
    }
    MANIFEST_SCAN_REPORT {
        bool usesAppZygote
        string zygotePreloadName
        int isolatedServiceCount
        int antifraudSdkCount
        int threatScore_0_to_100
    }
~~~

### 2.6 Static Threat Risk Weight Distribution (`ManifestRadar`)

~~~mermaid
pie title Static Threat Vector Weight Allocation (Capped at 100)
    "AppZygote Presence (45)" : 45
    "Native Antifraud ELF SDKs (max 35)" : 35
    "Internal Isolated Services (25)" : 25
    "Financial Domain Classification (15)" : 15
    "QUERY_ALL_PACKAGES Surface (10)" : 10
~~~

## 3. Hardware Register and Bitfield Specifications

### 3.1 32-Bit `PolicyFlags` Compact Bitfield Register

Every guarded package's complete 7-pillar policy is encoded into a single 32-bit primitive `int` inside an immutable `SparseIntArray` (zero heap object per policy entry):

<table>
  <thead>
    <tr>
      <th>Bits <code>31..7</code></th>
      <th>Bit <code>6</code></th>
      <th>Bit <code>5</code></th>
      <th>Bit <code>4</code></th>
      <th>Bit <code>3</code></th>
      <th>Bit <code>2</code></th>
      <th>Bits <code>1..0</code></th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>RESERVED (0)</code></td>
      <td><code>DISP</code><br/><code>0x40</code></td>
      <td><code>PKG</code><br/><code>0x20</code></td>
      <td><code>A11Y</code><br/><code>0x10</code></td>
      <td><code>DEV</code><br/><code>0x08</code></td>
      <td><code>ISO</code><br/><code>0x04</code></td>
      <td><code>AZ_MODE</code><br/><code>0x03</code></td>
    </tr>
  </tbody>
</table>

| Preset Identifier | Hex Mask | Decimal | Binary (`[6..0]`) | Active Coherent Surface |
| :--- | :---: | :---: | :---: | :--- |
| `PRESET_NONE` | `0x00` | `0` | `0000000` | Pass-through (zero interception) |
| `PRESET_WALLET_ALIPAY` | `0x3E` | `62` | `0111110` | `AZ:DOWN + ISO + DEV + A11Y + PKG` |
| `PRESET_BANKING_STRICT` | `0x7A` | `122` | `1111010` | `AZ:DOWN + DEV + A11Y + PKG + DISP` |
| `PRESET_AUDIT_MAX` | `0x7E` | `126` | `1111110` | `AZ:DOWN + ISO + DEV + A11Y + PKG + DISP` |

### 3.2 64-Bit Hardware-Atomic `IdentityGraph` Word (`AtomicLongArray[256]`)

<table>
  <thead>
    <tr>
      <th>Bits <code>63..32</code> (32-Bit Unsigned Key)</th>
      <th>Bits <code>31..0</code> (32-Bit Owner Value)</th>
      <th>ARM64 Memory Ordering</th>
      <th>Cache Geometry</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>isoUid</code> (<code>90000..99999</code> or multi-user <code>u*100000 + iso</code>)</td>
      <td><code>ownerAppId</code> (<code>10000..19999</code>)</td>
      <td>Single-word <code>LDAR</code> / <code>STLR</code> / <code>CAS</code></td>
      <td><code>256 * 8 B = 2048 B</code> (exactly 32 ARM64 64-byte L1 lines)</td>
    </tr>
  </tbody>
</table>

### 3.3 64-Bit `AccessibilityManagerService.addClient()` `IntPair` Projection

<table>
  <thead>
    <tr>
      <th>Bits <code>63..32</code> (<code>stateFlags</code>)</th>
      <th>Cleared Mask (<code>~0x1B</code>)</th>
      <th>Bits <code>31..0</code> (<code>relevantEventTypes</code>)</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Accessibility client capability bitmask returned by Binder IPC</td>
      <td>Clears bit <code>0</code> (<code>ENABLED</code>), bit <code>1</code> (<code>TOUCH_EXPLORATION</code>), bit <code>3</code> (<code>HIGH_TEXT</code>), bit <code>4</code> (<code>AUDIO_DESC</code>)</td>
      <td>Preserved verbatim (<code>0xFFFFFFFF</code>) so non-A11y window events remain stable</td>
    </tr>
  </tbody>
</table>

### 3.4 16-Bit `HookHealthMask` Telemetry Register

<table>
  <thead>
    <tr>
      <th>Bits <code>15..14</code></th>
      <th>Bits <code>13..12</code></th>
      <th>Bits <code>11..10</code></th>
      <th>Bits <code>9..8</code></th>
      <th>Bits <code>7..6</code></th>
      <th>Bits <code>5..4</code></th>
      <th>Bits <code>3..2</code></th>
      <th>Bits <code>1..0</code></th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>RSVD</code></td>
      <td><code>DISP</code> (P7)</td>
      <td><code>PKG</code> (P6)</td>
      <td><code>A11Y</code> (P5)</td>
      <td><code>USB</code> (P4)</td>
      <td><code>SET</code> (P3)</td>
      <td><code>ISO</code> (P2)</td>
      <td><code>AZ</code> (P1)</td>
    </tr>
  </tbody>
</table>

State encoding per 2-bit field: `00` = `NONE (--)`, `01` = `OK (Full Attachment)`, `10` = `FB (Fallback Attachment)`, `11` = `ER (Unavailable)`.

## 4. Formal Mathematical Specification

### Theorem 1 — Compact Policy Vector Encoding

Every target application policy is bijectively encoded in a 7-bit integer $\Phi_u \in [0, 127]$:

$$\Phi_u = m_{\mathrm{AZ}} + 2^{2} f_{\mathrm{ISO}} + 2^{3} f_{\mathrm{DEV}} + 2^{4} f_{\mathrm{A11Y}} + 2^{5} f_{\mathrm{PKG}} + 2^{6} f_{\mathrm{DISP}}$$

$$m_{\mathrm{AZ}} \in \{0, 1, 2, 3\} \equiv \{\text{OFF}, \text{AUTO}, \text{DOWNGRADE}, \text{HARD-DROP}\}$$

$$f_k \in \{0, 1\}, \qquad k \in \{\mathrm{ISO}, \mathrm{DEV}, \mathrm{A11Y}, \mathrm{PKG}, \mathrm{DISP}\}$$

### Theorem 2 — Lock-Free Reverse `IdentityGraph` & Complexity Bounds

Given calling UID $u$ from Binder IPC, the effective application identifier $a_{\mathrm{eff}}(u)$ bypasses privileged system UIDs ($a(u) \lt 10000$) in a single branch and resolves isolated/AppZygote UIDs ($a(u) \ge 90000$) via Fibonacci multiplicative hashing across $N = 256$ slots with maximum probe depth $L = 8$:

$$a(u) = u \bmod 100000$$

$$w(u) = (u \cdot \mathrm{0x9E3779B9}) \bmod 2^{32}$$

$$h(u) = \left(w(u) \oplus \left\lfloor \frac{w(u)}{2^{16}} \right\rfloor\right) \bmod 256$$

$$E_i = \left(u_{\mathrm{iso}} \cdot 2^{32}\right) + a_{\mathrm{owner}}$$

$$\mathrm{Mem}(\mathcal{G}) = 256 \times 8\,\mathrm{B} = 2048\,\mathrm{B} = 32 \times 64\,\mathrm{B}_{\mathrm{L1}}$$

$$T_{\mathrm{lookup}}(u) \le L \cdot t_{\mathrm{LDAR}} = 8 \cdot t_{\mathrm{LDAR}} = \mathcal{O}(1)$$

### Theorem 3 — Wait-Free Seqlock Invariant (`TelemetryHub`)

For ring buffer capacity $C = 64$ (power of two), writer thread $W$ atomically claims sequence index $q$ via `getAndIncrement()`, maps slot $j = q \bmod 64$, writes sentinel $-1$, commits primitive fields, and publishes $q + 1$. Reader $R$ accepts slot $j$ if and only if:

$$\mathrm{Valid}(j) \iff \left(s_{1} = s_{2}\right) \land \left(s_{1} \gt 0\right)$$

Because writers never spin on readers, Binder latency added to `system_server` threads by telemetry recording is strictly wait-free ($\mathcal{O}(1)$, zero mutexes, zero heap allocations).

### Theorem 4 — Cross-API Coherent Projection Equivalence

Let $\mathcal{V}_{\mathrm{DEV}} = \{\mathrm{Settings.call},\, \mathrm{Settings.query},\, \text{USB-STATE},\, \mathrm{UsbService}\}$ and $\mathcal{V}_{\mathrm{A11Y}} = \{\mathrm{Settings.secure},\, \mathrm{AMS.addClient},\, \mathrm{AMS.enabled},\, \mathrm{AMS.installed},\, \mathrm{AMS.push}\}$. For every guarded UID $u$ with $f_{\mathrm{DEV}} = 1$ (resp. $f_{\mathrm{A11Y}} = 1$), IsolPolicy enforces pairwise observational equivalence across every API endpoint $v_i, v_j$:

$$\forall v_i, v_j \in \mathcal{V}_{\mathrm{DEV}}:\quad \Pi_{v_i}(u) \equiv \Pi_{v_j}(u) \equiv \Omega_{\mathrm{clean}}$$

$$\forall v_i, v_j \in \mathcal{V}_{\mathrm{A11Y}}:\quad \Pi_{v_i}(u) \equiv \Pi_{v_j}(u) \equiv \Omega_{\mathrm{clean}}$$

### Theorem 5 — 64-Bit `IntPair` Projection & Static Risk Vector

For `AccessibilityManagerService.addClient()` return value $R_{\mathrm{raw}}$ and static package surface $p$:

$$R_{\mathrm{raw}} = \left(S_{\mathrm{state}} \cdot 2^{32}\right) + E_{\mathrm{events}}, \qquad M_{\mathrm{clear}} = 2^{0} + 2^{1} + 2^{3} + 2^{4} = \mathrm{0x1B}$$

$$S_{\mathrm{clean}} = \sum_{b \in \{0..31\} \setminus \{0,1,3,4\}} s_b \cdot 2^{b}, \qquad R_{\mathrm{out}} = \left(S_{\mathrm{clean}} \cdot 2^{32}\right) + E_{\mathrm{events}}$$

$$\mathcal{R}(p) = \min\left(100,\, 45 \cdot \mathbf{1}_{\mathrm{AZ}} + 25 \cdot \mathbf{1}_{\mathrm{ISO}} + \min\left(35,\, 20 \cdot |\mathcal{S}_{\mathrm{ELF}}|\right) + 10 \cdot \mathbf{1}_{\mathrm{QAP}} + 15 \cdot \mathbf{1}_{\mathrm{FIN}}\right)$$

## 5. Seven-Pillar Server-Side Matrix

<table>
  <thead>
    <tr>
      <th>Pillar</th>
      <th>Implementation</th>
      <th>AOSP Target Class &amp; Hooked Methods</th>
      <th>Safety Invariant &amp; Compatibility (API 29-36)</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>P1 — AppZygote</strong></td>
      <td><code>AppZygoteShield.java</code></td>
      <td><code>HostingRecord.byAppZygote</code>, <code>usesAppZygote</code>, <code>ProcessList.startProcessLocked</code> (all overloads), <code>startProcess</code>, <code>removeProcessNameLocked</code></td>
      <td>Dynamic parameter-type indexing per overload + <code>module.deoptimize()</code> + cross-thread <code>WeakHashMap</code> tracking + <code>IdentityGraph</code> population &amp; eviction. Zero SELinux timeouts in Downgrade mode.</td>
    </tr>
    <tr>
      <td><strong>P2 — Isolated Fold</strong></td>
      <td><code>IsolatedFoldShield.java</code></td>
      <td><code>ActiveServices.bringUpServiceLocked</code></td>
      <td>6-Gate Anti-Crash Filter: opt-in bit required, <code>FLAG_ISOLATED_PROCESS</code> set, <code>FLAG_EXTERNAL_SERVICE == 0</code>, <code>FLAG_USE_APP_ZYGOTE == 0</code>, <code>definingUid == appInfo.uid</code>, <code>instanceName == name</code>, plus explicit WebView/Chrome/GMS exclusion set.</td>
    </tr>
    <tr>
      <td><strong>P3 — Settings &amp; IPC</strong></td>
      <td><code>SettingsShield.java</code></td>
      <td><code>SettingsProvider.call</code> (3-arg &amp; 4-arg), <code>SettingsProvider.query</code> (attached via Dual-Checkpoint <code>onPackageLoaded</code> + <code>onPackageReady</code>)</td>
      <td>Returns single-key <code>Bundle("value")</code> omitting <code>_track_generation</code> (prevents <code>MemoryIntArray</code> cache poisoning), rewrites SQL cursors across full and single-column projections via <code>MatrixCursor</code>, and enforces Zero-Oracle IPC Gate.</td>
    </tr>
    <tr>
      <td><strong>P4 — USB &amp; ADB</strong></td>
      <td><code>UsbBroadcastShield.java</code></td>
      <td><code>ActivityManagerService.registerReceiverWithFeature</code>, <code>registerReceiver</code>, <code>UsbService.getCurrentFunctions</code>, <code>isFunctionEnabled</code></td>
      <td>Clones sticky <code>ACTION_USB_STATE</code> via <code>new Intent(sticky)</code> without mutating <code>mStickyBroadcasts</code>, sets <code>adb=false</code>, and masks <code>FUNCTION_ADB (1L)</code> while leaving USB MTP functional.</td>
    </tr>
    <tr>
      <td><strong>P5 — Accessibility</strong></td>
      <td><code>AccessibilityShield.java</code></td>
      <td><code>AccessibilityManagerService.addClient</code>, <code>getEnabledAccessibilityServiceList</code>, <code>getInstalledAccessibilityServiceList</code>, <code>getAccessibilityShortcutTargets</code>, <code>IAccessibilityManagerClient$Stub$Proxy.setState</code></td>
      <td>Clears <code>IntPair</code> flags <code>0x1B</code>, dynamically inspects <code>method.getReturnType()</code> for <code>ParceledListSlice</code> (Android 13-16) vs <code>List</code> (Android 10-12), and filters live push updates via <code>WeakHashMap&lt;IBinder, Integer&gt;</code>.</td>
    </tr>
    <tr>
      <td><strong>P6 — Package Delta</strong></td>
      <td><code>PackageChangeShield.java</code></td>
      <td><code>IPackageManagerBase</code>, <code>PackageManagerService$IPackageManagerImpl</code>, <code>PackageManagerService</code>, fallback <code>ComputerEngine.getChangedPackages</code></td>
      <td>Intercepts at Binder entrypoints before <code>clearCallingIdentity()</code>, returning <code>null</code> for guarded UIDs to close the post-boot package delta leak orthogonal to HMA-OSS.</td>
    </tr>
    <tr>
      <td><strong>P7 — Display Stealth</strong></td>
      <td><code>DisplayShield.java</code></td>
      <td><code>DisplayManagerService$BinderService.getDisplayIds</code>, <code>getDisplayInfo</code></td>
      <td>Restricts guarded callers to physical <code>Display.DEFAULT_DISPLAY (0)</code> and returns <code>null</code> for non-zero virtual/presentation display queries without touching <code>FLAG_SECURE</code>.</td>
    </tr>
  </tbody>
</table>

## 6. Polyglot Systems Internals

Each boundary from Linux 4.19 kernel namespaces and ARM64 memory ordering up to Binder AIDL contracts, ART hooks, SQL cursor sanitization, Gradle CI, and offline verification tooling is presented in its native language.

### 6.1 C — Linux 4.19 Non-SUSFS Mount Namespace & SELinux Context Split

~~~c
struct task_struct *zygote_fork_isolated(struct task_struct *zygote, uid_t iso_uid) {
    struct nsproxy *ns = copy_nsproxy(zygote->nsproxy);
    if (iso_uid >= 90000 && iso_uid <= 99999) {
        return create_isolated_task(ns, SELINUX_DOMAIN_ISOLATED_APP);
    }
    return create_untrusted_task(unmount_zygisk_mounts(ns), SELINUX_DOMAIN_UNTRUSTED_APP);
}
~~~

### 6.2 ARM64 Assembly — Kryo 260 Hardware-Atomic `IdentityGraph` (`LDAR`/`STLR`)

~~~asm
lsl     x10, x0, #32
and     x11, x1, #0xffffffff
orr     x10, x10, x11
add     x12, x2, x3, lsl #3
stlr    x10, [x12]

ldar    x13, [x12]
lsr     w14, x13, #32
cmp     w14, w0
b.ne    .Lprobe_next
and     w0, x13, #0xffffffff
ret
~~~

### 6.3 C++ — Native Binder IPC Boundary (`system_server` vs Clean Target `/proc/self/maps`)

~~~cpp
status_t BpBinder::transact(uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags) {
    binder_transaction_data tr{};
    tr.target.handle = mHandle;
    tr.code = code;
    tr.flags = flags;
    return IPCThreadState::self()->transact(mHandle, code, data, reply, flags);
}
~~~

> [!NOTE]
> Because `binderfs` stamps the kernel-verified `callingUid` into `binder_transaction_data.sender_euid` prior to waking the `system_server` worker thread, IsolPolicy resolves policy identity directly from `Binder.getCallingUid()` on the server side while the client process retains a 100% unmodified `/proc/self/maps` and ART method table.

### 6.4 AIDL — Intercepted AOSP System Binder Contracts

~~~aidl
interface IAccessibilityManager {
    long addClient(IAccessibilityManagerClient client, int userId);
    List<AccessibilityServiceInfo> getEnabledAccessibilityServiceList(int feedbackType, int userId);
    ParceledListSlice getInstalledAccessibilityServiceList(int userId);
}

interface IDisplayManager {
    int[] getDisplayIds(boolean includeDisabled);
    DisplayInfo getDisplayInfo(int displayId);
}

interface IUsbManager {
    long getCurrentFunctions();
    boolean isFunctionEnabled(String function);
}
~~~

### 6.5 Java — Lock-Free `IdentityGraph`, Wait-Free Seqlock & `IntPair` Projection

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

~~~java
void write(long nextSeq, long ts, int uid, int pil, int vec, int act) {
    seq = -1L;
    timestampMs = ts;
    appId = uid;
    pillar = pil;
    vector = vec;
    action = act;
    seq = nextSeq;
}
~~~

~~~java
long packed = (Long) res;
int state = (int) (packed >> 32);
int events = (int) packed;
int cleanState = state & ~A11Y_STATE_CLEAR_MASK;
if (cleanState != state) {
    telemetry.record(appId, PolicyFlags.PILLAR_A11Y, PolicyFlags.VEC_A11Y_CLIENT, PolicyFlags.ACT_SANITIZED);
    return (((long) cleanState) << 32) | (events & 0xFFFFFFFFL);
}
~~~

### 6.6 SQL — `SettingsProvider.query()` Direct Cursor Bypass Vectors

~~~sql
SELECT _id, name, value
FROM global
WHERE name IN ('development_settings_enabled', 'adb_enabled', 'adb_wifi_enabled');

SELECT value
FROM secure
WHERE name = 'accessibility_enabled';
~~~

> [!TIP]
> `SettingsShield.query()` handles both multi-column queries (`name` + `value`) and single-column projections (`projection = ["value"]`) by resolving target keys from URI path segments or `selectionArgs`, allocating a `MatrixCursor` strictly on demand when at least one row requires sanitization.

### 6.7 XML — Binary AXML Threat Surface & Adaptive Vector Icon

~~~xml
<application android:zygotePreloadName="com.target.security.Preload">
    <service
        android:name=".IsolatedProbeService"
        android:isolatedProcess="true"
        android:useAppZygote="true"
        android:externalService="false" />
</application>
~~~

~~~xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
~~~

### 6.8 Kotlin DSL — Gradle CI & `libxposed` API 102 Build Contract

~~~kotlin
android {
    namespace = "io.github.taro902.isolationpolicy"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.taro902.isolationpolicy"
        minSdk = 29
        targetSdk = 36
        versionCode = 4
        versionName = "2.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

tasks.matching { it.name.endsWith("AarMetadata") }.configureEach {
    enabled = false
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
}
~~~

### 6.9 Diff — Architectural Upgrade (`v2.0` to `v2.1.0`)

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

### 6.10 JSON — `v2.1.0` Policy Export Schema (Backward-Compatible with `v2.0`)

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

### 6.11 Properties — `META-INF/xposed/module.prop` & `gradle.properties`

~~~properties
minApiVersion=102
targetApiVersion=102
staticScope=true
exceptionMode=protective
autoHotReload=false
~~~

~~~properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.configuration-cache=false
android.useAndroidX=true
android.nonTransitiveRClass=true
~~~

### 6.12 YAML — Deterministic CI Release Pipeline Contract

~~~yaml
name: Build and Release IsolPolicy
on:
  push:
    branches: [ main ]
    tags: [ 'v*' ]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - run: ./gradlew assembleRelease assembleDebug
~~~

### 6.13 Python — Offline Policy JSON & Bitmask Verification Tool

~~~python
import json, sys

def decode_flags(mask: int) -> dict:
    az_names = {0: "OFF", 1: "AUTO", 2: "DOWNGRADE", 3: "HARD_DROP"}
    return {
        "hex": f"0x{mask & 0x7F:02X}",
        "az": az_names[mask & 0x03],
        "iso": bool(mask & 0x04),
        "dev": bool(mask & 0x08),
        "a11y": bool(mask & 0x10),
        "pkg": bool(mask & 0x20),
        "disp": bool(mask & 0x40),
    }

if __name__ == "__main__":
    data = json.load(open(sys.argv[1], encoding="utf-8"))
    for pkg, entry in sorted(data.get("policies", {}).items()):
        print(pkg, decode_flags(entry["flags"]))
~~~

### 6.14 Bash — Live Post-Boot Runtime Verification Commands

~~~bash
su -c "dumpsys package io.github.taro902.isolationpolicy | grep -E 'versionName|versionCode|userId'"
su -c "settings get global development_settings_enabled; settings get global adb_enabled"
su -c "dumpsys activity processes | grep -E 'AppZygote|isolated'"
~~~

## 7. Procedural Canvas Console

The control console (`io.github.taro902.isolationpolicy.ui.*`) eliminates Jetpack Compose, AppCompat, Material Components, and raster PNG assets completely. Every stepped 1-unit chamfer frame, 3D mechanical press bevel, 8x8/10x10 bitmask glyph, oscilloscope bar column, and threat meter renders directly through custom hardware-accelerated `Canvas` drawables with **zero heap allocations inside `onDraw()`**.

### Dual Precision Color Systems (`PixelTheme.java`)

| Token Role | `OBSIDIAN PHOSPHOR` (Dark) | `PAPER LCD` (Light Daylight) | Semantic Mapping |
| :--- | :---: | :---: | :--- |
| Canvas / Panel | `#0D0F12` / `#14181F` | `#E6E4DA` / `#F2F0E8` | Base viewport & elevated surface |
| Card / Active Card | `#1A2029` / `#212936` | `#F9F8F2` / `#ECEAD9` | Unprotected vs guarded row state |
| Bevel Light / Dark | `#323C4D` / `#06080B` | `#FFFFFF` / `#B5B09E` | 1-unit stepped mechanical 3D edge |
| Primary / Muted Ink | `#E6ECF5` / `#5D6B80` | `#14181F` / `#6B7280` | Tabular monospace (`tnum`) typography |
| Ok / Warn / Danger | `#4ADE80` / `#FBBF24` / `#F87171` | `#15803D` / `#B45309` / `#B91C1C` | Active shield / downgrade / drop or threat |
| Info / Focus | `#38BDF8` / `#A78BFA` | `#0369A1` / `#6D28D9` | Telemetry / ISO / DISP / A11Y highlight |

### Workspace Functional Surface

| Tab | Active Components | Frame-Time & Lifecycle Engineering |
| :--- | :--- | :--- |
| **`GUARD`** | Live daemon bar, 16-column `PixelScopeView`, 7-pillar counters, 80 ms debounced search, `ALL/THREATS/GUARDED/FINANCE/SYSTEM` filters, 6 one-tap granular chips (`AZ`, `ISO`, `DEV`, `A11Y`, `PKG`, `DISP`), `AppInspectorDialog`. | Partial `DiffUtil` payload rebind (`PAYLOAD_FLAGS`), 3-frame discrete scan-fill (90 ms), 4 MB bounded `PixelIconQuantizer` (`PIXEL-16` nearest-neighbor or `NATIVE`), flat View hierarchy (60 FPS on SDM660 / Adreno 512). |
| **`SCOPE`** | Live 7-bit Hook Health Matrix (`OK`/`FB`/`ER`), uptime/graph telemetry, per-pillar filter strip, `[LIVE/PAUSE]`, `[COPY LOG]`, `[PURGE]`, 64-event ring buffer viewer. | Polling executes solely during `onResume()` and cancels completely on `onPause()` (`0%` background CPU & battery). |
| **`RADAR`** | Offline `Self-Coherence Verifier` (`RUN TEST`: probes `XposedService`, Zero-Oracle IPC `PING`, active hook count, live host `DEV`/`ADB`/`A11Y` state) + Static Threat Surface index. | Zero `BlockedPrivateApi` reflection; streams Binary AXML via `AssetManager.openXmlResourceParser` and scans ZIP Central Directory headers without writing to eMMC. |
| **`VAULT`** | Batch presets (`BANK STRICT`, `WALLET/ALIPAY`, `AUDIT MAX`, `RESET ALL`), Clipboard & SAF `.json` Export/Import (`v2.0` compatible), dual theme switch, icon quantizer switch, polling interval, system app unlock. | Full console resident memory `< 28 MB`; Release APK footprint `419 KB` (R8 minified + resource shrunk). |

## 8. Modular Codebase Map

<details>
<summary><strong>Expand Complete 26-File Java &amp; Resource Tree</strong></summary>

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

</details>

## 9. Failure-Mode and Effects Analysis

| Failure Mode | Trigger Condition | Deterministic Mitigation in v2.1.0 |
| :--- | :--- | :--- |
| `system_server` Deadlock | Calling `PackageManager` inside Binder hooks while `mLock` is held | Zero `PackageManager` calls in all 7 shields; 100% lock-free `AtomicReference<PolicySnapshot>` + `AtomicLongArray` `IdentityGraph`. |
| WebView / Chrome Sandbox Crash | Stripping `FLAG_ISOLATED_PROCESS` on `BIND_EXTERNAL_SERVICE` | 6-gate guard in `IsolatedFoldShield` strictly ignores `FLAG_EXTERNAL_SERVICE`, `FLAG_USE_APP_ZYGOTE`, dynamic `bindIsolatedService`, and browser/WebView packages. |
| Silent `SettingsProvider` Miss | Hooking `SettingsProvider` only in `onSystemServerStarting` before APK load | Dual-Checkpoint `onPackageLoaded` + `onPackageReady` with idempotent `AtomicBoolean` CAS attachment guard. |
| Global `MemoryIntArray` Cache Poisoning | Returning `_track_generation` inside spoofed `SettingsProvider.call()` bundle | Constructs fresh single-key `Bundle("value")` omitting all generation tracking keys. |
| Accessibility `ClassCastException` | Returning `List` on Android 13-16 where `ParceledListSlice` is expected | Inspects `method.getReturnType()` at hook registration and constructs `ParceledListSlice.emptyList()` dynamically. |
| Sticky `USB_STATE` Corruption | Mutating AOSP `mStickyBroadcasts` Intent instance in place | Allocates `new Intent(sticky)` clone before setting `adb=false`, preserving system MTP state. |
| ClassLoader Memory Leak in `system_server` | Live hot-reloading of `system_server` classes on 4 GB RAM devices | `autoHotReload=false` in `module.prop`; live policy updates execute in 0 ms via lock-free `PolicySnapshot` replacement. |
| IPC Fingerprinting Oracle | Third-party detector probing `SettingsProvider.call("ISOLPOLICY_IPC_V21", ...)` | Strict `Binder.getCallingUid() == verifiedSelfAppId` check; unauthorized calls pass directly to `chain.proceed()`. |

## 10. Hardware Budget and Ecosystem Synergy

### Verified Reference Hardware Telemetry (`Asus Zenfone Max Pro M2 / X01BD`)

| Metric | Measured Value / Constraint | Engineering Rationale |
| :--- | :--- | :--- |
| Target Platform | SDM660 (Kryo 260), Adreno 512, 4 GB RAM, 64 GB eMMC 5.1 half-duplex | Strict memory and zero-write-amplification discipline required |
| OS &amp; Kernel | crDroid 10.1 (Android 14 / API 34), Kernel `4.19.304` non-GKI (No SUSFS / No KPM) | All stealth operates cleanly in userspace + `system_server` |
| Release APK Size | `419 KB` (R8 minified + resource shrunk + EC P-256 signed) | Zero Compose, zero AppCompat, zero Material, zero PNG bloat |
| `system_server` Overhead | `< 300 KB` RAM (`2048 B` IdentityGraph + 64 pre-allocated Seqlock slots) | Zero GC allocations on non-guarded Binder fast paths |
| `system_server` Disk I/O | `0 B/s` (all `Logger` calls stripped to zero bytecode when `DEBUG == false`) | Protects eMMC 5.1 latency and eliminates Logcat leakage |

### Orthogonal Stealth Stack Division of Responsibilities

| Layer | Component | Authoritative Scope | Overlap with IsolPolicy v2.1.0 |
| :--- | :--- | :--- | :--- |
| Zygisk &amp; Main Unmount | ReZygisk `v1.0.0` + Treat Wheel `v0.0.11` | Primary process (`UID 10xxx`) mount &amp; maps cleanup | Direct synergy with Pillar 2 (`ISO-FOLD`) |
| Package Visibility | HMA-OSS (`oss-175`) | App list hiding, activity launch guard, install source | Zero duplication; Pillar 6 closes `getChangedPackages` gap |
| Hardware Attestation | AlwaysStrong [Inject] `v1.0.4` | KeyMint / Keybox / VBMeta digest alignment | Zero overlap (never touches `/data/adb/tricky_store/`) |
| Overlay &amp; Network | mountify `v2.0.3` + PingPimp `v11.0` | Global OverlayFS &amp; network tuning | Zero global `umount`; WebUI binaries remain functional |
| Window Security | Enable Screenshot `v5.0.1` | `WindowManagerService` `FLAG_SECURE` | Zero overlap; Pillar 7 only filters virtual display IDs |

## 11. Build and Verification Protocol

> [!CAUTION]
> **Mandatory CI Guardrails:** Keep `org.gradle.configuration-cache=false` in `gradle.properties`, keep `AarMetadata` disabled in `app/build.gradle.kts` (`service:102.0.0` vs `compileSdk=36`), do not add `compileOnly(androidx.annotation:1.9.1)` alongside `recyclerview:1.4.0`, and never call `.edit()` on `getRemotePreferences()` from inside `system_server`.

### Post-Install & Post-Boot Verification Checklist

1. Install `IsolPolicy-v2.1.0-release.apk` (in-place upgrade over `v2.0` retains `io.github.taro902.isolationpolicy` storage and migrates legacy v2.0 policies automatically).
2. Confirm `io.github.taro902.isolationpolicy` is checked in the **HMA-OSS Blacklist Template**.
3. Confirm **IsolPolicy** is enabled in **LSPosed (Vector)** with scope set exclusively to **System Framework (`system`)**.
4. Reboot once to attach all 7 `system_server` shields (`autoHotReload=false`).
5. Launch IsolPolicy, open **`RADAR`**, and tap **`RUN TEST`** to verify:
   * `XposedService : OK (LSPosed API 102)`
   * `ZeroOracleIPC : AUTHENTICATED (v2.1.0)`
   * `Active Hooks  : 7/7`
6. Apply recommended presets in **`GUARD`** (applied atomically in 0 ms, no reboot required):
   * **E-Wallet / Alipay SDK (`id.dana`):** `WALLET/ALIPAY` (`0x3E` — `AZ:DOWN + ISO + DEV + A11Y + PKG`)
   * **Banking (`myBCA`, `BCA mobile`, `OCTO Mobile`, `BRImo`, `Livin'`):** `BANK STRICT` (`0x7A` — `AZ:DOWN + DEV + A11Y + PKG + DISP`)

## 12. License and Upstream Attribution

Distributed under the **Apache License 2.0**. Complete license terms and modification disclosures pursuant to Section 4(b) and Section 6 are retained in `LICENSE`, `NOTICE`, and packaged inside every compiled APK at `META-INF/LICENSE` and `META-INF/NOTICE`.

Derived from upstream [MhmRdd/Isolation-Policy](https://github.com/MhmRdd/Isolation-Policy) and independently re-architected under namespace `io.github.taro902.isolationpolicy` for `libxposed` API 102 with 7 coherent server-side shields, a hardware-atomic `IdentityGraph`, Zero-Oracle Seqlock telemetry, and a procedural Canvas console.
