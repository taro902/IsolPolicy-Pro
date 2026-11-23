<div align="center">

<h1>IsolPolicy</h1>

<p><strong>Server-Side AppZygote Isolation, Selective Isolated-Service Folding, and Cross-API Environment Coherence for Android 10-16</strong></p>

<p>
  <img src="https://img.shields.io/badge/version-2.1.1-4ADE80?style=flat-square&amp;labelColor=0B0E14" alt="Version 2.1.1" />
  <img src="https://img.shields.io/badge/language-Pure%20Java%2017-F5B742?style=flat-square&amp;labelColor=0B0E14" alt="Pure Java 17" />
  <img src="https://img.shields.io/badge/libxposed-API%20102-4CC2FF?style=flat-square&amp;labelColor=0B0E14" alt="libxposed API 102" />
  <img src="https://img.shields.io/badge/scope-system__server%20only-A78BFA?style=flat-square&amp;labelColor=0B0E14" alt="system_server only" />
  <img src="https://img.shields.io/badge/target%20injection-0%20bytes-4ADE80?style=flat-square&amp;labelColor=0B0E14" alt="0 bytes target injection" />
  <img src="https://img.shields.io/badge/classes-31%20Java%20files-94A6BF?style=flat-square&amp;labelColor=0B0E14" alt="31 Java files" />
  <img src="https://img.shields.io/badge/license-Apache--2.0-94A6BF?style=flat-square&amp;labelColor=0B0E14" alt="Apache 2.0" />
</p>

<p>
  <a href="#1-design-invariants">Invariants</a> &middot;
  <a href="#2-system-topology">Topology</a> &middot;
  <a href="#3-bitfields-and-formal-model">Bitfields</a> &middot;
  <a href="#4-seven-pillar-java-engine">7 Pillars</a> &middot;
  <a href="#5-binder-interception-flow">Interception Flow</a> &middot;
  <a href="#6-lifecycle-and-zero-oracle-ipc">Lifecycle &amp; IPC</a> &middot;
  <a href="#7-procedural-canvas-console">Pixel Console</a> &middot;
  <a href="#8-project-configuration-and-tree">Codebase</a> &middot;
  <a href="#9-failure-mode-analysis">FMEA</a> &middot;
  <a href="#10-verification-and-license">Verification</a>
</p>

</div>

<table>
  <thead>
    <tr>
      <th>Execution Boundary</th>
      <th>Memory &amp; Storage Budget</th>
      <th>Concurrency Model</th>
      <th>Live Verification (Android 14 / 4.19)</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>system_server</code> (<code>UID 1000</code>)<br/><code>scope: system</code><br/><code>autoHotReload=false</code></td>
      <td><code>&lt; 300 KB</code> in <code>system_server</code><br/><code>2048 B</code> <code>IdentityGraph</code><br/><code>0 B/s</code> server disk I/O</td>
      <td>Lock-free <code>AtomicReference</code><br/>0 <code>PackageManager</code> locks<br/>Wait-free 64-slot Seqlock</td>
      <td><code>7/7</code> active hooks<br/><code>ZeroOracleIPC</code> authenticated<br/><code>0 bytes</code> injected into target apps</td>
    </tr>
  </tbody>
</table>

> [!IMPORTANT]
> **Pure Java, Server-Side Only:** IsolPolicy v2.1.1 is written entirely in Java 17 on top of `io.github.libxposed:api:102.0.0` and `service:102.0.0`. Every hook executes exclusively inside `system_server`. Non-`system_server` processes call `detach()` immediately in `onModuleLoaded()`, leaving target application processes unmodified.

## 1. Design Invariants

Financial applications and runtime audit tools on Android 10-16 inspect environment integrity through multiple independent Binder services. On non-GKI kernels lacking kernel-level mount hiding (such as Linux `4.19` without SUSFS), four specific gaps break conventional hiding:

1. **AppZygote & Isolated Mount-Namespace Escape:** Userspace Zygisk unmount tools clean the primary application process (`UID 10xxx`). Services declared with `android:useAppZygote="true"` (`UID 90000-98999`) or internal `android:isolatedProcess="true"` (`UID 99000-99999`) fork outside that unmount list and can inspect raw mounts.
2. **Cross-API Contradictions:** Returning `"0"` for `Settings.Global.adb_enabled` in `SettingsProvider.call()` alone leaves conflicting values in direct `SettingsProvider.query()` cursors, sticky `android.hardware.usb.action.USB_STATE` broadcasts (`adb=true`), and `UsbService.getCurrentFunctions()`. Similarly, spoofing `Settings.Secure.accessibility_enabled` fails if `AccessibilityManagerService.addClient()` still returns active accessibility bits in its 64-bit `IntPair` return value or pushes live updates through `IAccessibilityManagerClient.setState()`.
3. **Cache Poisoning & `SettingsProvider` Load Order:** Returning AOSP `_track_generation` keys in spoofed `SettingsProvider` bundles contaminates shared `MemoryIntArray` caches across processes. In addition, `SettingsProvider.apk` loads into `system_server` after `onSystemServerStarting()`, requiring idempotent dual-checkpoint attachment across `onPackageLoaded()` and `onPackageReady()`.
4. **Telemetry Oracles & Post-Boot Package Deltas:** Custom IPC endpoints on system providers become detectable if unauthorized callers receive non-stock errors, and `PackageManager.getChangedPackages()` exposes post-boot installs outside standard package-list filtering.

