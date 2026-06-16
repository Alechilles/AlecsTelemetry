package com.alechilles.alecstelemetry.report;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.regex.Pattern;

/**
 * Redacts sensitive values from player-approved diagnostic attachments.
 */
public final class ManualReportRedactor {

    private static final Pattern BEARER_SECRET = Pattern.compile("(?i)(authorization\\s*:\\s*bearer\\s+)[^\\s]+");
    private static final Pattern DISCORD_TOKEN = Pattern.compile("[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}");
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern IPV4 = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\b");
    private static final Pattern WINDOWS_USER_PATH = Pattern.compile("(?i)[A-Z]:\\\\Users\\\\[^\\\\\\r\\n]+(?=\\\\|\\s|$)");

    private ManualReportRedactor() {
    }

    @Nonnull
    public static String redact(@Nullable String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String redacted = BEARER_SECRET.matcher(input).replaceAll("$1[redacted:secret]");
        redacted = DISCORD_TOKEN.matcher(redacted).replaceAll("[redacted:token]");
        redacted = EMAIL.matcher(redacted).replaceAll("[redacted:email]");
        redacted = IPV4.matcher(redacted).replaceAll("[redacted:ip]");
        redacted = WINDOWS_USER_PATH.matcher(redacted).replaceAll("[redacted:path]");
        return redacted;
    }
}
