package io.github.taro902.isolationpolicy;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.taro902.isolationpolicy.core.PolicyFlags;
import io.github.taro902.isolationpolicy.hook.HookGuard;
import io.github.taro902.isolationpolicy.hook.InputSurfaceShield;
import io.github.taro902.isolationpolicy.hook.IsolatedFoldShield;
import io.github.taro902.isolationpolicy.hook.ObserverCoherenceShield;
import io.github.taro902.isolationpolicy.hook.ProcessSurfaceShield;
import io.github.taro902.isolationpolicy.hook.SettingsShield;
import io.github.taro902.isolationpolicy.hook.TelemetryHub;
import io.github.taro902.isolationpolicy.radar.ManifestRadar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ShieldLogicAndGuardTest {

    @Test
    public void testHookGuardTripsOnEighthConsecutiveFault() throws Throwable {
        TelemetryHub hub = new TelemetryHub();
        hub.setPillarStatus(PolicyFlags.PILLAR_DISP, TelemetryHub.HEALTH_OK, 1);
        HookGuard guard = hub.createGuard(PolicyFlags.PILLAR_DISP, "TEST_DISP");
        final AtomicInteger bodyRuns = new AtomicInteger(0);

        HookGuard.PureCall failing = new HookGuard.PureCall() {
            @Override
            public Object call() {
                bodyRuns.incrementAndGet();
                throw new IllegalStateException("internal");
            }
        };
        HookGuard.PureCall fallback = new HookGuard.PureCall() {
            @Override
            public Object call() {
                return "PASS";
            }
        };

        for (int i = 1; i <= 7; i++) {
            assertEquals("PASS", guard.executePure(failing, fallback));
            assertFalse(guard.isTripped());
            assertEquals(i, guard.getConsecutiveFaults());
        }
        assertEquals("PASS", guard.executePure(failing, fallback));
        assertTrue(guard.isTripped());
        assertEquals(TelemetryHub.HEALTH_TRIPPED, TelemetryHub.decodePillarHealth(hub.computeHealthMask(), PolicyFlags.PILLAR_DISP));

        int runsAtTrip = bodyRuns.get();
        assertEquals("PASS", guard.executePure(failing, fallback));
        assertEquals(runsAtTrip, bodyRuns.get());
    }

    @Test
    public void testHookGuardResetsStreakOnSuccess() throws Throwable {
        HookGuard guard = new HookGuard(PolicyFlags.PILLAR_SET, "TEST_SET", null);
        HookGuard.PureCall fail = new HookGuard.PureCall() {
            @Override
            public Object call() {
                throw new RuntimeException("boom");
            }
        };
        HookGuard.PureCall ok = new HookGuard.PureCall() {
            @Override
            public Object call() {
                return "OK";
            }
        };
        for (int i = 0; i < 5; i++) {
            guard.executePure(fail, ok);
        }
        assertEquals(5, guard.getConsecutiveFaults());
        assertEquals("OK", guard.executePure(ok, ok));
        assertEquals(0, guard.getConsecutiveFaults());
        assertFalse(guard.isTripped());
    }

    @Test
    public void testHookGuardPropagatesOriginalTargetExceptionWithoutFault() {
        HookGuard guard = new HookGuard(PolicyFlags.PILLAR_PKG, "TEST_PKG", null);
        boolean caught = false;
        try {
            guard.executePure(new HookGuard.PureCall() {
                @Override
                public Object call() throws Throwable {
                    return HookGuard.invokePureTarget(new HookGuard.PureCall() {
                        @Override
                        public Object call() {
                            throw new SecurityException("caller denied");
                        }
                    });
                }
            }, new HookGuard.PureCall() {
                @Override
                public Object call() {
                    return null;
                }
            });
        } catch (SecurityException expected) {
            caught = true;
        } catch (Throwable other) {
            throw new AssertionError(other);
        }
        assertTrue(caught);
        assertEquals(0, guard.getConsecutiveFaults());
        assertEquals(0, guard.getTotalFaults());
        assertFalse(guard.isTripped());
    }

    @Test
    public void testSettingsSpoofMatrixAll17Keys() {
        int dev = PolicyFlags.FLAG_DEV_COHERENT;
        int a11y = PolicyFlags.FLAG_A11Y_COHERENT;
        assertEquals("0", SettingsShield.resolveSpoofedValue("development_settings_enabled", dev));
        assertEquals("0", SettingsShield.resolveSpoofedValue("adb_enabled", dev));
        assertEquals("0", SettingsShield.resolveSpoofedValue("adb_wifi_enabled", dev));
        assertEquals("0", SettingsShield.resolveSpoofedValue("stay_on_while_plugged_in", dev));
        assertEquals("0", SettingsShield.resolveSpoofedValue("allow_mock_location", dev));
        assertEquals("1", SettingsShield.resolveSpoofedValue("package_verifier_enable", dev));
        assertNull(SettingsShield.resolveSpoofedValue("debug_app", dev));
        assertTrue(SettingsShield.resolveSpoofedValue("accessibility_enabled", dev) == SettingsShield.UNMODIFIED);

        assertEquals("0", SettingsShield.resolveSpoofedValue("accessibility_enabled", a11y));
        assertEquals("", SettingsShield.resolveSpoofedValue("enabled_accessibility_services", a11y));
        assertEquals("0", SettingsShield.resolveSpoofedValue("touch_exploration_enabled", a11y));
        assertTrue(SettingsShield.resolveSpoofedValue("screen_brightness", dev | a11y) == SettingsShield.UNMODIFIED);
    }

    @Test
    public void testSettingsListSanitizerSelectiveRewrite() {
        int[] changed = new int[1];
        ArrayList<String> src = new ArrayList<String>(Arrays.asList(
            "airplane_mode_on=0",
            "adb_enabled=1",
            "development_settings_enabled=1",
            "debug_app=com.evil.dbg",
            "verifier_verify_adb_installs=0",
            "auto_time=1"
        ));
        ArrayList<String> out = SettingsShield.sanitizeSettingsList(src, PolicyFlags.FLAG_DEV_COHERENT, changed);
        assertNotNull(out);
        assertEquals(4, changed[0]);
        assertTrue(out.contains("airplane_mode_on=0"));
        assertTrue(out.contains("adb_enabled=0"));
        assertTrue(out.contains("development_settings_enabled=0"));
        assertTrue(out.contains("verifier_verify_adb_installs=1"));
        assertTrue(out.contains("auto_time=1"));
        assertFalse(out.contains("debug_app=com.evil.dbg"));

        ArrayList<String> clean = new ArrayList<String>(Arrays.asList("airplane_mode_on=0", "adb_enabled=0"));
        assertNull(SettingsShield.sanitizeSettingsList(clean, PolicyFlags.FLAG_DEV_COHERENT, changed));
        assertEquals(0, changed[0]);
    }

    @Test
    public void testInputSurfaceShieldAllowlistAndInjectors() {
        assertFalse(InputSurfaceShield.shouldFilterDescriptor(-1, false, 0, 0, 0x101, "Virtual", "virtual"));
        assertFalse(InputSurfaceShield.shouldFilterDescriptor(3, false, 0, 0, 0x1002, "NVTCapacitiveTouchScreen", "novatek"));
        assertFalse(InputSurfaceShield.shouldFilterDescriptor(4, false, 0, 0, 0x101, "uinput-fpc", "fpc1020"));
        assertFalse(InputSurfaceShield.shouldFilterDescriptor(5, false, 0, 0, 0x101, "gpio-keys", "gpio"));
        assertFalse(InputSurfaceShield.shouldFilterDescriptor(8, true, 0x046D, 0xC52B, 0x2002, "Logitech USB Mouse", "usb-1"));

        assertTrue(InputSurfaceShield.shouldFilterDescriptor(10, true, 0, 0, 0x1002, "scrcpy", "uhid-1"));
        assertTrue(InputSurfaceShield.shouldFilterDescriptor(11, true, 0, 0, 0x2002, "custom_pointer", "desc"));
        assertTrue(InputSurfaceShield.shouldFilterDescriptor(12, false, 0, 0, 0x1002, "uinput-touch", "uinput"));
        assertTrue(InputSurfaceShield.shouldFilterDescriptor(13, true, 0, 0, 0x101, "virtio_keyboard", "virtio"));
    }

    @Test
    public void testProcessSurfaceExitReasons() {
        assertTrue(ProcessSurfaceShield.isSensitiveExitReason(ProcessSurfaceShield.REASON_SIGNALED));
        assertTrue(ProcessSurfaceShield.isSensitiveExitReason(ProcessSurfaceShield.REASON_CRASH_NATIVE));
        assertTrue(ProcessSurfaceShield.isSensitiveExitReason(ProcessSurfaceShield.REASON_USER_REQUESTED));
        assertTrue(ProcessSurfaceShield.isSensitiveExitReason(ProcessSurfaceShield.REASON_FREEZER));
        assertFalse(ProcessSurfaceShield.isSensitiveExitReason(1));
        assertFalse(ProcessSurfaceShield.isSensitiveExitReason(4));
    }

    @Test
    public void testObserverCoherenceUriFilter() {
        int f = PolicyFlags.FLAG_OBS_COHERENCE | PolicyFlags.FLAG_DEV_COHERENT;
        assertTrue(ObserverCoherenceShield.shouldSuppressSettingsUri("settings", "/global/adb_enabled", f));
        assertTrue(ObserverCoherenceShield.shouldSuppressSettingsUri("settings", "/global/development_settings_enabled", f));
        assertFalse(ObserverCoherenceShield.shouldSuppressSettingsUri("settings", "/system/screen_brightness", f));
        assertFalse(ObserverCoherenceShield.shouldSuppressSettingsUri("settings", "/secure/accessibility_enabled", f));
        assertTrue(ObserverCoherenceShield.shouldSuppressSettingsUri("settings", "/secure/accessibility_enabled", f | PolicyFlags.FLAG_A11Y_COHERENT));
        assertFalse(ObserverCoherenceShield.shouldSuppressSettingsUri("settings", "/global/adb_enabled", PolicyFlags.FLAG_DEV_COHERENT));
        assertFalse(ObserverCoherenceShield.shouldSuppressSettingsUri("contacts", "/global/adb_enabled", f));
    }

    @Test
    public void testIsolatedFoldRendererSafety() {
        assertTrue(IsolatedFoldShield.isProtectedRenderer("com.android.chrome", "com.android.chrome:sandboxed_process0", "SandboxedProcessService"));
        assertTrue(IsolatedFoldShield.isProtectedRenderer("id.dana", "id.dana:webview_service", "WebViewService"));
        assertFalse(IsolatedFoldShield.isProtectedRenderer("id.dana", "id.dana:sec_isolated", "com.alipay.SecurityService"));
    }

    @Test
    public void testManifestRadarFinanceVsSandboxSeparation() {
        ManifestRadar.ScanReport chrome = ManifestRadar.buildReport("com.android.chrome", "Chrome", 40, 2, 1, Collections.<String>emptyList());
        assertTrue(chrome.isSandbox());
        assertFalse(chrome.isAutoEligible());
        assertEquals(PolicyFlags.PRESET_NONE, chrome.recommendedFlags);
        assertEquals("SAFE OFF", chrome.recommendLabel);

        ManifestRadar.ScanReport brave = ManifestRadar.buildReport("com.brave.browser", "Brave", 20, 1, 0, Collections.<String>emptyList());
        assertTrue(brave.isSandbox());
        assertEquals(PolicyFlags.PRESET_NONE, brave.recommendedFlags);

        ManifestRadar.ScanReport dana = ManifestRadar.buildReport("id.dana", "DANA", 2, 1, 0, Collections.singletonList("ALIPAY"));
        assertTrue(dana.isFinance());
        assertTrue(dana.isAutoEligible());
        assertEquals(PolicyFlags.PRESET_BANKING_STRICT, dana.recommendedFlags);

        ManifestRadar.ScanReport octo = ManifestRadar.buildReport("id.co.cimbniaga.mobile.android", "OCTO Mobile", 1, 1, 0, Collections.singletonList("VKEY"));
        assertTrue(octo.isFinance());
        assertEquals(ManifestRadar.RISK_CRITICAL, octo.riskLevel);
        assertEquals(PolicyFlags.PRESET_BANKING_MAX, octo.recommendedFlags);

        assertEquals("ALIPAY", ManifestRadar.matchElfSdkTag("lib/arm64-v8a/libsgmainso-6.5.so"));
        assertEquals("VKEY", ManifestRadar.matchElfSdkTag("lib/arm64-v8a/libvosWrapper.so"));
        assertEquals("PROMON", ManifestRadar.matchElfSdkTag("lib/arm64-v8a/libshield.so"));
    }
}
