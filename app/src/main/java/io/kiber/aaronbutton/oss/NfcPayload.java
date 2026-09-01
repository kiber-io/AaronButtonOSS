package io.kiber.aaronbutton.oss;

final class NfcPayload {
    private static final String PREFIX = "pita://polis/";

    private NfcPayload() {
    }

    static String encode(String androidId, String action) {
        if (androidId == null || androidId.trim().isEmpty()) {
            throw new IllegalArgumentException("Could not determine Android ID");
        }
        if (action == null || action.trim().isEmpty()
                || action.indexOf('\n') >= 0 || action.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Invalid action");
        }
        return PREFIX + androidId.trim() + ":" + action.trim();
    }

    static String actionFor(String payload, String androidId) {
        if (payload == null || androidId == null) {
            return null;
        }
        String value = payload.trim();
        if (value.startsWith(PREFIX)) {
            value = value.substring(PREFIX.length());
        }
        int separator = value.indexOf(':');
        if (separator <= 0 || !androidId.equals(value.substring(0, separator))) {
            return null;
        }
        String action = value.substring(separator + 1).trim();
        return action.isEmpty() ? null : action;
    }
}
