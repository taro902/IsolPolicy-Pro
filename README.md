<div align="center">

<h1>IsolPolicy</h1>
<p><b>In-Flight <code>app_zygote</code> Downgrade &amp; ProcessList Interception Module for LSPosed</b></p>

<p>
  <img src="https://img.shields.io/badge/api-libxposed_102-1f6feb?style=flat-square&labelColor=161b22" alt="libxposed 102" />
  <img src="https://img.shields.io/badge/target-system__server-238636?style=flat-square&labelColor=161b22" alt="system_server" />
  <img src="https://img.shields.io/badge/domain-u%3Ar%3Aapp__zygote%3As0-d29922?style=flat-square&labelColor=161b22" alt="app_zygote" />
  <img src="https://img.shields.io/badge/platform-Android_10--16-6e7681?style=flat-square&labelColor=161b22" alt="Android 10-16" />
  <img src="https://img.shields.io/badge/license-Apache--2.0-6e7681?style=flat-square&labelColor=161b22" alt="License" />
</p>

<p>
  <kbd>libxposed API 102</kbd>
  <kbd>HostingRecord Mutation</kbd>
  <kbd>ART Deoptimization</kbd>
  <kbd>Lock-Free Policy Cache</kbd>
  <kbd>Binder Hot-Reload</kbd>
</p>

</div>

<hr />

<h2>01. Threat Model &amp; AOSP Internals</h2>

<p>
Starting with Android 10 (API 29), the application manifest supports pairing <code>android:isolatedProcess="true"</code> with <code>android:useAppZygote="true"</code> on <code>&lt;service&gt;</code> components. When an application binds to such a service via <code>Context.bindIsolatedService()</code>, <code>system_server</code> does not fork the process from the primary Zygote. Instead, <code>com.android.server.am.ProcessList</code> spawns a per-application <b>App Zygote</b> running under the <code>u:r:app_zygote:s0</code> SELinux context.
</p>

<p>
If the application also declares an <code>android:zygotePreloadName</code> class implementing <code>android.app.ZygotePreload</code>, its <code>doPreload(ApplicationInfo)</code> callback executes directly inside <code>u:r:app_zygote:s0</code> <i>before</i> the child process undergoes dynamic transition to <code>u:r:isolated_app:s0</code>.
</p>

<table width="100%">
  <tr>
    <td width="50%" valign="top">
      <h4>SELinux Policy Oracle (<code>DirtySepolicy</code>)</h4>
      <p>
        In standard AOSP policy (<code>system/sepolicy/private/app_zygote.te</code>), the <code>app_zygote</code> domain is granted direct access to <code>selinuxfs</code> validation nodes:
      </p>
      <pre><code>allow app_zygote security:security {
    check_context
    compute_av
};</code></pre>
      <p>
        Standard <code>untrusted_app</code> and <code>isolated_app</code> domains are denied these permissions. Code running inside <code>ZygotePreload.doPreload()</code> can write arbitrary security context strings and access vectors to <code>/sys/fs/selinux/context</code> and <code>/sys/fs/selinux/access</code> to query the kernel's live policy database for non-stock types (<code>magisk</code>, <code>magisk_file</code>, <code>ksu</code>, <code>lsposed_file</code>) and permissive domain transitions.
      </p>
    </td>
    <td width="50%" valign="top">
      <h4>Namespace &amp; Unmount Evasion</h4>
      <p>
        On devices running userspace root managers without kernel-level <code>susfs</code> patches, mount namespace sanitization relies on Zygisk modules intercepting standard Zygote fork paths.
      </p>
      <p>
        Because <code>AppZygote</code> is managed through a distinct lifecycle in <code>ProcessList.startAppZygoteIfNeededLocked()</code> and forks children via <code>ChildZygoteProcess</code>, preload code can snapshot <code>/proc/self/mountinfo</code> and inspect mount propagation IDs or anonymous device minor gaps before standard per-app unmount routines run.
      </p>
    </td>
  </tr>
</table>

<hr />

<h2>02. The Side-Channel in Legacy Fork-Dropping</h2>

<p>
The original <b>IsolPolicy v1.1</b> by <a href="https://github.com/MhmRdd">MhmRdd</a> addressed this vector by hooking <code>ProcessList.startProcessLocked()</code> and short-circuiting the method with <code>param.setResult(Boolean.TRUE)</code> whenever <code>HostingRecord.usesAppZygote()</code> evaluated to <code>true</code>.
</p>

