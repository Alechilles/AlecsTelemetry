package com.alechilles.alecstelemetry.report;

import com.alechilles.alecstelemetry.crash.CrashReportEnvelope;
import com.alechilles.alecstelemetry.project.TelemetryProjectDescriptor;
import com.alechilles.alecstelemetry.project.TelemetryProjectRegistration;
import com.alechilles.alecstelemetry.runtime.TelemetryRuntimeSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualReportFormDefinitionTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsMissingRequiredField() {
        ManualReportEnvelope.CreateResult result = create(Map.of());

        assertFalse(result.accepted());
        assertTrue(result.validationErrors().contains("required_field:severity"));
    }

    @Test
    void rejectsInvalidRequiredEnumField() {
        ManualReportEnvelope.CreateResult result = create(Map.of("severity", "catastrophic"));

        assertFalse(result.accepted());
        assertTrue(result.validationErrors().contains("invalid_field:severity"));
    }

    @Test
    void rejectsWhenProjectReportsAreDisabled() {
        TelemetryProjectDescriptor descriptor = TelemetryProjectDescriptor.fromJson(
                """
                {
                  "projectId": "disabled-mod",
                  "displayName": "Disabled Mod"
                }
                """,
                null
        );

        ManualReportEnvelope.CreateResult result = ManualReportEnvelope.create(
                new TelemetryProjectRegistration(descriptor, "Example:Disabled Mod", "1.0.0", null),
                TelemetryRuntimeSettings.load(tempDir.resolve("runtime.json"), null),
                submission(Map.of("severity", "major")),
                new ManualReportContextSnapshot("singleplayer", 1, true, true),
                List.of(),
                environment(),
                runtime()
        );

        assertFalse(result.accepted());
        assertTrue(result.validationErrors().contains("project_reports_disabled"));
    }

    private ManualReportEnvelope.CreateResult create(Map<String, Object> formValues) {
        return ManualReportEnvelope.create(
                registration(),
                TelemetryRuntimeSettings.load(tempDir.resolve("runtime.json"), null),
                submission(formValues),
                new ManualReportContextSnapshot("singleplayer", 1, true, true),
                List.of(),
                environment(),
                runtime()
        );
    }

    private static ManualReportSubmission submission(Map<String, Object> formValues) {
        return new ManualReportSubmission(
                ManualReportKind.ISSUE,
                "Issue title",
                "Issue description",
                null,
                formValues,
                false,
                false,
                true,
                true,
                false
        );
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
                        }
                      }
                    }
                  }
                }
                """,
                null
        );
        return new TelemetryProjectRegistration(descriptor, "Example:Example Mod", "1.2.3", null);
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
        return CrashReportEnvelope.RuntimeMetadata.capture(List.of());
    }
}
