<div align="center">

<h1>IsolPolicy</h1>
<p><b>Cross-Thread <code>app_zygote</code> Interception, HostingRecord Downgrade &amp; Manifest Threat Radar for LSPosed</b></p>

<p>
  <img src="https://img.shields.io/badge/version-2.0-1f6feb?style=flat-square&labelColor=161b22" alt="Version 2.0" />
  <img src="https://img.shields.io/badge/api-libxposed_102-238636?style=flat-square&labelColor=161b22" alt="libxposed 102" />
  <img src="https://img.shields.io/badge/hooks-4--Layer_(AMS_%2B_ProcessStart)-d29922?style=flat-square&labelColor=161b22" alt="4-Layer Hooks" />
  <img src="https://img.shields.io/badge/radar-Binary_AXML_%2B_ServiceInfo-8957e5?style=flat-square&labelColor=161b22" alt="Manifest Radar" />
  <img src="https://img.shields.io/badge/platform-Android_10--16-6e7681?style=flat-square&labelColor=161b22" alt="Android 10-16" />
  <img src="https://img.shields.io/badge/license-Apache--2.0-6e7681?style=flat-square&labelColor=161b22" alt="License" />
</p>

<p>
  <kbd>4-Layer SystemServer Hooks</kbd>
  <kbd>Cross-Thread WeakHashMap Tracking</kbd>
  <kbd>Per-Package Mode Overrides</kbd>
  <kbd>Binary Manifest Preload Scanner</kbd>
  <kbd>Live Binder Hot-Reload</kbd>
</p>

</div>

<hr />

<h2>01. Threat Vector &amp; AOSP Execution Model</h2>

<p>
On Android 10 (API 29) and later, an application can declare an isolated service with <code>android:isolatedProcess="true"</code> and <code>android:useAppZygote="true"</code>, paired with an <code>android:zygotePreloadName</code> class on its <code>&lt;application&gt;</code> tag. When the app binds to that service via <code>Context.bindIsolatedService()</code>, <code>system_server</code> routes process creation away from the global Zygote and spawns a dedicated <b>App Zygote</b> operating inside the <code>u:r:app_zygote:s0</code> SELinux context.
</p>

<table width="100%">
  <tr>
    <td width="50%" valign="top">
      <h4>A. Kernel SELinuxfs Oracle (<code>DirtySepolicy</code>)</h4>
      <p>
        Before the child service transitions to <code>u:r:isolated_app:s0</code>, the App Zygote executes the app's <code>ZygotePreload.doPreload(ApplicationInfo)</code> callback inside <code>u:r:app_zygote:s0</code>.
      </p>
      <p>
        Under AOSP's <code>system/sepolicy/private/app_zygote.te</code>, this domain holds explicit kernel security privileges denied to <code>untrusted_app</code> and <code>isolated_app</code>:
      </p>
      <pre><code>allow app_zygote security:security {
    check_context
    compute_av
};</code></pre>
      <p>
        Preload code can write candidate security contexts and access queries directly to <code>/sys/fs/selinux/context</code> and <code>/sys/fs/selinux/access</code>, using the kernel's own security server to confirm the presence of <code>magisk</code>, <code>magisk_file</code>, <code>ksu</code>, or <code>lsposed_file</code> policy types and permissive rules.
      </p>
    </td>
    <td width="50%" valign="top">
      <h4>B. Namespace &amp; Unmount Evasion</h4>
      <p>
        On userspace root setups (Magisk / Kitsune Mask) running on kernels without <code>susfs</code> patches, mount table hygiene relies on Zygisk modules unmounting overlays when processes specialize from the primary Zygote.
      </p>
      <p>
        Because <code>AppZygote</code> is spawned through <code>ProcessList.createAppZygoteForProcessIfNeeded()</code> and managed via <code>ChildZygoteProcess</code>, preload code executes down a separate fork tree. Financial security SDKs (Alipay Security SDK, banking apps) and modern detection suites (Duck Detector) leverage this path to sample <code>/proc/self/mountinfo</code> and inspect anonymous device minor intervals prior to standard unmount passes.
      </p>
    </td>
  </tr>
