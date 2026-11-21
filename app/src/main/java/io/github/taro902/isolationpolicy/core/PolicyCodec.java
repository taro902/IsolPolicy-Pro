package io.github.taro902.isolationpolicy.core;

import android.util.SparseArray;
import android.util.SparseIntArray;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class PolicyCodec {

    private PolicyCodec() {
    }

    public static String encodePolicyMap(Map<String, Integer> pkgFlags) {
        if (pkgFlags == null || pkgFlags.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(pkgFlags.size() * 32);
        boolean first = true;
        for (Map.Entry<String, Integer> e : pkgFlags.entrySet()) {
            String pkg = e.getKey();
            Integer val = e.getValue();
            if (pkg == null || pkg.isEmpty() || val == null) {
                continue;
            }
            int clean = val & PolicyFlags.ALL_SHIELDS_MASK;
            if (clean == 0) {
                continue;
            }
            if (!first) {
                sb.append(';');
            }
            first = false;
            sb.append(pkg).append('=').append(clean);
        }
        return sb.toString();
    }

    public static Map<String, Integer> decodePolicyMap(String raw) {
        Map<String, Integer> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        int len = raw.length();
        int start = 0;
        while (start < len) {
            int semi = raw.indexOf(';', start);
            if (semi < 0) {
                semi = len;
            }
            int eq = raw.indexOf('=', start);
            if (eq > start && eq < semi) {
                String pkg = raw.substring(start, eq).trim();
                String num = raw.substring(eq + 1, semi).trim();
                if (!pkg.isEmpty()) {
                    try {
                        int flags = Integer.parseInt(num) & PolicyFlags.ALL_SHIELDS_MASK;
                        if (flags != 0) {
                            map.put(pkg, flags);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            start = semi + 1;
        }
        return map;
    }

    public static String encodeAppIdMap(SparseIntArray appIdFlags, SparseArray<String> appIdToPkg) {
        if (appIdFlags == null || appIdFlags.size() == 0) {
            return "";
        }
        int size = appIdFlags.size();
        StringBuilder sb = new StringBuilder(size * 36);
        boolean first = true;
        for (int i = 0; i < size; i++) {
            int appId = appIdFlags.keyAt(i);
            int flags = appIdFlags.valueAt(i) & PolicyFlags.ALL_SHIELDS_MASK;
            if (appId < 10000 || flags == 0) {
                continue;
            }
            if (!first) {
                sb.append(';');
            }
            first = false;
            sb.append(appId).append(':').append(flags);
            String pkg = appIdToPkg != null ? appIdToPkg.get(appId) : null;
            if (pkg != null && !pkg.isEmpty()) {
                sb.append(':').append(pkg);
            }
        }
        return sb.toString();
    }

    public static PolicySnapshot decodeSnapshot(
            String policyRaw,
            String appIdRaw,
            String legacyModesRaw,
            Set<String> legacyTargets,
            int selfAppId,
            int defaultAzMode,
            long revision
    ) {
        Map<String, Integer> pkgFlags = decodePolicyMap(policyRaw);
        if (pkgFlags.isEmpty()) {
            mergeLegacyPolicies(pkgFlags, legacyModesRaw, legacyTargets);
        }

        SparseIntArray appIdFlags = new SparseIntArray();
        SparseArray<String> appIdToPkg = new SparseArray<>();
        if (appIdRaw != null && !appIdRaw.isEmpty()) {
            int len = appIdRaw.length();
            int start = 0;
            while (start < len) {
                int semi = appIdRaw.indexOf(';', start);
                if (semi < 0) {
                    semi = len;
                }
                int c1 = appIdRaw.indexOf(':', start);
                if (c1 > start && c1 < semi) {
                    int c2 = appIdRaw.indexOf(':', c1 + 1);
                    String idStr = appIdRaw.substring(start, c1).trim();
                    String flStr = (c2 > c1 && c2 < semi)
                            ? appIdRaw.substring(c1 + 1, c2).trim()
                            : appIdRaw.substring(c1 + 1, semi).trim();
                    String pkg = (c2 > c1 && c2 < semi)
                            ? appIdRaw.substring(c2 + 1, semi).trim()
                            : null;
                    try {
                        int appId = Integer.parseInt(idStr);
                        int flags = Integer.parseInt(flStr) & PolicyFlags.ALL_SHIELDS_MASK;
                        if (appId >= 10000 && flags != 0) {
                            appIdFlags.put(appId, flags);
                            if (pkg != null && !pkg.isEmpty()) {
                                appIdToPkg.put(appId, pkg);
                                if (!pkgFlags.containsKey(pkg)) {
                                    pkgFlags.put(pkg, flags);
                                }
                            }
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                start = semi + 1;
            }
        }

        return new PolicySnapshot(appIdFlags, pkgFlags, appIdToPkg, selfAppId, defaultAzMode, revision);
    }

    private static void mergeLegacyPolicies(
            Map<String, Integer> out,
            String legacyModesRaw,
            Set<String> legacyTargets
    ) {
        if (legacyModesRaw != null && !legacyModesRaw.isEmpty()) {
            try {
                JSONObject obj = new JSONObject(legacyModesRaw);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String pkg = keys.next();
                    int az = parseLegacyAzValue(obj.opt(pkg));
                    if (az != PolicyFlags.AZ_OFF) {
                        out.put(pkg, az);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (legacyTargets != null) {
            for (String pkg : legacyTargets) {
                if (pkg != null && !pkg.isEmpty() && !out.containsKey(pkg)) {
                    out.put(pkg, PolicyFlags.AZ_DOWNGRADE);
                }
            }
        }
    }

    public static String exportJson(Map<String, Integer> pkgFlags, int defaultAzMode) {
        try {
            JSONObject root = new JSONObject();
            root.put("schema", "isolpolicy");
            root.put("version", PolicyFlags.VERSION_NAME);
            root.put("defaultAzMode", defaultAzMode & PolicyFlags.AZ_MASK);

            JSONObject policies = new JSONObject();
            if (pkgFlags != null) {
                for (Map.Entry<String, Integer> e : pkgFlags.entrySet()) {
                    String pkg = e.getKey();
                    Integer raw = e.getValue();
                    if (pkg == null || pkg.isEmpty() || raw == null) {
                        continue;
                    }
                    int flags = raw & PolicyFlags.ALL_SHIELDS_MASK;
                    if (flags == 0) {
                        continue;
                    }
                    JSONObject item = new JSONObject();
                    item.put("flags", flags);
                    item.put("az", PolicyFlags.getAzMode(flags));
                    item.put("iso", PolicyFlags.has(flags, PolicyFlags.FLAG_ISO_FOLD));
                    item.put("dev", PolicyFlags.has(flags, PolicyFlags.FLAG_DEV_COHERENT));
                    item.put("a11y", PolicyFlags.has(flags, PolicyFlags.FLAG_A11Y_COHERENT));
                    item.put("pkg", PolicyFlags.has(flags, PolicyFlags.FLAG_PKG_CHANGES));
                    item.put("disp", PolicyFlags.has(flags, PolicyFlags.FLAG_DISP_STEALTH));
                    policies.put(pkg, item);
                }
            }
            root.put("policies", policies);
            return root.toString(2);
        } catch (Throwable t) {
            return "{}";
        }
    }

    public static ImportResult importJson(String jsonText) {
        Map<String, Integer> map = new HashMap<>();
        int defaultAz = PolicyFlags.AZ_OFF;
        if (jsonText == null || jsonText.trim().isEmpty()) {
            return new ImportResult(map, defaultAz, false);
        }
        try {
            JSONObject root = new JSONObject(jsonText.trim());
            defaultAz = root.optInt("defaultAzMode", PolicyFlags.AZ_OFF) & PolicyFlags.AZ_MASK;

            JSONObject policies = root.optJSONObject("policies");
            if (policies != null) {
                Iterator<String> it = policies.keys();
                while (it.hasNext()) {
                    String pkg = it.next();
                    if (pkg == null || pkg.trim().isEmpty()) {
                        continue;
                    }
                    Object val = policies.opt(pkg);
                    int flags = parsePolicyEntry(val);
                    if (flags != 0) {
                        map.put(pkg.trim(), flags);
                    }
                }
                return new ImportResult(map, defaultAz, true);
            }

            JSONObject legacyModes = root.optJSONObject("modes");
            if (legacyModes == null) {
                legacyModes = root.optJSONObject("packages");
            }
            if (legacyModes != null) {
                Iterator<String> it = legacyModes.keys();
                while (it.hasNext()) {
                    String pkg = it.next();
                    int flags = parsePolicyEntry(legacyModes.opt(pkg));
                    if (flags != 0) {
                        map.put(pkg.trim(), flags);
                    }
                }
                return new ImportResult(map, defaultAz, true);
            }

            JSONArray arr = root.optJSONArray("targets");
            if (arr == null) {
                arr = root.optJSONArray("packages");
            }
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String pkg = arr.optString(i, "").trim();
                    if (!pkg.isEmpty()) {
                        map.put(pkg, PolicyFlags.AZ_DOWNGRADE);
                    }
                }
                return new ImportResult(map, defaultAz, true);
            }
            return new ImportResult(map, defaultAz, false);
        } catch (Throwable t) {
            return new ImportResult(map, defaultAz, false);
        }
    }

    private static int parsePolicyEntry(Object val) {
        if (val instanceof JSONObject) {
            JSONObject o = (JSONObject) val;
            if (o.has("flags")) {
                return o.optInt("flags", 0) & PolicyFlags.ALL_SHIELDS_MASK;
            }
            int flags = parseLegacyAzValue(o.opt("az"));
            if (o.optBoolean("iso", false)) flags |= PolicyFlags.FLAG_ISO_FOLD;
            if (o.optBoolean("dev", false)) flags |= PolicyFlags.FLAG_DEV_COHERENT;
            if (o.optBoolean("a11y", false)) flags |= PolicyFlags.FLAG_A11Y_COHERENT;
            if (o.optBoolean("pkg", false)) flags |= PolicyFlags.FLAG_PKG_CHANGES;
            if (o.optBoolean("disp", false)) flags |= PolicyFlags.FLAG_DISP_STEALTH;
            return flags & PolicyFlags.ALL_SHIELDS_MASK;
        }
        return parseLegacyAzValue(val);
    }

    private static int parseLegacyAzValue(Object val) {
        if (val instanceof Number) {
            int n = ((Number) val).intValue();
            if (n > 3) {
                return n & PolicyFlags.ALL_SHIELDS_MASK;
            }
            return n & PolicyFlags.AZ_MASK;
        }
        if (val instanceof String) {
            String s = ((String) val).trim().toUpperCase();
            if ("AUTO".equals(s)) return PolicyFlags.AZ_AUTO;
            if ("DOWN".equals(s) || "DOWNGRADE".equals(s)) return PolicyFlags.AZ_DOWNGRADE;
            if ("DROP".equals(s) || "HARD_DROP".equals(s) || "HARDDROP".equals(s)) return PolicyFlags.AZ_HARD_DROP;
            try {
                return Integer.parseInt(s) & PolicyFlags.ALL_SHIELDS_MASK;
            } catch (NumberFormatException ignored) {
            }
        }
        return PolicyFlags.AZ_OFF;
    }

    public static final class ImportResult {
        public final Map<String, Integer> policies;
        public final int defaultAzMode;
        public final boolean valid;

        public ImportResult(Map<String, Integer> policies, int defaultAzMode, boolean valid) {
            this.policies = policies;
            this.defaultAzMode = defaultAzMode;
            this.valid = valid;
        }
    }
}