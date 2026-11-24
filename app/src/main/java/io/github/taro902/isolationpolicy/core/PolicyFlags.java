package io.github.taro902.isolationpolicy.core;

public final class PolicyFlags {

    public static final int AZ_MASK = 0x03;
    public static final int AZ_OFF = 0;
    public static final int AZ_AUTO = 1;
    public static final int AZ_DOWNGRADE = 2;
    public static final int AZ_HARD_DROP = 3;

    public static final int FLAG_ISO_FOLD = 1 << 2;
    public static final int FLAG_DEV_COHERENT = 1 << 3;
    public static final int FLAG_A11Y_COHERENT = 1 << 4;
    public static final int FLAG_PKG_CHANGES = 1 << 5;
    public static final int FLAG_DISP_STEALTH = 1 << 6;
    public static final int FLAG_PROC_SURFACE = 1 << 7;
    public static final int FLAG_INPUT_SURFACE = 1 << 8;
    public static final int FLAG_OBS_COHERENCE = 1 << 9;
    public static final int FLAG_AUDIT_ONLY = 1 << 10;

    public static final int LEGACY_V21_MASK = 0x7F;
    public static final int ACTIVE_PILLARS_MASK = 0x3FF;
    public static final int ALL_SHIELDS_MASK = 0x7FF;

    public static final int PRESET_NONE = 0;
    public static final int PRESET_WALLET_ALIPAY = AZ_DOWNGRADE | FLAG_ISO_FOLD | FLAG_DEV_COHERENT | FLAG_A11Y_COHERENT | FLAG_PKG_CHANGES;
    public static final int PRESET_BANKING_STRICT = AZ_DOWNGRADE | FLAG_DEV_COHERENT | FLAG_A11Y_COHERENT | FLAG_PKG_CHANGES | FLAG_DISP_STEALTH;
    public static final int PRESET_BANKING_MAX = AZ_DOWNGRADE | FLAG_DEV_COHERENT | FLAG_A11Y_COHERENT | FLAG_PKG_CHANGES | FLAG_DISP_STEALTH | FLAG_PROC_SURFACE | FLAG_INPUT_SURFACE | FLAG_OBS_COHERENCE;
    public static final int PRESET_AUDIT_BANK = PRESET_BANKING_MAX | FLAG_AUDIT_ONLY;
    public static final int PRESET_AUDIT_MAX = AZ_DOWNGRADE | FLAG_ISO_FOLD | FLAG_DEV_COHERENT | FLAG_A11Y_COHERENT | FLAG_PKG_CHANGES | FLAG_DISP_STEALTH | FLAG_PROC_SURFACE | FLAG_INPUT_SURFACE | FLAG_OBS_COHERENCE | FLAG_AUDIT_ONLY;

    public static final String PREFS_GROUP = "isolpolicy_prefs";
    public static final String KEY_POLICY_MAP = "policy_map_v21";
    public static final String KEY_APPID_MAP = "appid_map_v21";
    public static final String KEY_SELF_APP_ID = "self_app_id";
    public static final String KEY_DEFAULT_AZ = "default_az_v21";
    public static final String KEY_LEGACY_V20_MODES = "pkg_modes";
    public static final String KEY_LEGACY_V20_TARGETS = "target_packages";

    public static final String IPC_METHOD = "ISOLPOLICY_IPC_V22";
    public static final String IPC_METHOD_LEGACY_V21 = "ISOLPOLICY_IPC_V21";
    public static final String IPC_ARG_CMD = "cmd";
    public static final String IPC_CMD_SNAP = "SNAP";
    public static final String IPC_CMD_PURGE = "PURGE";
    public static final String IPC_CMD_PING = "PING";

    public static final String UI_PREFS = "isolpolicy_ui_state";
    public static final String KEY_UI_THEME = "theme_mode";
    public static final String KEY_UI_PIXEL_ICON = "pixel_icons";
    public static final String KEY_UI_POLL_MS = "poll_ms";
    public static final String KEY_UI_SHOW_SYS = "show_sys";
    public static final String KEY_UI_HAPTIC = "haptic_keycap";
    public static final String KEY_LOCAL_POLICY = "local_policy_backup";

    public static final int PILLAR_AZ = 0;
    public static final int PILLAR_ISO = 1;
    public static final int PILLAR_SET = 2;
    public static final int PILLAR_USB = 3;
    public static final int PILLAR_A11Y = 4;
    public static final int PILLAR_PKG = 5;
    public static final int PILLAR_DISP = 6;
    public static final int PILLAR_PROC = 7;
    public static final int PILLAR_INP = 8;
    public static final int PILLAR_OBS = 9;
    public static final int PILLAR_COUNT = 10;

    public static final String[] PILLAR_CODES = {
        "AZ", "ISO", "SET", "USB", "A11Y", "PKG", "DISP", "PROC", "INP", "OBS"
    };

    public static final String[] PILLAR_TITLES = {
        "APP ZYGOTE",
        "ISO FOLD",
        "SETTINGS",
        "USB STATE",
        "ACCESSIBILITY",
        "PACKAGE",
        "DISPLAY",
        "PROCESS",
        "INPUT BUS",
        "OBSERVER"
    };

