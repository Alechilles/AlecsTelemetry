package com.alechilles.alecstelemetry.crash;

import com.hypixel.hytale.logger.HytaleLogger;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;

/**
 * HTTP implementation for crash report uploads.
 */
public final class HttpCrashReportClient implements CrashReportClient {

    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final HytaleLogger logger;

    public HttpCrashReportClient(int connectTimeoutMs,
                                 int readTimeoutMs,
                                 @Nullable HytaleLogger logger) {
        this.connectTimeoutMs = Math.max(100, connectTimeoutMs);
        this.readTimeoutMs = Math.max(100, readTimeoutMs);
        this.logger = logger;
    }

    @Nonnull
    @Override
    public UploadResult upload(@Nonnull DeliveryTarget target, @Nonnull String payloadJson) {
        DeliveryTarget normalizedTarget = target.normalize();
        if (normalizedTarget.endpoint().isBlank()) {
            return UploadResult.failure(0, "Endpoint is not configured.");
        }
        HttpURLConnection http = null;
        try {
            http = (HttpURLConnection) URI.create(normalizedTarget.endpoint()).toURL().openConnection();
            http.setConnectTimeout(connectTimeoutMs);
            http.setReadTimeout(readTimeoutMs);
            http.setRequestMethod("POST");
            http.setDoOutput(true);
            http.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            for (Map.Entry<String, String> header : normalizedTarget.headers().entrySet()) {
                http.setRequestProperty(header.getKey(), header.getValue());
            }

            byte[] payloadBytes = payloadJson.getBytes(StandardCharsets.UTF_8);
            http.setFixedLengthStreamingMode(payloadBytes.length);
            try (OutputStream output = http.getOutputStream()) {
                output.write(payloadBytes);
            }

            int statusCode = http.getResponseCode();
            String responseBody = readString(statusCode >= 200 && statusCode < 300 ? http.getInputStream() : http.getErrorStream());
            ResponseHints responseHints = parseResponseHints(responseBody);
            if (statusCode >= 200 && statusCode < 300) {
                return UploadResult.success(
                        statusCode,
                        responseHints.intakeLane(),
                        responseHints.recommendedHeartbeatIntervalSec()
                );
            }

            int retryAfterSec = parseRetryAfterSec(http.getHeaderField("Retry-After"));
            if (retryAfterSec <= 0) {
                retryAfterSec = responseHints.retryAfterSec();
            }
            return UploadResult.failure(
                    statusCode,
                    responseHints.error() == null ? "HTTP " + statusCode : responseHints.error(),
                    retryAfterSec,
                    responseHints.intakeLane(),
                    responseHints.recommendedHeartbeatIntervalSec()
            );
        } catch (Exception ex) {
            if (logger != null) {
                logger.at(Level.FINE).log("Crash telemetry upload request failed: "
                        + ex.getClass().getSimpleName()
                        + (ex.getMessage() == null || ex.getMessage().isBlank() ? "" : ": " + ex.getMessage()));
            }
            return UploadResult.failure(0, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        } finally {
            if (http != null) {
                http.disconnect();
            }
        }
    }

    @Nonnull
    private static String readString(@Nullable InputStream stream) {
        if (stream == null) {
            return "";
        }
        try (InputStream input = stream) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static int parseRetryAfterSec(@Nullable String value) {
        if (value == null || value.trim().isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    @Nonnull
    private static ResponseHints parseResponseHints(@Nonnull String responseBody) {
        if (responseBody.isBlank()) {
            return ResponseHints.EMPTY;
        }
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonObject intake = root.has("intake") && root.get("intake").isJsonObject()
                    ? root.getAsJsonObject("intake")
                    : null;
            return new ResponseHints(
                    stringOrNull(root, "error"),
                    intOrZero(root, "retryAfterSec"),
                    intake == null ? null : stringOrNull(intake, "lane"),
                    intake == null ? null : positiveIntegerOrNull(intake, "recommendedHeartbeatIntervalSec")
            );
        } catch (Exception ignored) {
            return ResponseHints.EMPTY;
        }
    }

    @Nullable
    private static String stringOrNull(@Nonnull JsonObject object, @Nonnull String memberName) {
        return object.has(memberName) && object.get(memberName).isJsonPrimitive()
                ? object.get(memberName).getAsString()
                : null;
    }

    private static int intOrZero(@Nonnull JsonObject object, @Nonnull String memberName) {
        Integer value = positiveIntegerOrNull(object, memberName);
        return value == null ? 0 : value;
    }

    @Nullable
    private static Integer positiveIntegerOrNull(@Nonnull JsonObject object, @Nonnull String memberName) {
        if (!object.has(memberName) || !object.get(memberName).isJsonPrimitive()) {
            return null;
        }
        try {
            int value = object.get(memberName).getAsInt();
            return value <= 0 ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }

    private record ResponseHints(@Nullable String error,
                                 int retryAfterSec,
                                 @Nullable String intakeLane,
                                 @Nullable Integer recommendedHeartbeatIntervalSec) {
        private static final ResponseHints EMPTY = new ResponseHints(null, 0, null, null);
    }
}
