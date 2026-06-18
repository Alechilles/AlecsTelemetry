package com.alechilles.alecstelemetry.consent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryConsentCoordinatorTest {

    @Test
    void firstRunConsentNoticeUsesOperatorFacingChatCopy() {
        assertEquals("telemetry.command.telemetry", TelemetryConsentCoordinator.ROOT_PERMISSION);
        assertEquals("#f0a33a", TelemetryConsentCoordinator.FIRST_RUN_NOTICE_COLOR);
        assertTrue(TelemetryConsentCoordinator.FIRST_RUN_NOTICE.contains("anonymous statistics"));
        assertTrue(TelemetryConsentCoordinator.FIRST_RUN_NOTICE.contains("crash, error, performance diagnostics"));
        assertTrue(TelemetryConsentCoordinator.FIRST_RUN_NOTICE.contains("`/telemetry consent`"));
    }
}