### Version Evolution (`v1.1` -> `v2.0` -> `v2.1.1`)

<table>
  <thead>
    <tr>
      <th>Component</th>
      <th>Upstream v1.1 (API 82)</th>
      <th>IsolPolicy v2.0 (API 102)</th>
      <th>IsolPolicy v2.1.1 (API 102)</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>Codebase</strong></td>
      <td>Legacy single-file hook</td>
      <td>Monolithic <code>BindHook</code></td>
      <td>31 modular Java classes across 5 acyclic packages</td>
    </tr>
    <tr>
      <td><strong>AppZygote &amp; Isolated</strong></td>
      <td>Hard abort (SELinux timeout)</td>
      <td>4-layer AppZygote Silent Downgrade</td>
      <td>4-layer AppZygote + 6-gate opt-in Isolated Folding</td>
    </tr>
    <tr>
      <td><strong>UID Resolution</strong></td>
      <td>Package scan</td>
      <td><code>WeakHashMap</code> per record</td>
      <td>Lock-free <code>2048 B</code> <code>AtomicLongArray</code> Reverse <code>IdentityGraph</code></td>
    </tr>
    <tr>
      <td><strong>Environment Coherence</strong></td>
      <td>None</td>
      <td>None</td>
      <td><code>Settings.call+query</code>, sticky <code>USB_STATE</code>, <code>UsbService</code>, <code>AMS.addClient+Push</code>, <code>getChangedPackages</code>, <code>DisplayManager</code></td>
    </tr>
    <tr>
      <td><strong>Telemetry &amp; IPC</strong></td>
      <td>Logcat</td>
      <td>Silent Release</td>
      <td>64-slot Seqlock ring buffer + Zero-Oracle IPC + 7-bit health register</td>
    </tr>
    <tr>
      <td><strong>Static Radar</strong></td>
      <td>None</td>
      <td>Binary AXML AppZygote check</td>
      <td>Binary AXML + zero-extract ZIP ELF classifier + <code>SANDBOX</code> separation</td>
    </tr>
    <tr>
      <td><strong>Console UI</strong></td>
      <td>AppCompat / Material</td>
      <td>Standard list UI (~305 KB)</td>
      <td>Procedural <code>Canvas</code> UI: 5x7 bitmap font, 3D keycaps, Bayer dithering, 4 palettes, zero Compose/AppCompat/PNG</td>
    </tr>
  </tbody>
</table>

## 2. System Topology

~~~mermaid
flowchart TB
    classDef console fill:#221A3B,stroke:#A78BFA,color:#E6EEF8,stroke-width:1.5px
    classDef core fill:#10283B,stroke:#4CC2FF,color:#E6EEF8,stroke-width:1.5px
    classDef shield fill:#112D21,stroke:#4AE38A,color:#E6EEF8,stroke-width:1.5px
    classDef stock fill:#30240E,stroke:#F5B742,color:#E6EEF8,stroke-width:1.5px

    subgraph UI_PROC["Console App Process - io.github.taro902.isolationpolicy"]
        UI["MainActivity Procedural Canvas Console"]:::console
        RAD["ManifestRadar Binary AXML and ZIP ELF Scanner"]:::console
        PREFS[("XposedService RemotePreferences")]:::console
    end

    subgraph SYS_PROC["system_server UID 1000 - Zero Target App Injection"]
        direction TB
        ENG["PolicyEngine AtomicReference PolicySnapshot"]:::core
        GRP[("IdentityGraph 256-Slot AtomicLongArray 2048 B")]:::core
        HUB[("TelemetryHub 64-Slot Seqlock and 16-Bit Health")]:::core
        GATE{"Zero-Oracle IPC Gate"}:::core

        P1["P1 AppZygoteShield"]:::shield
        P2["P2 IsolatedFoldShield"]:::shield
        P3["P3 SettingsShield Call and Query"]:::shield
        P4["P4 UsbBroadcastShield"]:::shield
        P5["P5 AccessibilityShield"]:::shield
        P6["P6 PackageChangeShield"]:::shield
        P7["P7 DisplayShield"]:::shield
    end

    APP["Guarded Target Process UID 10xxx - Clean Memory"]:::shield
    AOSP["Stock AOSP chain.proceed"]:::stock

    RAD -->|Risk Score and Preset| UI
    UI -->|Write 32-Bit Flags and AppId Map| PREFS
    PREFS -->|Read-Only Listener 0ms Swap| ENG

    P1 -->|Pack isoUid to ownerAppId| GRP
    GRP -->|O1 Owner Lookup| ENG
    ENG --> P1
    ENG --> P2
    ENG --> P3
    ENG --> P4
    ENG --> P5
    ENG --> P6
    ENG --> P7

    P1 --> HUB
    P2 --> HUB
    P3 --> HUB
    P4 --> HUB
    P5 --> HUB
    P6 --> HUB
    P7 --> HUB

    P3 --> GATE
    GATE -->|Verified selfAppId| HUB
    HUB -->|Compact Primitive Bundle| UI
    GATE -->|Any Other UID| AOSP
    APP -->|Standard Binder IPC| SYS_PROC
