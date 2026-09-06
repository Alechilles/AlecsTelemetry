package com.alechilles.beacon.consent;

import com.alechilles.beacon.runtime.TelemetryConsentRuntime;
import com.alechilles.beacon.runtime.TelemetryRuntimeDiagnostics;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TelemetryConsentPageToggleTest {
    private boolean supportsTelemetry = true;
    private TelemetryConsentSnapshot state = new TelemetryConsentSnapshot(
            true, true, false, false, false, false, false, true, false);

    private final TelemetryConsentRuntime runtime = (TelemetryConsentRuntime) Proxy.newProxyInstance(
            TelemetryConsentRuntime.class.getClassLoader(), new Class<?>[]{TelemetryConsentRuntime.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "consentProjectDiagnostics" -> diagnostics();
                case "consentDiagnostics" -> new TelemetryRuntimeDiagnostics(
                        true, 1, 1, 0, false, "", null, List.of(), List.of(diagnostics()));
                case "applyConsent" -> {
                    state = (TelemetryConsentSnapshot) args[1];
                    yield true;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });

    @Test
    void rowToggleSwitchesEverySupportedCategoryTogether() {
        TelemetryConsentPage page = new TelemetryConsentPage(null, runtime, false);
        page.toggleProject("example", false);
        assertEquals(new TelemetryConsentSnapshot(false, false, false, false, false, false, false, false, false), state);

        page.toggleProject("example", true);
        assertEquals(new TelemetryConsentSnapshot(true, true, false, false, false, false, false, true, false), state);
    }

    @Test
    void categoryChangesKeepProjectGateInSyncIncludingReenablingFromAllOff() {
        TelemetryConsentPage page = new TelemetryConsentPage(null, runtime, false);
        page.toggleCategory("example", "crash", false);
        assertTrue(state.projectEnabled());
        page.toggleCategory("example", "stats", false);
        assertFalse(state.projectEnabled());
        page.toggleCategory("example", "stats", true);
        assertTrue(state.projectEnabled());
        assertTrue(state.statsEnabled());
        assertFalse(state.crashEnabled());
    }

    @Test
    void globalCategoryCanEnableAProjectAfterItsRowWasDisabled() {
        TelemetryConsentPage page = new TelemetryConsentPage(null, runtime, false);
        page.toggleProject("example", false);
        page.toggleGlobalCategory("stats", true);
        assertEquals(new TelemetryConsentSnapshot(true, false, false, false, false, false, false, true, false), state);
        page.toggleGlobalCategory("stats", false);
        assertFalse(state.projectEnabled());
        assertFalse(state.statsEnabled());
    }

    @Test
    void projectWithoutTelemetryTypesCanStillBeDisabled() {
        supportsTelemetry = false;
        state = new TelemetryConsentSnapshot(true, false, false, false, false, false, false, false, false);
        assertRenderedEnabled(true);
        new TelemetryConsentPage(null, runtime, false).toggleProject("example", false);
        assertFalse(state.projectEnabled());
        assertRenderedEnabled(false);
    }

    @Test
    void enabledColumnReflectsSelectedTypesInsteadOfSeparateProjectFlag() {
        state = new TelemetryConsentSnapshot(true, false, false, false, false, false, false, false, false);
        assertRenderedEnabled(false);
        state = state.withCategory("stats", true);
        assertRenderedEnabled(true);
    }

    private void assertRenderedEnabled(boolean expected) {
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        TelemetryConsentPage.renderProjectRows(commands, events,
                TelemetryConsentViewModel.from(runtime.consentDiagnostics()).projects());
        var command = Arrays.stream(commands.getCommands())
                .filter(value -> "#TelemetryConsentRows[0] #TelemetryConsentProjectEnabled.Value".equals(value.selector))
                .findFirst().orElseThrow();
        assertEquals(expected, com.google.gson.JsonParser.parseString(command.data).getAsJsonObject().get("0").getAsBoolean());
        var toggle = Arrays.stream(events.getEvents())
                .filter(value -> "#TelemetryConsentRows[0] #TelemetryConsentProjectToggleButton".equals(value.selector))
                .findFirst().orElseThrow();
        assertEquals(Boolean.toString(!expected), com.google.gson.JsonParser.parseString(toggle.data)
                .getAsJsonObject().get("EnabledLiteral").getAsString());
    }

    private TelemetryRuntimeDiagnostics.ProjectDiagnostics diagnostics() {
        return new TelemetryRuntimeDiagnostics.ProjectDiagnostics(
                "example", "Example", state.projectEnabled(), true, "hosted", null, 0,
                "Example:Mod", "1.0", null, null, List.of(), "embedded",
                state.crashEnabled(), state.errorEnabled(), state.diagnosticsEnabled(), state.lifecycleEnabled(),
                state.performanceEnabled(), state.usageEnabled(), state.statsEnabled(), state.breadcrumbsEnabled(),
                supportsTelemetry, false, false, false, false, false, supportsTelemetry, false);
    }
}

