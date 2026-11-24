package io.github.taro902.isolationpolicy.radar;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.taro902.isolationpolicy.core.PolicyFlags;

public final class ManifestRadar {

    public static final int CATEGORY_GENERAL = 0;
    public static final int CATEGORY_FINANCE = 1;
    public static final int CATEGORY_SANDBOX = 2;

    public static final int RISK_LOW = 0;
    public static final int RISK_MEDIUM = 1;
    public static final int RISK_HIGH = 2;
    public static final int RISK_CRITICAL = 3;

    private static final Set<String> SANDBOX_EXACT = new HashSet<String>(Arrays.asList(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "com.brave.browser",
        "org.mozilla.firefox",
        "org.mozilla.fenix",
        "org.mozilla.focus",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.microsoft.emmx",
        "com.kiwibrowser.browser",
        "com.sec.android.app.sbrowser",
        "com.duckduckgo.mobile.android",
        "com.vivaldi.browser",
        "com.yandex.browser",
        "com.UCMobile.intl",
        "com.google.android.webview",
        "com.android.webview",
        "com.google.android.trichromelibrary",
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.facebook.orca",
        "com.instagram.android",
        "com.instaprime.android",
        "com.twitter.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.discord",
        "com.snapchat.android",
        "jp.naver.line.android",
        "com.google.android.youtube",
        "com.google.android.gm",
        "com.google.android.apps.docs",
        "com.google.android.googlequicksearchbox",
        "com.lemon.lvoverseas",
        "com.tongyi.intl",
        "ai.qwenlm.chat.android",
        "com.openai.chatgpt",
        "com.termux",
        "bin.mt.plus",
        "ru.zdevs.zarchiver",
        "app.pwhs.universalinstaller",
        "org.frknkrc44.hma_oss",
        "com.wstxda.viper4android",
        "com.wmods.wppenhacer",
        "io.github.taro902.isolationpolicy"
    ));

    private static final String[] FINANCE_EXACT_OR_PREFIX = {
        "id.dana",
        "com.bca",
        "com.bca.mybca",
        "id.co.bri",
        "id.bmri.livin",
        "id.co.bankmandiri",
        "id.co.bni",
        "src.com.bni",
        "id.co.cimbniaga",
        "com.octo",
        "com.shopee.id",
        "com.gojek.gopay",
        "com.gojek.app",
        "ovo.id",
        "com.telkom.mwallet",
        "id.co.bsi",
        "com.btpn.dc",
        "com.jago.digitalbanking",
        "id.co.seabank",
        "com.bankraya",
        "com.danamon",
        "com.permata",
        "com.panin",
        "com.maybank",
        "com.ocbcnisp",
        "com.uob",
        "com.dbs",
        "com.hsbc",
        "com.citibank",
        "com.bibit",
        "com.stockbit",
        "com.ajaib",
        "id.flip",
        "com.kredivo",
        "com.akulaku",
        "com.tokopedia.tkpd",
        "com.lazada.android",
        "com.grabtaxi.passenger",
        "com.paypal.android.p2pmobile"
    };

    private static final String[] FINANCE_KEYWORDS = {
        "bank",
        "wallet",
        "mbanking",
        "ebanking",
        "brimo",
        "livin",
        "mybca",
        "seabank",
        "octomobile",
        "finpay",
        "gopay"
    };

    private ManifestRadar() {
    }

    public static final class ScanReport {
        public final String packageName;
        public final int category;
        public final int riskLevel;
        public final int isolatedServiceCount;
        public final int appZygoteServiceCount;
        public final int externalServiceCount;
        public final List<String> sdkTags;
        public final int recommendedFlags;
        public final String recommendLabel;

        public ScanReport(
            String packageName,
            int category,
            int riskLevel,
            int isolatedServiceCount,
            int appZygoteServiceCount,
            int externalServiceCount,
            List<String> sdkTags,
            int recommendedFlags,
            String recommendLabel
        ) {
            this.packageName = packageName;
            this.category = category;
            this.riskLevel = riskLevel;
            this.isolatedServiceCount = isolatedServiceCount;
            this.appZygoteServiceCount = appZygoteServiceCount;
            this.externalServiceCount = externalServiceCount;
            this.sdkTags = sdkTags != null ? Collections.unmodifiableList(sdkTags) : Collections.<String>emptyList();
            this.recommendedFlags = PolicyFlags.sanitize(recommendedFlags);
            this.recommendLabel = recommendLabel != null ? recommendLabel : "OFF";
        }