~~~

## 3. Bitfields and Formal Model

### 3.1 32-Bit `PolicyFlags` Encoding

Each package's 7-pillar configuration fits inside a single primitive `int` stored in an immutable copy-on-write `SparseIntArray`:

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

| Preset | Hex | Decimal | Active Shields |
| :--- | :---: | :---: | :--- |
| `PRESET_NONE` | `0x00` | `0` | Pass-through |
| `WALLET CORE` | `0x3A` | `58` | `AZ:DOWN + DEV + A11Y + PKG` |
| `PRESET_WALLET_ALIPAY` | `0x3E` | `62` | `AZ:DOWN + ISO + DEV + A11Y + PKG` |
| `PRESET_BANKING_STRICT` | `0x7A` | `122` | `AZ:DOWN + DEV + A11Y + PKG + DISP` |
| `PRESET_AUDIT_MAX` | `0x7E` | `126` | `AZ:DOWN + ISO + DEV + A11Y + PKG + DISP` |

### 3.2 64-Bit `IdentityGraph`, `IntPair` & 16-Bit `HookHealthMask`

<table>
  <thead>
    <tr>
      <th>Structure</th>
      <th>Upper Bits</th>
      <th>Lower Bits</th>
      <th>Invariant</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>IdentityGraph</code> (<code>AtomicLongArray[256]</code>)</td>
      <td><code>63..32</code>: <code>isoUid</code> (<code>90000..99999</code>)</td>
      <td><code>31..0</code>: <code>ownerAppId</code> (<code>10000..19999</code>)</td>
      <td>Single 64-bit atomic word; <code>2048 B</code> fixed RAM; bounded 8-step probe</td>
    </tr>
    <tr>
      <td><code>AMS.addClient()</code> (<code>long IntPair</code>)</td>
      <td><code>63..32</code>: <code>stateFlags</code> (masked <code>~0x1B</code>)</td>
      <td><code>31..0</code>: <code>relevantEventTypes</code></td>
      <td>Clears A11y state bits while preserving non-A11y event flags</td>
    </tr>
    <tr>
      <td><code>HookHealthMask</code> (<code>AtomicInteger</code>)</td>
      <td><code>13..0</code>: 7 pillars &times; 2 bits</td>
      <td><code>00</code>=<code>--</code>, <code>01</code>=<code>OK</code>, <code>10</code>=<code>FB</code>, <code>11</code>=<code>ER</code></td>
      <td>Lock-free CAS updates reported directly to <code>SCOPE</code> and <code>RADAR</code></td>
    </tr>
  </tbody>
</table>

### 3.3 Compact Mathematical Specification

Policy bitmask $\Phi_u \in [0, 127]$ and Fibonacci multiplicative index $h(u)$ over $256$ `IdentityGraph` slots:

$$\Phi_u = m_{\mathrm{AZ}} + 2^{2} f_{\mathrm{ISO}} + 2^{3} f_{\mathrm{DEV}} + 2^{4} f_{\mathrm{A11Y}} + 2^{5} f_{\mathrm{PKG}} + 2^{6} f_{\mathrm{DISP}}$$

$$m_{\mathrm{AZ}} \in \{0, 1, 2, 3\} \equiv \{\text{OFF}, \text{AUTO}, \text{DOWNGRADE}, \text{HARD-DROP}\}$$

$$a(u) = u \bmod 100000, \qquad w(u) = (u \cdot \mathrm{0x9E3779B9}) \bmod 2^{32}$$

$$h(u) = \left(w(u) \oplus \left\lfloor \frac{w(u)}{2^{16}} \right\rfloor\right) \bmod 256, \qquad \mathrm{Mem}(\mathcal{G}) = 256 \times 8\,\mathrm{B} = 2048\,\mathrm{B}$$

