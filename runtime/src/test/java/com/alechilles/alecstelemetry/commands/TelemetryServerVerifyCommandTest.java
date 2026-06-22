package com.alechilles.alecstelemetry.commands;

import com.alechilles.alecstelemetry.coordinator.TelemetryServerVerificationResult;
import com.alechilles.alecstelemetry.core.TelemetryCoreEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryServerVerifyCommandTest {

    @Test
    void queuedMessageReportsSuccessfulUploadAsSent() {
        String message = TelemetryServerVerifyCommand.verificationQueuedMessage(new TelemetryServerVerificationResult(
                TelemetryServerVerificationResult.Status.QUEUED,
                1,
                new TelemetryCoreEngine.FlushSummary(1, 1, 0, null)
        ));

        assertEquals("Server verification heartbeat sent. Check the ModStats portal for verification status.", message);
    }

    @Test
    void queuedMessageDoesNotClaimSentWhenUploadFails() {
        String message = TelemetryServerVerifyCommand.verificationQueuedMessage(new TelemetryServerVerificationResult(
                TelemetryServerVerificationResult.Status.QUEUED,
                1,
                new TelemetryCoreEngine.FlushSummary(1, 0, 1, "HTTP status 403")
        ));

        assertTrue(message.contains("could not be delivered"));
        assertTrue(message.contains("Attempted 1, sent 0, pending 1"));
        assertTrue(message.contains("HTTP status 403"));
    }

    @Test
    void claimTokenArgumentReadsVerifyKey() {
        assertEquals("ms_claim_from_command", TelemetryServerVerifyCommand.claimTokenArgument(
                "/telemetry server verify ms_claim_from_command"
        ));
    }
}