</table>

<hr />

<h2>02. Cross-Thread Pipeline &amp; 4-Layer Hook Topology</h2>

<p>
Intercepting <code>useAppZygote</code> reliably across Android 10 through Android 16 (and heavily modified OEM frameworks like HyperOS, OneUI, and ColorOS) requires addressing two fundamental AOSP behaviors:
</p>

<ol>
  <li>
    <b>Asynchronous Thread Handoff:</b> In Android 11+, <code>ActiveServices.bringUpServiceLocked()</code> instantiates the <code>HostingRecord</code> and enters <code>ProcessList.startProcessLocked()</code> on the main <code>ActivityManagerService</code> (AMS) thread, but the actual Zygote socket dispatch occurs inside <code>ProcessList.startProcess()</code> on a separate background handler thread (<code>ProcessStart</code> / <code>mProcessStartHandler</code>). Simple <code>ThreadLocal</code> flags set during <code>startProcessLocked()</code> do not survive the jump to the <code>ProcessStart</code> thread.
  </li>
  <li>
    <b>Watchdog Timeout Side-Channel:</b> Upstream <b>IsolPolicy v1.1</b> short-circuits <code>startProcessLocked()</code> by returning <code>Boolean.TRUE</code> without forking. While that stops <code>AppZygote</code> from launching, it leaves an orphaned <code>ServiceRecord</code> in AMS. The caller's <code>bindIsolatedService()</code> connection hangs until its 6,000 ms watchdog timer expires—a deterministic anomaly flagged by modern detectors as <code>Enforcing with app_zygote carrier startup failure (timed out)</code>.
  </li>
</ol>

<p>
<b>IsolPolicy v2.0</b> solves both problems through a <b>4-layer interception pipeline</b> backed by weak identity tagging on the <code>HostingRecord</code> instance itself:
</p>

<pre><samp>┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│ THREAD 1: ActivityManagerService Main Thread (synchronized AMS Monitor)                      │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  [ActiveServices.bringUpServiceLocked]                                                       │
│    │  Reads ServiceInfo.FLAG_USE_APP_ZYGOTE (0x8)                                            │
│    ▼                                                                                         │
│  ┌────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ LAYER 1: HostingRecord.byAppZygote(instanceName, definingPkg, definingUid, ...)        │  │
│  ├────────────────────────────────────────────────────────────────────────────────────────┤  │
│  │ • Extracts definingPkg directly from factory arguments &amp; mDefiningPackageName field    │  │
│  │ • Tags (HostingRecord ➔ definingPkg) in synchronized WeakHashMap&lt;Object, String&gt;       │  │
│  │ • If target mode == DOWNGRADE: rewrites mHostingZygote (2 ➔ 0) at object birth         │  │
│  └────────────────────────────────────────────────────────────────────────────────────────┘  │
│    │                                                                                         │
│    ▼                                                                                         │
│  ┌────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ LAYER 2: ProcessList.startProcessLocked (Overload 1: returns ProcessRecord)            │  │
│  │          ProcessList.startProcessLocked (Overload 2 &amp; 3: returns boolean)              │  │
│  ├────────────────────────────────────────────────────────────────────────────────────────┤  │
│  │ • Resolves package via 4-source chain: WeakHashMap ➔ mDefiningPackageName              │  │
│  │   ➔ ApplicationInfo.packageName ➔ ProcessRecord.info.packageName                       │  │
│  │ • If target mode == HARD DROP:                                                         │  │
│  │     Overload 1 allocates ProcessRecord normally; Overload 2 short-circuits with        │  │
│  │     Boolean.TRUE so AMS receives a valid non-null ProcessRecord without forking.       │  │
│  │ • If target mode == DOWNGRADE:                                                         │  │
│  │     Mutates HostingRecord.mHostingZygote = 0 (ZYGOTE_REGULAR) &amp; marks sDowngraded      │  │
│  └────────────────────────────────────────────────────────────────────────────────────────┘  │
│    │                                                                                         │
└────┼─────────────────────────────────────────────────────────────────────────────────────────┘
     │  Posts Runnable to mProcessStartHandler (Cross-Thread Boundary)
     ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│ THREAD 2: ProcessStart Worker Thread (mProcessStartHandler)                                  │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ LAYER 3: ProcessList.startProcess(HostingRecord, entryPoint, ProcessRecord, ...)       │  │
