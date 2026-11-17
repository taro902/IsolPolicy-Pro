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

<h2>01. Executive Summary &amp; AOSP Threat Model</h2>

<p>
Starting in Android 10 (API 29), the Android framework allows an application to declare an isolated <code>&lt;service&gt;</code> with <code>android:isolatedProcess="true"</code> alongside <code>android:useAppZygote="true"</code>, optionally paired with an <code>android:zygotePreloadName</code> attribute on its <code>&lt;application&gt;</code> element:
</p>

~~~xml
<application
    android:label="TargetApplication"
    android:zygotePreloadName="com.example.security.AppZygotePreload">

    <service
        android:name="com.example.security.IsolatedProbeService"
        android:exported="false"
        android:isolatedProcess="true"
        android:useAppZygote="true" />
</application>
~~~

<p>
When the package parser processes this manifest, it sets two bitflags inside <code>android.content.pm.ServiceInfo.flags</code>: <code>FLAG_ISOLATED_PROCESS</code> (<code>0x00000002</code>) and <code>FLAG_USE_APP_ZYGOTE</code> (<code>0x00000008</code>). When the application subsequently invokes <code>Context.bindIsolatedService()</code> or <code>Context. bindService()</code>, <code>ActiveServices.bringUpServiceLocked()</code> inside <code>system_server</code> evaluates both bitmasks:
</p>

$$
\mathrm{isIsolated}(s) \iff (s.\mathrm{flags} \land \mathtt{0x00000002}) \neq 0, \qquad \mathrm{usesAppZygote}(s) \iff (s.\mathrm{flags} \land \mathtt{0x00000008}) \neq 0
$$

<p>
When both conditions evaluate to true, <code>system_server</code> does not fork the service from the primary system Zygote. Instead, it spawns a dedicated per-application <b>App Zygote</b> process running inside the privileged <code>u:r:app_zygote:s0</code> SELinux context. Prior to transitioning the child service into <code>u:r:isolated_app:s0</code>, the App Zygote loads the application's APK and executes <code>ZygotePreload.doPreload(ApplicationInfo)</code> directly inside <code>u:r:app_zygote:s0</code>.
</p>

<hr />

<h2>02. Kernel-Space <code>selinuxfs</code> Mechanics (<code>DirtySepolicy</code> Oracle)</h2>

<p>
On devices running userspace root solutions (Magisk or Kitsune Mask) over kernels without kernel-level <code>susfs</code> patches, the live SELinux policy loaded in kernel memory contains custom domain and type definitions required by root daemons and injection frameworks.
</p>

<p>
Ordinary application processes running in <code>u:r:untrusted_app:s0</code> or <code>u:r:isolated_app:s0</code> are strictly forbidden by AOSP SELinux policy from querying the kernel's security server via <code>/sys/fs/selinux/context</code> or <code>/sys/fs/selinux/access</code>. However, AOSP's <code>system/sepolicy/private/app_zygote.te</code> explicitly grants the <code>app_zygote</code> domain permission to validate security contexts and compute access vectors:
</p>

~~~text
allow app_zygote security:security { check_context compute_av };
allow app_zygote selinuxfs:file { open read write };
~~~

<p>
Inside the Linux kernel (<code>security/selinux/selinuxfs.c</code>), writing a candidate context string to <code>/sys/fs/selinux/context</code> invokes <code>sel_write_context()</code>, which checks <code>SECURITY__CHECK_CONTEXT</code> and then queries the kernel's internal SID hash table via <code>security_context_to_sid()</code>:
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
Because <code>sel_write_context()</code> and <code>sel_write_access()</code> execute entirely inside kernel space, they cannot be hooked or spoofed by userspace Zygisk or Xposed modules once a process is already running inside <code>u:r:app_zygote:s0</code>:
</p>

