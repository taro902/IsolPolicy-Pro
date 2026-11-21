package io.github.taro902.isolationpolicy.hook;

import androidx.annotation.NonNull;

import io.github.libxposed.api.XposedModule;
import io.github.taro902.isolationpolicy.core.Logger;

public final class Entry extends XposedModule {

    private static final String SETTINGS_PROVIDER_PKG = "com.android.providers.settings";

    private final PolicyEngine engine = new PolicyEngine();
    private final TelemetryHub telemetry = new TelemetryHub();
    private final AppZygoteShield appZygoteShield = new AppZygoteShield();
    private final IsolatedFoldShield isolatedFoldShield = new IsolatedFoldShield();
    private final SettingsShield settingsShield = new SettingsShield();
    private final UsbBroadcastShield usbBroadcastShield = new UsbBroadcastShield();
    private final AccessibilityShield accessibilityShield = new AccessibilityShield();
    private final PackageChangeShield packageChangeShield = new PackageChangeShield();
    private final DisplayShield displayShield = new DisplayShield();

    private volatile boolean systemServerMode;

    public Entry() {
        super();
    }

    @Override
    public void onModuleLoaded(@NonNull ModuleLoadedParam param) {
        if (!param.isSystemServer()) {
            detach();
            return;
        }
        systemServerMode = true;
        Logger.bind(this);
        engine.init(this);
    }

    @Override
    public void onSystemServerStarting(@NonNull SystemServerStartingParam param) {
        if (!systemServerMode) {
            return;
        }
        ClassLoader sysCl = param.getClassLoader();
        appZygoteShield.install(this, sysCl, engine, telemetry);
        isolatedFoldShield.install(this, sysCl, engine, telemetry);
        usbBroadcastShield.install(this, sysCl, engine, telemetry);
        accessibilityShield.install(this, sysCl, engine, telemetry);
        packageChangeShield.install(this, sysCl, engine, telemetry);
        displayShield.install(this, sysCl, engine, telemetry);
        settingsShield.attachIfNeeded(this, sysCl, engine, telemetry);
    }

    @Override
    public void onPackageLoaded(@NonNull PackageLoadedParam param) {
        if (!systemServerMode) {
            return;
        }
        if (SETTINGS_PROVIDER_PKG.equals(param.getPackageName())) {
            settingsShield.attachIfNeeded(this, param.getDefaultClassLoader(), engine, telemetry);
        }
    }

    @Override
    public void onPackageReady(@NonNull PackageReadyParam param) {
        if (!systemServerMode) {
            return;
        }
        if (SETTINGS_PROVIDER_PKG.equals(param.getPackageName())) {
            settingsShield.attachIfNeeded(this, param.getClassLoader(), engine, telemetry);
        }
    }
}