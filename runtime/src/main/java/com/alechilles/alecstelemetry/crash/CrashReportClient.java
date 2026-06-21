package com.alechilles.alecstelemetry.crash;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Network boundary for crash report uploads.
 */
public interface CrashReportClient {

    @Nonnull
    UploadResult upload(@Nonnull DeliveryTarget target, @Nonnull String payloadJson);

    /**
     * Resolved destination details for one project.
     */
    record DeliveryTarget(@Nonnull String endpoint, @Nonnull Map<String, String> headers) {
        @Nonnull
        public DeliveryTarget normalize() {
            String safeEndpoint = endpoint == null ? "" : endpoint.trim();
            LinkedHashMap<String, String> safeHeaders = new LinkedHashMap<>();
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    if (entry == null) {
                        continue;
                    }
                    String key = entry.getKey() == null ? "" : entry.getKey().trim();
                    String value = entry.getValue() == null ? "" : entry.getValue().trim();
                    if (!key.isBlank() && !value.isBlank()) {
                        safeHeaders.put(key, value);
                    }
                }
            }
            return new DeliveryTarget(safeEndpoint, Map.copyOf(safeHeaders));
        }
    }

    /**
     * Result for one upload attempt.
     */
    record UploadResult(boolean success,
                        int statusCode,
                        @Nullable String detail,
                        int retryAfterSec,
                        @Nullable String intakeLane,
                        @Nullable Integer recommendedHeartbeatIntervalSec) {
        @Nonnull
        public static UploadResult success(int statusCode) {
            return new UploadResult(true, statusCode, null, 0, null, null);
        }

        @Nonnull
        public static UploadResult success(int statusCode,
                                           @Nullable String intakeLane,
                                           @Nullable Integer recommendedHeartbeatIntervalSec) {
            return new UploadResult(true, statusCode, null, 0, normalizeNullable(intakeLane), positiveOrNull(recommendedHeartbeatIntervalSec));
        }

        @Nonnull
        public static UploadResult failure(int statusCode, @Nullable String detail) {
            return new UploadResult(false, statusCode, detail, 0, null, null);
        }

        @Nonnull
        public static UploadResult failure(int statusCode,
                                           @Nullable String detail,
                                           int retryAfterSec,
                                           @Nullable String intakeLane,
                                           @Nullable Integer recommendedHeartbeatIntervalSec) {
            return new UploadResult(
                    false,
                    statusCode,
                    detail,
                    Math.max(0, retryAfterSec),
                    normalizeNullable(intakeLane),
                    positiveOrNull(recommendedHeartbeatIntervalSec)
            );
        }

        public boolean shouldBackOff() {
            return statusCode == 429 || retryAfterSec > 0;
        }

        @Nonnull
        public String describeFailure() {
            String base = detail == null || detail.isBlank() ? "HTTP status " + statusCode : detail;
            if (!shouldBackOff()) {
                return base;
            }
            return retryAfterSec > 0
                    ? base + " (rate limited; retry after " + retryAfterSec + "s)"
                    : base + " (rate limited)";
        }

        @Nullable
        private static String normalizeNullable(@Nullable String value) {
            if (value == null || value.trim().isBlank()) {
                return null;
            }
            return value.trim();
        }

        @Nullable
        private static Integer positiveOrNull(@Nullable Integer value) {
            return value == null || value <= 0 ? null : value;
        }
    }
}