│  ├────────────────────────────────────────────────────────────────────────────────────────┤  │
│  │ • Deoptimized via libxposed API 102 module.deoptimize() to defeat AOT inlining         │  │
│  │ • Reads instance-bound WeakHashMap tag carried across threads by HostingRecord         │  │
│  │ • Enforces mHostingZygote = 0 prior to createAppZygoteForProcessIfNeeded() branch      │  │
│  └────────────────────────────────────────────────────────────────────────────────────────┘  │
│    │                                                                                         │
│    ▼                                                                                         │
│  ┌────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ LAYER 4: HostingRecord.usesAppZygote()                                                 │  │
│  ├────────────────────────────────────────────────────────────────────────────────────────┤  │
│  │ • Deoptimized getter guard; returns Boolean.FALSE for all downgraded instances         │  │
│  └────────────────────────────────────────────────────────────────────────────────────────┘  │
│    │                                                                                         │
│    └──► Routes spawn to Primary Zygote (Process.start)                                       │
│           ├──► Process launches directly in u:r:isolated_app:s0 (~5 ms)                      │
│           ├──► AppZygote &amp; ZygotePreload.doPreload() never execute                           │
│           ├──► ServiceConnection.onServiceConnected() completes without timeout              │
│           └──► /sys/fs/selinux/{context,access} returns natural kernel EACCES                │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘</samp></pre>

<hr />

<h2>03. Core Subsystems &amp; Capabilities</h2>

<table width="100%">
  <tr>
    <td width="50%" valign="top">
      <h4>1. Per-Package Tri-State Policy Engine</h4>
      <p>
        Instead of forcing a single global behavior across all apps, every selected package in the UI displays an interactive mode badge next to its checkbox. Tapping the badge cycles through three per-app states:
      </p>
      <ul>
        <li><kbd>AUTO</kbd>: Inherits the global default engine mode (<kbd>Silent Downgrade</kbd> unless <kbd>Global Hard Drop</kbd> is checked).</li>
        <li><kbd>DOWNGRADE</kbd>: Explicitly forces <code>mHostingZygote: 2 ➔ 0</code> for this package, spawning the service in <code>u:r:isolated_app:s0</code> with ~5 ms bind completion.</li>
        <li><kbd>HARD DROP</kbd>: Explicitly forces <code>startProcessLocked()</code> fork cancellation for this package.</li>
      </ul>
    </td>
    <td width="50%" valign="top">
      <h4>2. Binary AXML Threat Radar</h4>
      <p>
        On Android 14+ (API 34+), reflective access to <code>ApplicationInfo.zygotePreloadName</code> is blocked by ART's hidden-API enforcement (<code>BlockedPrivateApi</code>).
      </p>
      <p>
        IsolPolicy v2.0 bypasses this restriction cleanly using a two-stage scanner: it queries <code>PackageManager.GET_SERVICES</code> in memory to check <code>ServiceInfo.flags</code> for <code>0x8</code> (<code>FLAG_USE_APP_ZYGOTE</code>) and <code>0x2</code> (<code>FLAG_ISOLATED_PROCESS</code>), then opens candidate APKs via <code>AssetManager.openXmlResourceParser("AndroidManifest.xml")</code> to parse the raw binary <code>android:zygotePreloadName</code> attribute directly from the <code>&lt;application&gt;</code> tag.
      </p>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <h4>3. Lock-Free RAM Cache &amp; Binder Hot-Reload</h4>
      <p>
        <code>ProcessList.startProcessLocked()</code> runs inside the global AMS monitor lock. Calling <code>XSharedPreferences.reload()</code> inside that section triggers synchronous disk <code>stat()</code> operations on every spawn.
      </p>
      <p>
        v2.0 maintains the deny set, drop overrides, and downgrade overrides inside immutable <code>AtomicReference&lt;Set&lt;String&gt;&gt;</code> structures in RAM, synchronized asynchronously via <code>libxposed:service:102.0.0</code> <code>RemotePreferences</code> callbacks. Tapping <kbd>Apply changes (Hot-Reload)</kbd> updates <code>system_server</code> in real time without rebooting.
      </p>
    </td>
    <td width="50%" valign="top">
      <h4>4. Threat Filtering &amp; JSON Serialization</h4>
      <p>
        The manager UI automatically sorts packages with detected <code>[APP_ZYGOTE xN]</code> and <code>[PRELOAD: ClassName]</code> signatures to the top of the list and provides dedicated workflow tools:
      </p>
      <ul>
        <li><kbd>All</kbd> / <kbd>Threats</kbd> / <kbd>Selected</kbd> instant filter tabs.</li>
        <li><kbd>+Threats</kbd> one-click batch selector for all packages exposing <code>app_zygote</code> or preload components.</li>
        <li><kbd>Export</kbd> &amp; <kbd>Import</kbd> buttons to serialize and restore your complete policy and per-app overrides as JSON via the system clipboard.</li>
      </ul>
    </td>
  </tr>