        public boolean isSandbox() {
            return category == CATEGORY_SANDBOX;
        }

        public boolean isFinance() {
            return category == CATEGORY_FINANCE;
        }

        public boolean isAutoEligible() {
            return category == CATEGORY_FINANCE && PolicyFlags.isActive(recommendedFlags);
        }

        public String categoryLabel() {
            if (category == CATEGORY_FINANCE) {
                return "FINANCE";
            }
            if (category == CATEGORY_SANDBOX) {
                return "SANDBOX";
            }
            return "GENERAL";
        }

        public String riskLabel() {
            switch (riskLevel) {
                case RISK_CRITICAL:
                    return "CRIT";
                case RISK_HIGH:
                    return "HIGH";
                case RISK_MEDIUM:
                    return "MED";
                default:
                    return "LOW";
            }
        }
    }

    public static int classifyPackage(String packageName, String appLabel) {
        if (packageName == null || packageName.isEmpty()) {
            return CATEGORY_GENERAL;
        }
        String lowPkg = packageName.toLowerCase(Locale.US);
        if (SANDBOX_EXACT.contains(lowPkg)
            || lowPkg.contains("webview")
            || lowPkg.contains("trichrome")
            || lowPkg.contains("chromium")
            || lowPkg.startsWith("com.android.chrome")
            || lowPkg.startsWith("org.mozilla.")
        ) {
            return CATEGORY_SANDBOX;
        }
        for (int i = 0; i < FINANCE_EXACT_OR_PREFIX.length; i++) {
            String pat = FINANCE_EXACT_OR_PREFIX[i];
            if (lowPkg.equals(pat) || lowPkg.startsWith(pat + ".")) {
                return CATEGORY_FINANCE;
            }
        }
        String combined = appLabel != null ? (lowPkg + " " + appLabel.toLowerCase(Locale.US)) : lowPkg;
        for (int i = 0; i < FINANCE_KEYWORDS.length; i++) {
            if (combined.contains(FINANCE_KEYWORDS[i])) {
                return CATEGORY_FINANCE;
            }
        }
        return CATEGORY_GENERAL;
    }

    public static String matchElfSdkTag(String entryName) {
        if (entryName == null || !entryName.endsWith(".so")) {
            return null;
        }
        int slash = entryName.lastIndexOf('/');
        String lib = (slash >= 0 ? entryName.substring(slash + 1) : entryName).toLowerCase(Locale.US);
        if (lib.contains("sgmain") || lib.contains("sgsecurity") || lib.contains("sgmisc") || lib.contains("apdid") || lib.contains("zoloz") || lib.contains("alipay")) {
            return "ALIPAY";
        }
        if (lib.contains("voswrapper") || lib.contains("vguard") || lib.contains("vkey")) {
            return "VKEY";
        }
        if (lib.contains("promon") || lib.contains("libshield")) {
            return "PROMON";
        }
        if (lib.contains("appsealing") || lib.contains("libsealing")) {
            return "APPSEAL";
        }
        if (lib.contains("threatmetrix") || lib.contains("libtmx")) {
            return "TMX";
        }
        if (lib.contains("secmain") || lib.contains("dexhelper") || lib.contains("secexe")) {
            return "BANGCLE";
        }
        if (lib.contains("jiagu") || lib.contains("protectclass")) {
            return "JIAGU";
        }
        if (lib.contains("zdefend") || lib.contains("zimperium")) {
            return "ZIMPERIUM";
        }
        if (lib.contains("toolchecker") || lib.contains("tool-checker")) {
            return "ROOTBEER";
        }
        if (lib.contains("dexguard") || lib.contains("arxan") || lib.contains("pairip")) {
            return "HARDENED";
        }
        return null;
    }

