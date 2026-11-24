package io.github.taro902.isolationpolicy.hook;

import android.os.Binder;
import android.view.InputDevice;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import io.github.libxposed.api.XposedInterface;
import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class InputSurfaceShield {

    private static final int POINTER_SOURCE_BITS = 0x00001000 | 0x00002000 | 0x00004000;
    private static final ThreadLocal<Boolean> IN_SELF_LOOKUP = new ThreadLocal<Boolean>();

    private InputSurfaceShield() {
    }

    public static boolean shouldFilterDescriptor(
        int deviceId,
        boolean isExternal,
        int vendorId,
        int productId,
        int sources,
        String name,
        String descriptor
    ) {
        if (deviceId <= 0) {
            return false;
        }
        String lowName = name != null ? name.toLowerCase(Locale.US) : "";
        String lowDesc = descriptor != null ? descriptor.toLowerCase(Locale.US) : "";

        if (!isExternal && isInternalHardwareSensor(lowName)) {
            return false;
        }

        if (matchesStrictInjectorToken(lowName) || matchesStrictInjectorToken(lowDesc)) {
            return true;
        }

        boolean isPointerLike = (sources & POINTER_SOURCE_BITS) != 0;
        if ((isExternal || isPointerLike) && (matchesVirtualBusToken(lowName) || matchesVirtualBusToken(lowDesc))) {
            return true;
        }

        if (isExternal && vendorId == 0 && productId == 0 && isPointerLike) {
            return true;
        }

        return false;
    }

    public static boolean shouldFilterInputDevice(InputDevice dev) {
        if (dev == null) {
            return false;
        }
        int id = dev.getId();
        if (id <= 0 || dev.isVirtual()) {
            return false;
        }
        return shouldFilterDescriptor(
            id,
            dev.isExternal(),
            dev.getVendorId(),
            dev.getProductId(),
            dev.getSources(),
            dev.getName(),
            dev.getDescriptor()
        );
    }

    private static boolean isInternalHardwareSensor(String lowName) {
        return lowName.contains("fpc")
            || lowName.contains("goodix")
            || lowName.contains("fingerprint")
            || lowName.contains("gpio-keys")
            || lowName.contains("qpnp_pon")
            || lowName.contains("headset")
            || lowName.contains("snd-card");
    }

    private static boolean matchesStrictInjectorToken(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        return s.contains("scrcpy")
            || s.contains("uhid")
            || s.contains("virtio")
            || s.contains("monkey")
            || s.contains("inject")
            || s.contains("adb_input")
            || s.contains("autojs")
            || s.contains("touchsprite")
            || s.contains("vinput");
    }

    private static boolean matchesVirtualBusToken(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        return s.contains("uinput")
            || s.contains("virtual")
            || s.contains("adb");
    }

    public static void install(
        XposedInterface module,
        ClassLoader cl,
        final PolicyEngine engine,
        final TelemetryHub hub
    ) {
        Class<?> imsCls = CompatMatrix.findClass(cl, "com.android.server.input.InputManagerService");
        if (imsCls == null) {
            hub.setPillarStatus(PolicyFlags.PILLAR_INP, TelemetryHub.HEALTH_OFF, CompatMatrix.TIER_NONE);
            return;
        }

        final Method mGetDevice = CompatMatrix.findExactMethod(imsCls, "getInputDevice", int.class);
        Method mGetIds = CompatMatrix.findExactMethod(imsCls, "getInputDeviceIds");
        if (mGetDevice != null) {
            mGetDevice.setAccessible(true);
        }

        int installed = 0;

        if (mGetDevice != null) {
            HookGuard devGuard = hub.createGuard(PolicyFlags.PILLAR_INP, "INP_DEV");
            installed += CompatMatrix.hookMethod(module, mGetDevice, XposedInterface.PRIORITY_DEFAULT, devGuard, new HookGuard.Handler() {
                @Override
                public Object handle(XposedInterface.Chain chain) throws Throwable {
                    if (Boolean.TRUE.equals(IN_SELF_LOOKUP.get())) {
                        return HookGuard.proceedOriginal(chain);
                    }
                    List<Object> args = chain.getArgs();
                    if (args.isEmpty() || !(args.get(0) instanceof Integer)) {
                        return HookGuard.proceedOriginal(chain);
                    }
                    int devId = (Integer) args.get(0);
                    if (devId <= 0) {
                        return HookGuard.proceedOriginal(chain);
                    }
                    int callingUid = Binder.getCallingUid();
                    int flags = engine.resolvePolicy(callingUid);
                    if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_INPUT_SURFACE)) {
                        return HookGuard.proceedOriginal(chain);
                    }
                    Object ret = HookGuard.proceedOriginal(chain);
                    if (!(ret instanceof InputDevice)) {
                        return ret;
                    }
                    if (!shouldFilterInputDevice((InputDevice) ret)) {
                        return ret;
                    }
                    int effAppId = engine.resolveEffectiveAppId(callingUid);
                    if (PolicyFlags.isAuditOnly(flags)) {
                        hub.record(effAppId, PolicyFlags.PILLAR_INP, PolicyFlags.VEC_INP_DEVICE_INFO, PolicyFlags.ACT_AUDIT, true, devId);
                        return ret;
                    }
                    hub.record(effAppId, PolicyFlags.PILLAR_INP, PolicyFlags.VEC_INP_DEVICE_INFO, PolicyFlags.ACT_SUPPRESSED, false, devId);
                    return null;
                }
            });
        }

        if (mGetIds != null && mGetDevice != null) {
            HookGuard idsGuard = hub.createGuard(PolicyFlags.PILLAR_INP, "INP_IDS");
            installed += CompatMatrix.hookMethod(module, mGetIds, XposedInterface.PRIORITY_DEFAULT, idsGuard, new HookGuard.Handler() {
                @Override
                public Object handle(XposedInterface.Chain chain) throws Throwable {
                    int callingUid = Binder.getCallingUid();
                    int flags = engine.resolvePolicy(callingUid);
                    if (!PolicyFlags.hasFlag(flags, PolicyFlags.FLAG_INPUT_SURFACE)) {
                        return HookGuard.proceedOriginal(chain);
                    }
                    Object ret = HookGuard.proceedOriginal(chain);
                    if (!(ret instanceof int[])) {
                        return ret;
                    }
                    int[] ids = (int[]) ret;
                    if (ids.length == 0) {
                        return ret;
                    }
                    Object self = chain.getThisObject();
                    if (self == null) {
                        return ret;
                    }
                    int[] kept = new int[ids.length];
                    int keptCount = 0;
                    int removed = 0;
                    IN_SELF_LOOKUP.set(Boolean.TRUE);
                    try {
                        for (int i = 0; i < ids.length; i++) {
                            int id = ids[i];
                            if (id <= 0) {
                                kept[keptCount++] = id;
                                continue;
                            }
                            Object devObj = mGetDevice.invoke(self, Integer.valueOf(id));
                            if (devObj instanceof InputDevice && shouldFilterInputDevice((InputDevice) devObj)) {
                                removed++;
                            } else {
                                kept[keptCount++] = id;
                            }
                        }
                    } finally {
                        IN_SELF_LOOKUP.remove();
                    }
                    if (removed <= 0) {
                        return ret;
                    }
                    int effAppId = engine.resolveEffectiveAppId(callingUid);
                    if (PolicyFlags.isAuditOnly(flags)) {
                        hub.record(effAppId, PolicyFlags.PILLAR_INP, PolicyFlags.VEC_INP_DEVICE_IDS, PolicyFlags.ACT_AUDIT, true, removed);
                        return ret;
                    }
                    int[] trimmed = new int[keptCount];
                    System.arraycopy(kept, 0, trimmed, 0, keptCount);
                    hub.record(effAppId, PolicyFlags.PILLAR_INP, PolicyFlags.VEC_INP_DEVICE_IDS, PolicyFlags.ACT_SANITIZED, false, removed);
                    return trimmed;
                }
            });
        }

        hub.setPillarStatus(
            PolicyFlags.PILLAR_INP,
            installed > 0 ? TelemetryHub.HEALTH_OK : TelemetryHub.HEALTH_OFF,
            installed > 0 ? CompatMatrix.TIER_EXACT : CompatMatrix.TIER_NONE
        );
    }
}
