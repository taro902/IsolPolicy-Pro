<div align="center">

<img src="https://readme-typing-svg.demolab.com?font=JetBrains+Mono&weight=800&size=30&duration=2800&pause=1000&color=00E5FF&center=true&vCenter=true&width=750&height=55&lines=ISOLPOLICY+v2.0+%5BAPI+102+ULTIMATE%5D;SILENT+APP_ZYGOTE+DOWNGRADE+ENGINE;ZERO+TIMEOUTS+%E2%80%A2+ZERO+DISK+I%2FO+%E2%80%A2+ZERO+LEAKS" alt="IsolPolicy v2.0" />

<h3>Next-Gen <code>useAppZygote</code> Interception &amp; Silent Downgrade Engine for LSPosed (Vector)</h3>

<p>
  <img src="https://img.shields.io/badge/libxposed-API_102-00E5FF?style=for-the-badge&logo=android&logoColor=black&labelColor=0D1117" alt="API 102" />
  <img src="https://img.shields.io/badge/Android-10_%E2%80%93_16-00E676?style=for-the-badge&logo=android&logoColor=black&labelColor=0D1117" alt="Android 10-16" />
  <img src="https://img.shields.io/badge/Engine-Silent_Downgrade-FF6D00?style=for-the-badge&logo=linux&logoColor=white&labelColor=0D1117" alt="Engine" />
  <img src="https://img.shields.io/badge/IPC-Hot_Reload-D500F9?style=for-the-badge&logo=lightning&logoColor=white&labelColor=0D1117" alt="Hot Reload" />
  <img src="https://img.shields.io/badge/License-Apache_2.0-2979FF?style=for-the-badge&labelColor=0D1117" alt="License" />
</p>

<p>
  <kbd><b>Zero Legacy Warnings</b></kbd> •
  <kbd><b>5ms Instant Bind</b></kbd> •
  <kbd><b>ART Deoptimization</b></kbd> •
  <kbd><b>Lock-Free RAM Cache</b></kbd> •
  <kbd><b>Dead-Silent Logs</b></kbd>
</p>

<blockquote>
  <b>TL;DR:</b> Stop financial SDKs and root detectors from abusing <code>android:useAppZygote="true"</code> to snitch on your SELinux policy and mount table—without triggering the infamous 6-second carrier startup timeout flag.
</blockquote>

</div>

<hr />

<h2>⚡ The Backstory: Why Stock IsolPolicy Needed an Overhaul</h2>

<p>
If you are running a userspace root setup (<b>Magisk</b> or <b>Kitsune Mask</b>) on a kernel without SUSFS, the <code>u:r:app_zygote:s0</code> SELinux domain is your biggest blind spot.
</p>

<p>
On Android 10+, any app can declare a service with <code>android:isolatedProcess="true"</code> and <code>android:useAppZygote="true"</code>. Instead of forking from the global Zygote, <code>system_server</code> spins up a dedicated <b>App Zygote</b> and executes the app's custom <code>ZygotePreload</code> class inside the privileged <code>app_zygote</code> domain <i>before</i> transitioning to <code>isolated_app</code>.
</p>

<table width="100%">
  <tr>
    <td width="50%" valign="top">
      <h4>💀 1. The DirtySepolicy Oracle</h4>
      <p>
        AOSP's <code>app_zygote.te</code> explicitly grants <code>selinux_check_context</code> and <code>selinux_check_access</code>. That gives the preload free rein to hammer <code>/sys/fs/selinux/context</code> and <code>/sys/fs/selinux/access</code>, asking the kernel if root-related types like <code>magisk</code>, <code>magisk_file</code>, <code>ksu</code>, or <code>lsposed_file</code> exist in the loaded policy. Ordinary <code>untrusted_app</code> processes get shut down with <code>EACCES</code>, but <code>app_zygote</code> gets a VIP pass.
      </p>
    </td>
    <td width="50%" valign="top">
      <h4>🕵️ 2. Early Mount Table Sampling</h4>
      <p>
        Because <code>app_zygote</code> forks down a separate branch in <code>ProcessList</code>, aggressive security SDKs (Alipay Security SDK, banking apps, Duck Detector) use it to snapshot <code>/proc/self/mountinfo</code> before standard userspace unmount modules can scrub the namespace.
      </p>
    </td>
  </tr>