<p>
While effective at preventing <code>ZygotePreload</code> from running, dropping the fork outright introduces a deterministic timing anomaly. <code>ActivityManagerService</code> records the process start as accepted, and the caller's <code>bindIsolatedService()</code> call returns <code>true</code>. However, because no process was actually forked, the binder connection never arrives. The caller hangs until its internal watchdog timer expires:
</p>

<pre><samp>[SELinux Carrier Probe]
  ├── Context validity oracle : Unsupported (Carrier state = failed)
  ├── App-zygote seqno oracle : Unavailable (Failure = SELinux carrier probe timed out)
  └── Verdict                 : Enforcing with app_zygote carrier startup failure

[Native Throne Hunt]
  └── ksuThroneHunt           : CARRIER_SETUP_FAILED (IllegalStateException: connection timed out)</samp></pre>

<p>
To eliminate this timeout signature, this fork replaces hard fork-dropping with an <b>in-flight Zygote routing downgrade</b> inside <code>system_server</code>:
</p>

<pre><samp>┌────────────────────────────────────────────────────────────────────────────────────────┐
│ A. UNPATCHED AOSP PATH                                                                 │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ bindIsolatedService(useAppZygote = true)                                               │
│   └──► HostingRecord (mHostingZygote = 2 [APP_ZYGOTE])                                 │
│          └──► ProcessList.startProcessLocked()                                         │
│                 └──► AppZygote.getProcess().start()                                    │
│                        ├──► u:r:app_zygote:s0 ➔ ZygotePreload.doPreload()              │
│                        │      └──► Queries /sys/fs/selinux/{context,access} (EXPOSED)  │
│                        └──► dyntransition ➔ u:r:isolated_app:s0                        │
└────────────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────────┐
│ B. LEGACY v1.1 PATH (HARD FORK DROP)                                                   │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ bindIsolatedService(useAppZygote = true)                                               │
│   └──► HostingRecord (mHostingZygote = 2 [APP_ZYGOTE])                                 │
│          └──► ProcessList.startProcessLocked() short-circuited ➔ returns Boolean.TRUE  │
│                 └──► No child process spawned; ServiceRecord orphaned in AMS           │
│                        └──► ServiceConnection blocks for 6,000ms (TIMEOUT SIGNATURE)   │
└────────────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────────┐
│ C. v2.0 DEFAULT PATH (IN-FLIGHT REGULAR ZYGOTE DOWNGRADE)                              │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ bindIsolatedService(useAppZygote = true)                                               │
│   └──► HostingRecord intercepted in ProcessList.startProcessLocked()                   │
│          ├──► Mutates HostingRecord.mHostingZygote: 2 (APP_ZYGOTE) ➔ 0 (REGULAR)       │
│          └──► Forces HostingRecord.usesAppZygote() ➔ false on current AMS thread       │
│                 └──► Dispatches to primary Zygote (Process.start)                      │
│                        ├──► Spawns directly in u:r:isolated_app:s0 (~5ms)              │
│                        ├──► AppZygote &amp; ZygotePreload.doPreload() never execute        │
│                        ├──► ServiceConnection.onServiceConnected() completes normally  │
│                        └──► /sys/fs/selinux/{context,access} returns natural EACCES    │
└────────────────────────────────────────────────────────────────────────────────────────┘</samp></pre>

<hr />

<h2>03. Architectural Improvements</h2>