Wait-free Seqlock acceptance rule for ring slot $j = q \bmod 64$ and 64-bit `IntPair` projection ($M_{\mathrm{clear}} = \mathrm{0x1B}$):

$$\mathrm{Valid}(j) \iff \left(s_{1} = s_{2}\right) \land \left(s_{1} \gt 0\right)$$

$$R_{\mathrm{out}} = \left(\left(S_{\mathrm{state}} \setminus \{2^{0}, 2^{1}, 2^{3}, 2^{4}\}\right) \cdot 2^{32}\right) + E_{\mathrm{events}}$$

Deterministic static risk score $\mathcal{R}(p) \in [0, 100]$ for non-sandbox applications:

$$\mathcal{R}(p) = \min\left(100,\, 45 \cdot \mathbf{1}_{\mathrm{AZ}} + 25 \cdot \mathbf{1}_{\mathrm{ISO}} + \min\left(35,\, 20 \cdot |\mathcal{S}_{\mathrm{ELF}}|\right) + 10 \cdot \mathbf{1}_{\mathrm{QAP}} + 15 \cdot \mathbf{1}_{\mathrm{FIN}}\right)$$

## 4. Seven-Pillar Java Engine

<table>
  <thead>
    <tr>
      <th>Pillar</th>
      <th>Java Class</th>
      <th>Hooked AOSP Targets</th>
      <th>Core Behavior &amp; Guardrails</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><strong>P1 — AppZygote</strong></td>
      <td><code>AppZygoteShield.java</code></td>
      <td><code>HostingRecord.byAppZygote</code>, <code>usesAppZygote</code>, <code>ProcessList.startProcessLocked</code>, <code>startProcess</code>, <code>removeProcessNameLocked</code></td>
      <td>4-layer cross-thread Silent Downgrade (<code>mHostingZygote = 0</code>) or Hard Drop + <code>module.deoptimize()</code> + live <code>IdentityGraph</code> registration and eviction.</td>
    </tr>
    <tr>
      <td><strong>P2 — Isolated Fold</strong></td>
      <td><code>IsolatedFoldShield.java</code></td>
      <td><code>ActiveServices.bringUpServiceLocked</code></td>
      <td>Opt-in folding of internal <code>FLAG_ISOLATED_PROCESS</code> helpers into clean <code>UID 10xxx</code>. Enforces 6 strict guards blocking <code>FLAG_EXTERNAL_SERVICE</code>, <code>FLAG_USE_APP_ZYGOTE</code>, dynamic instances, and WebView/Chrome/GMS packages.</td>
    </tr>
    <tr>
      <td><strong>P3 — Settings &amp; IPC</strong></td>
      <td><code>SettingsShield.java</code></td>
      <td><code>SettingsProvider.call</code> (3/4-arg), <code>SettingsProvider.query</code></td>
      <td>Attached idempotently via <code>onPackageLoaded</code> + <code>onPackageReady</code>. Returns single-key <code>Bundle("value")</code> without <code>_track_generation</code>, sanitizes full and single-column SQL cursors via <code>MatrixCursor</code>, and hosts the Zero-Oracle IPC Gate.</td>
    </tr>
    <tr>
      <td><strong>P4 — USB &amp; ADB</strong></td>
      <td><code>UsbBroadcastShield.java</code></td>
      <td><code>ActivityManagerService.registerReceiverWithFeature</code>, <code>UsbService.getCurrentFunctions</code>, <code>isFunctionEnabled</code></td>
      <td>Clones sticky <code>ACTION_USB_STATE</code> via <code>new Intent(sticky)</code> with <code>adb=false</code> (never mutates system sticky intents) and strips bit 0 (<code>FUNCTION_ADB = 1L</code>) while preserving USB MTP and Tethering.</td>
    </tr>
    <tr>
      <td><strong>P5 — Accessibility</strong></td>
      <td><code>AccessibilityShield.java</code></td>
      <td><code>AccessibilityManagerService.addClient</code>, <code>getEnabledAccessibilityServiceList</code>, <code>getInstalledAccessibilityServiceList</code>, <code>IAccessibilityManagerClient$Stub$Proxy.setState</code></td>
      <td>Clears <code>IntPair</code> mask <code>0x1B</code>, dynamically matches <code>ParceledListSlice</code> (Android 13-16) vs <code>List</code> (Android 10-12), and filters live push callbacks via <code>WeakHashMap&lt;IBinder, Integer&gt;</code>.</td>
    </tr>
    <tr>
      <td><strong>P6 — Package Delta</strong></td>
      <td><code>PackageChangeShield.java</code></td>
      <td><code>IPackageManagerBase</code>, <code>PackageManagerService$IPackageManagerImpl</code>, <code>PackageManagerService</code>, fallback <code>ComputerEngine</code></td>
      <td>Returns <code>null</code> for <code>getChangedPackages()</code> at Binder entrypoints before <code>clearCallingIdentity()</code>, orthogonal to HMA-OSS app-list hiding.</td>
    </tr>
    <tr>
      <td><strong>P7 — Display Stealth</strong></td>
      <td><code>DisplayShield.java</code></td>
      <td><code>DisplayManagerService$BinderService.getDisplayIds</code>, <code>getDisplayInfo</code></td>
      <td>Projects display list to physical <code>Display.DEFAULT_DISPLAY (0)</code> and returns <code>null</code> for non-zero virtual/presentation display info queries without touching <code>FLAG_SECURE</code>.</td>
    </tr>
  </tbody>