</table>

<p>
The original <b>IsolPolicy v1.1</b> by <a href="https://github.com/MhmRdd"><b>@MhmRdd</b></a> was clutch when it dropped, but modern detectors caught onto its trick. Stock v1.1 hooks <code>ProcessList#startProcessLocked</code> and flat-out drops the fork by returning <code>Boolean.TRUE</code>—telling <code>ActivityManagerService</code> the process launched when nothing was actually forked. When the target app calls <code>bindIsolatedService()</code>, it hangs until it hits a <b>6-second timeout</b>, which bleeding-edge detectors immediately flag as:
</p>

<pre><samp>⚠️ [WARNING] SELinux : Enforcing with app_zygote carrier startup failure
   └──► Failure = SELinux carrier probe timed out (6000ms)
⚠️ [WARNING] Native  : ksuThroneHunt = CARRIER_SETUP_FAILED (transaction timed out)</samp></pre>

<p>
<b>IsolPolicy v2.0 (API 102)</b> eliminates that dead giveaway completely.
</p>

<hr />

<h2>🔥 Head-to-Head: Stock v1.1 vs. IsolPolicy v2.0</h2>

<table width="100%">
  <thead>
    <tr>
      <th align="left">Architecture / Metric</th>
      <th align="left">Stock IsolPolicy v1.1</th>
      <th align="left">🛡️ IsolPolicy v2.0 (API 102)</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><b>Xposed API Standard</b></td>
      <td>Legacy API 82 (<code>de.robv.android.xposed</code>)</td>
      <td><b>Modern <code>libxposed API 102</code></b> (<code>XposedModule</code> &amp; <code>XposedService</code>)</td>
    </tr>
    <tr>
      <td><b>LSPosed Vector UI</b></td>
      <td>Displays yellow <i>"deprecated feature"</i> banner</td>
      <td><b>100% Clean</b> (Native blue <kbd>API 102</kbd> badge, zero warnings)</td>
    </tr>
    <tr>
      <td><b>Default Interception</b></td>
      <td><b>Hard Drop</b> (<code>setResult(true)</code> ➔ 6s timeout hang)</td>
      <td><b>Silent Downgrade</b> (<code>mHostingZygote: 2 ➔ 0</code> ➔ ~5ms bind)</td>
    </tr>
    <tr>
      <td><b>Carrier Timeout Probe</b></td>
      <td>Flagged as <code>carrier startup failure / timed out</code></td>
      <td><b>Undetected</b> (Binds cleanly inside standard <code>u:r:isolated_app:s0</code>)</td>
    </tr>
    <tr>
      <td><b>ART Inlining Defense</b></td>
      <td>None (vulnerable to AOT inlining in <code>services.jar</code>)</td>
      <td><b>Full <code>deoptimize()</code></b> on <code>ProcessList</code> &amp; <code>HostingRecord</code></td>
    </tr>
    <tr>
      <td><b>Policy Lookup Speed</b></td>
      <td>Disk <code>stat()</code> via <code>sPrefs.reload()</code> inside AMS lock</td>
      <td><b>Lock-Free O(1) RAM <code>AtomicReference</code></b> (Zero Disk I/O)</td>
    </tr>
    <tr>
      <td><b>Policy Sync &amp; Reload</b></td>
      <td>Deprecated <code>MODE_WORLD_READABLE</code> XML file</td>
      <td><b>Real-Time Binder IPC</b> (Instant Hot-Reload without rebooting)</td>
    </tr>
    <tr>
      <td><b>System &amp; WebView Apps</b></td>
      <td>Hard-locked in UI (<code>[oem]</code> checkboxes disabled)</td>
      <td><b>Unlockable on Demand</b> via UI toggle (<kbd>Unlock System Apps</kbd>)</td>
    </tr>
    <tr>
      <td><b>Logcat / Xposed Footprint</b></td>
      <td>Broadcasts <code>[IsolPolicy]</code> strings into system logs</td>
      <td><b>Dead Silent in Release Builds</b> (Zero logcat / bridge leaks)</td>
    </tr>
  </tbody>
