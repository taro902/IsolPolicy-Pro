package io.github.taro902.isolationpolicy.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class PolicyCodec {

    private static final int MAX_JSON_LENGTH = 2 * 1024 * 1024;
    private static final int MAX_JSON_DEPTH = 16;

    private PolicyCodec() {
    }

    public static final class ImportResult {
        public final boolean valid;
        public final int defaultAzMode;
        public final LinkedHashMap<String, Integer> policies;
        public final String sourceVersion;
        public final int importedCount;
        public final String error;

        public ImportResult(
            boolean valid,
            int defaultAzMode,
            LinkedHashMap<String, Integer> policies,
            String sourceVersion,
            int importedCount,
            String error
        ) {
            this.valid = valid;
            this.defaultAzMode = defaultAzMode & PolicyFlags.AZ_MASK;
            this.policies = policies != null ? policies : new LinkedHashMap<String, Integer>();
            this.sourceVersion = sourceVersion != null ? sourceVersion : "";
            this.importedCount = importedCount;
            this.error = error != null ? error : "";
        }

        public static ImportResult failure(String error) {
            return new ImportResult(false, PolicyFlags.AZ_OFF, new LinkedHashMap<String, Integer>(), "", 0, error);
        }
    }

    public static String encodePolicyMap(Map<String, Integer> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        TreeMap<String, Integer> sorted = new TreeMap<String, Integer>();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            String pkg = e.getKey();
            Integer val = e.getValue();
            if (isValidPackageToken(pkg) && val != null) {
                int clean = PolicyFlags.sanitize(val);
                if (PolicyFlags.isActive(clean)) {
                    sorted.put(pkg, clean);
                }
            }
        }
        StringBuilder sb = new StringBuilder(sorted.size() * 32);
        for (Map.Entry<String, Integer> e : sorted.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue()).append(';');
        }
        return sb.toString();
    }

    public static LinkedHashMap<String, Integer> decodePolicyMap(String raw) {
        LinkedHashMap<String, Integer> out = new LinkedHashMap<String, Integer>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        int len = raw.length();
        int start = 0;
        while (start < len) {
            int semi = raw.indexOf(';', start);
            int end = semi >= 0 ? semi : len;
            int eq = raw.indexOf('=', start);
            if (eq > start && eq < end) {
                String pkg = raw.substring(start, eq).trim();
                String num = raw.substring(eq + 1, end).trim();
                if (isValidPackageToken(pkg) && !num.isEmpty()) {
                    try {
                        int clean = PolicyFlags.sanitize(Integer.parseInt(num));
                        if (PolicyFlags.isActive(clean)) {
                            out.put(pkg, clean);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            start = end + 1;
        }
        return out;
    }

    public static String encodeAppIdMap(Map<String, Integer> pkgFlags, Map<String, Integer> pkgToAppId) {
        if (pkgFlags == null || pkgFlags.isEmpty() || pkgToAppId == null || pkgToAppId.isEmpty()) {
            return "";
        }
        TreeMap<Integer, String> ordered = new TreeMap<Integer, String>();
        TreeMap<Integer, Integer> mergedFlags = new TreeMap<Integer, Integer>();
        for (Map.Entry<String, Integer> e : pkgFlags.entrySet()) {
            String pkg = e.getKey();
            Integer f = e.getValue();
            Integer appId = pkgToAppId.get(pkg);
            if (isValidPackageToken(pkg) && f != null && appId != null && appId >= PolicySnapshot.FIRST_APP_UID) {
                int clean = PolicyFlags.sanitize(f);
                if (PolicyFlags.isActive(clean)) {
                    Integer prev = mergedFlags.get(appId);
                    mergedFlags.put(appId, prev == null ? clean : PolicyFlags.sanitize(prev | clean));
                    if (!ordered.containsKey(appId)) {
                        ordered.put(appId, pkg);
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder(mergedFlags.size() * 40);
        for (Map.Entry<Integer, Integer> e : mergedFlags.entrySet()) {
            int appId = e.getKey();
            String pkg = ordered.get(appId);
            sb.append(appId).append(':').append(e.getValue()).append(':').append(pkg != null ? pkg : "").append(';');
        }
        return sb.toString();
    }

    public static void decodeAppIdMap(
        String raw,
        Map<Integer, Integer> outAppIdFlags,
        Map<Integer, String> outAppIdPkg
    ) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        int len = raw.length();
        int start = 0;
        while (start < len) {
            int semi = raw.indexOf(';', start);
            int end = semi >= 0 ? semi : len;
            int c1 = raw.indexOf(':', start);
            if (c1 > start && c1 < end) {
                int c2 = raw.indexOf(':', c1 + 1);
                if (c2 > c1 + 1 && c2 <= end) {
                    String idStr = raw.substring(start, c1).trim();
                    String flagStr = raw.substring(c1 + 1, c2).trim();
                    String pkg = raw.substring(c2 + 1, end).trim();
                    try {
                        int appId = Integer.parseInt(idStr);
                        int flags = PolicyFlags.sanitize(Integer.parseInt(flagStr));
                        if (appId >= PolicySnapshot.FIRST_APP_UID) {
                            if (outAppIdFlags != null && PolicyFlags.isActive(flags)) {
                                Integer prev = outAppIdFlags.get(appId);
                                outAppIdFlags.put(appId, prev == null ? flags : PolicyFlags.sanitize(prev | flags));
                            }
                            if (outAppIdPkg != null && !pkg.isEmpty()) {
                                outAppIdPkg.put(appId, pkg);
                            }
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            start = end + 1;
        }
    }

    public static LinkedHashMap<String, Integer> decodeLegacyV20(String legacyModes, Set<String> legacyTargets) {
        LinkedHashMap<String, Integer> out = new LinkedHashMap<String, Integer>();
        if (legacyTargets != null) {
            for (String pkg : legacyTargets) {
                if (isValidPackageToken(pkg)) {
                    out.put(pkg, PolicyFlags.AZ_DOWNGRADE);
                }
            }
        }
        if (legacyModes != null && !legacyModes.isEmpty()) {
            LinkedHashMap<String, Integer> decoded = decodePolicyMap(legacyModes);
            for (Map.Entry<String, Integer> e : decoded.entrySet()) {
                int mode = e.getValue() & PolicyFlags.AZ_MASK;
                if (mode != PolicyFlags.AZ_OFF) {
                    out.put(e.getKey(), mode);
                }
            }
        }
        return out;
    }

    public static String exportJson(String version, int defaultAzMode, Map<String, Integer> policies) {
        String safeVersion = version != null && !version.isEmpty() ? version : "2.2.0";
        TreeMap<String, Integer> sorted = new TreeMap<String, Integer>();
        if (policies != null) {
            for (Map.Entry<String, Integer> e : policies.entrySet()) {
                String pkg = e.getKey();
                Integer v = e.getValue();
                if (isValidPackageToken(pkg) && v != null) {
                    int clean = PolicyFlags.sanitize(v);
                    if (PolicyFlags.isActive(clean)) {
                        sorted.put(pkg, clean);
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder(256 + sorted.size() * 140);
        sb.append("{\n");
        sb.append("  \"schema\": \"isolpolicy\",\n");
        sb.append("  \"schemaVersion\": 22,\n");
        sb.append("  \"version\": ");
        appendJsonString(sb, safeVersion);
        sb.append(",\n");
        sb.append("  \"defaultAzMode\": ").append(defaultAzMode & PolicyFlags.AZ_MASK).append(",\n");
        sb.append("  \"policies\": {");
        if (!sorted.isEmpty()) {
            sb.append('\n');
            int i = 0;
            int total = sorted.size();
            for (Map.Entry<String, Integer> e : sorted.entrySet()) {
                int f = e.getValue();
                sb.append("    ");
                appendJsonString(sb, e.getKey());
                sb.append(": {");
                sb.append("\"flags\": ").append(f);
                sb.append(", \"az\": ").append(PolicyFlags.azMode(f));
                sb.append(", \"iso\": ").append(PolicyFlags.hasFlag(f, PolicyFlags.FLAG_ISO_FOLD));
                sb.append(", \"dev\": ").append(PolicyFlags.hasFlag(f, PolicyFlags.FLAG_DEV_COHERENT));
                sb.append(", \"a11y\": ").append(PolicyFlags.hasFlag(f, PolicyFlags.FLAG_A11Y_COHERENT));
                sb.append(", \"pkg\": ").append(PolicyFlags.hasFlag(f, PolicyFlags.FLAG_PKG_CHANGES));
                sb.append(", \"disp\": ").append(PolicyFlags.hasFlag(f, PolicyFlags.FLAG_DISP_STEALTH));
                sb.append(", \"proc\": ").append(PolicyFlags.hasFlag(f, PolicyFlags.FLAG_PROC_SURFACE));
                sb.append(", \"inp\": ").append(PolicyFlags.hasFlag(f, PolicyFlags.FLAG_INPUT_SURFACE));
                sb.append(", \"obs\": ").append(PolicyFlags.hasFlag(f, PolicyFlags.FLAG_OBS_COHERENCE));
                sb.append(", \"audit\": ").append(PolicyFlags.hasFlag(f, PolicyFlags.FLAG_AUDIT_ONLY));
                sb.append('}');
                i++;
                if (i < total) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            sb.append("  ");
        }
        sb.append("}\n");
        sb.append("}\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static ImportResult importJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return ImportResult.failure("EMPTY_JSON");
        }
        if (json.length() > MAX_JSON_LENGTH) {
            return ImportResult.failure("JSON_TOO_LARGE");
        }
        Object parsed;
        try {
            JsonParser parser = new JsonParser(json);
            parsed = parser.parseRoot();
        } catch (IllegalArgumentException ex) {
            return ImportResult.failure(ex.getMessage() != null ? ex.getMessage() : "MALFORMED_JSON");
        }
        if (!(parsed instanceof Map)) {
            return ImportResult.failure("ROOT_NOT_OBJECT");
        }
        Map<String, Object> root = (Map<String, Object>) parsed;
        String version = asString(root.get("version"), "");
        int defaultAz = asInt(root.get("defaultAzMode"), PolicyFlags.AZ_OFF) & PolicyFlags.AZ_MASK;
        LinkedHashMap<String, Integer> out = new LinkedHashMap<String, Integer>();

        Object policiesObj = root.get("policies");
        if (policiesObj instanceof Map) {
            Map<String, Object> pMap = (Map<String, Object>) policiesObj;
            for (Map.Entry<String, Object> e : pMap.entrySet()) {
                String pkg = e.getKey();
                if (!isValidPackageToken(pkg)) {
                    continue;
                }
                Object entryVal = e.getValue();
                int flags = 0;
                if (entryVal instanceof Number) {
                    flags = PolicyFlags.sanitize(((Number) entryVal).intValue());
                } else if (entryVal instanceof Map) {
                    flags = decodePolicyObject((Map<String, Object>) entryVal);
                }
                if (PolicyFlags.isActive(flags)) {
                    out.put(pkg, flags);
                }
            }
        }

        Object modesObj = root.get("modes");
        if (modesObj instanceof Map) {
            Map<String, Object> mMap = (Map<String, Object>) modesObj;
            for (Map.Entry<String, Object> e : mMap.entrySet()) {
                String pkg = e.getKey();
                if (isValidPackageToken(pkg) && !out.containsKey(pkg)) {
                    int az = asInt(e.getValue(), PolicyFlags.AZ_OFF) & PolicyFlags.AZ_MASK;
                    if (az != PolicyFlags.AZ_OFF) {
                        out.put(pkg, az);
                    }
                }
            }
        }

        importLegacyStringList(root.get("targets"), out);
        importLegacyStringList(root.get("packages"), out);

        if (policiesObj == null && modesObj == null && root.get("targets") == null && root.get("packages") == null) {
            return ImportResult.failure("NO_POLICY_SECTION");
        }

        return new ImportResult(true, defaultAz, out, version, out.size(), "");
    }

    private static int decodePolicyObject(Map<String, Object> obj) {
        Object flagsField = obj.get("flags");
        if (flagsField instanceof Number) {
            return PolicyFlags.sanitize(((Number) flagsField).intValue());
        }
        int f = asInt(obj.get("az"), PolicyFlags.AZ_OFF) & PolicyFlags.AZ_MASK;
        if (asBoolean(obj.get("iso"))) {
            f |= PolicyFlags.FLAG_ISO_FOLD;
        }
        if (asBoolean(obj.get("dev"))) {
            f |= PolicyFlags.FLAG_DEV_COHERENT;
        }
        if (asBoolean(obj.get("a11y"))) {
            f |= PolicyFlags.FLAG_A11Y_COHERENT;
        }
        if (asBoolean(obj.get("pkg"))) {
            f |= PolicyFlags.FLAG_PKG_CHANGES;
        }
        if (asBoolean(obj.get("disp"))) {
            f |= PolicyFlags.FLAG_DISP_STEALTH;
        }
        if (asBoolean(obj.get("proc"))) {
            f |= PolicyFlags.FLAG_PROC_SURFACE;
        }
        if (asBoolean(obj.get("inp"))) {
            f |= PolicyFlags.FLAG_INPUT_SURFACE;
        }
        if (asBoolean(obj.get("obs"))) {
            f |= PolicyFlags.FLAG_OBS_COHERENCE;
        }
        if (asBoolean(obj.get("audit"))) {
            f |= PolicyFlags.FLAG_AUDIT_ONLY;
        }
        return PolicyFlags.sanitize(f);
    }

    private static void importLegacyStringList(Object listObj, Map<String, Integer> out) {
        if (!(listObj instanceof List)) {
            return;
        }
        List<?> list = (List<?>) listObj;
        for (Object item : list) {
            if (item instanceof String) {
                String pkg = ((String) item).trim();
                if (isValidPackageToken(pkg) && !out.containsKey(pkg)) {
                    out.put(pkg, PolicyFlags.AZ_DOWNGRADE);
                }
            }
        }
    }

    private static boolean isValidPackageToken(String pkg) {
        if (pkg == null || pkg.isEmpty() || pkg.length() > 180) {
            return false;
        }
        for (int i = 0; i < pkg.length(); i++) {
            char c = pkg.charAt(i);
            if (c == ';' || c == '=' || c == ':' || c <= 0x20 || c >= 0x7F) {
                return false;
            }
        }
        return true;
    }

    private static String asString(Object o, String fallback) {
        return o instanceof String ? (String) o : fallback;
    }

    private static int asInt(Object o, int fallback) {
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        if (o instanceof String) {
            try {
                return Integer.parseInt(((String) o).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static boolean asBoolean(Object o) {
        if (o instanceof Boolean) {
            return (Boolean) o;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue() != 0;
        }
        return false;
    }

    private static void appendJsonString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        String hex = Integer.toHexString(c);
                        sb.append("\\u");
                        for (int k = hex.length(); k < 4; k++) {
                            sb.append('0');
                        }
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        sb.append('"');
    }

    private static final class JsonParser {
        private final String src;
        private final int len;
        private int pos;

        JsonParser(String src) {
            this.src = src;
            this.len = src.length();
            this.pos = 0;
        }

        Object parseRoot() {
            skipWs();
            Object v = parseValue(0);
            skipWs();
            if (pos != len) {
                throw new IllegalArgumentException("TRAILING_CHARACTERS");
            }
            return v;
        }

        private Object parseValue(int depth) {
            if (depth > MAX_JSON_DEPTH) {
                throw new IllegalArgumentException("MAX_DEPTH_EXCEEDED");
            }
            skipWs();
            if (pos >= len) {
                throw new IllegalArgumentException("UNEXPECTED_EOF");
            }
            char c = src.charAt(pos);
            if (c == '{') {
                return parseObject(depth + 1);
            }
            if (c == '[') {
                return parseArray(depth + 1);
            }
            if (c == '"') {
                return parseString();
            }
            if (c == 't') {
                expectLiteral("true");
                return Boolean.TRUE;
            }
            if (c == 'f') {
                expectLiteral("false");
                return Boolean.FALSE;
            }
            if (c == 'n') {
                expectLiteral("null");
                return null;
            }
            if (c == '-' || (c >= '0' && c <= '9')) {
                return parseNumber();
            }
            throw new IllegalArgumentException("UNEXPECTED_TOKEN");
        }

        private Map<String, Object> parseObject(int depth) {
            pos++;
            skipWs();
            LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();
            if (pos < len && src.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                if (pos >= len || src.charAt(pos) != '"') {
                    throw new IllegalArgumentException("EXPECTED_KEY");
                }
                String key = parseString();
                skipWs();
                if (pos >= len || src.charAt(pos) != ':') {
                    throw new IllegalArgumentException("EXPECTED_COLON");
                }
                pos++;
                Object val = parseValue(depth);
                map.put(key, val);
                skipWs();
                if (pos >= len) {
                    throw new IllegalArgumentException("UNTERMINATED_OBJECT");
                }
                char next = src.charAt(pos);
                if (next == '}') {
                    pos++;
                    return map;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("EXPECTED_COMMA");
                }
                pos++;
            }
        }

        private List<Object> parseArray(int depth) {
            pos++;
            skipWs();
            ArrayList<Object> list = new ArrayList<Object>();
            if (pos < len && src.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue(depth));
                skipWs();
                if (pos >= len) {
                    throw new IllegalArgumentException("UNTERMINATED_ARRAY");
                }
                char next = src.charAt(pos);
                if (next == ']') {
                    pos++;
                    return list;
                }
                if (next != ',') {
                    throw new IllegalArgumentException("EXPECTED_COMMA");
                }
                pos++;
            }
        }

        private String parseString() {
            pos++;
            StringBuilder sb = new StringBuilder(32);
            while (pos < len) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= len) {
                        throw new IllegalArgumentException("BAD_ESCAPE");
                    }
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            if (pos + 4 > len) {
                                throw new IllegalArgumentException("BAD_UNICODE");
                            }
                            String hex = src.substring(pos, pos + 4);
                            pos += 4;
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException ex) {
                                throw new IllegalArgumentException("BAD_UNICODE");
                            }
                            break;
                        default:
                            throw new IllegalArgumentException("BAD_ESCAPE");
                    }
                } else if (c < 0x20) {
                    throw new IllegalArgumentException("CONTROL_CHAR");
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("UNTERMINATED_STRING");
        }

        private Number parseNumber() {
            int start = pos;
            if (src.charAt(pos) == '-') {
                pos++;
            }
            if (pos >= len || src.charAt(pos) < '0' || src.charAt(pos) > '9') {
                throw new IllegalArgumentException("BAD_NUMBER");
            }
            while (pos < len && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') {
                pos++;
            }
            boolean isFloat = false;
            if (pos < len && src.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                if (pos >= len || src.charAt(pos) < '0' || src.charAt(pos) > '9') {
                    throw new IllegalArgumentException("BAD_NUMBER");
                }
                while (pos < len && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') {
                    pos++;
                }
            }
            if (pos < len && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < len && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                    pos++;
                }
                if (pos >= len || src.charAt(pos) < '0' || src.charAt(pos) > '9') {
                    throw new IllegalArgumentException("BAD_NUMBER");
                }
                while (pos < len && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') {
                    pos++;
                }
            }
            String numStr = src.substring(start, pos);
            try {
                if (isFloat) {
                    return Double.valueOf(numStr);
                }
                long lv = Long.parseLong(numStr);
                if (lv >= Integer.MIN_VALUE && lv <= Integer.MAX_VALUE) {
                    return Integer.valueOf((int) lv);
                }
                return Long.valueOf(lv);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("BAD_NUMBER");
            }
        }

        private void expectLiteral(String lit) {
            int l = lit.length();
            if (pos + l > len || !src.regionMatches(pos, lit, 0, l)) {
                throw new IllegalArgumentException("BAD_LITERAL");
            }
            pos += l;
        }

        private void skipWs() {
            while (pos < len) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    pos++;
                } else {
                    break;
                }
            }
        }
    }
}
