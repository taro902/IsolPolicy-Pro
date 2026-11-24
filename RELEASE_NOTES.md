# IsolPolicy-Pro v2.2.3

## Critical AOSP Hook Correctness Fixes
- Fixed inverted `IntPair` bit-shift in `AccessibilityShield.addClient()`: now strips `A11Y_STATE_STRIP_MASK` (`0x1B`) from the upper 32 bits (`pair >>> 32`, `stateFlags`) while preserving the lower 32 bits (`relevantEventTypes`) and high-text-contrast bit `0x04` intact.
- Fixed dynamic isolated instance safety gate in `IsolatedFoldShield`: inspects `ServiceRecord.name` and `ServiceRecord.instanceName` as `android.content.ComponentName` (`!decl.equals(inst)` and `:` instance suffix) instead of `String`, plus added Gate 7 (`r.isolatedProc != null`) to prevent mid-lifecycle mutation of active isolated processes.
- Activated ART deoptimization (`CompatMatrix.tryDeoptimize()` inside `hookMethods()` and targeted `CompatMatrix.deoptimizeCallers()` across `ActiveServices`, `ProcessList`, `DisplayManagerService`, `AccessibilityManagerService`, `UsbService`, and `ContentService`) to prevent AOT-inlined callers in `services.odex` from bypassing hooks.
- Added live `DEOPT` counter telemetry (`KEY_DEOPT_COUNT`) surfaced in `VAULT -> MODULE IDENTITY`.
- Added 5 regression JUnit tests in `RegressionAuditTest` (54 total JVM unit tests enforced in CI).

## Upgrade Notes
- Signed with the same persistent EC P-256 keystore (`BEA4B3F2...0752FC`) as v2.2.0-v2.2.2. Install directly in-place and reboot once (no uninstall or HMA-OSS/LSPosed re-check required).
