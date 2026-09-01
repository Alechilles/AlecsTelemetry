package com.alechilles.beacon.commands;

final class TelemetryCommandPermissions {
    private static final String[] ADMIN_GROUPS = {
            "hytale:Admin",
            "OP",
            "Admin",
            "Operator"
    };

    private TelemetryCommandPermissions() {
    }

    static String[] adminGroups() {
        return ADMIN_GROUPS.clone();
    }
}