    public static final int VEC_UNKNOWN = 0;
    public static final int VEC_AZ_HOSTING_CREATE = 1;
    public static final int VEC_AZ_USES_QUERY = 2;
    public static final int VEC_AZ_START_LOCKED = 3;
    public static final int VEC_AZ_START_FALLBACK = 4;
    public static final int VEC_AZ_GRAPH_BIND = 5;
    public static final int VEC_ISO_FOLD_SERVICE = 6;
    public static final int VEC_ISO_FOLD_MATCH = 7;
    public static final int VEC_SET_CALL_GLOBAL = 8;
    public static final int VEC_SET_CALL_SECURE = 9;
    public static final int VEC_SET_CALL_SYSTEM = 10;
    public static final int VEC_SET_LIST_GLOBAL = 11;
    public static final int VEC_SET_LIST_SECURE = 12;
    public static final int VEC_SET_LIST_SYSTEM = 13;
    public static final int VEC_SET_QUERY_SINGLE = 14;
    public static final int VEC_SET_QUERY_BULK = 15;
    public static final int VEC_SET_A11Y_COHERENT = 16;
    public static final int VEC_USB_STICKY_INTENT = 17;
    public static final int VEC_USB_CUR_FUNCS = 18;
    public static final int VEC_USB_UNLOCKED_FUNCS = 19;
    public static final int VEC_USB_IS_ENABLED = 20;
    public static final int VEC_USB_ADB_WIFI_PORT = 21;
    public static final int VEC_A11Y_ADD_CLIENT = 22;
    public static final int VEC_A11Y_ENABLED_LIST = 23;
    public static final int VEC_A11Y_INSTALLED_LIST = 24;
    public static final int VEC_A11Y_SHORTCUTS = 25;
    public static final int VEC_A11Y_PUSH_STATE = 26;
    public static final int VEC_PKG_CHANGED = 27;
    public static final int VEC_PKG_INSTALL_SESSIONS = 28;
    public static final int VEC_DISP_GET_IDS = 29;
    public static final int VEC_DISP_GET_INFO = 30;
    public static final int VEC_DISP_PUSH_EVENT = 31;
    public static final int VEC_PROC_EXIT_REASONS = 32;
    public static final int VEC_PROC_MONKEY = 33;
    public static final int VEC_PROC_ERROR_STATE = 34;
    public static final int VEC_INP_DEVICE_IDS = 35;
    public static final int VEC_INP_DEVICE_INFO = 36;
    public static final int VEC_OBS_SETTINGS_PUSH = 37;
    public static final int VEC_OBS_REGISTER_FALLBACK = 38;

    private static final String[] VECTOR_NAMES = {
        "UNKNOWN",
        "AZ_BY_APP_ZYGOTE",
        "AZ_USES_QUERY",
        "AZ_START_LOCKED",
        "AZ_START_FALLBACK",
        "AZ_GRAPH_BIND",
        "ISO_FOLD_SVC",
        "ISO_FOLD_MATCH",
        "SET_GET_GLOBAL",
        "SET_GET_SECURE",
        "SET_GET_SYSTEM",
        "SET_LIST_GLOBAL",
        "SET_LIST_SECURE",
        "SET_LIST_SYSTEM",
        "SET_QUERY_ONE",
        "SET_QUERY_BULK",
        "SET_A11Y_KEY",
        "USB_STICKY",
        "USB_CUR_FN",
        "USB_UNLOCK_FN",
        "USB_IS_ADB",
        "ADB_WIFI_PORT",
        "A11Y_ADD_CLIENT",
        "A11Y_EN_LIST",
        "A11Y_INST_LIST",
        "A11Y_SHORTCUT",
        "A11Y_PUSH_STATE",
        "PKG_CHANGED",
        "PKG_SESSIONS",
        "DISP_GET_IDS",
        "DISP_GET_INFO",
        "DISP_PUSH_EVT",
        "PROC_EXIT_INFO",
        "PROC_MONKEY",
        "PROC_ERR_STATE",
        "INP_DEV_IDS",
        "INP_DEV_INFO",
        "OBS_PUSH_URI",
        "OBS_REG_URI"
    };

    public static final int ACT_ALLOW = 0;
    public static final int ACT_ENFORCED = 1;
    public static final int ACT_DOWNGRADED = 2;
    public static final int ACT_DROPPED = 3;
    public static final int ACT_FOLDED = 4;
    public static final int ACT_SANITIZED = 5;
    public static final int ACT_SUPPRESSED = 6;
    public static final int ACT_AUDIT = 7;

    private static final String[] ACTION_NAMES = {
        "ALLOW",
        "ENFORCE",
        "DOWNGRD",
        "DROP",
        "FOLD",
        "SANITZ",
        "SUPPR",
        "AUDIT"
    };

    private PolicyFlags() {
    }

    public static int sanitize(int flags) {
        return flags & ALL_SHIELDS_MASK;
    }

    public static int azMode(int flags) {
        return flags & AZ_MASK;
    }

