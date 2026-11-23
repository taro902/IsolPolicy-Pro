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

    public static final int ALL_SHIELDS_MASK = 0x7F;

    public static final int PRESET_NONE = 0;
    public static final int PRESET_WALLET_ALIPAY =
            AZ_DOWNGRADE | FLAG_ISO_FOLD | FLAG_DEV_COHERENT | FLAG_A11Y_COHERENT | FLAG_PKG_CHANGES;
    public static final int PRESET_BANKING_STRICT =
            AZ_DOWNGRADE | FLAG_DEV_COHERENT | FLAG_A11Y_COHERENT | FLAG_PKG_CHANGES | FLAG_DISP_STEALTH;
    public static final int PRESET_AUDIT_MAX =
            AZ_DOWNGRADE | FLAG_ISO_FOLD | FLAG_DEV_COHERENT | FLAG_A11Y_COHERENT | FLAG_PKG_CHANGES | FLAG_DISP_STEALTH;

    public static final int PILLAR_APP_ZYGOTE = 1;
    public static final int PILLAR_ISO_FOLD = 2;
    public static final int PILLAR_SETTINGS = 3;
    public static final int PILLAR_USB = 4;
    public static final int PILLAR_A11Y = 5;
    public static final int PILLAR_PKG_CHANGE = 6;
    public static final int PILLAR_DISPLAY = 7;

    public static final int HOOK_BIT_APP_ZYGOTE = 0;
    public static final int HOOK_BIT_ISO_FOLD = 1;
    public static final int HOOK_BIT_SETTINGS = 2;
    public static final int HOOK_BIT_USB = 3;
    public static final int HOOK_BIT_A11Y = 4;
    public static final int HOOK_BIT_PKG_CHANGE = 5;
    public static final int HOOK_BIT_DISPLAY = 6;

    public static final int VEC_AZ_BY_RECORD = 1;
    public static final int VEC_AZ_USES = 2;
    public static final int VEC_AZ_START_LOCKED = 3;
    public static final int VEC_AZ_START_PROC = 4;
    public static final int VEC_ISO_SERVICE = 5;
    public static final int VEC_SET_GLOBAL_CALL = 6;
    public static final int VEC_SET_SECURE_CALL = 7;
    public static final int VEC_SET_QUERY = 8;
    public static final int VEC_USB_STICKY = 9;
    public static final int VEC_USB_FUNCS = 10;
    public static final int VEC_A11Y_CLIENT = 11;
    public static final int VEC_A11Y_ENABLED = 12;
    public static final int VEC_A11Y_INSTALLED = 13;
    public static final int VEC_A11Y_PUSH = 14;
    public static final int VEC_PKG_CHANGED = 15;
    public static final int VEC_DISP_IDS = 16;
    public static final int VEC_DISP_INFO = 17;

    public static final int ACT_DOWNGRADED = 1;
    public static final int ACT_HARD_DROPPED = 2;
    public static final int ACT_FOLDED = 3;
    public static final int ACT_SPOOFED = 4;
    public static final int ACT_SANITIZED = 5;
    public static final int ACT_CLEARED = 6;
    public static final int ACT_HIDDEN = 7;

    public static final String PREFS_GROUP = "isolpolicy_prefs";
    public static final String KEY_POLICY_MAP = "policy_map_v21";
    public static final String KEY_APPID_MAP = "appid_map_v21";
    public static final String KEY_SELF_APP_ID = "self_app_id";
    public static final String KEY_DEFAULT_AZ = "default_az_v21";
    public static final String KEY_LEGACY_V20_MODES = "pkg_modes";
    public static final String KEY_LEGACY_V20_TARGETS = "target_packages";

    public static final String IPC_METHOD = "ISOLPOLICY_IPC_V21";
    public static final String IPC_ARG_CMD = "cmd";
    public static final String IPC_CMD_SNAPSHOT = "SNAP";
    public static final String IPC_CMD_PURGE = "PURGE";
    public static final String IPC_CMD_PING = "PING";

    public static final String MODULE_PACKAGE = "io.github.taro902.isolationpolicy";
    public static final String VERSION_NAME = "2.1.1";

    private PolicyFlags() {
    }

    public static int getAzMode(int flags) {
        return flags & AZ_MASK;
    }

    public static int withAzMode(int flags, int azMode) {
        return (flags & ~AZ_MASK) | (azMode & AZ_MASK);
    }

    public static int cycleAzMode(int flags) {
        int next = (getAzMode(flags) + 1) & AZ_MASK;
        return withAzMode(flags, next);
    }

    public static boolean has(int flags, int bit) {
        return (flags & bit) != 0;
    }

    public static int toggle(int flags, int bit) {
        return (flags ^ bit) & ALL_SHIELDS_MASK;
    }

    public static boolean isActive(int flags) {
        return (flags & ALL_SHIELDS_MASK) != 0;
    }

    public static int activeShieldCount(int flags) {
        int count = getAzMode(flags) != AZ_OFF ? 1 : 0;
        if (has(flags, FLAG_ISO_FOLD)) count++;
        if (has(flags, FLAG_DEV_COHERENT)) count++;
        if (has(flags, FLAG_A11Y_COHERENT)) count++;
        if (has(flags, FLAG_PKG_CHANGES)) count++;
        if (has(flags, FLAG_DISP_STEALTH)) count++;
        return count;
    }

    public static String azLabel(int azMode) {
        switch (azMode & AZ_MASK) {
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

    public static String pillarName(int pillar) {
        switch (pillar) {
            case PILLAR_APP_ZYGOTE:
                return "AZ";
            case PILLAR_ISO_FOLD:
                return "ISO";
            case PILLAR_SETTINGS:
                return "SET";
            case PILLAR_USB:
                return "USB";
            case PILLAR_A11Y:
                return "A11Y";
            case PILLAR_PKG_CHANGE:
                return "PKG";
            case PILLAR_DISPLAY:
                return "DISP";
            default:
                return "SYS";
        }
    }

    public static String vectorName(int vec) {
        switch (vec) {
            case VEC_AZ_BY_RECORD:
                return "byAppZygote";
            case VEC_AZ_USES:
                return "usesAppZygote";
            case VEC_AZ_START_LOCKED:
                return "startProcessLocked";
            case VEC_AZ_START_PROC:
                return "startProcess";
            case VEC_ISO_SERVICE:
                return "bringUpService";
            case VEC_SET_GLOBAL_CALL:
                return "GET_global";
            case VEC_SET_SECURE_CALL:
                return "GET_secure";
            case VEC_SET_QUERY:
                return "Settings.query";
            case VEC_USB_STICKY:
                return "USB_STATE";
            case VEC_USB_FUNCS:
                return "UsbFunctions";
            case VEC_A11Y_CLIENT:
                return "AMS.addClient";
            case VEC_A11Y_ENABLED:
                return "AMS.getEnabled";
            case VEC_A11Y_INSTALLED:
                return "AMS.getInstalled";
            case VEC_A11Y_PUSH:
                return "AMS.statePush";
            case VEC_PKG_CHANGED:
                return "getChangedPkgs";
            case VEC_DISP_IDS:
                return "getDisplayIds";
            case VEC_DISP_INFO:
                return "getDisplayInfo";
            default:
                return "unknown";
        }
    }

    public static String actionName(int act) {
        switch (act) {
            case ACT_DOWNGRADED:
                return "DOWNGRADE";
            case ACT_HARD_DROPPED:
                return "HARD_DROP";
            case ACT_FOLDED:
                return "FOLD_MAIN";
            case ACT_SPOOFED:
                return "SPOOFED";
            case ACT_SANITIZED:
                return "SANITIZED";
            case ACT_CLEARED:
                return "CLEARED";
            case ACT_HIDDEN:
                return "HIDDEN";
            default:
                return "PASS";
        }
    }
}