package com.alechilles.beacon.coordinator;

import java.util.Map;

/**
 * JDK-only bridge exposed by one process-wide project contribution.
 *
 * <p>No Telemetry runtime value types cross this boundary. Implementations may live in an
 * isolated plugin classloader and are invoked reflectively by
 * {@link TelemetryProjectContributionRegistry}.</p>
 */
public interface TelemetryProjectContributionBridge {

    int contributionAbiVersion();

    Map<String, Object> candidate();

    Map<String, Object> dispatch(String token,
                                 String operation,
                                 Map<String, Object> payload,
                                 Throwable throwable);
}