</table>

### Key Java Implementation Excerpts

#### Lock-Free 64-Bit `IdentityGraph` (`hook/PolicyEngine.java`)

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

#### Wait-Free Seqlock Telemetry Ring (`hook/TelemetryHub.java`)

~~~java
public void record(int appId, int pillar, int vector, int action) {
    pillarCounts[0].incrementAndGet();
    if (pillar >= 1 && pillar <= 7) {
        pillarCounts[pillar].incrementAndGet();
    }
    long seq = writeSeq.getAndIncrement();
    EventSlot slot = ring[(int) (seq & (CAPACITY - 1))];
    slot.write(seq + 1L, System.currentTimeMillis(), appId, pillar, vector, action);
}
~~~

#### Six-Gate WebView-Safe Isolated Folding (`hook/IsolatedFoldShield.java`)

~~~java
int sFlags = sInfo.flags;
if ((sFlags & FLAG_ISOLATED) == 0) return;
if ((sFlags & FLAG_EXTERNAL) != 0 || (sFlags & FLAG_APP_ZYGOTE) != 0) return;
if (fIsolatedProc != null && fIsolatedProc.get(sr) != null) return;
if (aInfo.uid < 10000 || aInfo.packageName == null) return;
if (PROTECTED_SANDBOX_PKGS.contains(aInfo.packageName)) return;
if (!engine.hasFlagForUidOrPackage(aInfo.uid, aInfo.packageName, PolicyFlags.FLAG_ISO_FOLD)) return;

sInfo.flags = sFlags & ~FLAG_ISOLATED;
~~~

#### Dual-Path `SettingsProvider` & Zero-Oracle IPC Gate (`hook/SettingsShield.java`)

~~~java
if (PolicyFlags.IPC_METHOD.equals(method)) {
    if (!engine.isVerifiedSelfUid(callingUid)) {
        return chain.proceed();
    }
    return handleAuthenticatedIpc(cmd, engine, telemetry);
}

String spoofed = lookupSpoofValue(method, key, flags);
if (spoofed != null) {
    telemetry.record(appId, PolicyFlags.PILLAR_SETTINGS, vec, PolicyFlags.ACT_SPOOFED);
    Bundle out = new Bundle(1);
    out.putString("value", SENTINEL_NULL.equals(spoofed) ? null : spoofed);
    return out;
}
~~~

#### Coherent `IntPair` Projection (`hook/AccessibilityShield.java`)

~~~java
long packed = (Long) res;
int state = (int) (packed >> 32);
int events = (int) packed;
int cleanState = state & ~A11Y_STATE_CLEAR_MASK;
if (cleanState != state) {
    int appId = engine.resolveEffectiveAppId(callingUid);
    telemetry.record(appId, PolicyFlags.PILLAR_A11Y, PolicyFlags.VEC_A11Y_CLIENT, PolicyFlags.ACT_SANITIZED);
    return (((long) cleanState) << 32) | (events & 0xFFFFFFFFL);
}
~~~

## 5. Binder Interception Flow