<table width="100%">
  <tr>
    <td width="50%" valign="top">
      <h4>1. In-Place <code>HostingRecord</code> Mutation</h4>
      <p>
        In AOSP's <code>com.android.server.am.HostingRecord</code>, the target Zygote is governed by the integer field <code>mHostingZygote</code> (where <code>0</code> is <code>REGULAR_ZYGOTE</code>, <code>1</code> is <code>WEBVIEW_ZYGOTE</code>, and <code>2</code> is <code>APP_ZYGOTE</code>).
      </p>
      <p>
        When a denied package requests an <code>APP_ZYGOTE</code> spawn, <code>BindHook</code> rewrites <code>mHostingZygote</code> to <code>0</code> before <code>startProcessLocked()</code> branches, backed by a <code>ThreadLocal&lt;Boolean&gt;</code> guard on <code>HostingRecord.usesAppZygote()</code>. The service still launches as a valid isolated process, but through the standard Zygote where <code>ZygotePreload</code> is ignored.
      </p>
    </td>
    <td width="50%" valign="top">
      <h4>2. ART AOT Deoptimization</h4>
      <p>
        On Android 14+ builds, <code>HostingRecord.usesAppZygote()</code> is a trivial getter that the ART compiler frequently inlines directly into <code>ProcessList.startProcessLocked()</code> and <code>startAppZygoteIfNeededLocked()</code> inside <code>services.odex</code>.
      </p>
      <p>
        Using <code>libxposed</code> API 102, the module explicitly invokes <code>deoptimize()</code> on all <code>startProcessLocked</code> overloads, <code>startAppZygoteIfNeededLocked</code>, and <code>usesAppZygote</code> during <code>onSystemServerStarting()</code>, guaranteeing that inlined call sites fall back to the hooked implementation.
      </p>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <h4>3. Lock-Free Policy Cache &amp; Binder IPC</h4>
      <p>
        <code>ProcessList.startProcessLocked()</code> executes while holding the global <code>ActivityManagerService</code> monitor lock. Calling <code>XSharedPreferences.reload()</code> inside that critical section performs synchronous filesystem <code>stat()</code> calls on every process start.
      </p>
      <p>
        This implementation migrates to <code>io.github.libxposed:service:102.0.0</code>. The deny list is held in an immutable <code>Set&lt;String&gt;</code> inside an <code>AtomicReference</code> in RAM and updated via <code>OnSharedPreferenceChangeListener</code> over Binder IPC. Policy lookups are O(1) memory reads with zero disk I/O.
      </p>
    </td>
    <td width="50%" valign="top">
      <h4>4. Dual Engine Modes &amp; System Scope</h4>
      <p>
        The configuration UI exposes two runtime toggles that sync immediately to <code>system_server</code> without requiring a reboot:
      </p>
      <ul>
        <li><kbd>Hard Drop Mode</kbd>: Switches the engine from <i>Silent Downgrade</i> back to classic fork-dropping across both <code>boolean</code> and <code>ProcessRecord</code> return-type overloads of <code>startProcessLocked()</code>.</li>
        <li><kbd>Unlock System Apps</kbd>: Removes the hardcoded UID <code>&lt; 10000</code> / <code>FLAG_SYSTEM</code> filter in the package scanner, allowing users to target system WebView providers or privileged packages.</li>
      </ul>
    </td>
  </tr>
</table>

<hr />

<h2>04. Technical Comparison Matrix</h2>

<table width="100%">
  <thead>
    <tr>
      <th align="left">Subsystem / Parameter</th>
      <th align="left">Upstream v1.1</th>
      <th align="left">This Fork (v2.0)</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><b>Xposed API Contract</b></td>
      <td><code>de.robv.android.xposed:api:82</code> (Legacy)</td>
      <td><code>io.github.libxposed:api:102.0.0</code> + <code>service:102.0.0</code></td>
    </tr>
    <tr>
      <td><b>Module Entry &amp; Metadata</b></td>
      <td><code>assets/xposed_init</code> + Manifest <code>meta-data</code></td>
      <td><code>META-INF/xposed/{java_init.list,module.prop,scope.list}</code></td>
    </tr>
    <tr>
      <td><b>LSPosed Vector Status</b></td>
      <td>Flags deprecated <code>MODE_WORLD_READABLE</code> usage</td>
      <td>Native <kbd>API 102</kbd> classification; zero deprecation warnings</td>
    </tr>
    <tr>
      <td><b>Hook Coverage in <code>ProcessList</code></b></td>
      <td><code>boolean startProcessLocked(...)</code> only</td>
      <td>All <code>boolean</code> &amp; <code>ProcessRecord</code> overloads + <code>HostingRecord.usesAppZygote()</code></td>
    </tr>
    <tr>
      <td><b>AOT Inlining Mitigation</b></td>
      <td>None</td>
      <td>Explicit <code>XposedInterface#deoptimize</code> on target methods</td>
    </tr>
    <tr>
      <td><b>Child SELinux Context</b></td>
      <td>None (process never spawned)</td>
      <td><code>u:r:isolated_app:s0</code> (skips <code>u:r:app_zygote:s0</code>)</td>
    </tr>
    <tr>
      <td><b><code>ServiceConnection</code> Behavior</b></td>
      <td>Hangs caller until 6,000 ms timeout</td>
      <td>Completes <code>onServiceConnected()</code> in ~5 ms (or optional Drop Mode)</td>
    </tr>
    <tr>
      <td><b>AMS Critical Section I/O</b></td>
      <td><code>sPrefs.reload()</code> + <code>File.canRead()</code> per call</td>
      <td>Lock-free <code>AtomicReference.get()</code> in RAM</td>
    </tr>
    <tr>
      <td><b>Configuration Transport</b></td>
      <td>World-readable XML on disk</td>
      <td>Binder IPC via <code>RemotePreferences</code> (live hot-reload)</td>
    </tr>
    <tr>
      <td><b>Release Log Output</b></td>
      <td>Writes to <code>android.util.Log</code> &amp; <code>XposedBridge.log</code></td>
      <td>No-op in release builds (<code>BuildConfig.DEBUG</code> gated)</td>
    </tr>
  </tbody>
