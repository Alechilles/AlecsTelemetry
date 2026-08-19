package com.alechilles.alecstelemetry.runtime.profiler;

import com.alechilles.alecstelemetry.api.TelemetryProfilerAttribution;
import com.alechilles.alecstelemetry.api.TelemetryProfilerQualification;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/** Immutable project-scoped result produced by the local profiler analyzer. */
record ProfilerProjectWindow(@Nonnull String projectId,
                             @Nonnull String projectVersion,
                             @Nonnull String sparkVersion,
                             int windowKey,
                             double selectedWorldThreadSelfMilliseconds,
                             @Nonnull String qualificationRuleSet,
                             @Nonnull List<Path> paths) {

    static final String RULE_SET = "unqualified-v1";

    ProfilerProjectWindow(@Nonnull String projectId,
                          @Nonnull String projectVersion,
                          @Nonnull String sparkVersion,
                          int windowKey,
                          double selectedWorldThreadSelfMilliseconds,
                          @Nonnull List<Path> paths) {
        this(projectId, projectVersion, sparkVersion, windowKey,
                selectedWorldThreadSelfMilliseconds, RULE_SET, paths);
    }

    ProfilerProjectWindow {
        projectId = requiredText(projectId);
        projectVersion = requiredText(projectVersion);
        sparkVersion = requiredText(sparkVersion);
        if (!Double.isFinite(selectedWorldThreadSelfMilliseconds)
                || selectedWorldThreadSelfMilliseconds < 0.0d) {
            throw new IllegalArgumentException(
                    "selectedWorldThreadSelfMilliseconds must be finite and non-negative");
        }
        qualificationRuleSet = requiredText(qualificationRuleSet);
        paths = paths == null ? List.of() : List.copyOf(paths);
    }

    @Nonnull
    private static String requiredText(String value) {
        return value == null || value.isBlank() ? "<unknown>" : value.trim();
    }

    /** One direct or nearest-owner downstream path. */
    record Path(@Nonnull String fingerprint,
                @Nonnull ProfilerPathIdentity identity,
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

        Path(@Nonnull ProfilerPathIdentity identity,
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
            this(identity.fingerprint(), identity, attribution, ownedClassName, ownedMethodName,
                    ownedMethodDescriptor, firstExternalClassName, firstExternalMethodName,
                    firstExternalMethodDescriptor, sampledMilliseconds,
                    selectedWorldThreadSharePercent, qualification, representativePath);
        }

        Path {
            fingerprint = requiredText(fingerprint);
            identity = Objects.requireNonNull(identity, "identity");
            attribution = Objects.requireNonNull(attribution, "attribution");
            ownedClassName = requiredText(ownedClassName);
            ownedMethodName = requiredText(ownedMethodName);
            ownedMethodDescriptor = requiredText(ownedMethodDescriptor);
            firstExternalClassName = optionalText(firstExternalClassName);
            firstExternalMethodName = optionalText(firstExternalMethodName);
            firstExternalMethodDescriptor = optionalText(firstExternalMethodDescriptor);
            if (!Double.isFinite(sampledMilliseconds) || sampledMilliseconds < 0.0d
                    || !Double.isFinite(selectedWorldThreadSharePercent)
                    || selectedWorldThreadSharePercent < 0.0d) {
                throw new IllegalArgumentException("Profiler path measurements must be finite and non-negative");
            }
            qualification = Objects.requireNonNull(qualification, "qualification");
            representativePath = boundedPath(representativePath);
        }

        @Nonnull
        private static String requiredText(String value) {
            return value == null || value.isBlank() ? "<unknown>" : value.trim();
        }

        @Nullable
        private static String optionalText(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }

        @Nonnull
        private static List<String> boundedPath(List<String> values) {
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            return values.stream()
                    .limit(8)
                    .map(Path::requiredText)
                    .map(value -> value.length() <= 512 ? value : value.substring(0, 512))
                    .toList();
        }
    }
}