<ul>
  <li>
    <b>Type Existence Probing:</b> Code inside <code>ZygotePreload.doPreload()</code> writes strings such as <code>u:r:magisk:s0</code>, <code>u:object_r:magisk_file:s0</code>, <code>u:object_r:ksu_file:s0</code>, or <code>u:object_r:lsposed_file:s0</code> to <code>/sys/fs/selinux/context</code>. On a clean stock kernel, <code>security_context_to_sid()</code> returns <code>-EINVAL</code>. On a rooted kernel, it returns the canonicalized context string, proving root modification.
  </li>
  <li>
    <b>Access Vector Interrogation:</b> Writing source/target/class queries (such as <code>untrusted_app magisk binder call</code> or <code>system_server system_server process execmem</code>) to <code>/sys/fs/selinux/access</code> reveals modified policy edges injected by root managers.
  </li>
  <li>
    <b>Early Mount Namespace Sampling:</b> Because <code>AppZygote</code> forks down a separate path in <code>ProcessList.createAppZygoteForProcessIfNeeded()</code>, preload code can snapshot <code>/proc/self/mountinfo</code> and inspect anonymous device minor gaps (<code>0:XX</code>) before standard Zygisk unmount modules scrub the process mount table.
  </li>
</ul>

<hr />

<h2>03. Side-Channel Analysis: Fork-Dropping (v1.1) vs. In-Flight Downgrade (v2.0)</h2>

<p>
To prevent an application from executing code inside <code>u:r:app_zygote:s0</code>, interception must occur inside <code>system_server</code> before <code>AppZygote</code> is spawned. However, <i>how</i> <code>system_server</code> handles that interception determines whether the target application detects tampering:
</p>

~~~diff
  Caller invokes Context.bindIsolatedService(intent, Context.BIND_AUTO_CREATE, ...)
  └──► ActiveServices.bringUpServiceLocked() creates HostingRecord (mHostingZygote = 2)

- [Upstream v1.1 Behavior: Hard Fork Drop]
- 1. Hooks boolean ProcessList.startProcessLocked(ProcessRecord, HostingRecord, ...)
- 2. Short-circuits execution and returns Boolean.TRUE without spawning any child process
- 3. ActivityManagerService marks the ServiceRecord as starting, returning true to caller
- 4. Caller waits for ServiceConnection.onServiceConnected() callback that never arrives
- 5. After 6,000 ms, caller watchdog expires -> triggers "SELinux carrier probe timed out"
-    and "ksuThroneHunt: CARRIER_SETUP_FAILED" warnings in modern detection suites

