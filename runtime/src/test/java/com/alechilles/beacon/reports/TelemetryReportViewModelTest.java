package com.alechilles.beacon.reports;

import com.alechilles.beacon.project.TelemetryProjectDescriptor;
import com.alechilles.beacon.project.TelemetryProjectRegistration;
import com.alechilles.beacon.runtime.TelemetryRuntimeSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryReportViewModelTest {

    @TempDir
    Path tempDir;

    @Test
    void showsKindSelectorWhenIssueAndSuggestionAreEnabled() {
        TelemetryReportViewModel viewModel = TelemetryReportViewModel.from(
                registration(true),
                settings("{}").manualReports(),
                new TelemetryReportOpenRequest("issue", null, null)
        );

        assertTrue(viewModel.showKindSelector());
        assertEquals("issue", viewModel.selectedKind());
        assertTrue(viewModel.showContact());
        assertEquals(2, viewModel.fields().size());
        assertEquals("severity", viewModel.fields().getFirst().key());
    }

    @Test
    void hidesSuggestionWhenDescriptorDisablesSuggestionReports() {
        TelemetryReportViewModel viewModel = TelemetryReportViewModel.from(
                registration(false),
                settings("{}").manualReports(),
                new TelemetryReportOpenRequest("suggestion", null, null)
        );

        assertFalse(viewModel.showKindSelector());
        assertTrue(viewModel.issueEnabled());
        assertFalse(viewModel.suggestionEnabled());
        assertEquals("issue", viewModel.selectedKind());
    }

    @Test
    void hidesServerDisallowedOptionalSections() {
        TelemetryReportViewModel viewModel = TelemetryReportViewModel.from(
                registration(true),
                settings("""
                        {
                          "manualReports": {
                            "allowContact": false,
                            "allowCurrentServerLog": false,
                            "allowPreviousServerLog": false,
                            "allowLoadedModList": false,
                            "allowDiagnostics": false
                          }
                        }
                        """).manualReports(),
                new TelemetryReportOpenRequest("issue", null, null)
        );

        assertFalse(viewModel.showContact());
        assertFalse(viewModel.showCurrentServerLog());
        assertFalse(viewModel.showPreviousServerLog());
        assertFalse(viewModel.showLoadedModList());
        assertFalse(viewModel.showDiagnostics());
    }

    @Test
    void includesManualReviewWarningWhenServerRequiresReview() {
        TelemetryReportViewModel viewModel = TelemetryReportViewModel.from(
                registration(true),
                settings("""
                        {
                          "manualReports": {
                            "manualReviewRequired": true
                          }
                        }
                        """).manualReports(),
                new TelemetryReportOpenRequest("issue", null, null)
        );

        assertTrue(viewModel.manualReviewRequired());
    }

    private TelemetryRuntimeSettings settings(String rawJson) {
        try {
            Path settingsFile = tempDir.resolve("runtime-" + Math.abs(rawJson.hashCode()) + ".json");
            Files.writeString(settingsFile, rawJson);
            return TelemetryRuntimeSettings.load(settingsFile, null);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static TelemetryProjectRegistration registration(boolean suggestionEnabled) {
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
                          "values": ["minor", "major", "blocking"]
                        },
                        "steps": {
                          "type": "text",
                          "label": "Steps to reproduce",
                          "maxLength": 2000
                        }
                      }
                    },
                    "suggestion": {
                      "enabled": %s,
                      "fields": {
                        "category": {
                          "type": "enum",
                          "label": "Category",
                          "values": ["balance", "quality_of_life"]
                        }
                      }
                    },
                    "attachments": {
                      "currentServerLog": true,
                      "previousServerLog": true
                    },
                    "contact": {
                      "enabled": true
                    }
                  }
                }
                """.formatted(suggestionEnabled),
                null
        );
        return new TelemetryProjectRegistration(descriptor, "Example:Example Mod", "1.2.3", null);
    }
}
