package com.alechilles.alecstelemetry.report;

import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualReportEnvelopeTest {

    @TempDir
    Path tempDir;

    @Test
    void createsIssueReportEnvelopeWithSanitizedFields() {
        ManualReportEnvelope.CreateResult result = ManualReportEnvelope.create(
                registration(),
                TelemetryRuntimeSettings.load(tempDir.resolve("runtime.json"), null),
                new ManualReportSubmission(
                        ManualReportKind.ISSUE,
                        "A".repeat(150),
                        "Description",
                        "discord: example",
                        Map.of(
                                "severity", "major",
                                "steps", "Open config and click Save.",
                                "ignored", "drop me"
                        ),
                        true,
                        false,
                        true,
                        true,
                        true
                ),
                context(),
                List.of(new ManualReportAttachment.Manifest(
                        "attachment-1",
                        "current_server_log",
                        "current-server-log.txt",
                        "text/plain",
                        42,
                        "abc123"
                )),
                environment(),
                runtime()
        );

        assertTrue(result.accepted());
        ManualReportEnvelope envelope = result.envelope();
        assertNotNull(envelope);
        assertEquals(ManualReportEnvelope.EVENT_TYPE, envelope.eventType());
        assertEquals("issue", envelope.reportKind());
        assertEquals("example-mod", envelope.projectId());
        assertEquals("A".repeat(120), envelope.title());
        assertEquals("discord: example", envelope.contact().value());
        assertEquals("major", envelope.formValues().get("severity"));
        assertEquals("Open config and click Save.", envelope.formValues().get("steps"));
        assertFalse(envelope.formValues().containsKey("ignored"));
        assertEquals(1, envelope.attachmentManifests().size());
        assertTrue(envelope.reportId().length() > 10);

        JsonObject json = JsonParser.parseString(envelope.toJson()).getAsJsonObject();
        assertEquals("manual_report", json.get("eventType").getAsString());
        assertEquals("issue", json.get("reportKind").getAsString());
        assertTrue(json.has("reportId"));
        assertTrue(json.has("runtime"));
    }

    @Test
    void omitsContactWhenServerSettingsDisallowIt() throws Exception {
        Path settingsFile = tempDir.resolve("runtime.json");
        Files.writeString(settingsFile, """
                {
                  "manualReports": {
                    "allowContact": false
                  }
                }
                """);

        ManualReportEnvelope.CreateResult result = ManualReportEnvelope.create(
                registration(),
                TelemetryRuntimeSettings.load(settingsFile, null),
                new ManualReportSubmission(
                        ManualReportKind.SUGGESTION,
                        "Add config presets",
                        "Players could pick predefined presets.",
                        "email@example.com",
                        Map.of("category", "quality_of_life"),
                        false,
                        false,
                        false,
                        false,
                        false
                ),
                context(),
                List.of(),
                environment(),
                runtime()
        );

        assertTrue(result.accepted());
        assertFalse(result.envelope().contact().provided());
    }

    private static TelemetryProjectRegistration registration() {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "example-mod",
                  "displayName": "Example Mod",
                  "reports": {
                    "enabled": true,
                    "issue": {
                      "enabled": true,
                      "fields": {
                        "severity": {
                          "type": "enum",
                          "label": "Severity",
                          "required": true,
                          "values": ["minor", "moderate", "major", "blocking"]
                        },
                        "steps": {
                          "type": "text",
                          "label": "Steps to reproduce",
                          "maxLength": 2000
                        }
                      }
                    },
                    "suggestion": {
                      "enabled": true,
                      "fields": {
                        "category": {
                          "type": "enum",
                          "label": "Category",
                          "values": ["balance", "content", "quality_of_life", "other"]
                        }
                      }
                    },
                    "contact": {
                      "enabled": true,
                      "maxLength": 160
                    }
                  }
                }
                """,
                null
        );
        return new TelemetryProjectRegistration(descriptor, "Example:Example Mod", "1.2.3", null);
    }

    private static ManualReportContextSnapshot context() {
        return new ManualReportContextSnapshot("multiplayer", 12, true, true);
    }

    private static CrashReportEnvelope.EnvironmentSnapshot environment() {
        return CrashReportEnvelope.EnvironmentSnapshot.capture(
                "example-mod",
                "Example:Example Mod",
                "1.2.3",
                "dependency",
                runtime()
        );
    }

    private static CrashReportEnvelope.RuntimeMetadata runtime() {
        return CrashReportEnvelope.RuntimeMetadata.capture(
                List.of(new CrashReportEnvelope.LoadedModMetadata("Example:Example Mod", "1.2.3"))
        );
    }
}
