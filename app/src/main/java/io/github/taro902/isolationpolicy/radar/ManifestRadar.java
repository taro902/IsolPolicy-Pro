package io.github.taro902.isolationpolicy.radar;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.AssetManager;
import android.content.res.XmlResourceParser;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class ManifestRadar {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final int FLAG_ISOLATED = 0x0002;
    private static final int FLAG_EXTERNAL = 0x0004;
    private static final int FLAG_USE_APP_ZYGOTE = 0x0008;

    private static final String[] FINANCE_TOKENS = {
            "id.dana", "com.bca", "mybca", "cimbniaga", "octomobile",
            "id.co.bri", "brimo", "bankmandiri", "livin", "id.bni", "wondr",
            "seabank", "bankjago", "jago", "blubybcadigital", "ovo.id",
            "shopeepay", "com.shopee.id", "linkaja", "jenius", "btpn",
            "ocbc", "danamon", "permata", "bankbsi", "bsimobile",
            "allobank", "krom", "superbank", "neobank", "bnc", "flip",
            "bibit", "ajaib", "stockbit", "gojek", "gopay", "kredivo",
            "akulaku", "indodax", "pintu", "maybank", "panin", "mega",
            "uob", "hsbc", "dbs", "citi", "btn"
    };

    private static final String[] SANDBOX_PREFIXES = {
            "com.android.chrome", "com.chrome.", "com.brave.browser",
            "org.mozilla.firefox", "org.mozilla.fenix", "com.microsoft.emmx",
            "com.opera.", "com.sec.android.app.sbrowser", "com.vivaldi.browser",
            "com.duckduckgo.mobile.android", "com.kiwibrowser.",
            "com.google.android.webview", "com.android.webview", "org.chromium.",
            "com.whatsapp", "org.telegram.", "org.thunderdog.challegram",
            "com.facebook.", "com.instagram.", "com.instaprime.",
            "com.google.android.googlequicksearchbox", "com.google.android.apps.docs",
            "com.google.android.gm", "com.google.android.youtube",
            "com.zhiliaoapp.musically", "com.ss.android.ugc.", "com.twitter.android",
            "com.discord", "com.lemon.lvoverseas"
    };

    private static final ConcurrentHashMap<String, CachedScan> CACHE = new ConcurrentHashMap<>(128);

    private ManifestRadar() {
    }

    public static ScanReport scanPackage(Context context, PackageInfo pi) {
        if (pi == null || pi.applicationInfo == null) {
            return ScanReport.EMPTY;
        }
        String pkg = pi.packageName;
        long updated = pi.lastUpdateTime;
        CachedScan cached = CACHE.get(pkg);
        if (cached != null && cached.lastUpdateTime == updated) {
            return cached.report;
        }

        Set<String> azServices = new LinkedHashSet<>();
        Set<String> isoServices = new LinkedHashSet<>();
        int externalIsoCount = 0;

        if (pi.services != null) {
            for (ServiceInfo si : pi.services) {
                if (si == null) {
                    continue;
                }
                int f = si.flags;
                String sName = si.name != null ? si.name : "unnamed";
                if ((f & FLAG_USE_APP_ZYGOTE) != 0) {
                    azServices.add(sName);
                } else if ((f & FLAG_ISOLATED) != 0) {
                    if ((f & FLAG_EXTERNAL) != 0) {
                        externalIsoCount++;
                    } else {
                        isoServices.add(sName);
                    }
                }
            }
        }

        AxmlResult axml = inspectBinaryManifest(context, pkg);
        azServices.addAll(axml.azServices);
        isoServices.addAll(axml.isoServices);
        externalIsoCount = Math.max(externalIsoCount, axml.externalIsoCount);

        Set<String> sdks = scanNativeAntifraudSdks(pi.applicationInfo);
        boolean finance = isFinancePackage(pkg);
        boolean sandbox = !finance && isSandboxPackage(pkg);
        boolean usesAz = !azServices.isEmpty() || (axml.zygotePreloadName != null && !axml.zygotePreloadName.isEmpty());

        int score;
        int recFlags;
        if (sandbox) {
            score = (usesAz || !isoServices.isEmpty()) ? 12 : 0;
            recFlags = PolicyFlags.PRESET_NONE;
        } else {
            score = 0;
            if (usesAz) score += 45;
            if (!isoServices.isEmpty()) score += 25;
            if (!sdks.isEmpty()) score += Math.min(35, sdks.size() * 20);
            if (axml.queriesAllPackages) score += 10;
            if (finance) score += 15;
            if (score > 100) score = 100;
            recFlags = computeRecommendedFlags(usesAz, !isoServices.isEmpty(), sdks, finance, score);
        }

        ScanReport report = new ScanReport(
                usesAz,
                axml.zygotePreloadName,
                new ArrayList<>(azServices),
                new ArrayList<>(isoServices),
                externalIsoCount,
                axml.queriesAllPackages,
                new ArrayList<>(sdks),
                finance,
                sandbox,
                score,
                recFlags
        );
        CACHE.put(pkg, new CachedScan(updated, report));
        return report;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static int computeRecommendedFlags(
            boolean usesAz,
            boolean hasInternalIso,
            Set<String> sdks,
            boolean finance,
            int score
    ) {
        if (!usesAz && !hasInternalIso && sdks.isEmpty() && !finance) {
            return PolicyFlags.PRESET_NONE;
        }
        int flags = 0;
        if (usesAz) {
            flags |= PolicyFlags.AZ_DOWNGRADE;
        }
        if (hasInternalIso && !finance) {
            flags |= PolicyFlags.FLAG_ISO_FOLD;
        }
        if (finance || !sdks.isEmpty() || score >= 45) {
            flags |= PolicyFlags.FLAG_DEV_COHERENT;
            flags |= PolicyFlags.FLAG_A11Y_COHERENT;
            flags |= PolicyFlags.FLAG_PKG_CHANGES;
        }
        if (finance && !usesAz) {
            flags |= PolicyFlags.FLAG_DISP_STEALTH;
        }
        return flags & PolicyFlags.ALL_SHIELDS_MASK;
    }

    private static boolean isFinancePackage(String pkg) {
        if (pkg == null) {
            return false;
        }
        String lower = pkg.toLowerCase(Locale.ROOT);
        for (String token : FINANCE_TOKENS) {
            if (lower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSandboxPackage(String pkg) {
        if (pkg == null) {
            return false;
        }
        String lower = pkg.toLowerCase(Locale.ROOT);
        for (String prefix : SANDBOX_PREFIXES) {
            if (lower.equals(prefix) || lower.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static AxmlResult inspectBinaryManifest(Context context, String packageName) {
        AxmlResult out = new AxmlResult();
        XmlResourceParser xml = null;
        try {
            Context pkgCtx = context.createPackageContext(packageName, 0);
            AssetManager am = pkgCtx.getAssets();
            xml = am.openXmlResourceParser("AndroidManifest.xml");
            int event = xml.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String tag = xml.getName();
                    if ("application".equals(tag)) {
                        String preload = readAttrString(xml, "zygotePreloadName");
                        if (preload != null && !preload.isEmpty()) {
                            out.zygotePreloadName = preload;
                        }
                    } else if ("service".equals(tag)) {
                        String sName = readAttrString(xml, "name");
                        boolean useAz = readAttrBool(xml, "useAppZygote");
                        boolean iso = readAttrBool(xml, "isolatedProcess");
                        boolean ext = readAttrBool(xml, "externalService");
                        if (useAz) {
                            out.azServices.add(sName != null ? sName : "axml.service");
                        } else if (iso) {
                            if (ext) {
                                out.externalIsoCount++;
                            } else {
                                out.isoServices.add(sName != null ? sName : "axml.isolated");
                            }
                        }
                    } else if ("uses-permission".equals(tag)) {
                        String perm = readAttrString(xml, "name");
                        if ("android.permission.QUERY_ALL_PACKAGES".equals(perm)) {
                            out.queriesAllPackages = true;
                        }
                    }
                }
                event = xml.next();
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        } catch (Throwable ignored) {
        } finally {
            if (xml != null) {
                try {
                    xml.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return out;
    }

    private static String readAttrString(XmlResourceParser xml, String attrName) {
        String val = xml.getAttributeValue(ANDROID_NS, attrName);
        if (val != null) {
            return val;
        }
        int count = xml.getAttributeCount();
        for (int i = 0; i < count; i++) {
            if (attrName.equals(xml.getAttributeName(i))) {
                return xml.getAttributeValue(i);
            }
        }
        return null;
    }

    private static boolean readAttrBool(XmlResourceParser xml, String attrName) {
        boolean v = xml.getAttributeBooleanValue(ANDROID_NS, attrName, false);
        if (v) {
            return true;
        }
        String raw = readAttrString(xml, attrName);
        return "true".equalsIgnoreCase(raw) || "1".equals(raw);
    }

    private static Set<String> scanNativeAntifraudSdks(ApplicationInfo ai) {
        Set<String> found = new LinkedHashSet<>();
        if (ai == null) {
            return found;
        }
        if (ai.sourceDir != null) {
            scanApkEntries(ai.sourceDir, found);
        }
        if (ai.splitSourceDirs != null) {
            for (String split : ai.splitSourceDirs) {
                if (split != null) {
                    scanApkEntries(split, found);
                }
            }
        }
        return found;
    }

    private static void scanApkEntries(String apkPath, Set<String> found) {
        ZipFile zf = null;
        try {
            zf = new ZipFile(apkPath);
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.startsWith("lib/") || !name.endsWith(".so")) {
                    continue;
                }
                int slash = name.lastIndexOf('/');
                String lib = (slash >= 0 ? name.substring(slash + 1) : name).toLowerCase(Locale.ROOT);
                classifyNativeLib(lib, found);
            }
        } catch (Throwable ignored) {
        } finally {
            if (zf != null) {
                try {
                    zf.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void classifyNativeLib(String lib, Set<String> found) {
        if (lib.startsWith("libapse") || lib.startsWith("libsgmain") || lib.startsWith("libsgsecurity")) {
            found.add("ALIPAY");
        } else if (lib.contains("promon") || lib.contains("shield_jni")) {
            found.add("PROMON");
        } else if (lib.startsWith("libvguard") || lib.startsWith("libvoswrapper") || lib.startsWith("libvkey")) {
            found.add("VKEY");
        } else if (lib.contains("appsealing") || lib.contains("covault")) {
            found.add("APPSEALING");
        } else if (lib.startsWith("libtmx") || lib.contains("tmxprofiling")) {
            found.add("THREATMETRIX");
        } else if (lib.contains("dexprotector")) {
            found.add("DEXPROT");
        } else if (lib.contains("jiagu") || lib.contains("dexhelper") || lib.contains("secneo")) {
            found.add("PACKER");
        } else if (lib.startsWith("libliapp") || lib.contains("bangcle")) {
            found.add("HARDENED");
        }
    }

    private static final class AxmlResult {
        String zygotePreloadName;
        final Set<String> azServices = new LinkedHashSet<>();
        final Set<String> isoServices = new LinkedHashSet<>();
        int externalIsoCount;
        boolean queriesAllPackages;
    }

    private static final class CachedScan {
        final long lastUpdateTime;
        final ScanReport report;

        CachedScan(long lastUpdateTime, ScanReport report) {
            this.lastUpdateTime = lastUpdateTime;
            this.report = report;
        }
    }

    public static final class ScanReport {
        public static final ScanReport EMPTY = new ScanReport(
                false,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                0,
                false,
                Collections.emptyList(),
                false,
                false,
                0,
                0
        );

        public final boolean usesAppZygote;
        public final String zygotePreloadName;
        public final List<String> appZygoteServices;
        public final List<String> isolatedServices;
        public final int externalIsolatedCount;
        public final boolean queriesAllPackages;
        public final List<String> antifraudSdks;
        public final boolean financeLikely;
        public final boolean isSandbox;
        public final int threatScore;
        public final int recommendedFlags;

        public ScanReport(
                boolean usesAppZygote,
                String zygotePreloadName,
                List<String> appZygoteServices,
                List<String> isolatedServices,
                int externalIsolatedCount,
                boolean queriesAllPackages,
                List<String> antifraudSdks,
                boolean financeLikely,
                boolean isSandbox,
                int threatScore,
                int recommendedFlags
        ) {
            this.usesAppZygote = usesAppZygote;
            this.zygotePreloadName = zygotePreloadName;
            this.appZygoteServices = Collections.unmodifiableList(appZygoteServices);
            this.isolatedServices = Collections.unmodifiableList(isolatedServices);
            this.externalIsolatedCount = externalIsolatedCount;
            this.queriesAllPackages = queriesAllPackages;
            this.antifraudSdks = Collections.unmodifiableList(antifraudSdks);
            this.financeLikely = financeLikely;
            this.isSandbox = isSandbox;
            this.threatScore = threatScore;
            this.recommendedFlags = recommendedFlags;
        }

        public boolean isThreat() {
            if (isSandbox) {
                return false;
            }
            return usesAppZygote || !isolatedServices.isEmpty() || !antifraudSdks.isEmpty();
        }

        public boolean isActionableTarget() {
            return !isSandbox && (financeLikely || isThreat());
        }
    }
}