</table>

<hr />

<h2>04. Technical Specification Matrix</h2>

<table width="100%">
  <thead>
    <tr>
      <th align="left">Subsystem / Parameter</th>
      <th align="left">Upstream v1.1</th>
      <th align="left">IsolPolicy v2.0</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><b>Xposed Framework Contract</b></td>
      <td><code>de.robv.android.xposed:api:82</code> (Legacy)</td>
      <td><code>io.github.libxposed:api:102.0.0</code> + <code>service:102.0.0</code></td>
    </tr>
    <tr>
      <td><b>LSPosed Vector Classification</b></td>
      <td>Legacy module; triggers deprecated warning banner</td>
      <td>Native <kbd>API 102</kbd> module; zero deprecation warnings</td>
    </tr>
    <tr>
      <td><b>Hook Topology in <code>system_server</code></b></td>
      <td>Single hook on <code>boolean startProcessLocked(...)</code></td>
      <td>4-Layer: <code>byAppZygote</code> + <code>usesAppZygote</code> + <code>startProcessLocked</code> + <code>startProcess</code></td>
    </tr>
    <tr>
      <td><b>Cross-Thread State Tracking</b></td>
      <td>None</td>
      <td>Instance-keyed <code>WeakHashMap</code> bridging AMS &amp; <code>ProcessStart</code> threads</td>
    </tr>
    <tr>
      <td><b>ART Inlining Mitigation</b></td>
      <td>None</td>
      <td>Explicit <code>XposedInterface#deoptimize</code> across all target methods</td>
    </tr>
    <tr>
      <td><b>Package Identity Resolution</b></td>
      <td><code>ProcessRecord.info.packageName</code> only</td>
      <td>4-Source: Factory Args ➔ <code>mDefiningPackageName</code> ➔ <code>ApplicationInfo</code> ➔ <code>ProcessRecord</code></td>
    </tr>
    <tr>
      <td><b>Interception Granularity</b></td>
      <td>Global hard drop only</td>
      <td>Global default + Per-Package overrides (<kbd>AUTO</kbd> / <kbd>DOWNGRADE</kbd> / <kbd>HARD DROP</kbd>)</td>
    </tr>
    <tr>
      <td><b><code>ServiceConnection</code> Lifecycle</b></td>
      <td>Orphans service; hangs caller for 6,000 ms</td>
      <td>Completes <code>onServiceConnected()</code> in ~5 ms inside <code>u:r:isolated_app:s0</code></td>
    </tr>
    <tr>
      <td><b>Manifest Threat Detection</b></td>
      <td>None (alphabetical app list)</td>
      <td>In-memory <code>ServiceInfo</code> flags + binary AXML <code>XmlResourceParser</code> preload extractor</td>
    </tr>
    <tr>
      <td><b>AMS Critical Section I/O</b></td>
      <td>Synchronous disk <code>sPrefs.reload()</code> per call</td>
      <td>Lock-free O(1) RAM <code>AtomicReference</code> lookup (zero disk I/O)</td>
    </tr>
    <tr>
      <td><b>System / WebView Targeting</b></td>
      <td>Hardcoded lockout (<code>cb.setEnabled(false)</code>)</td>
      <td>Runtime unlockable via <kbd>Unlock System Apps</kbd> toggle</td>
    </tr>
    <tr>
      <td><b>Release Log Footprint</b></td>
      <td>Writes <code>[IsolPolicy]</code> traces to Logcat &amp; Xposed log</td>
      <td>Compiled out in release builds (<code>BuildConfig.DEBUG == false</code>)</td>
    </tr>
  </tbody>