~~~mermaid
sequenceDiagram
    participant App as Guarded App
    participant AM as ActivityManager and ActiveServices
    participant Worker as ProcessStart Worker
    participant Eng as PolicyEngine and IdentityGraph
    participant Svc as Settings AMS Usb DMS PMS
    participant Hub as TelemetryHub
    participant UI as IsolPolicy Console

    App->>AM: bindService with useAppZygote or isolatedProcess
    AM->>Eng: Lookup 32-bit PolicyFlags in O1
    alt AppZygote with AZ_DOWNGRADE
        AM->>AM: Set mHostingZygote to REGULAR_ZYGOTE in WeakHashMap
        AM->>Worker: Dispatch startProcess cross-thread
        Worker->>Hub: Record PILLAR_APP_ZYGOTE ACT_DOWNGRADED
    else Internal Isolated with FLAG_ISO_FOLD
        AM->>AM: Validate 6 WebView guards then clear FLAG_ISOLATED_PROCESS
        AM->>Hub: Record PILLAR_ISO_FOLD ACT_FOLDED
    else Isolated Process Permitted
        AM->>Eng: Atomic pack isoUid and ownerAppId into AtomicLongArray
    end

    App->>Svc: Settings call and SQL query cursor
    Svc->>Eng: Resolve ownerAppId via Snapshot or IdentityGraph
    Svc-->>App: Single-key Bundle without _track_generation and MatrixCursor
    Svc->>Hub: Record PILLAR_SETTINGS ACT_SPOOFED

    App->>Svc: registerReceiverWithFeature USB_STATE and UsbService
    Svc-->>App: Cloned sticky Intent adb false and cleared FUNCTION_ADB
    Svc->>Hub: Record PILLAR_USB ACT_SANITIZED

    App->>Svc: AMS addClient DMS getDisplayIds PMS getChangedPackages
    Svc-->>App: Masked IntPair display 0 only and null delta
    Svc->>Hub: Record A11Y DISPLAY PKG

    App->>Svc: Probe ISOLPOLICY_IPC_V21 from unauthorized UID
    Svc-->>App: Stock AOSP chain.proceed response with zero oracle

    UI->>Svc: Call ISOLPOLICY_IPC_V21 from verified selfAppId
    Svc->>Hub: Tear-free seqlock read of 64-slot ring buffer
    Svc-->>UI: Counts uptime graphSize events and 16-bit health mask
~~~

## 6. Lifecycle and Zero-Oracle IPC

~~~mermaid
stateDiagram-v2
    [*] --> ModuleLoaded: Zygote loads APK
    ModuleLoaded --> Detached: Non system_server calls detach
    Detached --> [*]

    ModuleLoaded --> EngineInit: system_server binds RemotePreferences
    EngineInit --> SystemServerBoot: onSystemServerStarting receives sysCl
    SystemServerBoot --> CoreAttached: Install Pillars 1 2 4 5 6 7 and probe P3

    CoreAttached --> CheckpointLoaded: onPackageLoaded settings provider
    CoreAttached --> CheckpointReady: onPackageReady settings provider

    CheckpointLoaded --> Operational: AtomicBoolean CAS false to true
    CheckpointReady --> Operational: Idempotent guard verified

    Operational --> SnapshotSwap: User toggles keycap in console
    SnapshotSwap --> Operational: AtomicReference replaced in 0ms
    Operational --> GraphEvict: Isolated process terminated
    GraphEvict --> Operational: AtomicLongArray slot cleared via CAS
~~~

| Caller (`Binder.getCallingUid()`) | IPC Command | Response | Oracle Surface |
| :--- | :---: | :--- | :--- |
| Any UID != `verifiedSelfAppId` | Any | `chain.proceed()` (Stock AOSP) | None (identical to unpatched AOSP) |
| `verifiedSelfAppId` | `SNAP` | Primitive arrays (`counts`, `health`, `uptime`, `graph`, `ev_*`) | Authenticated live telemetry snapshot |
| `verifiedSelfAppId` | `PING` | `ok=true`, `health`, `api`, `ver="2.1.1"` | Used by `RADAR` coherence check |
| `verifiedSelfAppId` | `PURGE` | `ok=true` | Resets counters and clears ring buffer |

## 7. Procedural Canvas Console

All UI chrome in `v2.1.1` is rendered directly in Java via `android.graphics.Canvas` with zero Jetpack Compose, zero AppCompat, zero Material Components, zero `.ttf` font files, and zero raster `.png` assets. Every `Paint`, `Rect`, and `BitmapShader` is pre-allocated outside `onDraw()`.

### Rendering Primitives (`ui/pixel/`)

* **`PixelFont.java` & `PixelTextView.java`:** Procedural 5x7 integer-scaled bitmap raster font (52 ASCII glyphs, `setAntiAlias(false)`) used for all headers, keycaps, badges, counters, and dock labels.
* **`PixelChrome.java`:** 9-slice stepped pixel borders with explicit corner-connector dots, 1-unit hard drop shadows, 4x4 Bayer ordered dithering `BitmapShader`, and left-edge guarded status rails.
* **`PixelKeycapView.java`:** Mechanical 3D keycap buttons with a 2-unit solid bottom lip that physically depresses downward on touch, integrated 4x4 pixel LEDs (or 4-block palette preview swatches), and 3-frame scan-fill transitions.
* **`PixelSpriteAtlas.java`:** Palette-indexed 2-bit dual-tone sprites (`14x14` shield crest, `12x12` dock icons for Shield, CRT Scope, Radar Dish, Vault Safe, `9x9` search lens, `7x7` close glyph).
* **`PixelCounterStrip.java` & `PixelScopeView.java`:** 7 clickable pillar counter tiles (`AZ` through `DISP` — tapping any tile jumps directly to `SCOPE` filtered to that pillar) paired with an auto-seeded 16x8 spectrum oscilloscope with floating peak-hold caps.
* **`PixelDockBar.java`:** Physical cartridge-style bottom dock where the active tab pops upward by 2 pixel units above a Bayer-dithered inactive rail.
* **`ManifestRadar.java`:** Offline Binary AXML (`AssetManager.openXmlResourceParser`) + zero-extract ZIP Central Directory ELF classifier (`ALIPAY`, `PROMON`, `VKEY`, `APPSEALING`, `THREATMETRIX`, `DEXPROT`, `PACKER`, `HARDENED`). Separates browsers and chat/media apps cleanly into `SANDBOX` (`SAFE OFF`) so `AUTO BANK` and batch presets never touch Chrome, Brave, Firefox, WhatsApp, Telegram, Instagram, or Facebook.