    public static int withAzMode(int flags, int azMode) {
        return sanitize((flags & ~AZ_MASK) | (azMode & AZ_MASK));
    }

    public static boolean hasFlag(int flags, int bit) {
        return (flags & bit) != 0;
    }

    public static int toggleFlag(int flags, int bit) {
        return sanitize(flags ^ bit);
    }

    public static boolean isAuditOnly(int flags) {
        return (flags & FLAG_AUDIT_ONLY) != 0;
    }

    public static boolean isActive(int flags) {
        return (flags & ACTIVE_PILLARS_MASK) != 0;
    }

    public static boolean hasAnyProtection(int flags) {
        return (flags & ACTIVE_PILLARS_MASK) != 0;
    }

    public static int pillarFlagBit(int pillar) {
        switch (pillar) {
            case PILLAR_AZ:
                return AZ_MASK;
            case PILLAR_ISO:
                return FLAG_ISO_FOLD;
            case PILLAR_SET:
            case PILLAR_USB:
                return FLAG_DEV_COHERENT;
            case PILLAR_A11Y:
                return FLAG_A11Y_COHERENT;
            case PILLAR_PKG:
                return FLAG_PKG_CHANGES;
            case PILLAR_DISP:
                return FLAG_DISP_STEALTH;
            case PILLAR_PROC:
                return FLAG_PROC_SURFACE;
            case PILLAR_INP:
                return FLAG_INPUT_SURFACE;
            case PILLAR_OBS:
                return FLAG_OBS_COHERENCE;
            default:
                return 0;
        }
    }

    public static boolean isPillarEnabled(int flags, int pillar) {
        if (pillar == PILLAR_AZ) {
            return azMode(flags) != AZ_OFF;
        }
        int bit = pillarFlagBit(pillar);
        return bit != 0 && (flags & bit) != 0;
    }

    public static int countActivePillars(int flags) {
        int count = 0;
        if (azMode(flags) != AZ_OFF) {
            count++;
        }
        if (hasFlag(flags, FLAG_ISO_FOLD)) {
            count++;
        }
        if (hasFlag(flags, FLAG_DEV_COHERENT)) {
            count += 2;
        }
        if (hasFlag(flags, FLAG_A11Y_COHERENT)) {
            count++;
        }
        if (hasFlag(flags, FLAG_PKG_CHANGES)) {
            count++;
        }
        if (hasFlag(flags, FLAG_DISP_STEALTH)) {
            count++;
        }
        if (hasFlag(flags, FLAG_PROC_SURFACE)) {
            count++;
        }
        if (hasFlag(flags, FLAG_INPUT_SURFACE)) {
            count++;
        }
        if (hasFlag(flags, FLAG_OBS_COHERENCE)) {
            count++;
        }
        return count;
    }

    public static String azLabel(int az) {
        switch (az & AZ_MASK) {
            case AZ_AUTO:
                return "AUTO";
            case AZ_DOWNGRADE:
                return "DOWN";
            case AZ_HARD_DROP:
                return "DROP";
            default:
                return "OFF";
        }
    }

    public static String pillarCode(int pillar) {
        if (pillar >= 0 && pillar < PILLAR_CODES.length) {
            return PILLAR_CODES[pillar];
        }
        return "UNK";
    }

    public static String vectorName(int vectorCode) {
        if (vectorCode >= 0 && vectorCode < VECTOR_NAMES.length) {
            return VECTOR_NAMES[vectorCode];
        }
        return "VEC_" + vectorCode;
    }

    public static String actionName(int actionCode) {
        if (actionCode >= 0 && actionCode < ACTION_NAMES.length) {
            return ACTION_NAMES[actionCode];
        }
        return "ACT_" + actionCode;
    }

    public static String formatMaskHex(int flags) {
        int s = sanitize(flags);
        String hex = Integer.toHexString(s).toUpperCase();
        while (hex.length() < 3) {
            hex = "0" + hex;
        }
        return "0x" + hex;
    }

    public static String summarize(int flags) {
        int s = sanitize(flags);
        if (!isActive(s)) {
            return "OFF";
        }
        StringBuilder sb = new StringBuilder(48);
        int az = azMode(s);
        if (az != AZ_OFF) {
            sb.append("AZ:").append(azLabel(az));
        }
        appendTag(sb, s, FLAG_ISO_FOLD, "ISO");
        appendTag(sb, s, FLAG_DEV_COHERENT, "DEV");
        appendTag(sb, s, FLAG_A11Y_COHERENT, "A11Y");
        appendTag(sb, s, FLAG_PKG_CHANGES, "PKG");
        appendTag(sb, s, FLAG_DISP_STEALTH, "DISP");
        appendTag(sb, s, FLAG_PROC_SURFACE, "PROC");
        appendTag(sb, s, FLAG_INPUT_SURFACE, "INP");
        appendTag(sb, s, FLAG_OBS_COHERENCE, "OBS");
        appendTag(sb, s, FLAG_AUDIT_ONLY, "AUDIT");
        return sb.toString();
    }

    private static void appendTag(StringBuilder sb, int flags, int bit, String tag) {
        if ((flags & bit) != 0) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(tag);
        }
    }
}