</table>

<hr />

<h2>05. Diagnostic Console &amp; Policy Schema</h2>

<details open>
  <summary><b>Built-In Self-Test Console (<code>IsolationPolicyService</code>)</b></summary>
  <br />
  <p>
    On startup and on every policy apply, the manager binds to an internal isolated service declared with <code>android:useAppZygote="true"</code> and <code>android:zygotePreloadName=".AppZygote"</code>, measuring bind latency and verifying that <code>AppZygote.doPreload()</code> was bypassed:
  </p>
  <table width="100%">
    <tr>
      <td width="50%" valign="top">
        <b>Silent Downgrade Mode (Default)</b>
        <pre><samp>module           : active (LSPosed API 102)
hook layers      : 4/4 (byAppZygote + usesAppZygote + startProcess*)
default engine   : silent downgrade (isolated_app)
threat radar     : 3 app_zygote/preload  |  14 isolated
isolated service : bound in 5 ms
preload          : neutralized ( bypassed app_zygote )
ctx@now          : u:r:isolated_app:s0:c512,c768
isolated         : true
uid              : 99021
eligible: 142    selected: 4    overrides: 1</samp></pre>
      </td>
      <td width="50%" valign="top">
        <b>Hard Drop Mode (Global or Per-App Override)</b>
        <pre><samp>module           : active (LSPosed API 102)
hook layers      : 4/4 (byAppZygote + usesAppZygote + startProcess*)
default engine   : hard drop (skip fork)
threat radar     : 3 app_zygote/preload  |  14 isolated
isolated service : not bound
reason           : connection timed out (hard drop active)
eligible: 142    selected: 4    overrides: 1</samp></pre>
      </td>
    </tr>
  </table>
</details>

<details>
  <summary><b>Clipboard JSON Policy Export Format</b></summary>
  <br />
  <pre><code>{
  "version": 2,
  "mode_drop": false,
  "allow_system": false,
  "denied": [
    "id.dana",
    "com.eltavine.duckdetector",
    "io.github.mhmrdd.isolationpolicy"
  ],
  "drop_overrides": [],
  "downgrade_overrides": [
    "com.eltavine.duckdetector"
  ]
}</code></pre>
</details>

<hr />

<h2>06. Credits, Attribution &amp; License</h2>

<ul>
  <li>
    <b><a href="https://github.com/MhmRdd">MhmRdd (Mohammed Riad)</a></b> — Author of the original <a href="https://github.com/MhmRdd/Isolation-Policy">Isolation-Policy</a> module and the <code>ProcessList#startProcessLocked</code> interception concept and self-test harness upon which this derivative work is built.
  </li>
  <li>
    <b><a href="https://github.com/LSPosed/DirtySepolicy">LSPosed / DirtySepolicy</a></b> — Research demonstrating <code>app_zygote</code> SELinux policy interrogation via <code>/sys/fs/selinux/{context,access}</code>.
  </li>
  <li>
    <b><a href="https://github.com/libxposed">libxposed API &amp; Service</a></b> — Modern Xposed module interface and remote preference IPC specification.
  </li>
</ul>

<p>
Licensed under the <b>Apache License, Version 2.0</b>. See <a href="LICENSE"><code>LICENSE</code></a> and <a href="NOTICE"><code>NOTICE</code></a> for attribution and modification records pursuant to Section 4 of the Apache License.
</p>