+ [IsolPolicy v2.0 Default Behavior: In-Flight Regular Zygote Downgrade]
+ 1. Intercepts HostingRecord at creation time in HostingRecord.byAppZygote() and across
+    ProcessList.startProcessLocked() + ProcessList.startProcess()
+ 2. Mutates HostingRecord.mHostingZygote in-place from 2 (APP_ZYGOTE) to 0 (REGULAR_ZYGOTE)
+ 3. Forces HostingRecord.usesAppZygote() to evaluate to Boolean.FALSE across all threads
+ 4. system_server dispatches the spawn to the primary system Zygote via Process.start()
+ 5. Child service launches directly inside u:r:isolated_app:s0 in ~5 ms
+ 6. AppZygote is never created and ZygotePreload.doPreload() is never invoked
+ 7. ServiceConnection.onServiceConnected() completes normally with zero timeout anomaly
+ 8. Any subsequent attempt by the service to open /sys/fs/selinux/* fails with natural EACCES
~~~

<hr />

<h2>04. Cross-Thread Pipeline &amp; 4-Layer Hook Topology</h2>

<p>
A critical architectural detail in Android 11 through Android 16 is that process creation inside <code>com.android.server.am.ProcessList</code> is asynchronous and spans multiple threads:
</p>

<ol>
  <li>
    <b>Thread 1 (AMS Main Thread):</b> <code>ActiveServices.bringUpServiceLocked()</code> constructs the <code>HostingRecord</code> via <code>HostingRecord.byAppZygote()</code> and invokes the three overloads of <code>ProcessList.startProcessLocked()</code> while holding the global <code>ActivityManagerService</code> monitor lock.
  </li>
  <li>
    <b>Thread 2 (<code>ProcessStart</code> Handler Thread):</b> The third overload of <code>startProcessLocked()</code> packages the spawn parameters into a lambda runnable and posts it to <code>mService.mProcessStartHandler</code>. That worker thread executes <code>ProcessList.startProcess(HostingRecord, ...)</code>, which evaluates <code>hostingRecord.usesAppZygote()</code> to decide whether to call <code>createAppZygoteForProcessIfNeeded()</code> or <code>Process.start()</code>.
  </li>
</ol>

<p>
Because execution crosses from the AMS main thread onto the <code>ProcessStart</code> worker thread, a simple <code>ThreadLocal&lt;Boolean&gt;</code> guard set during <code>startProcessLocked()</code> is lost before <code>startProcess()</code> runs. Furthermore, on Overload 1 of <code>startProcessLocked(String, ApplicationInfo, ...)</code>, the <code>ProcessRecord</code> object has not yet been allocated when the method enters.
</p>

<p>
<b>IsolPolicy v2.0</b> addresses this by binding package identity and downgrade state directly to the <code>HostingRecord</code> object reference via a synchronized <code>WeakHashMap&lt;Object, String&gt;</code> (`sHostingRecordPkgMap`) and `sDowngradedRecords` map. Because the same `HostingRecord` reference is passed into the runnable posted to `mProcessStartHandler`, the tag travels seamlessly across threads:
</p>

~~~mermaid
sequenceDiagram
    autonumber
    participant App as Target App
    participant AMS as ActiveServices (AMS Thread)
    participant L12 as BindHook Layer 1 & 2 (AMS)
    participant Worker as ProcessList (ProcessStart Thread)
    participant L34 as BindHook Layer 3 & 4 (Worker)
    participant Zygote as Primary Zygote

    App->>AMS: bindIsolatedService(intent, BIND_AUTO_CREATE)
    AMS->>L12: HostingRecord.byAppZygote(cn, definingPkg, uid, ...)
    Note over L12: Layer 1 extracts definingPkg<br/>Tags HostingRecord in WeakHashMap<br/>Rewrites mHostingZygote from 2 to 0
    L12-->>AMS: HostingRecord (mHostingZygote = 0)
    AMS->>L12: ProcessList.startProcessLocked(app, hr, ...)
    Note over L12: Layer 2 resolves pkg via 4-source chain<br/>If HARD DROP: Overload 2 returns TRUE<br/>If DOWNGRADE: enforces mHostingZygote = 0
    L12->>Worker: Post Runnable to mProcessStartHandler
    Worker->>L34: ProcessList.startProcess(hr, entryPoint, app, ...)
    Note over L34: Layer 3 reads WeakHashMap tag on worker thread<br/>Layer 4 forces hr.usesAppZygote() to FALSE
    L34->>Zygote: Process.start(entryPoint, ..., isolated = true)
    Zygote-->>App: Spawns in u:r:isolated_app:s0 (~5 ms)
    Note right of App: ZygotePreload never runs<br/>Service binds with zero timeout<br/>selinuxfs probes hit EACCES
~~~

<hr />

<h2>05. Decision Graph: 4-Source Resolution &amp; Tri-State Engine</h2>

<p>
To ensure compatibility across AOSP, LineageOS/crDroid, HyperOS/MIUI, OneUI, and ColorOS—where internal method signatures on <code>ProcessList</code> frequently vary—package identity is resolved through a 4-stage fallback chain before evaluating the package's tri-state policy (<kbd>AUTO</kbd>, <kbd>DOWNGRADE</kbd>, or <kbd>HARD DROP</kbd>):
</p>

~~~mermaid
flowchart TD
    A["Incoming Process Spawn in system_server"] --> B{"usesAppZygote == true OR tagged in sDowngradedRecords?"}
    B -- "No" --> Z["Proceed Unmodified"]
    B -- "Yes" --> C["4-Source Package Identity Resolver"]

    subgraph Resolver ["Universal Package Resolution Chain"]
        C --> R1["1. sHostingRecordPkgMap WeakHashMap Tag"]
        R1 --> R2["2. HostingRecord.mDefiningPackageName Field"]
        R2 --> R3["3. Direct ApplicationInfo.packageName Argument"]
        R3 --> R4["4. ProcessRecord.info.packageName Field"]
    end

    R4 --> D{"Package in sDeniedPackages RAM Set?"}
    D -- "No" --> Z
    D -- "Yes" --> E{"Evaluate Per-Package Mode Override"}

    E -- "In sDropOverrides" --> DROP["Execute Hard Drop Mode"]
    E -- "In sDowngradeOverrides" --> DOWN["Execute Silent Downgrade Mode"]
    E -- "Mode == AUTO" --> F{"Check sGlobalDropMode"}
    F -- "True" --> DROP
    F -- "False" --> DOWN

    DROP --> DROP_ACT["Overload 1 allocates ProcessRecord normally<br/>Overload 2 returns Boolean.TRUE without fork"]
    DOWN --> DOWN_ACT["Mutate HostingRecord.mHostingZygote = 0<br/>Force usesAppZygote = FALSE<br/>Spawn via Global Zygote in u:r:isolated_app:s0"]
~~~

<hr />

<h2>06. Implementation Internals</h2>

<h3>A. 4-Layer Hook Registration &amp; ART Deoptimization</h3>

<p>
In Android 14+ builds, trivial getters such as <code>HostingRecord.usesAppZygote()</code> are frequently inlined by the ART compiler directly into <code>ProcessList.startProcessLocked()</code> and <code>startProcess()</code> inside <code>services.odex</code>. Using <code>libxposed</code> API 102, <code>BindHook</code> invokes <code>module.deoptimize()</code> across all target methods in <code>HostingRecord</code> and <code>ProcessList</code> during <code>onSystemServerStarting()</code>:
</p>

~~~java
private static void hookProcessList(final XposedModule module, ClassLoader cl) {
    try {
        Class<?> plClass = Class.forName(PROCESS_LIST, false, cl);
        Method[] methods = plClass.getDeclaredMethods();
        for (final Method m : methods) {
            String name = m.getName();
            if ("startProcessLocked".equals(name)) {
                try { module.deoptimize(m); } catch (Throwable ignored) {}
                installStartProcessLockedHook(module, m);
            } else if ("startProcess".equals(name)) {
                try { module.deoptimize(m); } catch (Throwable ignored) {}
                installStartProcessWorkerHook(module, m);
            } else if ("startAppZygoteIfNeededLocked".equals(name)
                    || "createAppZygoteForProcessIfNeeded".equals(name)) {
                try { module.deoptimize(m); } catch (Throwable ignored) {}
            }
        }
    } catch (Throwable t) {
        Logger.e("hookProcessList failed", t);
    }
}
~~~

<h3>B. Overload-Aware Hard Drop vs. Silent Downgrade</h3>

<p>
In AOSP's <code>ActiveServices.bringUpServiceLocked()</code>, the caller expects <code>ActivityManagerService.startProcessLocked()</code> (Overload 1, returning <code>ProcessRecord</code>) to return a non-null <code>ProcessRecord</code>. Overload 1 allocates the <code>ProcessRecord</code> and delegates to Overload 2 (returning <code>boolean</code>). Therefore, when <kbd>HARD DROP</kbd> is selected, <code>BindHook</code> allows Overload 1 to allocate the <code>ProcessRecord</code> and short-circuits specifically on the <code>boolean</code> overload:
</p>

~~~java
if (shouldDrop(pkg)) {
    if (returnsBoolean) {
        return Boolean.TRUE;
    }
    return chain.proceed();
}

downgradeToRegularZygote(hr);
sForceRegularZygote.set(Boolean.TRUE);
try {
    return chain.proceed();
} finally {
    sForceRegularZygote.set(Boolean.FALSE);
}
~~~

<h3>C. Binary AXML <code>XmlResourceParser</code> Threat Radar</h3>

<p>
When targeting Android 14+ (API 34+), reflective access to the hidden field <code>ApplicationInfo.zygotePreloadName</code> is blocked both by Android Lint (<code>BlockedPrivateApi</code>) and by ART runtime enforcement. To identify installed packages that declare a <code>ZygotePreload</code> class without triggering hidden-API restrictions, the scanner opens the target APK's compiled binary <code>AndroidManifest.xml</code> stream directly via <code>AssetManager.openXmlResourceParser()</code>:
</p>

~~~java
private static String extractManifestZygotePreload(PackageManager pm, ApplicationInfo ai) {
    XmlResourceParser parser = null;
    try {
        Resources res = pm.getResourcesForApplication(ai);
        AssetManager assets = res.getAssets();
        parser = assets.openXmlResourceParser("AndroidManifest.xml");
        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                if ("application".equals(parser.getName())) {
                    String preload = parser.getAttributeValue(ANDROID_NS, "zygotePreloadName");
                    if (preload != null && !preload.isEmpty()) {
                        return preload;
                    }
                    break;
                }
            }
            eventType = parser.next();
        }
    } catch (Throwable ignored) {
    } finally {
        if (parser != null) {
            try { parser.close(); } catch (Throwable ignored) {}
        }
    }
    return null;
}
~~~

<hr />

<h2>07. Complexity &amp; Specification Matrix</h2>

<p>
In upstream v1.1, every <code>app_zygote</code> spawn invoked <code>XSharedPreferences.reload()</code> inside <code>startProcessLocked()</code>, performing synchronous filesystem <code>stat()</code> calls while holding the global AMS lock. In v2.0, policy evaluation is an $\mathcal{O}(1)$ volatile memory lookup against an immutable <code>AtomicReference&lt;Set&lt;String&gt;&gt;</code> updated via Binder IPC:
</p>

$$
T_{\mathrm{lookup}}(n) = \mathcal{O}(1) \quad \text{via } \mathtt{AtomicReference.get().contains(pkg)}, \qquad \mathrm{I/O}_{\mathrm{disk}} = 0 \text{ bytes/spawn}
$$

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
      <td>Triggers yellow deprecated feature warning</td>
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
      <td><b>AMS Critical Section I/O</b></td>
      <td>Synchronous disk <code>sPrefs.reload()</code> per spawn</td>
      <td>Lock-free $\mathcal{O}(1)$ RAM <code>AtomicReference</code> read (zero disk I/O)</td>
    </tr>
    <tr>
      <td><b>Release Log Footprint</b></td>
      <td>Writes <code>[IsolPolicy]</code> traces to Logcat &amp; Xposed log</td>
      <td>Compiled out in release builds (<code>BuildConfig.DEBUG == false</code>)</td>
    </tr>
  </tbody>
</table>

<hr />

<h2>08. Diagnostic Telemetry &amp; Policy Serialization</h2>

<p>
The manager UI embeds an internal isolated service declared with <code>android:isolatedProcess="true"</code>, <code>android:useAppZygote="true"</code>, and <code>android:zygotePreloadName="io.github.taro902.isolationpolicy.AppZygote"</code>. On launch and on every policy apply, it binds to this service to measure bind latency and verify that <code>AppZygote.doPreload()</code> was neutralized:
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

<p>
Complete policy configurations—including global flags, denied package sets, and per-package mode overrides—can be exported and imported via the system clipboard using the following JSON structure:
</p>

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

<hr />

<h2>09. Credits, Attribution &amp; License Compliance</h2>

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
