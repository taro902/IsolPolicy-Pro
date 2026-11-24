package io.github.taro902.isolationpolicy.hook;

import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;
import io.github.taro902.isolationpolicy.BuildConfig;
import io.github.taro902.isolationpolicy.core.Logger;

public final class Entry extends XposedModule {

    private static final PolicyEngine ENGINE = new PolicyEngine();
    private static final TelemetryHub HUB = new TelemetryHub();
    private static final AtomicBoolean SYSTEM_INSTALLED = new AtomicBoolean(false);

    public Entry() {
        super();
    }

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        Logger.attach(this);
        try {
            HUB.setFrameworkApiVersion(getApiVersion());
        } catch (Throwable ignored) {
        }
        if (param != null && param.isSystemServer()) {
            Logger.i("IsolPolicy " + BuildConfig.VERSION_NAME + " loaded in system_server (api=" + HUB.getFrameworkApiVersion() + ")");
        }
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        if (param == null || !SYSTEM_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        Logger.attach(this);
        ClassLoader cl = param.getClassLoader();
        ENGINE.attachModule(this);

        installPillarSafe("AZ", new Runnable() {
            @Override
            public void run() {
                AppZygoteShield.install(Entry.this, cl, ENGINE, HUB);
            }
        });
        installPillarSafe("ISO", new Runnable() {
            @Override
            public void run() {
                IsolatedFoldShield.install(Entry.this, cl, ENGINE, HUB);
            }
        });
        installPillarSafe("SET_EARLY", new Runnable() {
            @Override
            public void run() {
                SettingsShield.installIfPresent(Entry.this, cl, ENGINE, HUB, BuildConfig.VERSION_NAME);
            }
        });
        installPillarSafe("USB", new Runnable() {
            @Override
            public void run() {
                UsbBroadcastShield.install(Entry.this, cl, ENGINE, HUB);
            }
        });
        installPillarSafe("A11Y", new Runnable() {
            @Override
            public void run() {
                AccessibilityShield.install(Entry.this, cl, ENGINE, HUB);
            }
        });
        installPillarSafe("PKG", new Runnable() {
            @Override
            public void run() {
                PackageSurfaceShield.install(Entry.this, cl, ENGINE, HUB);
            }
        });
        installPillarSafe("DISP", new Runnable() {
            @Override
            public void run() {
                DisplayShield.install(Entry.this, cl, ENGINE, HUB);
            }
        });
        installPillarSafe("PROC", new Runnable() {
            @Override
            public void run() {
                ProcessSurfaceShield.install(Entry.this, cl, ENGINE, HUB);
            }
        });
        installPillarSafe("INP", new Runnable() {
            @Override
            public void run() {
                InputSurfaceShield.install(Entry.this, cl, ENGINE, HUB);
            }
        });
        installPillarSafe("OBS", new Runnable() {
            @Override
            public void run() {
                ObserverCoherenceShield.install(Entry.this, cl, ENGINE, HUB);
            }
        });

        int mask = HUB.computeHealthMask();
        Logger.i("IsolPolicy " + BuildConfig.VERSION_NAME + " active pillars=" + TelemetryHub.countHealthyPillars(mask) + "/10");
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (param == null) {
            return;
        }
        String pkg = param.getPackageName();
        if ("com.android.providers.settings".equals(pkg) || "android".equals(pkg)) {
            SettingsShield.installIfPresent(this, param.getDefaultClassLoader(), ENGINE, HUB, BuildConfig.VERSION_NAME);
        }
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (param == null) {
            return;
        }
        String pkg = param.getPackageName();
        if ("com.android.providers.settings".equals(pkg) || "android".equals(pkg)) {
            SettingsShield.installIfPresent(this, param.getClassLoader(), ENGINE, HUB, BuildConfig.VERSION_NAME);
        }
    }

    private static void installPillarSafe(String name, Runnable action) {
        try {
            action.run();
        } catch (Throwable t) {
            Logger.e("pillar init failed: " + name, t);
        }
    }
}