    public static ScanReport buildReport(
        String packageName,
        String appLabel,
        int isolatedServiceCount,
        int appZygoteServiceCount,
        int externalServiceCount,
        List<String> sdkTags
    ) {
        int category = classifyPackage(packageName, appLabel);
        List<String> safeTags = sdkTags != null ? sdkTags : Collections.<String>emptyList();

        if (category == CATEGORY_SANDBOX) {
            return new ScanReport(
                packageName,
                CATEGORY_SANDBOX,
                RISK_LOW,
                isolatedServiceCount,
                appZygoteServiceCount,
                externalServiceCount,
                safeTags,
                PolicyFlags.PRESET_NONE,
                "SAFE OFF"
            );
        }

        boolean hasHardSdk = safeTags.contains("VKEY")
            || safeTags.contains("PROMON")
            || safeTags.contains("APPSEAL")
            || safeTags.contains("ZIMPERIUM");
        boolean hasAnySdk = !safeTags.isEmpty();

        int risk;
        int recFlags;
        String recLabel;

        if (category == CATEGORY_FINANCE) {
            if (hasHardSdk) {
                risk = RISK_CRITICAL;
                recFlags = PolicyFlags.PRESET_BANKING_MAX;
                recLabel = "BANK MAX";
            } else if (hasAnySdk || appZygoteServiceCount > 0 || isolatedServiceCount > 0) {
                risk = RISK_HIGH;
                recFlags = PolicyFlags.PRESET_BANKING_STRICT;
                recLabel = "BANK STD";
            } else {
                risk = RISK_MEDIUM;
                recFlags = PolicyFlags.PRESET_BANKING_STRICT;
                recLabel = "BANK STD";
            }
        } else {
            if (hasHardSdk) {
                risk = RISK_HIGH;
                recFlags = PolicyFlags.PRESET_BANKING_STRICT;
                recLabel = "GUARD STD";
            } else if (hasAnySdk || appZygoteServiceCount > 0) {
                risk = RISK_MEDIUM;
                recFlags = PolicyFlags.AZ_DOWNGRADE | PolicyFlags.FLAG_DEV_COHERENT | PolicyFlags.FLAG_PKG_CHANGES;
                recLabel = "LIGHT";
            } else {
                risk = RISK_LOW;
                recFlags = PolicyFlags.PRESET_NONE;
                recLabel = "OFF";
            }
        }

        return new ScanReport(
            packageName,
            category,
            risk,
            isolatedServiceCount,
            appZygoteServiceCount,
            externalServiceCount,
            safeTags,
            recFlags,
            recLabel
        );
    }

    public static ScanReport scanInstalledApp(PackageManager pm, ApplicationInfo ai, String appLabel) {
        if (ai == null) {
            return buildReport("", appLabel, 0, 0, 0, Collections.<String>emptyList());
        }
        int isoCount = 0;
        int azCount = 0;
        int extCount = 0;
        if (pm != null) {
            try {
                PackageInfo pi = pm.getPackageInfo(ai.packageName, PackageManager.GET_SERVICES);
                if (pi != null && pi.services != null) {
                    for (int i = 0; i < pi.services.length; i++) {
                        ServiceInfo si = pi.services[i];
                        if (si == null) {
                            continue;
                        }
                        if ((si.flags & ServiceInfo.FLAG_ISOLATED_PROCESS) != 0) {
                            isoCount++;
                        }
                        if ((si.flags & ServiceInfo.FLAG_USE_APP_ZYGOTE) != 0) {
                            azCount++;
                        }
                        if ((si.flags & ServiceInfo.FLAG_EXTERNAL_SERVICE) != 0) {
                            extCount++;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        ArrayList<String> tags = new ArrayList<String>(4);
        HashSet<String> seen = new HashSet<String>(8);
        scanApkCentralDirectory(ai.sourceDir, seen, tags);
        if (ai.splitSourceDirs != null) {
            for (int i = 0; i < ai.splitSourceDirs.length; i++) {
                scanApkCentralDirectory(ai.splitSourceDirs[i], seen, tags);
            }
        }
        Collections.sort(tags);

        return buildReport(ai.packageName, appLabel, isoCount, azCount, extCount, tags);
    }

    private static void scanApkCentralDirectory(String apkPath, Set<String> seen, List<String> outTags) {
        if (apkPath == null || apkPath.isEmpty()) {
            return;
        }
        ZipFile zf = null;
        try {
            zf = new ZipFile(apkPath);
            Enumeration<? extends ZipEntry> entries = zf.entries();
            int scanned = 0;
            while (entries.hasMoreElements() && scanned < 4096) {
                ZipEntry entry = entries.nextElement();
                scanned++;
                String name = entry.getName();
                if (name == null || !name.startsWith("lib/")) {
                    continue;
                }
                String tag = matchElfSdkTag(name);
                if (tag != null && seen.add(tag)) {
                    outTags.add(tag);
                }
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
}
