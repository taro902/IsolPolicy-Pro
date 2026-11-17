<div align="center">

<h1>IsolPolicy</h1>
<p><b>Cross-Thread <code>app_zygote</code> Interception, HostingRecord Downgrade &amp; Binary Manifest Threat Radar for LSPosed</b></p>

<p>
  <img src="https://img.shields.io/badge/version-2.0-1f6feb?style=flat-square&labelColor=161b22" alt="Version 2.0" />
  <img src="https://img.shields.io/badge/namespace-io.github.taro902.isolationpolicy-238636?style=flat-square&labelColor=161b22" alt="Namespace" />
  <img src="https://img.shields.io/badge/api-libxposed_102-007ec6?style=flat-square&labelColor=161b22" alt="libxposed 102" />
  <img src="https://img.shields.io/badge/hooks-4--Layer_(AMS_%2B_ProcessStart)-d29922?style=flat-square&labelColor=161b22" alt="4-Layer Hooks" />
  <img src="https://img.shields.io/badge/radar-Binary_AXML_%2B_ServiceInfo-8957e5?style=flat-square&labelColor=161b22" alt="Manifest Radar" />
  <img src="https://img.shields.io/badge/license-Apache--2.0-6e7681?style=flat-square&labelColor=161b22" alt="License" />
</p>

<p>
  <kbd>libxposed API 102</kbd>
  <kbd>4-Layer SystemServer Hooks</kbd>
  <kbd>Cross-Thread WeakHashMap</kbd>
  <kbd>Tri-State Per-App Policy</kbd>
  <kbd>Binary AXML Scanner</kbd>
</p>

</div>

<hr />

<h2>01. Kernel-Space &amp; AOSP Threat Model</h2>

<p>
On Android 10 (API 29) and higher, a <code>&lt;service&gt;</code> declaration may combine <code>android:isolatedProcess="true"</code> with <code>android:useAppZygote="true"</code>. During package parsing, the framework encodes these attributes into <code>ServiceInfo.flags</code> and evaluates two bitwise predicates inside <code>ActiveServices.bringUpServiceLocked()</code>:
</p>

$$\text{isIsolated}(\text{si}) \iff (\text{si.flags} \ \& \ \mathtt{0x00000002}) \neq 0, \qquad \text{usesAppZygote}(\text{si}) \iff (\text{si.flags} \ \& \ \mathtt{0x00000008}) \neq 0$$

<p>
When both predicates hold, <code>system_server</code> bypasses the global Zygote and spawns a per-application <b>App Zygote</b> inside the <code>u:r:app_zygote:s0</code> SELinux domain. If the package also declares an <code>android:zygotePreloadName</code> class implementing <code>android.app.ZygotePreload</code>, its <code>doPreload(ApplicationInfo)</code> method executes inside <code>u:r:app_zygote:s0</code> prior to transitioning to <code>u:r:isolated_app:s0</code>.
</p>

<table width="100%">
  <tr>
    <td width="50%" valign="top">
      <h4>A. Kernel <code>selinuxfs</code> Oracle (<code>DirtySepolicy</code>)</h4>
      <p>
        In AOSP's <code>system/sepolicy/private/app_zygote.te</code>, <code>app_zygote</code> is granted direct write access to <code>/sys/fs/selinux/context</code> and <code>/sys/fs/selinux/access</code>. Inside the Linux kernel (<code>security/selinux/selinuxfs.c</code>), writes to <code>/sys/fs/selinux/context</code> invoke <code>sel_write_context()</code>, which queries the live policy hash table directly:
      </p>
~~~c
static ssize_t sel_write_context(struct file *file, char *buf, size_t size)
{
    struct selinux_fs_info *fsi = file_inode(file)->i_sb->s_fs_info;
    struct selinux_state *state = fsi->state;
    char *canon = NULL;
    u32 sid, len;
    ssize_t length;

    length = avc_has_perm(&selinux_state,
                          current_sid(), SECINITSID_SECURITY,
                          SECCLASS_SECURITY, SECURITY__CHECK_CONTEXT, NULL);
    if (length)
        goto out;

    length = security_context_to_sid(state, buf, size, &sid, GFP_KERNEL);
    if (length)
        goto out;

    length = security_sid_to_context(state, sid, &canon, &len);
    if (length)
        goto out;

    length = simple_transaction_get(file, canon, len);
out:
    kfree(canon);
    return length;
}
~~~
      <p>
        Because <code>untrusted_app</code> and <code>isolated_app</code> fail the <code>SECURITY__CHECK_CONTEXT</code> check with <code>-EACCES</code>, hostile SDKs use <code>app_zygote</code> preload code to probe whether custom root types (<code>u:r:magisk:s0</code>, <code>u:object_r:magisk_file:s0</code>, <code>u:object_r:ksu_file:s0</code>, <code>u:object_r:lsposed_file:s0</code>) resolve to a valid SID or return <code>-EINVAL</code>.
      </p>
    </td>
    <td width="50%" valign="top">
      <h4>B. Asynchronous Mount Namespace Sampling</h4>
      <p>
        On userspace root setups (Magisk / Kitsune Mask) operating without kernel-level <code>susfs</code> patches, mount namespace sanitization relies on Zygisk modules unmounting overlays during standard Zygote specialization.
      </p>
      <p>
        Because <code>AppZygote</code> is spawned via <code>ProcessList.createAppZygoteForProcessIfNeeded()</code> and managed by <code>ChildZygoteProcess</code>, preload code runs down an auxiliary fork tree and can snapshot <code>/proc/self/mountinfo</code> or inspect anonymous device minor gaps before per-app unmount routines complete.
      </p>
      <h4>C. Legacy Watchdog Timeout Side-Channel</h4>
      <p>
        Comparing the process lifecycle between upstream <b>v1.1</b> (which drops the fork) and <b>v2.0</b> (which reroutes the Zygote type in-flight):
      </p>
