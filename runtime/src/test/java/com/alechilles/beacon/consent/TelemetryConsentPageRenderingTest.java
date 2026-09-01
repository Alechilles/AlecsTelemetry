package com.alechilles.beacon.consent;

import com.hypixel.hytale.protocol.packets.interface_.CustomUICommandType;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryConsentPageRenderingTest {

    @Test
    void rendersAndBindsDiagnosticsCategory() {
        TelemetryConsentSnapshot consent = new TelemetryConsentSnapshot(
                true, true, true, true, true, true, true, true, true
        );
        TelemetryConsentViewModel.ProjectRow project = new TelemetryConsentViewModel.ProjectRow(
                "diagnostics-project",
                "Diagnostics Project",
                false,
                "hosted",
                0,
                "Example:diagnostics-project",
                "1.0.0",
                null,
                null,
                "embedded",
                consent,
                consent
        );
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();

        TelemetryConsentPage.renderProjectRows(commands, events, List.of(project));

        String diagnosticsSelector = "#TelemetryConsentRows[0] #TelemetryConsentDiagnosticsEnabled";
        String diagnosticsToggleSelector = "#TelemetryConsentRows[0] #TelemetryConsentDiagnosticsToggleButton";
        assertTrue(java.util.Arrays.stream(commands.getCommands())
                .anyMatch(command -> (diagnosticsSelector + ".Value").equals(command.selector)));
        assertTrue(java.util.Arrays.stream(events.getEvents())
                .anyMatch(event -> diagnosticsSelector.equals(event.selector)
                        && event.data != null
                        && event.data.contains("diagnostics")));
        assertTrue(java.util.Arrays.stream(events.getEvents())
                .anyMatch(event -> diagnosticsToggleSelector.equals(event.selector)
                        && event.data != null
                        && event.data.contains("diagnostics")));
    }

    @Test
    void rendersAndBindsProjectsBeyondTheFormerEightRowLimit() {
        List<TelemetryConsentViewModel.ProjectRow> projects = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            projects.add(project("project-" + index));
        }
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();

        TelemetryConsentPage.renderProjectRows(commands, events, projects);

        assertEquals(CustomUICommandType.Clear, commands.getCommands()[0].type);
        assertEquals("#TelemetryConsentRows", commands.getCommands()[0].selector);
        long appendedRows = java.util.Arrays.stream(commands.getCommands())
                .filter(command -> command.type == CustomUICommandType.Append)
                .filter(command -> "#TelemetryConsentRows".equals(command.selector))
                .filter(command -> "TelemetryConsentProjectRow.ui".equals(command.text))
                .count();
        assertEquals(11, appendedRows);
        assertTrue(java.util.Arrays.stream(commands.getCommands())
                .anyMatch(command -> "#TelemetryConsentRows[10] #TelemetryConsentProjectName.Text".equals(command.selector)));
        assertTrue(java.util.Arrays.stream(events.getEvents())
                .anyMatch(event -> "#TelemetryConsentRows[10] #TelemetryConsentProjectToggleButton".equals(event.selector)
                        && event.data != null
                        && event.data.contains("project-10")));
    }

    private static TelemetryConsentViewModel.ProjectRow project(String projectId) {
        TelemetryConsentSnapshot enabled = new TelemetryConsentSnapshot(
                true, true, true, true, true, true, true, true
        );
        return new TelemetryConsentViewModel.ProjectRow(
                projectId,
                "Project " + projectId,
                false,
                "hosted",
                0,
                "Example:" + projectId,
                "1.0.0",
                null,
                null,
                "embedded",
                enabled,
                enabled
        );
    }
}
