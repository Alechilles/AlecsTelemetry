package com.alechilles.alecstelemetry.coordinator;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TelemetryRuntimeCandidateTest {

    @Test
    void highestCompatibleRuntimeVersionWinsOverStandalone() {
        TelemetryRuntimeCandidate standalone = candidate(
                "standalone",
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.3",
                "Alechilles:Alec's Telemetry"
        );
        TelemetryRuntimeCandidate embedded = candidate(
                "embedded-tamework",
                TelemetryRuntimeOrigin.EMBEDDED,
                "0.1.4",
                "Alechilles:Alec's Tamework!"
        );

        assertEquals(embedded, TelemetryRuntimeCandidate.selectWinner(List.of(standalone, embedded), 1));
    }

    @Test
    void incompatibleNewerRuntimeIsIgnored() {
        TelemetryRuntimeCandidate standalone = candidate(
                "standalone",
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.3",
                "Alechilles:Alec's Telemetry"
        );
        TelemetryRuntimeCandidate incompatible = new TelemetryRuntimeCandidate(
                "embedded-new",
                TelemetryRuntimeOrigin.EMBEDDED,
                "9.0.0",
                2,
                "Alechilles:Future Mod",
                "1.0.0",
                Path.of("Future.jar"),
                Path.of("UserData").resolve("Telemetry")
        );

        assertEquals(standalone, TelemetryRuntimeCandidate.selectWinner(List.of(standalone, incompatible), 1));
    }

    @Test
    void standaloneWinsTieOnEqualRuntimeVersion() {
        TelemetryRuntimeCandidate standalone = candidate(
                "standalone",
                TelemetryRuntimeOrigin.STANDALONE,
                "0.1.4",
                "Alechilles:Alec's Telemetry"
        );
        TelemetryRuntimeCandidate embedded = candidate(
                "embedded-tamework",
                TelemetryRuntimeOrigin.EMBEDDED,
                "0.1.4",
                "Alechilles:Alec's Tamework!"
        );

        assertEquals(standalone, TelemetryRuntimeCandidate.selectWinner(List.of(embedded, standalone), 1));
    }

    @Test
    void noWinnerWhenNoCandidateUsesRequiredProtocol() {
        TelemetryRuntimeCandidate incompatible = candidate(
                "embedded-tamework",
                TelemetryRuntimeOrigin.EMBEDDED,
                "0.1.4",
                "Alechilles:Alec's Tamework!",
                2
        );

        assertNull(TelemetryRuntimeCandidate.selectWinner(List.of(incompatible), 1));
    }

    private static TelemetryRuntimeCandidate candidate(String providerId,
                                                       TelemetryRuntimeOrigin origin,
                                                       String runtimeVersion,
                                                       String pluginIdentifier) {
        return candidate(providerId, origin, runtimeVersion, pluginIdentifier, 1);
    }

    private static TelemetryRuntimeCandidate candidate(String providerId,
                                                       TelemetryRuntimeOrigin origin,
                                                       String runtimeVersion,
                                                       String pluginIdentifier,
                                                       int protocolVersion) {
        return new TelemetryRuntimeCandidate(
                providerId,
                origin,
                runtimeVersion,
                protocolVersion,
                pluginIdentifier,
                "1.0.0",
                Path.of(providerId + ".jar"),
                Path.of("UserData").resolve("Telemetry")
        );
    }
}
