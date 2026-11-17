package io.github.mhmrdd.isolationpolicy;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public final class BindHook {

    private static final String HOSTING_RECORD = "com.android.server.am.HostingRecord";
    private static final String PROCESS_RECORD = "com.android.server.am.ProcessRecord";
    private static final String PROCESS_LIST = "com.android.server.am.ProcessList";

    private static final int ZYGOTE_REGULAR = 0;
    private static final int ZYGOTE_APP = 2;

    private static final AtomicReference<Set<String>> sDeniedPackages =
            new AtomicReference<>(Collections.singleton(Constants.APPLICATION_ID));
    private static final AtomicReference<Set<String>> sDropOverrides =
            new AtomicReference<>(Collections.<String>emptySet());
    private static final AtomicReference<Set<String>> sDowngradeOverrides =
            new AtomicReference<>(Collections.<String>emptySet());
    private static final AtomicBoolean sGlobalDropMode = new AtomicBoolean(false);

    private static final Map<Object, String> sHostingRecordPkgMap =
            Collections.synchronizedMap(new WeakHashMap<Object, String>());
    private static final Map<Object, Boolean> sDowngradedRecords =
            Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());

    private static final ThreadLocal<Boolean> sForceRegularZygote =
            new ThreadLocal<Boolean>() {
                @Override
                protected Boolean initialValue() {
                    return Boolean.FALSE;
                }
            };

    private static volatile SharedPreferences sRemotePrefs;
    private static volatile SharedPreferences.OnSharedPreferenceChangeListener sPrefListener;

    private static volatile Field sHostingZygoteField;
    private static volatile Field sDefiningPackageNameField;
    private static volatile Method sUsesAppZygoteMethod;
    private static volatile Field sProcessRecordInfoField;

    private BindHook() {}

    public static void install(final XposedModule module, final ClassLoader cl) {
        initRemotePrefs(module);
        initReflectionCache(cl);
        hookHostingRecord(module, cl);
        hookProcessList(module, cl);
    }

    private static void initRemotePrefs(final XposedModule module) {
        try {
            final SharedPreferences prefs = module.getRemotePreferences(Constants.PREFS_NAME);
            if (prefs == null) return;
            sRemotePrefs = prefs;
            syncPrefs(prefs);
            SharedPreferences.OnSharedPreferenceChangeListener listener =
                    new SharedPreferences.OnSharedPreferenceChangeListener() {
                        @Override
                        public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
                            syncPrefs(sp);
                        }
                    };
            sPrefListener = listener;
            prefs.registerOnSharedPreferenceChangeListener(listener);
        } catch (Throwable t) {
            Logger.e("initRemotePrefs failed", t);
        }
    }

    private static void syncPrefs(SharedPreferences prefs) {
        try {
            Set<String> rawDenied = prefs.getStringSet(Constants.KEY_DENIED, Collections.<String>emptySet());
            Set<String> nextDenied = new HashSet<>();
            if (rawDenied != null) {
                nextDenied.addAll(rawDenied);
            }
            nextDenied.add(Constants.APPLICATION_ID);
            sDeniedPackages.set(Collections.unmodifiableSet(nextDenied));

            Set<String> rawDrop = prefs.getStringSet(Constants.KEY_DROP_OVERRIDES, Collections.<String>emptySet());
            Set<String> nextDrop = new HashSet<>();
            if (rawDrop != null) {
                nextDrop.addAll(rawDrop);
            }
            sDropOverrides.set(Collections.unmodifiableSet(nextDrop));

            Set<String> rawDown = prefs.getStringSet(Constants.KEY_DOWNGRADE_OVERRIDES, Collections.<String>emptySet());
            Set<String> nextDown = new HashSet<>();
            if (rawDown != null) {
                nextDown.addAll(rawDown);
            }
            sDowngradeOverrides.set(Collections.unmodifiableSet(nextDown));

            sGlobalDropMode.set(prefs.getBoolean(Constants.KEY_MODE_DROP, false));
        } catch (Throwable t) {
            Logger.e("syncPrefs failed", t);
        }
    }

    private static void initReflectionCache(ClassLoader cl) {
        try {
            Class<?> hrClass = Class.forName(HOSTING_RECORD, false, cl);
            try {
                Field f = hrClass.getDeclaredField("mHostingZygote");
                f.setAccessible(true);
                sHostingZygoteField = f;
            } catch (Throwable ignored) {}
            try {
                Field f = hrClass.getDeclaredField("mDefiningPackageName");
                f.setAccessible(true);
                sDefiningPackageNameField = f;
            } catch (Throwable ignored) {}
            try {
                Method m = hrClass.getDeclaredMethod("usesAppZygote");
                m.setAccessible(true);
                sUsesAppZygoteMethod = m;
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}

        try {
            Class<?> prClass = Class.forName(PROCESS_RECORD, false, cl);
            Field f = prClass.getDeclaredField("info");
            f.setAccessible(true);
            sProcessRecordInfoField = f;
        } catch (Throwable ignored) {}
    }

    private static void hookHostingRecord(final XposedModule module, ClassLoader cl) {
        try {
            Class<?> hrClass = Class.forName(HOSTING_RECORD, false, cl);
            Method[] methods = hrClass.getDeclaredMethods();
            for (final Method m : methods) {
                String name = m.getName();
                if ("usesAppZygote".equals(name) && m.getParameterTypes().length == 0) {
                    try { module.deoptimize(m); } catch (Throwable ignored) {}
                    module.hook(m)
                        .setPriority(XposedInterface.PRIORITY_HIGHEST)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            if (Boolean.TRUE.equals(sForceRegularZygote.get())) {
                                return Boolean.FALSE;
                            }
                            Object self = chain.getThisObject();
                            if (self != null) {
                                if (Boolean.TRUE.equals(sDowngradedRecords.get(self))) {
                                    return Boolean.FALSE;
                                }
                                if (isForcedRegularOnInstance(self)) {
                                    return Boolean.FALSE;
                                }
                            }
                            return chain.proceed();
                        });
                } else if ("byAppZygote".equals(name)) {
                    try { module.deoptimize(m); } catch (Throwable ignored) {}
                    module.hook(m)
                        .setPriority(XposedInterface.PRIORITY_HIGHEST)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            if (result == null) return null;

                            String pkg = extractPackageFromByAppZygoteArgs(chain.getArgs(), result);
                            if (pkg != null && !pkg.isEmpty()) {
                                sHostingRecordPkgMap.put(result, pkg);
                                if (isDenied(pkg) && !shouldDrop(pkg)) {
                                    downgradeToRegularZygote(result);
                                }
                            }
                            return result;
                        });
                }
            }
        } catch (Throwable t) {
            Logger.e("hookHostingRecord failed", t);
        }
    }

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
                } else if ("startAppZygoteIfNeededLocked".equals(name) || "createAppZygoteForProcessIfNeeded".equals(name)) {
                    try { module.deoptimize(m); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable t) {
            Logger.e("hookProcessList failed", t);
        }
    }

    private static void installStartProcessLockedHook(final XposedModule module, final Method method) {
        final Class<?> returnType = method.getReturnType();
        final boolean returnsBoolean = (returnType == Boolean.TYPE || returnType == Boolean.class);
        final boolean returnsProcessRecord = PROCESS_RECORD.equals(returnType.getName());
        if (!returnsBoolean && !returnsProcessRecord) {
            return;
        }

        module.hook(method)
            .setPriority(XposedInterface.PRIORITY_HIGHEST)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                List<Object> args = chain.getArgs();
                Object hr = null;
                Object pr = null;
                ApplicationInfo ai = null;

                for (Object arg : args) {
                    if (arg == null) continue;
                    if (arg instanceof ApplicationInfo && ai == null) {
                        ai = (ApplicationInfo) arg;
                        continue;
                    }
                    String cn = arg.getClass().getName();
                    if (hr == null && HOSTING_RECORD.equals(cn)) {
                        hr = arg;
                    } else if (pr == null && PROCESS_RECORD.equals(cn)) {
                        pr = arg;
                    }
                }

                if (hr == null) {
                    return chain.proceed();
                }

                boolean isAppZygote = usesAppZygote(hr) || Boolean.TRUE.equals(sDowngradedRecords.get(hr));
                if (!isAppZygote) {
                    return chain.proceed();
                }

                String pkg = resolvePackageUniversal(hr, pr, ai);
                if (pkg == null || !isDenied(pkg)) {
                    return chain.proceed();
                }

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
            });
    }

    private static void installStartProcessWorkerHook(final XposedModule module, final Method method) {
        module.hook(method)
            .setPriority(XposedInterface.PRIORITY_HIGHEST)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(chain -> {
                List<Object> args = chain.getArgs();
                Object hr = null;
                Object pr = null;

                for (Object arg : args) {
                    if (arg == null) continue;
                    String cn = arg.getClass().getName();
                    if (hr == null && HOSTING_RECORD.equals(cn)) {
                        hr = arg;
                    } else if (pr == null && PROCESS_RECORD.equals(cn)) {
                        pr = arg;
                    }
                }

                if (hr == null) {
                    return chain.proceed();
                }

                boolean isAppZygote = usesAppZygote(hr) || Boolean.TRUE.equals(sDowngradedRecords.get(hr));
                if (!isAppZygote) {
                    return chain.proceed();
                }

                String pkg = resolvePackageUniversal(hr, pr, null);
                if (pkg == null || !isDenied(pkg)) {
                    return chain.proceed();
                }

                if (!shouldDrop(pkg)) {
                    downgradeToRegularZygote(hr);
                    sForceRegularZygote.set(Boolean.TRUE);
                    try {
                        return chain.proceed();
                    } finally {
                        sForceRegularZygote.set(Boolean.FALSE);
                    }
                }
                return chain.proceed();
            });
    }

    private static String extractPackageFromByAppZygoteArgs(List<Object> args, Object hrInstance) {
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof ComponentName) {
                    String cnPkg = ((ComponentName) arg).getPackageName();
                    if (cnPkg != null && !cnPkg.isEmpty()) {
                        return cnPkg;
                    }
                }
            }
            for (Object arg : args) {
                if (arg instanceof String) {
                    String s = (String) arg;
                    if (!s.isEmpty() && s.indexOf('.') > 0 && s.indexOf(':') < 0 && s.indexOf('/') < 0) {
                        return s;
                    }
                }
            }
        }
        return readDefiningPackageField(hrInstance);
    }

    private static String readDefiningPackageField(Object hostingRecord) {
        if (hostingRecord == null) return null;
        try {
            Field f = sDefiningPackageNameField;
            if (f == null) {
                f = hostingRecord.getClass().getDeclaredField("mDefiningPackageName");
                f.setAccessible(true);
                sDefiningPackageNameField = f;
            }
            Object val = f.get(hostingRecord);
            if (val instanceof String) {
                String s = (String) val;
                if (!s.isEmpty()) return s;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void downgradeToRegularZygote(Object hostingRecord) {
        if (hostingRecord == null) return;
        sDowngradedRecords.put(hostingRecord, Boolean.TRUE);
        try {
            Field f = sHostingZygoteField;
            if (f == null) {
                f = hostingRecord.getClass().getDeclaredField("mHostingZygote");
                f.setAccessible(true);
                sHostingZygoteField = f;
            }
            f.setInt(hostingRecord, ZYGOTE_REGULAR);
        } catch (Throwable ignored) {}
    }

    private static boolean isForcedRegularOnInstance(Object hostingRecord) {
        try {
            Field f = sHostingZygoteField;
            if (f != null) {
                return f.getInt(hostingRecord) == ZYGOTE_REGULAR;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean usesAppZygote(Object hostingRecord) {
        try {
            Field f = sHostingZygoteField;
            if (f == null) {
                f = hostingRecord.getClass().getDeclaredField("mHostingZygote");
                f.setAccessible(true);
                sHostingZygoteField = f;
            }
            return f.getInt(hostingRecord) == ZYGOTE_APP;
        } catch (Throwable ignored) {}

        try {
            Method m = sUsesAppZygoteMethod;
            if (m == null) {
                m = hostingRecord.getClass().getDeclaredMethod("usesAppZygote");
                m.setAccessible(true);
                sUsesAppZygoteMethod = m;
            }
            Object res = m.invoke(hostingRecord);
            if (res instanceof Boolean) {
                return ((Boolean) res).booleanValue();
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static String resolvePackageUniversal(Object hostingRecord, Object processRecord, ApplicationInfo directAi) {
        if (hostingRecord != null) {
            String mapped = sHostingRecordPkgMap.get(hostingRecord);
            if (mapped != null && !mapped.isEmpty()) {
                return mapped;
            }
            String defPkg = readDefiningPackageField(hostingRecord);
            if (defPkg != null && !defPkg.isEmpty()) {
                sHostingRecordPkgMap.put(hostingRecord, defPkg);
                return defPkg;
            }
        }

        if (directAi != null && directAi.packageName != null && !directAi.packageName.isEmpty()) {
            if (hostingRecord != null) {
                sHostingRecordPkgMap.put(hostingRecord, directAi.packageName);
            }
            return directAi.packageName;
        }

        if (processRecord != null) {
            try {
                Field f = sProcessRecordInfoField;
                if (f == null) {
                    f = processRecord.getClass().getDeclaredField("info");
                    f.setAccessible(true);
                    sProcessRecordInfoField = f;
                }
                Object info = f.get(processRecord);
                if (info instanceof ApplicationInfo) {
                    String pkg = ((ApplicationInfo) info).packageName;
                    if (pkg != null && !pkg.isEmpty()) {
                        if (hostingRecord != null) {
                            sHostingRecordPkgMap.put(hostingRecord, pkg);
                        }
                        return pkg;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static boolean isDenied(String pkg) {
        if (pkg == null) return false;
        if (Constants.APPLICATION_ID.equals(pkg)) return true;
        Set<String> denied = sDeniedPackages.get();
        return denied != null && denied.contains(pkg);
    }

    private static boolean shouldDrop(String pkg) {
        if (pkg == null) return sGlobalDropMode.get();
        Set<String> dropSet = sDropOverrides.get();
        if (dropSet != null && dropSet.contains(pkg)) {
            return true;
        }
        Set<String> downSet = sDowngradeOverrides.get();
        if (downSet != null && downSet.contains(pkg)) {
            return false;
        }
        return sGlobalDropMode.get();
    }
}
