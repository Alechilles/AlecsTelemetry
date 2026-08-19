package com.alechilles.alecstelemetry.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable evidence for one attributed profiler path. */
public record TelemetryProfilerPathEvidence(
        @Nonnull String fingerprint,
        @Nonnull TelemetryProfilerAttribution attribution,
        @Nonnull String ownedClassName,
        @Nonnull String ownedMethodName,
        @Nonnull String ownedMethodDescriptor,
        @Nullable String firstExternalClassName,
        @Nullable String firstExternalMethodName,
        @Nullable String firstExternalMethodDescriptor,
        double sampledMilliseconds,
        double selectedWorldThreadSharePercent,
        @Nonnull TelemetryProfilerQualification qualification,
        @Nonnull List<String> representativePath) {

    public TelemetryProfilerPathEvidence {
        fingerprint = requiredText(fingerprint);
        attribution = Objects.requireNonNull(attribution, "attribution");
        ownedClassName = requiredText(ownedClassName);
        ownedMethodName = requiredText(ownedMethodName);
        ownedMethodDescriptor = requiredText(ownedMethodDescriptor);
        firstExternalClassName = optionalText(firstExternalClassName);
        firstExternalMethodName = optionalText(firstExternalMethodName);
        firstExternalMethodDescriptor = optionalText(firstExternalMethodDescriptor);
        requireFiniteNonNegative(sampledMilliseconds, "sampledMilliseconds");
        requireFiniteNonNegative(selectedWorldThreadSharePercent, "selectedWorldThreadSharePercent");
        qualification = Objects.requireNonNull(qualification, "qualification");
        representativePath = immutablePath(representativePath);
    }

    @Nonnull
    private static String requiredText(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "<unknown>";
        }
        return value.trim();
    }

    @Nullable
    private static String optionalText(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Nonnull
    private static List<String> immutablePath(@Nullable List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        ArrayList<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            normalized.add(requiredText(value));
        }
        return List.copyOf(normalized);
    }

    private static void requireFiniteNonNegative(double value, @Nonnull String fieldName) {
        if (!Double.isFinite(value) || value < 0.0d) {
            throw new IllegalArgumentException(fieldName + " must be finite and non-negative");
        }
    }
}
