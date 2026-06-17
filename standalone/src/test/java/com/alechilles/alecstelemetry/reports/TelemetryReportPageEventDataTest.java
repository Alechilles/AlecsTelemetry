package com.alechilles.alecstelemetry.reports;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TelemetryReportPageEventDataTest {

    @Test
    void parsesBooleanValuesWithoutTreatingSelectorsAsBooleans() {
        assertEquals(Boolean.TRUE, TelemetryReportPage.parseUiBoolean("true"));
        assertEquals(Boolean.FALSE, TelemetryReportPage.parseUiBoolean("false"));
        assertNull(TelemetryReportPage.parseUiBoolean("#TelemetryReportRoot #TelemetryReportLoadedMods.Value"));
        assertNull(TelemetryReportPage.parseUiBoolean(""));
    }
}