~~~diff
  Caller invokes Context.bindIsolatedService(useAppZygote = true)
  └──► ActiveServices.bringUpServiceLocked() allocates ServiceRecord

- [Upstream v1.1: Hard Fork Drop]
- ProcessList.startProcessLocked() short-circuited ➔ returns Boolean.TRUE
- No child PID is forked; ServiceRecord remains pending in AMS
- Caller blocks for 6,000 ms until ServiceConnection watchdog expires
- Flagged by detectors: "app_zygote carrier startup failure (timed out)"

+ [IsolPolicy v2.0: In-Flight Regular Zygote Downgrade]
+ HostingRecord.mHostingZygote mutated in-place: 2 (APP_ZYGOTE) ➔ 0 (REGULAR)
+ Dispatched to primary Zygote (Process.start) on ProcessStart worker thread
+ Spawns directly into u:r:isolated_app:s0 (~5 ms bind completion)
+ ZygotePreload.doPreload() is completely skipped; /sys/fs/selinux/* returns EACCES
~~~
    </td>
  </tr>
</table>

<hr />

<h2>02. Cross-Thread Sequence &amp; 4-Layer Hook Topology</h2>

<p>
In Android 11 through Android 16, process creation inside <code>system_server</code> is split across two threads: <code>ActiveServices.bringUpServiceLocked()</code> and <code>ProcessList.startProcessLocked()</code> execute on the main <code>ActivityManagerService</code> (AMS) thread, which then posts an asynchronous task onto the <code>ProcessStart</code> handler thread (<code>mProcessStartHandler</code>) to invoke <code>ProcessList.startProcess()</code>.
</p>

<p>
A simple <code>ThreadLocal</code> flag set during <code>startProcessLocked()</code> is lost when execution crosses onto the <code>ProcessStart</code> worker thread. <b>IsolPolicy v2.0</b> bridges this boundary by tagging the <code>HostingRecord</code> instance itself inside a synchronized <code>WeakHashMap&lt;Object, String&gt;</code> at creation time:
</p>

~~~mermaid
sequenceDiagram
    autonumber
    participant App as Target App (Caller)
    participant AMS as ActiveServices (AMS Thread)
    participant Hook1 as BindHook Layer 1 & 2 (AMS)
    participant Worker as ProcessList (ProcessStart Thread)
    participant Hook2 as BindHook Layer 3 & 4 (Worker)
    participant Zygote as Primary Zygote (Global)

    App->>AMS: bindIsolatedService(intent, BIND_AUTO_CREATE)
    AMS->>Hook1: HostingRecord.byAppZygote(cn, definingPkg, uid, ...)
    Note over Hook1: Layer 1 extracts definingPkg,<br/>tags (hr ➔ pkg) in WeakHashMap,<br/>rewrites mHostingZygote: 2 ➔ 0
    Hook1-->>AMS: HostingRecord (mHostingZygote = 0)
    AMS->>Hook1: ProcessList.startProcessLocked(app, hr, ...)
    Note over Hook1: Layer 2 resolves pkg from 4 sources.<br/>If HARD DROP: Overload 2 returns TRUE.<br/>If DOWNGRADE: enforces mHostingZygote = 0
    Hook1->>Worker: Post Runnable to mProcessStartHandler
    Worker->>Hook2: ProcessList.startProcess(hr, entryPoint, app, ...)
    Note over Hook2: Layer 3 reads WeakHashMap tag across threads;<br/>Layer 4 forces hr.usesAppZygote() ➔ FALSE
    Hook2->>Zygote: Process.start(entryPoint, ..., isolated = true)
    Zygote-->>App: Spawns directly in u:r:isolated_app:s0 (~5 ms)
    Note right of App: ZygotePreload.doPreload() never runs.<br/>onServiceConnected() succeeds cleanly.<br/>/sys/fs/selinux/* returns EACCES.
~~~

<hr />

<h2>03. Decision Graph: 4-Source Resolution &amp; Tri-State Engine</h2>

<p>
Every intercepted spawn passes through a 4-stage package identity resolver followed by per-package mode evaluation (<kbd>AUTO</kbd>, <kbd>DOWNGRADE</kbd>, or <kbd>HARD DROP</kbd>):
</p>

~~~mermaid
flowchart TD
    A[Incoming Process Spawn in system_server] --> B{Is usesAppZygote == true<br/>or tagged in sDowngradedRecords?}
    B -- No --> Z[Proceed Unmodified]
    B -- Yes --> C[4-Source Package Identity Resolver]

    subgraph Resolver [Universal Package Resolution Chain]
        C --> R1[1. sHostingRecordPkgMap WeakHashMap Tag]
        R1 --> R2[2. HostingRecord.mDefiningPackageName Field]
        R2 --> R3[3. Direct ApplicationInfo.packageName Arg]
        R3 --> R4[4. ProcessRecord.info.packageName Field]
    end

    R4 --> D{Is Resolved Package<br/>in sDeniedPackages RAM Set?}
    D -- No --> Z
    D -- Yes --> E{Evaluate Per-Package Mode Override}

    E -- In sDropOverrides --> DROP[Execute Hard Drop Mode]
    E -- In sDowngradeOverrides --> DOWN[Execute Silent Downgrade Mode]
    E -- Mode == AUTO --> F{Check sGlobalDropMode}
    F -- True --> DROP
    F -- False --> DOWN

    DROP --> DROP_ACT[Overload 1 allocates ProcessRecord;<br/>Overload 2 returns Boolean.TRUE without fork]
    DOWN --> DOWN_ACT[Mutate HostingRecord.mHostingZygote = 0;<br/>Force usesAppZygote = FALSE;<br/>Spawn via Global Zygote in u:r:isolated_app:s0]
~~~

<hr />

<h2>04. Core Implementation Internals</h2>

<table width="100%">
  <tr>
    <td width="50%" valign="top">
      <h4>Cross-Thread <code>HostingRecord</code> Mutation</h4>
      <p>
        By intercepting <code>HostingRecord.byAppZygote()</code> and all overloads of <code>startProcessLocked()</code> and <code>startProcess()</code> with explicit ART deoptimization (<code>module.deoptimize()</code>), the module prevents AOT-inlined call sites in <code>services.odex</code> from bypassing the hook:
      </p>
~~~java
private static void downgradeToRegularZygote(Object hr) {
    if (hr == null) return;
    sDowngradedRecords.put(hr, Boolean.TRUE);
    try {
        Field f = sHostingZygoteField;
        if (f == null) {
            f = hr.getClass().getDeclaredField("mHostingZygote");
            f.setAccessible(true);
            sHostingZygoteField = f;
        }
        f.setInt(hr, ZYGOTE_REGULAR);
    } catch (Throwable ignored) {}
}
~~~
    </td>
    <td width="50%" valign="top">
      <h4>Binary AXML <code>XmlResourceParser</code> Radar</h4>
      <p>
        Targeting API 34+ triggers Android's <code>BlockedPrivateApi</code> restriction on reflective reads of <code>ApplicationInfo.zygotePreloadName</code>. The scanner extracts the preload class directly from the APK's compiled binary <code>AndroidManifest.xml</code>:
      </p>
~~~java
Resources res = pm.getResourcesForApplication(ai);
XmlResourceParser parser = res.getAssets()
    .openXmlResourceParser("AndroidManifest.xml");
while ((type = parser. next()) != XmlPullParser.END_DOCUMENT) {
    if (type == XmlPullParser.START_TAG
            && "application".equals(parser.getName())) {
        return parser.getAttributeValue(
            ANDROID_NS, "zygotePreloadName");
    }
}
~~~
    </td>
  </tr>
</table>

<hr />

<h2>05. Technical Specification Matrix</h2>

<p>
Lookup complexity inside the global <code>ActivityManagerService</code> critical section is reduced from synchronous disk I/O to constant-time $\mathcal{O}(1)$ volatile memory reads:
</p>

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
      <td><b>Package Namespace</b></td>
      <td><code>io.github.mhmrdd.isolationpolicy</code></td>
      <td><code>io.github.taro902.isolationpolicy</code></td>
    </tr>
    <tr>
      <td><b>Xposed Framework Contract</b></td>
      <td><code>de.robv.android.xposed:api:82</code> (Legacy)</td>
      <td><code>io.github.libxposed:api:102.0.0</code> + <code>service:102.0.0</code></td>
    </tr>
    <tr>
      <td><b>LSPosed Vector Status</b></td>
      <td>Flags deprecated <code>MODE_WORLD_READABLE</code> banner</td>
      <td>Native <kbd>API 102</kbd> classification; zero deprecation warnings</td>
    </tr>
    <tr>
      <td><b>Hook Topology in <code>system_server</code></b></td>
      <td>Single hook on <code>boolean startProcessLocked(...)</code></td>
      <td>4-Layer: <code>byAppZygote</code> + <code>usesAppZygote</code> + <code>startProcessLocked</code> + <code>startProcess</code></td>
    </tr>
    <tr>
      <td><b>Cross-Thread State Tracking</b></td>
      <td>None</td>
      <td>Synchronized <code>WeakHashMap</code> bridging AMS &amp; <code>ProcessStart</code> threads</td>
    </tr>
    <tr>
      <td><b>ART Inlining Defense</b></td>
      <td>None</td>
      <td>Explicit <code>XposedInterface#deoptimize</code> across all target methods</td>
    </tr>
    <tr>
      <td><b>Identity Resolution Chain</b></td>
      <td><code>ProcessRecord.info.packageName</code> only</td>
      <td>4-Source: Factory Args ➔ <code>mDefiningPackageName</code> ➔ <code>ApplicationInfo</code> ➔ <code>ProcessRecord</code></td>
    </tr>
    <tr>
      <td><b>Policy Granularity</b></td>
      <td>Global hard drop only</td>
      <td>Global default + Per-Package tri-state (<kbd>AUTO</kbd> / <kbd>DOWNGRADE</kbd> / <kbd>HARD DROP</kbd>)</td>
    </tr>
    <tr>
      <td><b><code>ServiceConnection</code> Lifecycle</b></td>
      <td>Orphans service; hangs caller for 6,000 ms</td>
      <td>Completes <code>onServiceConnected()</code> in ~5 ms inside <code>u:r:isolated_app:s0</code></td>
    </tr>
    <tr>
      <td><b>Threat Radar Scanner</b></td>
      <td>None (plain alphabetical list)</td>
      <td>In-memory <code>ServiceInfo</code> flags + binary AXML <code>XmlResourceParser</code> preload reader</td>
    </tr>
    <tr>
      <td><b>AMS Critical Section Complexity</b></td>
      <td>Synchronous disk <code>sPrefs.reload()</code> per spawn</td>
      <td>Lock-free $\mathcal{O}(1)$ RAM <code>AtomicReference</code> read (zero disk I/O)</td>
    </tr>
    <tr>
      <td><b>Release Log Output</b></td>
      <td>Writes <code>[IsolPolicy]</code> traces to Logcat &amp; Xposed log</td>
      <td>Compiled out in release builds (<code>BuildConfig.DEBUG == false</code>)</td>
    </tr>
  </tbody>
</table>

<hr />

<h2>06. Diagnostic Console &amp; JSON Serialization</h2>

<details open>
  <summary><b>Built-In Self-Test Console (<code>IsolationPolicyService</code>)</b></summary>
  <br />
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

<details open>
  <summary><b>Clipboard JSON Policy Export Schema</b></summary>
  <br />
~~~json
{
  "version": 2,
  "mode_drop": false,
  "allow_system": false,
  "denied": [
    "id.dana",
    "com.eltavine.duckdetector",
    "io.github.taro902.isolationpolicy"
  ],
  "drop_overrides": [],
  "downgrade_overrides": [
    "com.eltavine.duckdetector"
  ]
}
~~~
</details>

<hr />

<h2>07. Credits, Attribution &amp; License Compliance</h2>

<ul>
  <li>
    <b><a href="https://github.com/MhmRdd">MhmRdd (Mohammed Riad)</a></b> — Author of the original <a href="https://github.com/MhmRdd/Isolation-Policy">Isolation-Policy</a> (<code>io.github.mhmrdd.isolationpolicy</code>) and <b>NoHello</b>. This repository is an independent derivative work building upon MhmRdd's <code>ProcessList#startProcessLocked</code> interception concept and self-test service architecture.
  </li>
  <li>
    <b><a href="https://github.com/LSPosed/DirtySepolicy">LSPosed / DirtySepolicy</a></b> — Proof-of-concept demonstrating <code>app_zygote</code> SELinux policy interrogation via <code>/sys/fs/selinux/{context,access}</code>.
  </li>
  <li>
    <b><a href="https://github.com/libxposed">libxposed API &amp; Service</a></b> — Modern Xposed module interface and remote preference IPC specification.
  </li>
</ul>

<p>
Licensed under the <b>Apache License, Version 2.0</b>. All modifications and namespace refactoring details are documented in <a href="NOTICE"><code>NOTICE</code></a> and <a href="LICENSE"><code>LICENSE</code></a> in accordance with Section 4 of the Apache License.
</p>
