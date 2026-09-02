package com.alechilles.beacon.embedded;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Immutable descriptor and logical identity supplied by an embedded component.
 */
public final class TelemetryProjectContribution {
    private final Class<?> resourceAnchor;
    private final String descriptorResource;
    private final String logicalPluginIdentifier;
    private final String logicalPluginVersion;

    private TelemetryProjectContribution(@Nonnull Class<?> resourceAnchor,
                                         @Nonnull String descriptorResource,
                                         @Nonnull String logicalPluginIdentifier,
                                         @Nonnull String logicalPluginVersion) {
        this.resourceAnchor = Objects.requireNonNull(resourceAnchor, "resourceAnchor");
        this.descriptorResource = requireResource(descriptorResource);
        this.logicalPluginIdentifier = requireText(logicalPluginIdentifier, "logicalPluginIdentifier");
        this.logicalPluginVersion = requireText(logicalPluginVersion, "logicalPluginVersion");
    }

    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    @Nonnull
    public Class<?> resourceAnchor() {
        return resourceAnchor;
    }

    @Nonnull
    public String descriptorResource() {
        return descriptorResource;
    }

    @Nonnull
    public String logicalPluginIdentifier() {
        return logicalPluginIdentifier;
    }

    @Nonnull
    public String logicalPluginVersion() {
        return logicalPluginVersion;
    }

    @Nonnull
    private static String requireResource(String value) {
        String normalized = requireText(value, "descriptorResource");
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("descriptorResource must not be blank");
            }
        }
        return normalized;
    }

    @Nonnull
    private static String requireText(String value, @Nonnull String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    /** Builder for an immutable contribution. */
    public static final class Builder {
        private Class<?> resourceAnchor;
        private String descriptorResource;
        private String logicalPluginIdentifier;
        private String logicalPluginVersion;

        private Builder() {
        }

        @Nonnull
        public Builder descriptorResource(@Nonnull Class<?> resourceAnchor,
                                          @Nonnull String descriptorResource) {
            this.resourceAnchor = Objects.requireNonNull(resourceAnchor, "resourceAnchor");
            this.descriptorResource = requireResource(descriptorResource);
            return this;
        }

        @Nonnull
        public Builder resourceAnchor(@Nonnull Class<?> resourceAnchor) {
            this.resourceAnchor = Objects.requireNonNull(resourceAnchor, "resourceAnchor");
            return this;
        }

        @Nonnull
        public Builder descriptorResource(@Nonnull String descriptorResource) {
            this.descriptorResource = requireResource(descriptorResource);
            return this;
        }

        @Nonnull
        public Builder logicalPluginIdentifier(@Nonnull String logicalPluginIdentifier) {
            this.logicalPluginIdentifier = requireText(logicalPluginIdentifier, "logicalPluginIdentifier");
            return this;
        }

        @Nonnull
        public Builder logicalPluginVersion(@Nonnull String logicalPluginVersion) {
            this.logicalPluginVersion = requireText(logicalPluginVersion, "logicalPluginVersion");
            return this;
        }

        @Nonnull
        public TelemetryProjectContribution build() {
            return new TelemetryProjectContribution(
                    Objects.requireNonNull(resourceAnchor, "resourceAnchor"),
                    Objects.requireNonNull(descriptorResource, "descriptorResource"),
                    Objects.requireNonNull(logicalPluginIdentifier, "logicalPluginIdentifier"),
                    Objects.requireNonNull(logicalPluginVersion, "logicalPluginVersion")
            );
        }
    }
}
