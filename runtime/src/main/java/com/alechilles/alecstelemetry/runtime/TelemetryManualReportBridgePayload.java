package com.alechilles.alecstelemetry.runtime;

import com.alechilles.alecstelemetry.report.ManualReportEnvelope;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JDK-only manual report bridge payload conversion helpers.
 */
public final class TelemetryManualReportBridgePayload {
    private TelemetryManualReportBridgePayload() {
    }

    @Nonnull
    public static Map<String, Object> envelopeSummary(@Nonnull ManualReportEnvelope envelope) {
        return Map.of("envelopeJson", envelope.toJson());
    }

    @Nonnull
    public static List<Map<String, Object>> envelopeSummaries(@Nonnull List<ManualReportEnvelope> envelopes) {
        ArrayList<Map<String, Object>> summaries = new ArrayList<>(envelopes.size());
        for (ManualReportEnvelope envelope : envelopes) {
            summaries.add(envelopeSummary(envelope));
        }
        return List.copyOf(summaries);
    }

    @Nonnull
    public static List<ManualReportEnvelope> envelopesFromSummaries(@Nonnull List<Map<String, Object>> summaries) {
        ArrayList<ManualReportEnvelope> envelopes = new ArrayList<>(summaries.size());
        for (Map<String, Object> summary : summaries) {
            ManualReportEnvelope envelope = envelopeFromSummary(summary);
            if (envelope != null) {
                envelopes.add(envelope);
            }
        }
        return List.copyOf(envelopes);
    }

    @Nullable
    public static ManualReportEnvelope envelopeFromSummary(@Nonnull Map<String, Object> summary) {
        Object value = summary.get("envelopeJson");
        if (value == null) {
            return null;
        }
        String rawJson = value.toString().trim();
        if (rawJson.isBlank()) {
            return null;
        }
        try {
            return ManualReportEnvelope.fromJson(rawJson);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