</table>

<hr />

<h2>🧠 Under the Hood: The Silent Downgrade Engine</h2>

<p>
Instead of killing the process spawn and leaving the caller hanging for 6 seconds, <b>IsolPolicy v2.0</b> performs an in-flight surgical mutation on the <code>HostingRecord</code> object right inside <code>system_server</code>:
</p>

<pre><samp>┌──────────────────────────────────────────────────────────────────────────────────┐
│ 1. STOCK ANDROID (VULNERABLE)                                                    │
├──────────────────────────────────────────────────────────────────────────────────┤
│ App calls bindIsolatedService(useAppZygote = true)                               │
│   └──► HostingRecord (mHostingZygote = 2 / APP_ZYGOTE)                           │
│          └──► Forks via AppZygote (u:r:app_zygote:s0)                            │
│                 ├──► Runs ZygotePreload.doPreload()                              │
│                 │      └──► 💀 Reads /sys/fs/selinux/{context,access} -> BUSTED! │
│                 └──► Transitions to u:r:isolated_app:s0                          │
└──────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────────┐
│ 2. STOCK ISOLPOLICY v1.1 (HARD DROP / TIMEOUT ANOMALY)                           │
├──────────────────────────────────────────────────────────────────────────────────┤
│ App calls bindIsolatedService(useAppZygote = true)                               │
│   └──► ProcessList.startProcessLocked short-circuited (returns true, no fork)    │
│          └──► Caller waits 6,000ms for Binder...                                 │
│                 └──► ⚠️ FLAGGED: "app_zygote carrier startup failure / timeout"  │
└──────────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────────┐
│ 3. ISOLPOLICY v2.0 (SILENT DOWNGRADE ENGINE — DEFAULT)                           │
├──────────────────────────────────────────────────────────────────────────────────┤
│ App calls bindIsolatedService(useAppZygote = true)                               │
│   └──► Mutates HostingRecord in-place (mHostingZygote: 2 ➔ 0 / REGULAR_ZYGOTE)   │
│   └──► Forces HostingRecord.usesAppZygote() ➔ false                              │
│          └──► Forks from Global Zygote directly into u:r:isolated_app:s0 (~5ms)  │
│                 ├──► 🛡️ AppZygote &amp; ZygotePreload are 100% bypassed              │
│                 ├──► ⚡ Service connects in 5ms (zero timeout anomaly)            │
│                 └──► 🔒 Any /sys/fs/selinux/* probe hits natural kernel EACCES   │
└──────────────────────────────────────────────────────────────────────────────────┘</samp></pre>

<hr />

<h2>🛠️ Core Engineering Highlights</h2>

<table width="100%">
  <tr>
    <td width="50%" valign="top">
      <h3>1. Dual-Mode Interception</h3>
      <p>
        Switch between two interception engines on the fly without rebooting:
      </p>
      <ul>
        <li><b>Silent Downgrade (Default):</b> Reroutes <code>APP_ZYGOTE (2)</code> requests to <code>REGULAR_ZYGOTE (0)</code>. The isolated service binds normally inside <code>u:r:isolated_app:s0</code> without ever touching <code>app_zygote</code>.</li>
        <li><b>Hard Drop Mode:</b> Toggle <kbd>Hard Drop Mode</kbd> in the UI to short-circuit both <code>boolean</code> and <code>ProcessRecord</code> overloads of <code>startProcessLocked</code>.</li>
      </ul>
    </td>
    <td width="50%" valign="top">
      <h3>2. Zero Disk Thrashing in AMS</h3>
      <p>
        <code>ProcessList.startProcessLocked</code> runs while holding the global <code>ActivityManagerService</code> lock. Stock v1.1 called <code>sPrefs.reload()</code> on every spawn, forcing synchronous disk reads. v2.0 stores the deny set in a lock-free <code>AtomicReference&lt;Set&lt;String&gt;&gt;</code> in RAM and updates it asynchronously via <code>OnSharedPreferenceChangeListener</code>.
      </p>
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <h3>3. ART Deoptimization (API 102)</h3>
      <p>
        Android 14+ ROMs heavily inline small helper methods like <code>HostingRecord.usesAppZygote()</code> during AOT compilation of <code>services.jar</code>. IsolPolicy v2.0 invokes <code>module.deoptimize()</code> across all <code>startProcessLocked</code>, <code>startAppZygoteIfNeededLocked</code>, and <code>usesAppZygote</code> targets so no call site slips past the hook.
      </p>
    </td>
    <td width="50%" valign="top">
      <h3>4. System &amp; WebView Unlocking</h3>
      <p>
        Some banking SDKs delegate isolated environment checks to system components or WebView providers. Flip <kbd>Unlock System Apps</kbd> in the top bar to expose and lock down <code>[sys]</code> packages that were previously greyed out.
      </p>
    </td>
  </tr>
</table>

<hr />

<h2>📦 Installation &amp; Quick Start</h2>

<ol>
  <li>Install the compiled <b>IsolPolicy v2.0 (API 102)</b> APK.</li>
  <li>Open <b>LSPosed (Vector)</b>, enable <b>IsolPolicy</b> (<code>System Framework</code> is pre-configured via <code>scope.list</code>), and <b>reboot once</b> to initialize the <code>system_server</code> hooks.</li>
  <li>Add <b>IsolPolicy</b> to your <b>HMA / HMA-OSS</b> blacklist template so target apps cannot detect the manager package.</li>
  <li>Open the <b>IsolPolicy</b> app from your launcher:
    <ul>
      <li>Select your banking apps, e-wallets (e.g., DANA), games, or root detectors.</li>
      <li>Leave <kbd>Hard Drop Mode</kbd> unchecked to use <b>Silent Downgrade</b> (recommended), or check it for classic fork-dropping.</li>
      <li>Tap <kbd>Apply changes (Hot-Reload)</kbd> — your updated policy is pushed directly to <code>system_server</code> over Binder IPC in real time.</li>
    </ul>
  </li>
</ol>

<details>
  <summary><b>🔍 Built-In Self-Verification Diagnostic</b></summary>
  <br />
  <p>
    IsolPolicy includes an internal isolated service probe (<code>IsolationPolicyService</code>) that tests your active interception mode every time you open the app or tap <kbd>Apply changes (Hot-Reload)</kbd>:
  </p>
  <pre><samp>module           : active (LSPosed API 102)
engine mode      : silent downgrade (isolated_app)
isolated service : bound
preload          : blocked (safe isolated_app)
ctx@now          : u:r:isolated_app:s0:c512,c768
isolated         : true</samp></pre>
</details>

<hr />

<h2>🤝 Credits &amp; Acknowledgments</h2>

<ul>
  <li>
    <b><a href="https://github.com/MhmRdd">MhmRdd (Mohammed Riad)</a></b> — Author of the original <a href="https://github.com/MhmRdd/Isolation-Policy"><b>IsolPolicy</b></a> (<code>io.github.mhmrdd.isolationpolicy</code>) and <b>NoHello</b>. Massive respect for pioneering the <code>ProcessList#startProcessLocked</code> interception architecture and the built-in self-test harness that this project builds upon.
  </li>
  <li>
    <b><a href="https://github.com/JingMatrix/LSPosed">LSPosed / JingMatrix (Vector)</a></b> — For the modern <code>libxposed API 102</code> specification, <code>RemotePreferences</code> Binder service, and the <a href="https://github.com/LSPosed/DirtySepolicy">DirtySepolicy</a> research.
  </li>
</ul>

<hr />

<div align="center">
  <sub>Licensed under the <a href="LICENSE">Apache License, Version 2.0</a>. Built for educational and defensive security research.</sub>
</div>
