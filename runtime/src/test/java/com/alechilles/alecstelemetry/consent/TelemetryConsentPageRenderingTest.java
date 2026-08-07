package com.alechilles.alecstelemetry.consent;

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
    void rendersAndBindsProjectsBeyondTheFormerEightRowLimit() {
        List<TelemetryConsentViewModel.ProjectRow> projects = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            projects.add(project("project-" + index));
        }
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();

        TelemetryConsentPage.renderProjectRows(commands, events, projects);

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