### Four Curated Hardware Palettes (`PixelPalette.java`)

| Palette | Mode | Canvas / Panel / Card | Semantic Accents |
| :--- | :---: | :---: | :--- |
| **`CRT PHOSPHOR`** | Dark Default | `#0B0E14` / `#131822` / `#1A2130` | `#4AE38A` · `#4CC2FF` · `#F5B742` · `#F25C6B` · `#A78BFA` |
| **`DMG PAPER`** | Daylight LCD | `#E7E3D4` / `#F2EFE4` / `#FAF8F0` | `#1A6B3C` · `#1E56A0` · `#A15C13` · `#A62C2B` · `#543C8A` |
| **`AMBER CRT`** | Monochrome Dark | `#110D08` / `#1B140C` / `#231A10` | `#FFB000` · `#FFD166` · `#FF922B` · `#FF6B35` |
| **`POCKET OLIVE`** | 4-Shade Handheld | `#CADC9F` / `#BDCE94` / `#D4E5AB` | `#0F380F` · `#306230` · `#8BAC0F` · `#9BBC0F` |

## 8. Project Configuration and Tree

### Gradle Build Contract (`app/build.gradle.kts`)

~~~kotlin
android {
    namespace = "io.github.taro902.isolationpolicy"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.taro902.isolationpolicy"
        minSdk = 29
        targetSdk = 36
        versionCode = 5
        versionName = "2.1.1"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASS")
                keyAlias = System.getenv("KEY_ALIAS") ?: "isolationpolicy"
                keyPassword = System.getenv("KEYPASS")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
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

### Xposed API 102 Descriptor (`META-INF/xposed/module.prop`) & ProGuard (`app/proguard-rules.pro`)

~~~properties
minApiVersion=102
targetApiVersion=102
staticScope=true
exceptionMode=protective
autoHotReload=false
~~~

~~~text
-dontwarn io.github.libxposed.**
-keep class io.github.taro902.isolationpolicy.hook.Entry { *; }
-keep class io.github.libxposed.service.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
~~~

### Policy Export Format (`v2.1.1` — Backward-Compatible with `v2.0`)

~~~json
{
  "schema": "isolpolicy",
  "version": "2.1.1",
  "defaultAzMode": 0,
  "policies": {
    "id.dana": {
      "flags": 58,
      "az": 2,
      "iso": false,
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

### Manifest & Adaptive Vector Launcher (`app/src/main/AndroidManifest.xml`)

~~~xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
    <application
        android:allowBackup="false"
        android:description="@string/module_desc"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="false"
        android:theme="@style/Theme.IsolPolicy.Pixel">
        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:launchMode="singleTask">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
~~~

### Modular 31-Class Java Layout

| Package | Files | Runtime Process | Responsibility |
| :--- | :---: | :--- | :--- |
| `core/` | `4` | Shared | Bitmask contract, immutable snapshot, JSON/wire codec, zero-bytecode release logger |
| `hook/` | `10` | `system_server` ONLY | `Entry`, `PolicyEngine`, `TelemetryHub`, and 7 server-side shields |
| `radar/` | `2` | Console App | Binary AXML + ZIP ELF threat profiler and Zero-Oracle IPC client |
| `ui/pixel/` | `12` | Console App | Procedural bitmap font, 9-slice chrome, Bayer dither, 3D keycaps, sprites, oscilloscope, dock |
| `ui/` | `3` | Console App | `MainActivity` 4-tab controller, `AppPolicyAdapter`, `AppInspectorDialog` |

<details>
<summary><strong>Expand Complete 31-File Java &amp; Resource Tree</strong></summary>

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
│           ├── PixelChrome.java
│           ├── PixelCounterStrip.java
│           ├── PixelDockBar.java
│           ├── PixelFont.java
│           ├── PixelIconQuantizer.java
│           ├── PixelKeycapView.java
│           ├── PixelPalette.java
│           ├── PixelScopeView.java
│           ├── PixelSpriteAtlas.java
│           ├── PixelTagBarView.java
│           ├── PixelTextView.java
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

## 9. Failure-Mode Analysis

| Failure Mode | Trigger | Mitigation in v2.1.1 |
| :--- | :--- | :--- |
| `system_server` Deadlock | Calling `PackageManager` inside Binder hooks | Zero `PackageManager` calls in all 7 shields; lock-free `AtomicReference` + `AtomicLongArray`. |
| WebView / Chrome Sandbox Crash | Folding `FLAG_EXTERNAL_SERVICE` or browser sandboxes | 6-gate guard in `IsolatedFoldShield` + `SANDBOX` classification in `ManifestRadar`. |
| Silent `SettingsProvider` Miss | Hooking only in `onSystemServerStarting` | Dual-Checkpoint `onPackageLoaded` + `onPackageReady` with idempotent `AtomicBoolean` CAS. |
| `MemoryIntArray` Cache Poisoning | Returning `_track_generation` inside spoofed bundles | Fresh single-key `Bundle("value")` omitting generation metadata. |
| Direct SQLite Cursor Bypass | Calling `SettingsProvider.query()` with single `"value"` column | Infers queried key from URI segments or `selectionArgs` before building `MatrixCursor`. |
| Accessibility `ClassCastException` | Returning `List` where Android 13-16 expects `ParceledListSlice` | Dynamic `method.getReturnType()` inspection at hook registration. |
| Sticky `USB_STATE` Corruption | Mutating system `mStickyBroadcasts` intent in place | Allocates `new Intent(sticky)` clone before setting `adb=false`, preserving MTP and Tethering. |
| IPC Fingerprinting Oracle | Unauthorized app calling `ISOLPOLICY_IPC_V21` | Verifies `Binder.getCallingUid() == verifiedSelfAppId`; others pass directly to `chain.proceed()`. |

## 10. Verification and License

### Orthogonal Ecosystem Compatibility

| Layer | Component | Interaction with IsolPolicy v2.1.1 |
| :--- | :--- | :--- |
| Zygisk &amp; Primary Unmount | ReZygisk + Treat Wheel | Direct synergy with Pillar 1 (`AZ`) and Pillar 2 (`ISO`) |
| Package List Hiding | HMA-OSS (`oss-175`) | Orthogonal; Pillar 6 adds `getChangedPackages` filtering |
| Attestation &amp; VBMeta | AlwaysStrong [Inject] | Zero overlap (does not access `/data/adb/tricky_store/`) |
| Overlay &amp; Network | mountify + PingPimp | Zero global `umount`, zero network permissions, zero sysctl writes |
| Window Security | Enable Screenshot | Zero overlap; Pillar 7 filters virtual display IDs without touching `FLAG_SECURE` |

### Post-Boot Verification Checklist

1. Install signed `IsolPolicy-v2.1.1-release.apk` (verified by `apksigner verify` in CI with EC P-256 `v1/v2/v3`).
2. Check `io.github.taro902.isolationpolicy` in **HMA-OSS Blacklist Template**.
3. Enable **IsolPolicy** in **LSPosed (Vector)** with scope set exclusively to **System Framework (`system`)**.
4. Reboot once, open **`RADAR`**, and tap **`RUN CHECK`** to verify `SERVICE: LSPosed API 102`, `IPC GATE: AUTHENTICATED v2.1.1`, and `HOOKS: 7 OF 7 ATTACHED`.
5. Configure targets in **`GUARD`** (live in `0 ms`, no reboot required):
   * **E-Wallet (`id.dana`):** `AZ:DOWN` + `DEV` + `A11Y` + `PKG` (`ISO: OFF`)
   * **Banking (`OCTO`, `myBCA`, `BCA mobile`, `BRImo`, `Livin'`, `SeaBank`):** `AZ:DOWN` + `DEV` + `A11Y` + `PKG` + `DISP`
   * **Browsers &amp; Chat (`Chrome`, `Brave`, `Firefox`, `WhatsApp`, `Telegram`, `Instagram`):** Keep **OFF** (automatically classified as `SANDBOX` and skipped by `AUTO BANK`).

### License &amp; Upstream Attribution

Distributed under the **Apache License 2.0**. License terms and modification disclosures pursuant to Section 4(b) and Section 6 are preserved in `LICENSE`, `NOTICE`, and inside the APK at `META-INF/LICENSE` and `META-INF/NOTICE`.

Derived from [MhmRdd/Isolation-Policy](https://github.com/MhmRdd/Isolation-Policy) and independently re-architected under namespace `io.github.taro902.isolationpolicy` for `libxposed` API 102.
