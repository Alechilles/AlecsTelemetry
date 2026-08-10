package com.alechilles.alecstelemetry.consent;

import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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

    @Test
    void capabilityExpansionNoticeNamesAddedCategoriesAndReviewCommand() {
        TelemetryProjectRegistration creditor = new TelemetryProjectRegistration(
                TelemetryProjectDescriptor.fromJson(
                        """
                        {
                          "projectId": "creditor",
                          "displayName": "Creditor",
                          "ownerPluginIdentifiers": ["Author:Creditor"]
                        }
                        """,
                        null
                ),
                "Author:Creditor",
                "1.4.0",
                Path.of("Creditor.jar")
        );

        String notice = TelemetryConsentCoordinator.reviewNotice(
                List.of(creditor),
                Map.of("creditor", List.of("error", "usage"))
        );

        assertTrue(notice.contains("Creditor"));
        assertTrue(notice.contains("Errors and Usage"));
        assertTrue(notice.contains("remain disabled"));
        assertTrue(notice.contains("`/telemetry consent`"));
    }
}