</table>

<hr />

<h2>05. Deployment &amp; Diagnostic Verification</h2>

<ol>
  <li>Compile or install the signed <b>IsolPolicy</b> release APK.</li>
  <li>Enable the module in <b>LSPosed (Vector)</b>. The required <code>system</code> scope is pre-populated via <code>META-INF/xposed/scope.list</code>. Reboot once to initialize the <code>system_server</code> hooks.</li>
  <li>Add <code>io.github.mhmrdd.isolationpolicy</code> to your <b>HMA / HMA-OSS</b> blacklist template for all target applications.</li>
  <li>Open the <b>IsolPolicy</b> manager UI, select target packages, choose your interception mode, and tap <kbd>Apply changes (Hot-Reload)</kbd>. Changes take effect in <code>system_server</code> immediately without rebooting.</li>
</ol>

<details open>
  <summary><b>Built-In Self-Test Output (<code>IsolationPolicyService</code>)</b></summary>
  <br />
  <p>
    The manager application bundles an internal test service declared with <code>android:isolatedProcess="true"</code>, <code>android:useAppZygote="true"</code>, and <code>android:zygotePreloadName=".AppZygote"</code>. On startup and on every policy apply, it binds to this service to verify the active interception path:
  </p>
  <table width="100%">
    <tr>
      <td width="50%" valign="top">
        <b>Silent Downgrade Mode (Default)</b>
        <pre><samp>module           : active (LSPosed API 102)
engine mode      : silent downgrade (isolated_app)
isolated service : bound
preload          : blocked (safe isolated_app)
ctx@now          : u:r:isolated_app:s0:c512,c768
isolated         : true
uid              : 99014</samp></pre>
      </td>
      <td width="50%" valign="top">
        <b>Hard Drop Mode (Optional)</b>
        <pre><samp>module           : active (LSPosed API 102)
engine mode      : hard drop (skip fork)
isolated service : not bound
reason           : connection timed out (hard drop active)</samp></pre>
      </td>
    </tr>
  </table>
</details>

<hr />

<h2>06. Credits &amp; References</h2>

<ul>
  <li>
    <b><a href="https://github.com/MhmRdd">MhmRdd (Mohammed Riad)</a></b> — Author of the original <a href="https://github.com/MhmRdd/Isolation-Policy">Isolation-Policy</a> module and the <code>ProcessList#startProcessLocked</code> hook design upon which this project is based.
  </li>
  <li>
    <b><a href="https://github.com/LSPosed/DirtySepolicy">LSPosed / DirtySepolicy</a></b> — Proof-of-concept demonstrating <code>app_zygote</code> SELinux policy interrogation via <code>/sys/fs/selinux/{context,access}</code>.
  </li>
  <li>
    <b><a href="https://android.googlesource.com/platform/system/sepolicy/+/refs/heads/main/private/app_zygote.te">AOSP <code>system/sepolicy/private/app_zygote.te</code></a></b> — Upstream SELinux rules defining <code>app_zygote</code> security class permissions.
  </li>
  <li>
    <b><a href="https://github.com/libxposed">libxposed API &amp; Service</a></b> — Modern Xposed module interface and remote preference IPC specification.
  </li>
</ul>

<hr />

<div align="center">
  <sub>Licensed under the <a href="LICENSE">Apache License, Version 2.0</a>.</sub>
</div>
