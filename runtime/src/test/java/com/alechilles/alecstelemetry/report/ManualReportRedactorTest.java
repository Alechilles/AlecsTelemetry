package com.alechilles.alecstelemetry.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualReportRedactorTest {

    @Test
    void redactsCommonSecretsAndIdentifiers() {
        String redacted = ManualReportRedactor.redact(
                """
                Authorization: Bearer abc.def.ghi
                Bot token: MTAxMzQ1Njc4OTAxMjM0NTY3.Oabcde.secret
                Player email test@example.com
                Connecting to 192.168.1.10
                C:\\Users\\Somebody\\AppData\\Roaming\\Hytale
                Normal gameplay line remains.
                """
        );

        assertTrue(redacted.contains("[redacted:secret]"));
        assertTrue(redacted.contains("[redacted:token]"));
        assertTrue(redacted.contains("[redacted:email]"));
        assertTrue(redacted.contains("[redacted:ip]"));
        assertTrue(redacted.contains("[redacted:path]"));
        assertTrue(redacted.contains("Normal gameplay line remains."));
        assertFalse(redacted.contains("abc.def.ghi"));
        assertFalse(redacted.contains("test@example.com"));
        assertFalse(redacted.contains("192.168.1.10"));
        assertFalse(redacted.contains("Somebody"));
    }
}
